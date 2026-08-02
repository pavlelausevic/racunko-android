package com.racunko.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile

/**
 * v1.5.0-rc1 — a single file-access model: SAF.
 *
 * All bills/confirmations/QRs live under a `Racunko` container inside one
 * user-granted tree (normally the public Download folder):
 *
 *   <granted>/Racunko/Racuni    → renamed bill PDFs/images
 *   <granted>/Racunko/Potvrde   → renamed confirmation PDFs/images
 *   <granted>/Racunko           → QR PNGs (the stored copy; Gallery additionally
 *                                 publishes a gallery-visible copy to
 *                                 Pictures/Racunko via MediaStore.Images)
 *
 * MediaStore is no longer a storage/enumeration backend — its only remaining
 * use is publishing that one gallery-visible QR image (see [Gallery]). The tree
 * grant is the single source of truth for every read/write/rename/delete.
 */
enum class FileKind { RACUN, POTVRDA }

data class StoredFile(val uri: Uri, val name: String, val lastModified: Long)

object RacunkoTree {
    /** Human-readable default location, shown in onboarding / Settings. */
    const val LABEL = "Download/Racunko"
    /** MediaStore.Images RELATIVE_PATH for the gallery-visible QR copy. */
    const val PICTURES_REL = "Pictures/Racunko"
}

interface FileStore {
    /** Human-readable active location, shown in Settings. */
    val locationLabel: String

    fun list(kind: FileKind): List<StoredFile>

    fun existingNames(kind: FileKind): Set<String>

    /** In-place rename; returns the (possibly unchanged) uri, or null on failure. */
    fun rename(uri: Uri, currentName: String, newName: String, kind: FileKind): Uri?

    /** Stream-copy an external uri into the kind's folder under [name]. */
    fun importFile(kind: FileKind, src: Uri, name: String, mime: String): StoredFile?

    /** Delete a file we own; returns true on success. Failures are non-fatal. */
    fun delete(uri: Uri): Boolean
}

/**
 * Fallback used only before onboarding binds a real tree. Never touches disk;
 * the UI blocks all file actions until a grant exists, so this is inert.
 */
object NoopStore : FileStore {
    override val locationLabel: String get() = RacunkoTree.LABEL
    override fun list(kind: FileKind): List<StoredFile> = emptyList()
    override fun existingNames(kind: FileKind): Set<String> = emptySet()
    override fun rename(uri: Uri, currentName: String, newName: String, kind: FileKind): Uri? = null
    override fun importFile(kind: FileKind, src: Uri, name: String, mime: String): StoredFile? = null
    override fun delete(uri: Uri): Boolean = false
}

// ----------------------------------------------------------------- SAF tree

/**
 * The granted tree. Subfolder resolution follows the create-once discipline:
 * [LazyOnce] guarantees `ensureFolders` runs at most once per session — never
 * per processed file (root cause of the v1.2 duplicate-folders bug).
 */
class SafStore(private val context: Context, private val treeUri: Uri) : FileStore {

    private val saf = SafRepository(context)
    private val folders = LazyOnce { saf.ensureFolders(treeUri) }

    fun isAccessible(): Boolean = folders.get() != null

    /** The `Racunko` container — where QR PNGs are stored (Change 2). */
    fun containerFolder(): DocumentFile? = folders.get()?.first

    private fun folder(kind: FileKind): DocumentFile? = folders.get()?.let {
        if (kind == FileKind.RACUN) it.second else it.third
    }

    override val locationLabel: String
        get() {
            val seg = treeUri.lastPathSegment?.substringAfterLast(':')?.ifEmpty { null }
                ?: return RacunkoTree.LABEL
            // rc2: the grant is normally Download/Racunko itself — don't append Racunko twice.
            return if (seg.substringAfterLast('/').equals(SafRepository.CONTAINER, ignoreCase = true)) seg
            else "$seg/${SafRepository.CONTAINER}"
        }

    override fun list(kind: FileKind): List<StoredFile> =
        folder(kind)?.let { dir ->
            // Images are valid bill inputs too (photographed uplatnica), so list
            // them in both folders — not PDF-only for Racuni.
            saf.listDocuments(dir, includeImages = true).map {
                StoredFile(it.uri, it.name ?: "?", it.lastModified())
            }
        } ?: emptyList()

    override fun existingNames(kind: FileKind): Set<String> =
        folder(kind)?.listFiles()?.mapNotNull { it.name }?.toSet() ?: emptySet()

    override fun rename(uri: Uri, currentName: String, newName: String, kind: FileKind): Uri? {
        if (currentName == newName) return uri
        return saf.rename(uri, newName)
    }

    override fun importFile(kind: FileKind, src: Uri, name: String, mime: String): StoredFile? {
        val dir = folder(kind) ?: return null
        val doc = saf.copyIntoFolder(src, dir, name, mime) ?: return null
        return StoredFile(doc.uri, doc.name ?: name, doc.lastModified())
    }

    override fun delete(uri: Uri): Boolean = try {
        DocumentFile.fromSingleUri(context, uri)?.delete() ?: false
    } catch (_: Exception) {
        false
    }

    /**
     * Writes a QR PNG into the `Racunko` container; returns its uri or null.
     * [fileName] must include the `.png` extension. A stale same-named file is
     * removed first so QR regeneration stays idempotent.
     */
    fun savePng(bitmap: Bitmap, fileName: String): Uri? {
        val dir = containerFolder() ?: return null
        dir.findFile(fileName)?.takeIf { it.isFile }?.let { runCatching { it.delete() } }
        val doc = dir.createFile("image/png", fileName) ?: return null
        return try {
            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: throw IllegalStateException("no output")
            doc.uri
        } catch (_: Exception) {
            runCatching { doc.delete() }
            null
        }
    }
}

// ------------------------------------------------------------------ manager

/** The single [FileStore], backed by the persisted SAF tree grant. */
class StorageManager(private val context: Context) {

    @Volatile
    private var saf: SafStore? = null

    fun store(): FileStore = saf ?: NoopStore

    /** The SAF store when a tree is bound — needed for QR writes into the tree. */
    fun racunkoStore(): SafStore? = saf

    fun isReady(): Boolean = saf != null

    /**
     * v1.5.0-rc3: create `Download/Racunko` up-front via MediaStore (API 29+, no
     * permission) so the folder EXISTS before onboarding. The SAF grant dialog can
     * then land on it with an ENABLED "Use this folder" button (the Downloads ROOT
     * would be greyed). Best-effort and idempotent — leaves one small readme so the
     * folder is non-empty/visible; if it already exists, does nothing.
     */
    fun ensurePublicFolder() {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val already = runCatching {
            resolver.query(
                collection, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("Download/Racunko/%"), null
            )?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
        if (already) return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "O_fascikli.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Racunko")
        }
        runCatching {
            resolver.insert(collection, values)?.let { uri ->
                resolver.openOutputStream(uri)?.use {
                    it.write("Racunko cuva vase racune i potvrde u ovoj fascikli (Racuni, Potvrde).".toByteArray())
                }
            }
        }
    }

    /** Binds the granted tree; false when it's inaccessible (permission lost). */
    fun setTree(treeUri: Uri?): Boolean {
        if (treeUri == null) {
            saf = null
            return true
        }
        val s = SafStore(context, treeUri)
        if (!s.isAccessible()) return false
        saf = s
        return true
    }
}
