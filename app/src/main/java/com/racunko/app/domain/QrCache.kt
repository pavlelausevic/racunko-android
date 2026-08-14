package com.racunko.app.domain

import android.content.Context
import java.io.File

/** A QR PNG plus how it was produced — see [QrCache] for why that matters. */
data class QrBytes(val png: ByteArray, val generated: Boolean) {
    // ByteArray in a data class: identity equals would make two identical codes
    // look different and defeat `remember(qrPng)` in the card.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is QrBytes && generated == other.generated && png.contentEquals(other.png))

    override fun hashCode(): Int = 31 * png.contentHashCode() + generated.hashCode()
}

/**
 * Disk cache for QR PNGs, in `cacheDir/qr`.
 *
 * A bill card shows its code as soon as it is opened, but the bytes live
 * nowhere: `CardRecordEntity` deliberately does not store the bitmap, so after a
 * cold start every visible card would have to re-derive it — a PdfRenderer pass
 * plus a decode each. That is the cost the storage round is not willing to pay
 * per card, so the result is kept here.
 *
 * `cacheDir` is the right home for it: no permission, private to the app,
 * nothing visible in a file explorer, and the system may evict it whenever it
 * needs the space. A miss is never an error — the bytes are simply derived
 * again.
 *
 * The `.gen` marker in the file name records HOW the code was produced. A code
 * rebuilt from the stored fields is not the issuer's own code (the payee name,
 * payment code and model are not reconstructed literally), so it has to carry
 * the verify-before-paying notice. Keeping that in the name means one file per
 * QR and no side metadata to fall out of sync.
 */
object QrCache {

    private const val GENERATED_MARKER = ".gen"

    /**
     * The reference number when the bill has one, else the file name. The
     * reference survives a rename; the file name does not, and callers that
     * rename a QR-less bill drop the stale entry themselves.
     */
    fun keyFor(roDigits: String, nameBase: String): String =
        (roDigits.ifBlank { nameBase })
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .take(80)
            .ifBlank { "unkeyed" }

    fun read(context: Context, key: String): QrBytes? {
        readVariant(context, key, generated = false)?.let { return it }
        return readVariant(context, key, generated = true)
    }

    fun write(context: Context, key: String, value: QrBytes) {
        runCatching {
            remove(context, key)
            fileFor(context, key, value.generated).writeBytes(value.png)
        }
    }

    /** Drops both variants — on rename, delete, or purge. */
    fun remove(context: Context, key: String) {
        runCatching { fileFor(context, key, generated = false).delete() }
        runCatching { fileFor(context, key, generated = true).delete() }
    }

    private fun readVariant(context: Context, key: String, generated: Boolean): QrBytes? {
        val f = fileFor(context, key, generated)
        val bytes = runCatching { if (f.isFile) f.readBytes() else null }.getOrNull()
        return if (bytes != null && bytes.isNotEmpty()) QrBytes(bytes, generated) else null
    }

    private fun fileFor(context: Context, key: String, generated: Boolean): File =
        File(
            File(context.cacheDir, "qr").apply { mkdirs() },
            key + (if (generated) GENERATED_MARKER else "") + ".png"
        )
}
