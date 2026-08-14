package com.racunko.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

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
    /** Human-readable label for the OPTIONAL visible folder, shown in Settings. */
    const val LABEL = "Download/Racunko"
    /** What Settings says when storage is the app's own (the default). */
    const val PRIVATE_LABEL = "u aplikaciji"
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

// -------------------------------------------------------------- private store

/**
 * v1.7 — THE DEFAULT STORE. Bills and confirmations live in the app's own
 * `filesDir`, so a person installs Računko and it works: no permission, no
 * grant dialog, no first-run screen, and nothing of theirs visible to any other
 * app. The visible folder still exists as an OPTION (see the SAF store below),
 * because a folder you can open is what makes an archive feel owned — but it is
 * a choice now, not the price of entry.
 *
 *   filesDir/racuni/    → renamed bill PDFs/images
 *   filesDir/potvrde/   → renamed confirmation PDFs/images
 *
 * Simpler than [SafStore] on purpose: plain `java.io.File`, no document tree, no
 * grant that can be revoked. Uris are `file://` — every reader in the app goes
 * through `ContentResolver`, which handles that scheme; the one place that
 * cannot is an outgoing share, where the file must be handed over as a
 * `FileProvider` uri instead (`MainViewModel.shareableUri`).
 *
 * THE TRADE this makes: uninstalling the app deletes the archive. That is why
 * export/import is not optional and must ship in the same release.
 */
class PrivateStore(private val context: Context) : FileStore {

    private fun dir(kind: FileKind): File {
        val name = if (kind == FileKind.RACUN) "racuni" else "potvrde"
        return File(context.filesDir, name).apply { mkdirs() }
    }

    /** Both folders, for the exporter and for whole-archive operations. */
    fun folders(): List<File> = listOf(dir(FileKind.RACUN), dir(FileKind.POTVRDA))

    private fun fileOf(uri: Uri): File? = uri.path?.let { File(it) }

    override val locationLabel: String get() = RacunkoTree.PRIVATE_LABEL

    override fun list(kind: FileKind): List<StoredFile> =
        dir(kind).listFiles()?.filter { it.isFile }
            ?.map { StoredFile(Uri.fromFile(it), it.name, it.lastModified()) }
            ?: emptyList()

    override fun existingNames(kind: FileKind): Set<String> =
        dir(kind).listFiles()?.filter { it.isFile }?.map { it.name }?.toSet() ?: emptySet()

    override fun rename(uri: Uri, currentName: String, newName: String, kind: FileKind): Uri? {
        if (currentName == newName) return uri
        val src = fileOf(uri) ?: return null
        val dst = File(dir(kind), newName)
        if (!src.exists() || dst.exists()) return null
        return if (src.renameTo(dst)) Uri.fromFile(dst) else null
    }

    override fun importFile(kind: FileKind, src: Uri, name: String, mime: String): StoredFile? {
        val dst = File(dir(kind), name)
        return try {
            context.contentResolver.openInputStream(src)?.use { input ->
                dst.outputStream().use { input.copyTo(it) }
            } ?: return null
            StoredFile(Uri.fromFile(dst), dst.name, dst.lastModified())
        } catch (_: Exception) {
            runCatching { dst.delete() }
            null
        }
    }

    override fun delete(uri: Uri): Boolean =
        runCatching { fileOf(uri)?.delete() ?: false }.getOrDefault(false)
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

    /** Locate a file by name in one of the two folders — the mirror needs this. */
    fun uriFor(kind: FileKind, name: String): Uri? =
        folder(kind)?.findFile(name)?.takeIf { it.isFile }?.uri

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

// ------------------------------------------------------------------- mirror

/**
 * v1.7 step 4 — „Čuvaj i kopiju u mojoj fascikli".
 *
 * The app's own storage stays the source of truth; the visible folder is a
 * SHADOW of it. Reads never touch the mirror, so a revoked grant or a folder the
 * user emptied by hand can slow nothing down and lose nothing. Writes are
 * best-effort: if the mirror fails, the archive is still correct and the only
 * consequence is that the folder is stale until the next export.
 *
 * Wrapping the primary is what keeps this honest — every path that writes goes
 * through `FileStore`, so there is no second place to remember to mirror from.
 */
private class MirrorStore(
    private val primary: PrivateStore,
    private val mirror: SafStore
) : FileStore {

    override val locationLabel: String get() = primary.locationLabel

    override fun list(kind: FileKind): List<StoredFile> = primary.list(kind)
    override fun existingNames(kind: FileKind): Set<String> = primary.existingNames(kind)

    override fun rename(uri: Uri, currentName: String, newName: String, kind: FileKind): Uri? {
        val out = primary.rename(uri, currentName, newName, kind) ?: return null
        runCatching {
            mirror.uriFor(kind, currentName)?.let { mirror.rename(it, currentName, newName, kind) }
        }
        return out
    }

    override fun importFile(kind: FileKind, src: Uri, name: String, mime: String): StoredFile? {
        val out = primary.importFile(kind, src, name, mime) ?: return null
        // Copy from the file we just wrote, not from [src]: a one-shot content
        // uri may already be spent by the time the primary copy is done.
        runCatching { mirror.importFile(kind, out.uri, name, mime) }
        return out
    }

    override fun delete(uri: Uri): Boolean {
        val name = uri.lastPathSegment?.substringAfterLast('/')
        val out = primary.delete(uri)
        if (name != null) runCatching {
            FileKind.entries.forEach { k -> mirror.uriFor(k, name)?.let { mirror.delete(it) } }
        }
        return out
    }
}

/**
 * v1.7: the store is ALWAYS backed by [PrivateStore]. A SAF tree, when the user
 * turns the optional copy on in Settings, only adds a mirror on top — never the
 * primary, and never something the app waits for. `store()` therefore has no
 * failure mode and no "not ready yet": there is nothing to grant and nothing to
 * lose.
 */
class StorageManager(private val context: Context) {

    private val private = PrivateStore(context)

    @Volatile
    private var saf: SafStore? = null

    fun store(): FileStore = saf?.let { MirrorStore(private, it) } ?: private

    /** The app's own storage, typed — the exporter needs the real folders. */
    fun privateStore(): PrivateStore = private

    /** The optional visible folder, when the user has turned it on. */
    fun racunkoStore(): SafStore? = saf

    /** Binds the optional tree; false when it's inaccessible (permission lost). */
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
