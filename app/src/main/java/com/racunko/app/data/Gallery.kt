package com.racunko.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlin.math.max

/**
 * QR PNG storage (v1.5.0-rc1). The stored copy lives in the SAF `Racunko`
 * container (the single storage model). Because some devices' galleries do not
 * index files under `Download`, a second, gallery-visible copy is published via
 * `MediaStore.Images` into `Pictures/Racunko` — the one legitimate remaining
 * MediaStore use (image publishing, not app storage). The user must be able to
 * scan the QR from the gallery inside their bank app.
 *
 * The uris of all created copies are joined with '\n' into one string, stored on
 * the bill record; deleting a QR removes every copy (SAF + Pictures).
 */
object Gallery {

    private const val SEPARATOR = "\n"

    /**
     * Saves the QR PNG into the SAF tree AND publishes a gallery copy; returns
     * the joined uris or null. v1.4.6: when [caption] is given, the file name is
     * drawn in an added white band BELOW the code — never over the modules or
     * quiet zone — so a saved / shared QR is self-identifying.
     */
    fun save(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        safStore: SafStore?,
        caption: String? = null
    ): String? {
        // v1.4.7 Change 1: captioning is cosmetic — if it fails for any reason,
        // fall back to the plain QR so the core save is never broken.
        val out = if (caption.isNullOrBlank()) bitmap
        else runCatching { withCaption(bitmap, caption) }.getOrDefault(bitmap)
        val uris = mutableListOf<Uri>()
        // 1) canonical copy inside the granted SAF tree
        safStore?.savePng(out, "$displayName.png")?.let { uris.add(it) }
        // 2) gallery-visible copy in Pictures/Racunko (MediaStore.Images)
        insertPng(
            context, out, displayName,
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            RacunkoTree.PICTURES_REL
        )?.let { uris.add(it) }
        if (out != bitmap) out.recycle()
        return if (uris.isEmpty()) null else uris.joinToString(SEPARATOR)
    }

    /**
     * Writes the captioned PNG to [target] — the temp copy behind „Podeli QR",
     * which lives in `cacheDir/share` and is handed out through FileProvider.
     * Same caption band as a saved copy: a QR that lands in a bank app or a chat
     * has to say which bill it is.
     */
    fun writeCaptionedPng(bitmap: Bitmap, caption: String?, target: java.io.File): Boolean =
        runCatching {
            val out = if (caption.isNullOrBlank()) bitmap
            else runCatching { withCaption(bitmap, caption) }.getOrDefault(bitmap)
            target.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (out != bitmap) out.recycle()
            true
        }.getOrDefault(false)

    /** Adds a white bottom band with the file name; the QR itself is untouched. */
    private fun withCaption(qr: Bitmap, caption: String): Bitmap {
        val band = max(40, qr.width / 8)
        val result = Bitmap.createBitmap(qr.width, qr.height + band, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(qr, 0f, 0f, null)
        val paint = Paint().apply {
            color = Color.BLACK; isAntiAlias = true; textAlign = Paint.Align.CENTER
            textSize = band * 0.45f
        }
        // shrink to fit the width (leave side padding), never grow past the band
        val padded = qr.width * 0.92f
        while (paint.measureText(caption) > padded && paint.textSize > 8f) {
            paint.textSize -= 1f
        }
        val y = qr.height + band / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(caption, qr.width / 2f, y, paint)
        return result
    }

    /** Deletes every stored copy — SAF documents and MediaStore images alike. */
    fun delete(context: Context, joinedUris: String?) {
        joinedUris ?: return
        val resolver = context.contentResolver
        for (part in joinedUris.split(SEPARATOR)) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: continue
            try {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.deleteDocument(resolver, uri)
                } else {
                    resolver.delete(uri, null, null)
                }
            } catch (_: Exception) {
                // RecoverableSecurityException / already gone — keep going, the
                // record is cleared regardless.
                runCatching { resolver.delete(uri, null, null) }
            }
        }
    }

    private fun insertPng(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        collection: Uri,
        relativePath: String
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(collection, values)
        } catch (_: Exception) {
            null
        } ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: throw IllegalStateException("no output")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null, null
            )
            uri
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }
}
