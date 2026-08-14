package com.racunko.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.racunko.app.parser.AddressEntry
import com.racunko.app.parser.SpaceBinding
import org.json.JSONArray
import org.json.JSONObject

/**
 * v1.7 — export and import, the counterweight to private storage.
 *
 * Once the archive lives in `filesDir`, uninstalling the app takes it with it.
 * That is only acceptable because getting it out is a first-class action rather
 * than a recovery procedure, so export and import are deliberately symmetric:
 * whatever export writes, import can read back, on any device.
 *
 * **Files plus a manifest, not a ZIP.** The files stay files — openable,
 * previewable, and already self-describing thanks to the naming scheme, so the
 * folder is a live copy AND a backup at once. But a file name carries only
 * provider, address, month and amount; the database also holds what the name
 * cannot say — deadlines, reminders, which confirmation belongs to which bill,
 * per-space bindings, payee memory, the address book, custom provider names.
 * Those ride along in `racunko.json` beside the files.
 *
 * Import therefore reads the manifest, restores the rows, and reattaches them to
 * the files BY NAME. A folder with no manifest still imports — you get the files
 * and whatever their names encode, which is exactly what „Dodaj iz fajla" has
 * always given. A manifest with no files restores the memory and the settings.
 * Neither half is required for the other to be useful.
 *
 * `org.json` is used on purpose: it ships with Android, so a backup format does
 * not drag in a serialization dependency that a future reader would also need.
 */
object Archive {

    const val MANIFEST = "racunko.json"
    private const val VERSION = 1

    data class Payload(
        val bills: List<BillEntity>,
        val cards: List<CardRecordEntity>,
        val payees: List<PayeeProfileEntity>,
        val addresses: List<AddressEntry>,
        val providerOverrides: Map<String, String>,
        val customProviders: List<String>,
        val spaceBindings: List<SpaceBinding>
    )

    data class Result(val files: Int, val manifest: Boolean)

    // ------------------------------------------------------------------ export

    /**
     * Writes every bill and confirmation into [treeUri] plus the manifest.
     * Existing same-named files are replaced, so exporting twice into the same
     * folder updates it instead of growing it.
     */
    fun export(context: Context, treeUri: Uri, store: FileStore, payload: Payload): Result {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return Result(0, false)
        var count = 0
        for (kind in FileKind.entries) {
            val sub = childDir(root, if (kind == FileKind.RACUN) "Racuni" else "Potvrde")
                ?: continue
            for (f in store.list(kind)) {
                if (copyOut(context, f.uri, sub, f.name)) count++
            }
        }
        val ok = writeText(context, root, MANIFEST, toJson(payload).toString(2))
        return Result(count, ok)
    }

    private fun childDir(root: DocumentFile, name: String): DocumentFile? =
        root.findFile(name)?.takeIf { it.isDirectory } ?: root.createDirectory(name)

    private fun copyOut(context: Context, src: Uri, dir: DocumentFile, name: String): Boolean =
        runCatching {
            dir.findFile(name)?.takeIf { it.isFile }?.let { it.delete() }
            val doc = dir.createFile(mimeFor(name), name) ?: return false
            context.contentResolver.openInputStream(src)?.use { input ->
                context.contentResolver.openOutputStream(doc.uri)?.use { input.copyTo(it) }
                    ?: return false
            } ?: return false
            true
        }.getOrDefault(false)

    private fun writeText(context: Context, dir: DocumentFile, name: String, text: String): Boolean =
        runCatching {
            dir.findFile(name)?.takeIf { it.isFile }?.let { it.delete() }
            val doc = dir.createFile("application/json", name) ?: return false
            context.contentResolver.openOutputStream(doc.uri)?.use {
                it.write(text.toByteArray())
            } ?: return false
            true
        }.getOrDefault(false)

