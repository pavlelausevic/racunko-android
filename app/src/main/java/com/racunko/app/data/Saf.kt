package com.racunko.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.racunko.app.parser.BillName

/** Storage Access Framework operations on the granted 0RACUNI tree. */
class SafRepository(private val context: Context) {

    fun takePersistablePermission(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    fun hasPermission(treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }

    /**
     * v1.5.0-rc1: the granted tree is normally the whole Download folder, so we
     * keep our files inside a `Racunko` container, then `Racuni`/`Potvrde` in it —
     * all created idempotently (findFile before createDirectory, the v1.3
     * duplicate-folder discipline, now on SAF). Returns (container, racuni,
     * potvrde) or null if the tree is inaccessible. If the user granted the
     * `Racunko` folder itself, we do NOT nest a second one.
     */
    fun ensureFolders(treeUri: Uri): Triple<DocumentFile, DocumentFile, DocumentFile>? {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (!root.isDirectory || !root.canWrite()) return null
        val container =
            if (root.name.equals(CONTAINER, ignoreCase = true)) root
            else root.findFile(CONTAINER)?.takeIf { it.isDirectory }
                ?: root.createDirectory(CONTAINER) ?: return null
        val racuni = container.findFile(RACUNI)?.takeIf { it.isDirectory }
            ?: container.createDirectory(RACUNI) ?: return null
        val potvrde = container.findFile(POTVRDE)?.takeIf { it.isDirectory }
            ?: container.createDirectory(POTVRDE) ?: return null
        return Triple(container, racuni, potvrde)
    }

    companion object {
        const val CONTAINER = "Racunko"
        const val RACUNI = "Racuni"
        const val POTVRDE = "Potvrde"
    }

    fun listPdfs(folder: DocumentFile): List<DocumentFile> =
        folder.listFiles()
            .filter { it.isFile && it.name?.endsWith(".pdf", ignoreCase = true) == true }
            .sortedByDescending { it.lastModified() }

    /** Confirmations may also be images (jpeg/png/webp) — OCR input (v1.2). */
    fun listDocuments(folder: DocumentFile, includeImages: Boolean): List<DocumentFile> =
        folder.listFiles()
            .filter { doc ->
                val n = doc.name?.lowercase() ?: return@filter false
                doc.isFile && (
                    n.endsWith(".pdf") || (includeImages &&
                        (n.endsWith(".jpg") || n.endsWith(".jpeg") ||
                            n.endsWith(".png") || n.endsWith(".webp")))
                    )
            }
            .sortedByDescending { it.lastModified() }

    /** Rename in place via DocumentsContract; returns the new uri or null on failure. */
    fun rename(documentUri: Uri, newName: String): Uri? = try {
        DocumentsContract.renameDocument(context.contentResolver, documentUri, newName)
    } catch (e: Exception) {
        null
    }

    fun uniqueNameIn(folder: DocumentFile, base: String, ext: String, ignoreCurrent: String? = null): String {
        val existing = folder.listFiles()
            .mapNotNull { it.name }
            .filter { it != ignoreCurrent }
            .toSet()
        return BillName.unique(base, ext, existing)
    }

    /** Copy an incoming shared stream into a folder (temporary timestamp name). */
    fun copyIntoFolder(src: Uri, folder: DocumentFile, name: String, mime: String): DocumentFile? {
        val target = folder.createFile(mime, name) ?: return null
        return try {
            context.contentResolver.openInputStream(src)?.use { input ->
                context.contentResolver.openOutputStream(target.uri)?.use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            target
        } catch (e: Exception) {
            try { target.delete() } catch (_: Exception) {}
            null
        }
    }
}
