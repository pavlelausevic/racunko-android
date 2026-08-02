package com.racunko.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.racunko.app.parser.AddressEntry
import com.racunko.app.parser.SpaceBinding
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "racunko_settings")

data class AppSettings(
    /** v1.3 (Change 4): optional custom SAF tree; null = default Download/Racunko. */
    val customRootUri: String?,
    /** Pre-v1.3 0RACUNI tree, no longer used — only triggers the upgrade notice. */
    val hadLegacyRoot: Boolean,
    val legacyNoticeDismissed: Boolean,
    val addresses: List<AddressEntry>,
    val providerOverrides: Map<String, String>,
    val language: String,
    /** v1.4.7 Change 4: persisted OPEN_DOCUMENT_TREE grant for the Download folder. */
    val downloadsTreeUri: String?,
    /** v1.5.2 Change B1: sub-labels bound to per-space ids (InfoStan IDENT). */
    val spaceBindings: List<SpaceBinding>
)

class SettingsRepository(private val context: Context) {

    private val keyLegacyRoot = stringPreferencesKey("root_uri") // pre-v1.3
    private val keyCustomRoot = stringPreferencesKey("custom_root_uri")
    private val keyLegacyDismissed = stringPreferencesKey("legacy_notice_dismissed")
    private val keyAddresses = stringPreferencesKey("addresses")
    private val keyOverrides = stringPreferencesKey("provider_overrides")
    private val keyLanguage = stringPreferencesKey("language")
    private val keyDownloadsTree = stringPreferencesKey("downloads_tree_uri")
    private val keySpaceBindings = stringPreferencesKey("space_bindings")

    suspend fun load(): AppSettings {
        val prefs = context.dataStore.data.first()
        // v1.5.1 Change 3: no seed — an empty book shows the „Napravi šifarnik"
        // CTA instead of silently carrying demo entries the user never asked for.
        val addresses = prefs[keyAddresses]?.let { decodeAddresses(it) } ?: emptyList()
        val overrides = prefs[keyOverrides]?.let { decodeOverrides(it) } ?: emptyMap()
        return AppSettings(
            customRootUri = prefs[keyCustomRoot],
            hadLegacyRoot = prefs[keyLegacyRoot] != null,
            legacyNoticeDismissed = prefs[keyLegacyDismissed] == "1",
            addresses = addresses,
            providerOverrides = overrides,
            language = prefs[keyLanguage] ?: "system",
            downloadsTreeUri = prefs[keyDownloadsTree],
            spaceBindings = prefs[keySpaceBindings]?.let { decodeBindings(it) } ?: emptyList()
        )
    }

    suspend fun saveSpaceBindings(bindings: List<SpaceBinding>) {
        context.dataStore.edit { prefs ->
            prefs[keySpaceBindings] = bindings.joinToString("\n") {
                "${it.spaceId}\t${it.addressLabel}\t${it.subLabel}"
            }
        }
    }

    // One "spaceId<TAB>label<TAB>sub" binding per line.
    private fun decodeBindings(s: String): List<SpaceBinding> =
        s.split('\n').mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@mapNotNull null
            val id = parts[0].trim()
            val sub = parts[2].trim()
            if (id.isEmpty() || sub.isEmpty()) null
            else SpaceBinding(spaceId = id, addressLabel = parts[1].trim(), subLabel = sub)
        }

    suspend fun saveDownloadsTreeUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(keyDownloadsTree) else prefs[keyDownloadsTree] = uri
        }
    }

    suspend fun saveCustomRootUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(keyCustomRoot) else prefs[keyCustomRoot] = uri
        }
    }

    suspend fun dismissLegacyNotice() {
        context.dataStore.edit { it[keyLegacyDismissed] = "1" }
    }

    suspend fun saveAddresses(addresses: List<AddressEntry>) {
        context.dataStore.edit { it[keyAddresses] = encodeAddresses(addresses) }
    }

    suspend fun saveOverrides(overrides: Map<String, String>) {
        context.dataStore.edit { prefs ->
            prefs[keyOverrides] = overrides.entries.joinToString("\n") { "${it.key}\t${it.value}" }
        }
    }

    suspend fun saveLanguage(language: String) {
        context.dataStore.edit { it[keyLanguage] = language }
    }

    // One "label<TAB>pattern" pair per line; lines sharing a label form one entry.
    private fun encodeAddresses(addresses: List<AddressEntry>): String =
        addresses.flatMap { a -> a.patterns.map { p -> "${a.label}\t$p" } }.joinToString("\n")

    private fun decodeAddresses(s: String): List<AddressEntry> {
        val order = mutableListOf<String>()
        val map = mutableMapOf<String, MutableList<String>>()
        for (line in s.split('\n')) {
            val i = line.indexOf('\t')
            if (i <= 0) continue
            val label = line.substring(0, i).trim()
            val pattern = line.substring(i + 1).trim()
            if (label.isEmpty() || pattern.isEmpty()) continue
            if (label !in map) {
                map[label] = mutableListOf()
                order.add(label)
            }
            map.getValue(label).add(pattern)
        }
        return order.map { AddressEntry(it, map.getValue(it)) }
    }

    private fun decodeOverrides(s: String): Map<String, String> =
        s.split('\n').mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i <= 0) return@mapNotNull null
            val k = line.substring(0, i).trim()
            val v = line.substring(i + 1).trim()
            if (k.isEmpty() || v.isEmpty()) null else k to v
        }.toMap()
}