    private fun mimeFor(name: String): String = when {
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".webp", true) -> "image/webp"
        else -> "image/jpeg"
    }

    // ------------------------------------------------------------------ import

    /** Copies files from [treeUri] into the app and returns the manifest, if any. */
    fun importFiles(context: Context, treeUri: Uri, store: FileStore): Pair<Result, Payload?> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return Result(0, false) to null
        var count = 0
        // Accept both an exported folder (Racuni/Potvrde) and a flat one: bills
        // and confirmations are told apart by the naming scheme, and a person who
        // points at a folder of PDFs means "take these".
        val places = listOf(
            root.findFile("Racuni")?.takeIf { it.isDirectory } to FileKind.RACUN,
            root.findFile("Potvrde")?.takeIf { it.isDirectory } to FileKind.POTVRDA,
            root to null
        )
        for ((dir, forced) in places) {
            dir ?: continue
            for (doc in dir.listFiles()) {
                if (!doc.isFile) continue
                val name = doc.name ?: continue
                if (name.equals(MANIFEST, ignoreCase = true)) continue
                if (!looksImportable(name)) continue
                val kind = forced ?: kindOf(name)
                if (name in store.existingNames(kind)) continue
                if (store.importFile(kind, doc.uri, name, mimeFor(name)) != null) count++
            }
        }
        val manifest = root.findFile(MANIFEST)?.takeIf { it.isFile }?.let { doc ->
            runCatching {
                context.contentResolver.openInputStream(doc.uri)?.use {
                    fromJson(JSONObject(it.readBytes().decodeToString()))
                }
            }.getOrNull()
        }
        return Result(count, manifest != null) to manifest
    }

    private fun looksImportable(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".pdf") || n.endsWith(".jpg") || n.endsWith(".jpeg") ||
            n.endsWith(".png") || n.endsWith(".webp")
    }

    /** `uplata_` is the confirmation prefix the whole app already agrees on. */
    private fun kindOf(name: String): FileKind =
        if (name.startsWith("uplata_", ignoreCase = true)) FileKind.POTVRDA else FileKind.RACUN

    // -------------------------------------------------------------------- json

    private fun toJson(p: Payload): JSONObject = JSONObject().apply {
        put("version", VERSION)
        put("bills", JSONArray().apply {
            p.bills.forEach {
                put(JSONObject().apply {
                    put("roKey", it.roKey); put("altKey", it.altKey)
                    put("provider", it.provider); put("address", it.address)
                    putOpt("month", it.month); putOpt("year2", it.year2)
                    putOpt("amount", it.amount)
                    put("recipientAccount", it.recipientAccount)
                    put("finalName", it.finalName)
                    put("paired", it.paired); put("timestamp", it.timestamp)
                })
            }
        })
        put("cards", JSONArray().apply {
            p.cards.forEach {
                put(JSONObject().apply {
                    put("name", it.name); put("mode", it.mode)
                    put("provider", it.provider); put("address", it.address)
                    putOpt("month", it.month); putOpt("year2", it.year2)
                    putOpt("amount", it.amount)
                    put("roDigits", it.roDigits)
                    put("recipientAccount", it.recipientAccount)
                    put("accountVerified", it.accountVerified)
                    put("hasQr", it.hasQr); put("qrGenerated", it.qrGenerated)
                    put("matched", it.matched); put("paired", it.paired)
                    put("isImage", it.isImage); put("dismissed", it.dismissed)
                    put("timestamp", it.timestamp)
                    putOpt("dueDateEpochDay", it.dueDateEpochDay)
                    put("remindEnabled", it.remindEnabled)
                    put("remindDaysBefore", it.remindDaysBefore)
                    put("remindHour", it.remindHour); put("remindMinute", it.remindMinute)
                })
            }
        })
        put("payees", JSONArray().apply {
            p.payees.forEach {
                put(JSONObject().apply {
                    put("account", it.account); put("provider", it.provider)
                    put("addressLabel", it.addressLabel); put("displayName", it.displayName)
                    put("lastReferenceShape", it.lastReferenceShape)
                    put("updatedAt", it.updatedAt)
                })
            }
        })
        put("addresses", JSONArray().apply {
            p.addresses.forEach {
                put(JSONObject().apply {
                    put("label", it.label)
                    put("patterns", JSONArray(it.patterns))
                })
            }
        })
        put("providerOverrides", JSONObject(p.providerOverrides.toMap()))
        put("customProviders", JSONArray(p.customProviders))
        put("spaceBindings", JSONArray().apply {
            p.spaceBindings.forEach {
                put(JSONObject().apply {
                    put("spaceId", it.spaceId)
                    put("addressLabel", it.addressLabel)
                    put("subLabel", it.subLabel)
                })
            }
        })
    }

    private fun JSONObject.intOrNull(k: String): Int? = if (isNull(k)) null else optInt(k)
    private fun JSONObject.longOrNull(k: String): Long? = if (isNull(k)) null else optLong(k)

    private fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> =
        (0 until length()).mapNotNull { i -> runCatching { f(getJSONObject(i)) }.getOrNull() }

    private fun fromJson(o: JSONObject): Payload = Payload(
        bills = o.optJSONArray("bills")?.map { b ->
            BillEntity(
                roKey = b.getString("roKey"),
                altKey = b.optString("altKey"),
                provider = b.optString("provider"),
                address = b.optString("address"),
                month = b.intOrNull("month"),
                year2 = b.intOrNull("year2"),
                amount = b.longOrNull("amount"),
                recipientAccount = b.optString("recipientAccount"),
                finalName = b.optString("finalName"),
                // A gallery uri from another device means nothing here — the QR is
                // derived anyway, and a stale uri would make pairing try to delete
                // a stranger's file.
                qrImageUri = null,
                paired = b.optBoolean("paired"),
                timestamp = b.optLong("timestamp")
            )
        } ?: emptyList(),
        cards = o.optJSONArray("cards")?.map { c ->
            CardRecordEntity(
                name = c.getString("name"),
                mode = c.optString("mode", "RACUN"),
                // Rebound to the local file on import; a foreign uri is worthless.
                uri = "",
                provider = c.optString("provider"),
                address = c.optString("address"),
                month = c.intOrNull("month"),
                year2 = c.intOrNull("year2"),
                amount = c.longOrNull("amount"),
                roDigits = c.optString("roDigits"),
                recipientAccount = c.optString("recipientAccount"),
                accountVerified = c.optBoolean("accountVerified"),
                hasQr = c.optBoolean("hasQr"),
                qrGenerated = c.optBoolean("qrGenerated"),
                qrImageUri = null,
                matched = c.optBoolean("matched"),
                paired = c.optBoolean("paired"),
                isImage = c.optBoolean("isImage"),
                dismissed = c.optBoolean("dismissed"),
                timestamp = c.optLong("timestamp"),
                dueDateEpochDay = c.longOrNull("dueDateEpochDay"),
                remindEnabled = c.optBoolean("remindEnabled", true),
                remindDaysBefore = c.optInt("remindDaysBefore", 3),
                remindHour = c.optInt("remindHour", 10),
                remindMinute = c.optInt("remindMinute", 0)
            )
        } ?: emptyList(),
        payees = o.optJSONArray("payees")?.map { p ->
            PayeeProfileEntity(
                account = p.getString("account"),
                provider = p.optString("provider"),
                addressLabel = p.optString("addressLabel"),
                displayName = p.optString("displayName"),
                lastReferenceShape = p.optString("lastReferenceShape"),
                updatedAt = p.optLong("updatedAt")
            )
        } ?: emptyList(),
        addresses = o.optJSONArray("addresses")?.map { a ->
            val pats = a.optJSONArray("patterns")
            AddressEntry(
                a.getString("label"),
                (0 until (pats?.length() ?: 0)).mapNotNull { pats?.optString(it) }
                    .filter { it.isNotBlank() }
            )
        } ?: emptyList(),
        providerOverrides = o.optJSONObject("providerOverrides")?.let { j ->
            j.keys().asSequence().associateWith { j.optString(it) }
        } ?: emptyMap(),
        customProviders = o.optJSONArray("customProviders")?.let { a ->
            (0 until a.length()).mapNotNull { a.optString(it) }.filter { it.isNotBlank() }
        } ?: emptyList(),
        spaceBindings = o.optJSONArray("spaceBindings")?.map { s ->
            SpaceBinding(
                s.getString("spaceId"),
                s.optString("addressLabel"),
                s.optString("subLabel")
            )
        } ?: emptyList()
    )
}
