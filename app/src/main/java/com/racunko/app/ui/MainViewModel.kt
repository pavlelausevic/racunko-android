package com.racunko.app.ui

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.racunko.app.R
import com.racunko.app.data.AppDb
import com.racunko.app.data.Archive
import com.racunko.app.data.FileKind
import com.racunko.app.data.Gallery
import com.racunko.app.data.RacunkoTree
import com.racunko.app.data.SafRepository
import com.racunko.app.data.SettingsRepository
import com.racunko.app.data.StorageManager
import com.racunko.app.data.StoredFile
import com.racunko.app.domain.CardItem
import com.racunko.app.domain.CardMode
import com.racunko.app.domain.Pipeline
import com.racunko.app.domain.QrCache
import com.racunko.app.parser.AddressEntry
import com.racunko.app.parser.BillName
import com.racunko.app.parser.Months
import com.racunko.app.parser.ProviderDetector
import com.racunko.app.parser.ProviderNames
import com.racunko.app.parser.Report
import com.racunko.app.parser.ReportLine
import com.racunko.app.parser.SpaceBinding
import com.racunko.app.parser.SpaceNaming
import com.racunko.app.parser.registry.DocType
import com.racunko.app.parser.registry.IntakeAction
import com.racunko.app.parser.registry.IntakeGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FileRow(
    val uriString: String,
    val name: String,
    /** v1.5.1 Change 2: already processed → „već obrađen" tag, checkbox disabled. */
    val processed: Boolean,
    val lastModified: Long
)

/** v1.5.2 Change A: one picked/shared file awaiting the user's type decision. */
data class PendingIntake(
    val uriString: String,
    val action: IntakeAction,
    /** The pre-highlighted choice (classifier's suggestion/lean), if any. */
    val suggested: CardMode?,
    /** True when it arrived via share-into (routes confirmations with focus). */
    val fromShare: Boolean
)

data class UiState(
    val loaded: Boolean = false,
    /** v1.3: true when a custom SAF tree overrides the default Download/Racunko. */
    val customLocation: Boolean = false,
    val locationLabel: String = RacunkoTree.LABEL,
    /** One-time upgrade notice: the old 0RACUNI structure is no longer used. */
    val legacyNotice: Boolean = false,
    val tab: Int = 0,
    val racuniFiles: List<FileRow> = emptyList(),
    val potvrdeFiles: List<FileRow> = emptyList(),
    val items: List<CardItem> = emptyList(),
    val addresses: List<AddressEntry> = emptyList(),
    val providerOverrides: Map<String, String> = emptyMap(),
    val language: String = "system",
    val busy: Boolean = false,
    val unpairedBills: List<String> = emptyList(),
    val focusItemId: String? = null,
    /** v1.4.2 Change 1: one-time-per-session notice after generating a QR. */
    val showQrDisclaimer: Boolean = false,
    /** v1.4.2 Change 5: bill cards selected for the summary report. */
    val reportSelection: Set<String> = emptySet(),
    /** The built report text, shown in a sheet with Kopiraj/Podeli. */
    val reportText: String? = null,
    /**
     * v1.5.2 Change A: files whose type check needs the user — a recognized
     * mismatch (warn with suggestion) or an unknown type (ask). Head is shown.
     */
    val pendingIntake: List<PendingIntake>? = null,
    /** v1.5.2 Change B1: sub-labels bound to per-space ids (InfoStan IDENT). */
    val spaceBindings: List<SpaceBinding> = emptyList(),
    /** v1.6: user-added provider names — extra chips for manual entry. */
    val customProviders: List<String> = emptyList(),
    /** v1.7: the OPTIONAL visible folder. null = storage is the app's own only. */
    val downloadsTreeUri: String? = null,
    /**
     * v1.7.2: the document being looked at, full screen, inside the app. Held as
     * state rather than passed down as a callback because cards already talk to
     * the view model directly, and the viewer has to survive recomposition of the
     * list underneath it.
     */
    val viewing: ViewedDoc? = null
)

/** A document open in the in-app viewer. */
data class ViewedDoc(val uri: String, val name: String)

class MainViewModel(
    app: Application,
    /** v1.6: survives process death, so a selection isn't lost by backgrounding. */
    private val saved: SavedStateHandle
) : AndroidViewModel(app) {

    /**
     * The selection is remembered by FILE NAME, not by card id: ids are minted
     * per session, while the name is the card's stable identity (it is the Room
     * primary key). On the way back the names are matched to the rebuilt cards.
     */
    private var savedSelectionNames: List<String>
        get() = saved.get<ArrayList<String>>(KEY_SELECTION) ?: emptyList()
        set(value) { saved[KEY_SELECTION] = ArrayList(value) }

    /** Single place that changes the selection, so persistence can't drift. */
    private fun applySelection(ids: Set<String>) {
        savedSelectionNames = _state.value.items.filter { it.id in ids }.map { it.currentName }
        _state.value = _state.value.copy(reportSelection = ids)
    }

    private val settings = SettingsRepository(app)
    private val dao = AppDb.get(app).bills()
    private val payeeDao = AppDb.get(app).payees()
    private val cardDao = AppDb.get(app).cards()
    private val saf = SafRepository(app)
    private val storage = StorageManager(app)
    private val pipeline = Pipeline(app, dao, payeeDao) { storage.racunkoStore() }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settings.load()
            // v1.7: storage is the app's own and always available — nothing to
            // grant, so the list loads on first launch with no screen in front of
            // it. A tree, if the user turned the optional visible copy on, is
            // bound quietly and never gates anything.
            val tree = s.downloadsTreeUri?.takeIf { saf.hasPermission(Uri.parse(it)) }
            val mirrored = tree != null &&
                withContext(Dispatchers.IO) { storage.setTree(Uri.parse(tree)) }
            _state.value = _state.value.copy(
                loaded = true,
                customLocation = false,
                locationLabel = storage.store().locationLabel,
                legacyNotice = false,
                addresses = s.addresses,
                providerOverrides = s.providerOverrides,
                language = s.language,
                downloadsTreeUri = if (mirrored) tree else null,
                spaceBindings = s.spaceBindings,
                customProviders = s.customProviders
            )
            refreshFiles()
            reconcileCards() // rebuild the card list from DB + the two folders
        }
    }

    /**
     * The user turned the optional visible copy on and picked a folder. The
     * archive is copied out once immediately — a mirror that starts empty and
     * only fills as new bills arrive would look like data loss.
     */
    fun onTreeGranted(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val bound = withContext(Dispatchers.IO) {
                runCatching { saf.takePersistablePermission(uri) }
                storage.setTree(uri) // ensureFolders creates the container idempotently
            }
            if (!bound) {
                _state.value = _state.value.copy(busy = false)
                toast(R.string.toast_folder_error)
                return@launch
            }
            settings.saveDownloadsTreeUri(uri.toString())
            val mirror = storage.racunkoStore()
            val copied = withContext(Dispatchers.IO) {
                var n = 0
                if (mirror != null) for (kind in FileKind.entries) {
                    for (f in storage.privateStore().list(kind)) {
                        if (f.name !in mirror.existingNames(kind) &&
                            mirror.importFile(kind, f.uri, f.name, "application/octet-stream") != null
                        ) n++
                    }
                }
                n
            }
            _state.value = _state.value.copy(
                downloadsTreeUri = uri.toString(),
                locationLabel = storage.store().locationLabel,
                busy = false
            )
            toast(R.string.toast_mirror_on, copied)
            refreshFiles()
        }
    }

    /**
     * Called on return-to-foreground, so anything that arrived while we were
     * backgrounded shows up by itself — no „Potraži", no button. There is no
     * readiness check any more: the app's own storage is always there.
     */
    fun onResume() {
        if (_state.value.loaded) viewModelScope.launch {
            refreshFiles()
            syncCards()
        }
    }

    // ------------------------------------------------ card persistence (Change 1)

    private fun CardItem.toRecord() = com.racunko.app.data.CardRecordEntity(
        name = currentName,
        mode = mode.name,
        uri = uri,
        provider = provider,
        address = address,
        month = month?.month,
        year2 = month?.year2,
        amount = amount,
        roDigits = roDigits,
        recipientAccount = recipientAccount,
        accountVerified = accountVerified,
        hasQr = hasQr,
        qrGenerated = qrGenerated,
        qrImageUri = qrImageUri,
        matched = matched,
        paired = paired,
        isImage = !currentName.endsWith(".pdf", true),
        dismissed = false,
        timestamp = System.currentTimeMillis(),
        addressHint = addressHint,
        dueDateEpochDay = dueDateEpochDay,
        remindEnabled = remindEnabled,
        remindDaysBefore = remindDaysBefore,
        remindHour = remindHour,
        remindMinute = remindMinute
    )

    private fun com.racunko.app.data.CardRecordEntity.toCardItem(
        freshUri: String,
        qrPng: ByteArray?
    ) = CardItem(
        id = "rec:$name",
        mode = if (mode == "POTVRDA") CardMode.POTVRDA else CardMode.RACUN,
        origName = name,
        uri = freshUri,
        provider = provider,
        address = address,
        month = if (month != null && year2 != null) com.racunko.app.parser.MonthYear(month, year2) else null,
        amount = amount,
        roDigits = roDigits,
        recipientAccount = recipientAccount,
        accountVerified = accountVerified,
        hasQr = hasQr,
        qrPng = qrPng,
        qrGenerated = qrGenerated,
        qrImageUri = qrImageUri,
        matched = matched,
        paired = paired,
        addressHint = addressHint,
        dueDateEpochDay = dueDateEpochDay,
        remindEnabled = remindEnabled,
        remindDaysBefore = remindDaysBefore,
        remindHour = remindHour,
        remindMinute = remindMinute,
        currentName = name
    )

    /**
     * Persist a processed card. Only an ERROR card is skipped.
     *
     * v1.7.1: the name used to be a condition — a card whose file could not be
     * renamed (`BillName.PROCESSED`) was never written down. That quietly punished
     * exactly the bill that needs the most help: one whose address could not be
     * proven keeps its original file name, so its pairing, its deadline and its
     * reminder were forgotten on the next start and the user had to redo work the
     * app had already done. Records are keyed by file name and `reconcileCards`
     * drops any whose file has vanished, so writing one down for an un-renamed
     * file costs nothing and leaves nothing dangling.
     */
    private fun persist(card: CardItem) {
        if (card.status == com.racunko.app.domain.CardStatus.ERROR) return
        viewModelScope.launch { cardDao.upsert(card.toRecord()) }
    }

    /** Persist current in-memory cards, then rebuild from DB + disk. */
    private suspend fun syncCards() {
        for (c in _state.value.items) {
            if (c.status != com.racunko.app.domain.CardStatus.ERROR) {
                runCatching { cardDao.upsert(c.toRecord()) }
            }
        }
        reconcileCards()
    }

    /**
     * Rebuild the card list from persisted records reconciled with the folder:
     * drop records whose file vanished, backfill processed files with no record,
     * refresh URIs, re-attach paired-confirmation sub-rows. Idempotent — cards
     * are keyed by file name, so it never duplicates.
     */
    private suspend fun reconcileCards() {
        val store = storage.store()
        val disk = withContext(Dispatchers.IO) { store.list(FileKind.RACUN) + store.list(FileKind.POTVRDA) }
        val uriByName = disk.associate { it.name to it.uri.toString() }

        for (r in cardDao.all()) if (r.name !in uriByName) cardDao.deleteByName(r.name)
        val have = cardDao.all().map { it.name }.toSet()
        for ((name, uri) in uriByName) {
            if (name !in have && BillName.PROCESSED.matches(name)) {
                BillName.parse(name)?.let { p ->
                    cardDao.upsert(
                        CardItem(
                            id = "rec:$name",
                            mode = if (p.confirmation) CardMode.POTVRDA else CardMode.RACUN,
                            origName = name, uri = uri,
                            provider = p.provider, address = p.address, month = p.month,
                            amount = p.amount, matched = p.confirmation, currentName = name
                        ).toRecord()
                    )
                }
            }
        }

        val remembered = savedSelectionNames.toSet()
        val inMem = _state.value.items.associateBy { it.currentName }
        var items = cardDao.all().filter { !it.dismissed }.mapNotNull { rec ->
            val uri = uriByName[rec.name] ?: return@mapNotNull null
            rec.toCardItem(uri, qrPng = inMem[rec.name]?.qrPng)
        }
        // paired confirmation → sub-row under its bill
        items = items.map { card ->
            if (card.mode == CardMode.RACUN && card.paired && card.pairedConfName == null) {
                val confName = uriByName.keys.firstOrNull {
                    it.substringBeforeLast('.') == "uplata_${card.nameBase}"
                }
                if (confName != null) card.copy(pairedConfName = confName, pairedConfUri = uriByName[confName])
                else card
            } else card
        }
        // v1.6: cards get fresh ids on a rebuild, so a selection that survived
        // process death is re-attached by file name.
        val selection =
            if (remembered.isEmpty()) _state.value.reportSelection
            else items.filter { it.currentName in remembered }.map { it.id }.toSet()
        _state.value = _state.value.copy(items = items, reportSelection = selection)
    }

    // ------------------------------------------------- storage (v1.5.0-rc1)

    /** Pick the folder the optional visible copy is written to. */
    fun chooseCustomLocation(uri: Uri) = onTreeGranted(uri)

    // ------------------------------------------------- izvoz / uvoz (v1.7)

    /**
     * Write the whole archive — files plus `racunko.json` — into a folder the
     * user picked. This is what makes private storage safe: the copy is theirs,
     * readable without Računko, and re-importable into it.
     */
    fun exportArchive(treeUri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val app = getApplication<Application>()
            val s = settings.load()
            val payload = Archive.Payload(
                bills = dao.all(),
                cards = cardDao.all(),
                payees = payeeDao.all(),
                addresses = s.addresses,
                providerOverrides = s.providerOverrides,
                customProviders = s.customProviders,
                spaceBindings = s.spaceBindings
            )
            val res = withContext(Dispatchers.IO) {
                runCatching { saf.takePersistablePermission(treeUri) }
                Archive.export(app, treeUri, storage.store(), payload)
            }
            _state.value = _state.value.copy(busy = false)
            if (res.manifest) toast(R.string.toast_exported, res.files)
            else toast(R.string.toast_folder_error)
        }
    }

    /**
     * Read a folder back in: copy the files the app does not already have, then
     * restore the rows the manifest carries. Both halves are optional — a folder
     * of plain PDFs still imports, and a manifest alone restores the memory.
     */
    fun importArchive(treeUri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val app = getApplication<Application>()
            val (res, payload) = withContext(Dispatchers.IO) {
                runCatching { saf.takePersistablePermission(treeUri) }
                Archive.importFiles(app, treeUri, storage.store())
            }
            if (payload != null) {
                // Rows first, then reconcile: reconcileCards() drops any record
                // whose file is missing, so an entry that arrived without its file
                // simply does not survive — no dangling cards.
                payload.bills.forEach { dao.upsert(it) }
                payload.cards.forEach { cardDao.upsert(it) }
                payload.payees.forEach { payeeDao.upsert(it) }
                if (payload.addresses.isNotEmpty()) settings.saveAddresses(payload.addresses)
                if (payload.providerOverrides.isNotEmpty()) settings.saveOverrides(payload.providerOverrides)
                if (payload.customProviders.isNotEmpty()) settings.saveCustomProviders(payload.customProviders)
                if (payload.spaceBindings.isNotEmpty()) settings.saveSpaceBindings(payload.spaceBindings)
                val s = settings.load()
                _state.value = _state.value.copy(
                    addresses = s.addresses,
                    providerOverrides = s.providerOverrides,
                    customProviders = s.customProviders,
                    spaceBindings = s.spaceBindings
                )
            }
            refreshFiles()
            reconcileCards()
            _state.value = _state.value.copy(busy = false)
            // „Uvezeno: 0 fajlova" is true and useless when what arrived was the
            // ADDRESS BOOK: the count is files only, and a manifest-only folder has
            // none. It read as „nothing happened" on a successful import.
            if (payload != null) toast(R.string.toast_imported_settings, res.files, payload.addresses.size)
            else toast(R.string.toast_imported, res.files)
        }
    }

    /**
     * Turn the optional visible copy off. Nothing is deleted — neither the
     * archive nor what is already in the folder. Turning a mirror off should not
     * be a way to lose files by accident.
     */
    fun resetLocation() {
        viewModelScope.launch {
            storage.setTree(null)
            settings.saveDownloadsTreeUri(null)
            _state.value = _state.value.copy(
                downloadsTreeUri = null,
                customLocation = false,
                locationLabel = storage.store().locationLabel
            )
            toast(R.string.toast_mirror_off)
        }
    }

    fun dismissLegacyNotice() {
        viewModelScope.launch {
            settings.dismissLegacyNotice()
            _state.value = _state.value.copy(legacyNotice = false)
        }
    }

    // -------------------------------------------------------------------- files

    fun refreshFiles() {
        viewModelScope.launch {
            val unpaired = dao.all().filter { !it.paired }.map { it.finalName }
            // v1.5.1 Change 2: reconcile against the persisted card records so the
            // block survives restarts. A list-only-deleted (dismissed) file stays
            // selectable — re-processing it is the way to bring its card back.
            val records = cardDao.all()
            val active = records.filterNot { it.dismissed }.map { it.name }.toSet()
            val dismissed = records.filter { it.dismissed }.map { it.name }.toSet()
            val store = storage.store()
            withContext(Dispatchers.IO) {
                val r = store.list(FileKind.RACUN).map { it.toRow(active, dismissed) }
                val p = store.list(FileKind.POTVRDA).map { it.toRow(active, dismissed) }
                _state.value = _state.value.copy(
                    racuniFiles = r, potvrdeFiles = p, unpairedBills = unpaired
                )
            }
        }
    }

    private fun StoredFile.toRow(activeRecords: Set<String>, dismissedRecords: Set<String>) = FileRow(
        uriString = uri.toString(),
        name = name,
        processed = when {
            name in dismissedRecords -> false
            name in activeRecords -> true
            else -> BillName.PROCESSED.matches(name)
        },
        lastModified = lastModified
    )

    // --------------------------------------------------------------- processing

    fun setTab(tab: Int) {
        // Clear the selection when switching tabs so it never spans both lists.
        _state.value = _state.value.copy(tab = tab)
        applySelection(emptySet())
    }

    /** v1.6: „Izaberi sve" — every card the current filters are showing. */
    fun selectAll(ids: List<String>) {
        applySelection(ids.toSet())
    }

    fun clearFocus() {
        _state.value = _state.value.copy(focusItemId = null)
    }

    fun processSelected(uriStrings: List<String>, mode: CardMode) {
        if (uriStrings.isEmpty()) {
            toast(R.string.toast_no_selection)
            return
        }
        viewModelScope.launch {
            val store = storage.store()
            val kind = if (mode == CardMode.RACUN) FileKind.RACUN else FileKind.POTVRDA
            _state.value = _state.value.copy(busy = true)
            val listed = withContext(Dispatchers.IO) { store.list(kind) }
            for (uriString in uriStrings) {
                val file = listed.firstOrNull { it.uri.toString() == uriString } ?: continue
                // v1.4.2 Change 3: route by actual type, not by assuming PDF — a JPG
                // selected from the folder list must go through OCR, in both tabs.
                val isImage = !file.name.endsWith(".pdf", true)
                processOne(file, mode, isImage)
            }
            // v1.4.7 Change 3: jump to the top so the newest results are visible.
            _state.value = _state.value.copy(busy = false, focusItemId = _state.value.items.firstOrNull()?.id)
            refreshFiles()
        }
    }

    /**
     * Primary entry point (v1.3 Change 1/2): the "+" pickers open at Downloads.
     * The picked file is copied via MediaStore into the correct subfolder; if
     * the source can be deleted (e.g. it sat in Downloads root), it is deleted
     * after a successful write — failures are ignored and the copy is kept.
     * Files already inside our folders are processed in place.
     */
    fun processPicked(uris: List<Uri>, mode: CardMode) {
        viewModelScope.launch {
            val store = storage.store()
            val kind = if (mode == CardMode.RACUN) FileKind.RACUN else FileKind.POTVRDA
            val app = getApplication<Application>()
            _state.value = _state.value.copy(busy = true)
            // v1.7.2: adding files says how many arrived, the same way importing a
            // folder does. Silence on success is what made a skipped file and a
            // finished one look identical.
            var takenIn = 0
            for (uri in uris) {
                val name = queryDisplayName(uri) ?: "fajl_${System.currentTimeMillis()}"
                val mime = app.contentResolver.getType(uri)
                    ?: if (name.endsWith(".pdf", true)) "application/pdf" else "image/jpeg"
                val isImage = mime.startsWith("image/")
                // Change 4: image bills (uplatnica photo) are allowed and routed
                // through OCR + QR-decode; confirmations already accept images.
                // already inside our folder? -> process in place
                val inPlace = withContext(Dispatchers.IO) {
                    store.list(kind).firstOrNull { it.name == name }
                }
                // v1.7.2: a name that is already in the archive means the import is
                // skipped and the copy we HAVE is reprocessed. That is the right
                // thing to do, but doing it in silence is what made this look like
                // data loss on device: no new card, the card that is there does not
                // visibly change, and the picked file stays in Downloads because
                // "move semantics" only deletes a source that was actually imported.
                // Three things a person reads as "the app ate my file". So say it.
                if (inPlace != null) toast(R.string.toast_already_in_archive, name)
                val file = inPlace ?: withContext(Dispatchers.IO) {
                    val imported = store.importFile(kind, uri, name, mime)
                    if (imported != null) {
                        // move semantics: best-effort delete of the source
                        runCatching {
                            android.provider.DocumentsContract.deleteDocument(app.contentResolver, uri)
                        }
                    }
                    imported
                } ?: continue
                if (inPlace == null) takenIn++
                processOne(file, mode, isImage)
            }
            // v1.4.7 Change 3: jump to the top so the newest results are visible.
            _state.value = _state.value.copy(busy = false, focusItemId = _state.value.items.firstOrNull()?.id)
            refreshFiles()
            // Only what was actually taken in. A file already in the archive has
            // said its piece above and must not be counted as newly imported.
            if (takenIn > 0) toast(R.string.toast_imported, takenIn)
        }
    }

    private suspend fun processOne(file: StoredFile, mode: CardMode, isImage: Boolean = false) {
        val s = _state.value
        val store = storage.store()
        val item = withContext(Dispatchers.IO) {
            if (mode == CardMode.RACUN) {
                if (isImage) pipeline.processImageBill(file, store, s.addresses, s.providerOverrides, s.spaceBindings)
                else pipeline.processBill(file, store, s.addresses, s.providerOverrides, s.spaceBindings)
            } else if (isImage) {
                val ocr = runCatching { pipeline.ocrImage(file.uri) }.getOrDefault("")
                pipeline.processConfirmation(file, store, s.addresses, s.providerOverrides, ocrText = ocr)
            } else {
                pipeline.processConfirmation(file, store, s.addresses, s.providerOverrides)
            }
        }
        // Change 3: a bill already paired in the DB gets its confirmation sub-row back
        val withSubRow = if (item.mode == CardMode.RACUN && item.paired) {
            val conf = withContext(Dispatchers.IO) {
                store.list(FileKind.POTVRDA).firstOrNull {
                    it.name.substringBeforeLast('.') == "uplata_${item.nameBase}"
                }
            }
            if (conf != null) item.copy(pairedConfName = conf.name, pairedConfUri = conf.uri.toString())
            else item
        } else item
        // A file that already has a card is being REPROCESSED, not added — from the
        // picker when the name is already in the archive, and from „Obradi" on the
        // folder list, which only ever runs on files that are already there. Cards
        // are keyed by file name (that is what `reconcileCards` and `cardDao` use),
        // so prepending a second one put the SAME bill in the list twice, with a
        // fresh id and no way to tell the copies apart. It also healed itself on the
        // next rebuild, which is worse, not better: the duplicate vanished on
        // restart and left the impression the app had lost something. Replace in
        // place — the card is not new, and its position in its address section is
        // not news either.
        val at = _state.value.items.indexOfFirst { it.currentName == withSubRow.currentName }
        _state.value = _state.value.copy(
            items = if (at >= 0) _state.value.items.toMutableList().also { it[at] = withSubRow }
            else listOf(withSubRow) + _state.value.items
        )
        persist(withSubRow)
        if (withSubRow.matched) {
            markBillCardPaired(
                withSubRow.currentName.removePrefix("uplata_").substringBeforeLast('.'),
                withSubRow.currentName,
                withSubRow.uri
            )
            toast(R.string.toast_paired, withSubRow.currentName)
        }
    }

    // ------------------------------------------- intake guard (v1.5.2 Change A)

    /**
     * ONE code path for manual add and share-into: classify first, then act.
     * A clear type that agrees with the intent (or share-in with no intent)
     * proceeds silently — including QR-less paper bills, which the classifier
     * recognizes by fingerprint; a recognized MISMATCH warns with a suggestion;
     * UNKNOWN asks „račun ili potvrda?". QR absence alone never triggers anything.
     */
    fun intake(uris: List<Uri>, intended: CardMode?) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.first { it.loaded }
            val app = getApplication<Application>()
            _state.value = _state.value.copy(busy = true)
            val proceedBills = mutableListOf<Uri>()
            val proceedConfs = mutableListOf<Uri>()
            val pending = mutableListOf<PendingIntake>()
            val intendedType = when (intended) {
                CardMode.RACUN -> DocType.BILL
                CardMode.POTVRDA -> DocType.CONFIRMATION
                null -> null
            }
            for (uri in uris) {
                val mime = app.contentResolver.getType(uri) ?: ""
                val name = queryDisplayName(uri).orEmpty()
                val isImage = mime.startsWith("image/") ||
                    name.lowercase().matches(Regex(".*\\.(jpg|jpeg|png|webp)$"))
                val guess = withContext(Dispatchers.IO) { pipeline.classifyDocument(uri, isImage) }
                when (IntakeGuard.decide(guess, intendedType)) {
                    IntakeAction.PROCEED -> {
                        val mode = intended
                            ?: if (guess.type == DocType.CONFIRMATION) CardMode.POTVRDA else CardMode.RACUN
                        if (mode == CardMode.RACUN) proceedBills.add(uri) else proceedConfs.add(uri)
                    }
                    IntakeAction.WARN_SUGGEST_CONFIRMATION -> pending.add(
                        PendingIntake(uri.toString(), IntakeAction.WARN_SUGGEST_CONFIRMATION,
                            suggested = CardMode.POTVRDA, fromShare = intended == null)
                    )
                    IntakeAction.WARN_SUGGEST_BILL -> pending.add(
                        PendingIntake(uri.toString(), IntakeAction.WARN_SUGGEST_BILL,
                            suggested = CardMode.RACUN, fromShare = intended == null)
                    )
                    IntakeAction.ASK_TYPE -> pending.add(
                        PendingIntake(
                            uri.toString(), IntakeAction.ASK_TYPE,
                            suggested = when (guess.lean) {
                                DocType.BILL -> CardMode.RACUN
                                DocType.CONFIRMATION -> CardMode.POTVRDA
                                else -> intended
                            },
                            fromShare = intended == null
                        )
                    )
                }
            }
            _state.value = _state.value.copy(
                busy = false,
                pendingIntake = pending.takeIf { it.isNotEmpty() }
            )
            if (proceedBills.isNotEmpty()) {
                if (intended == null) _state.value = _state.value.copy(tab = 0)
                processPicked(proceedBills, CardMode.RACUN)
            }
            if (proceedConfs.isNotEmpty()) {
                if (intended == null) receiveShared(proceedConfs)
                else processPicked(proceedConfs, CardMode.POTVRDA)
            }
        }
    }

    /** The user answered the head intake dialog: route the file as [asMode]. */
    fun resolveIntake(asMode: CardMode) {
        val head = _state.value.pendingIntake?.firstOrNull() ?: return
        popIntake()
        val uri = Uri.parse(head.uriString)
        if (asMode == CardMode.POTVRDA && head.fromShare) {
            receiveShared(listOf(uri))
        } else {
            if (asMode == CardMode.RACUN && head.fromShare) {
                _state.value = _state.value.copy(tab = 0)
            }
            processPicked(listOf(uri), asMode)
        }
    }

    /**
     * v1.7.2: look at the document itself, without leaving the app. The card can
     * say „adresa?" or „iznos?" and there was no way to check WHICH bill that is
     * short of handing the file to another app; a person who cannot see the paper
     * cannot correct the reading of it.
     */
    fun viewDocument(cardId: String) {
        val card = _state.value.items.firstOrNull { it.id == cardId } ?: return
        _state.value = _state.value.copy(viewing = ViewedDoc(card.uri, card.currentName))
    }

    /** Look at a file that has no card of its own — a paired confirmation. */
    fun viewFile(uri: String, name: String) {
        _state.value = _state.value.copy(viewing = ViewedDoc(uri, name))
    }

    fun closeViewer() {
        _state.value = _state.value.copy(viewing = null)
    }

    /** Otkaži on the head intake dialog — skip that file, show the next. */
    fun skipIntake() = popIntake()

    private fun popIntake() {
        val rest = _state.value.pendingIntake?.drop(1)
        _state.value = _state.value.copy(pendingIntake = rest?.takeIf { it.isNotEmpty() })
    }

    // ---------------------------------------------------------- share targets

    /** Share-into (v1.4.8 → v1.5.2): same classifier-guarded path as manual add. */
    fun onSharedIn(uris: List<Uri>) = intake(uris, intended = null)

    /**
     * Confirmations routed here from [resolveShare]: delivered to the existing
     * instance via onNewIntent; stays on Računi, focuses the paired bill.
     */
    fun receiveShared(uris: List<Uri>) {
        viewModelScope.launch {
            _state.first { it.loaded } // cold start via share: wait for settings
            val store = storage.store()
            // v1.4.1 Bug 2: do NOT force the Potvrde tab. Računi is primary; a paired
            // confirmation shows as a sub-row inside its bill card there.
            _state.value = _state.value.copy(busy = true)
            var focusBillId: String? = null
            var unpairedConfId: String? = null
            for (uri in uris) {
                val mime = getApplication<Application>().contentResolver.getType(uri) ?: "application/pdf"
                val isImage = mime.startsWith("image/")
                val ext = if (isImage) mime.substringAfterLast('/', "jpg") else "pdf"
                val name = "potvrda_${System.currentTimeMillis()}.$ext"
                val copied = withContext(Dispatchers.IO) {
                    store.importFile(FileKind.POTVRDA, uri, name, mime)
                } ?: continue
                val s = _state.value
                val item = withContext(Dispatchers.IO) {
                    if (isImage) {
                        val ocr = runCatching { pipeline.ocrImage(copied.uri) }.getOrDefault("")
                        pipeline.processConfirmation(copied, store, s.addresses, s.providerOverrides, ocrText = ocr)
                    } else {
                        pipeline.processConfirmation(copied, store, s.addresses, s.providerOverrides)
                    }
                }
                _state.value = _state.value.copy(items = listOf(item) + _state.value.items)
                persist(item)
                if (item.matched) {
                    val billName = item.currentName.removePrefix("uplata_").substringBeforeLast('.')
                    markBillCardPaired(billName, item.currentName, item.uri)
                    // focus the paired BILL on the Računi tab (its sub-row now shows the confirmation)
                    if (focusBillId == null) {
                        focusBillId = _state.value.items
                            .firstOrNull { it.mode == CardMode.RACUN && it.nameBase == billName }?.id
                    }
                    toast(R.string.toast_paired, item.currentName)
                } else {
                    if (unpairedConfId == null) unpairedConfId = item.id
                    toast(R.string.toast_received)
                }
            }
            // Stay on Računi and scroll to the paired bill; only fall back to
            // Potvrde when a confirmation could not be paired at all.
            val newTab = if (focusBillId != null) 0 else if (unpairedConfId != null) 1 else _state.value.tab
            _state.value = _state.value.copy(
                busy = false, tab = newTab, focusItemId = focusBillId ?: unpairedConfId
            )
            refreshFiles()
        }
    }

    // ------------------------------------------------------------------- edits

    fun applyEdit(id: String, field: String, raw: String) {
        val item = _state.value.items.firstOrNull { it.id == id } ?: return
        // A manual edit means the user owns this value — drop the "predloženo"
        // hint on the field they just touched (Change 6).
        var edited = when (field) {
            "provider" -> item.copy(provider = BillName.sanitizeToken(raw), providerSuggested = false)
            "address" -> item.copy(address = BillName.sanitizeToken(raw), addressSuggested = false)
            "month" -> item.copy(month = Months.fromToken(raw))
            "amount" -> item.copy(amount = raw.filter { it.isDigit() }.toLongOrNull())
            else -> return
        }
        edited = edited.copy(
            addrAmbiguous = if (field == "address") emptyList() else edited.addrAmbiguous,
            // an edited address token resolves the collision prompt (v1.5.2 B2)
            needsSpaceTag = if (field == "address") false else edited.needsSpaceTag
        )
        viewModelScope.launch {
            val store = storage.store()
            val result = withContext(Dispatchers.IO) { pipeline.applyEdits(edited, store) }
            replaceItem(result)
            if (result.currentName != item.currentName) {
                toast(R.string.toast_renamed, result.currentName)
            }
            refreshFiles()
        }
    }

    /**
     * v1.5.2 Change B2: apply a manual space tag to a colliding bill —
     * `SG26` → `SG26-G1` — and optionally bind it to the bill's spaceId
     * („Zapamti za ovaj prostor") so next month it applies automatically.
     */
    fun applySpaceTag(cardId: String, subRaw: String, remember: Boolean) {
        val item = _state.value.items.firstOrNull { it.id == cardId } ?: return
        val sub = SpaceNaming.sanitizeSub(subRaw)
        if (sub.isEmpty()) return
        val token = SpaceNaming.addressToken(item.address, sub)
        viewModelScope.launch {
            if (remember && item.spaceId.isNotBlank()) {
                val updated = _state.value.spaceBindings
                    .filterNot { it.spaceId == item.spaceId } + SpaceBinding(item.spaceId, item.address, sub)
                settings.saveSpaceBindings(updated)
                _state.value = _state.value.copy(spaceBindings = updated)
            }
            val edited = item.copy(address = token, needsSpaceTag = false, addrAmbiguous = emptyList())
            val store = storage.store()
            val result = withContext(Dispatchers.IO) { pipeline.applyEdits(edited, store) }
            replaceItem(result)
            if (result.currentName != item.currentName) {
                toast(R.string.toast_renamed, result.currentName)
            }
            refreshFiles()
        }
    }

    /**
     * v1.6: the bill's payment deadline and its own reminder. The deadline may
     * be cleared back to null — it is optional, and an empty one is a legitimate
     * final state, not an unfinished edit.
     */
    fun setDue(cardId: String, dueEpochDay: Long?, remind: Boolean, daysBefore: Int, hour: Int) {
        val item = _state.value.items.firstOrNull { it.id == cardId } ?: return
        replaceItem(
            item.copy(
                dueDateEpochDay = dueEpochDay,
                remindEnabled = remind,
                remindDaysBefore = daysBefore,
                remindHour = hour
            )
        )
    }

    /** Change 5b: „Napravi QR" — generate an IPS QR for a bill that lacks one. */
    fun generateQr(cardId: String) {
        val item = _state.value.items.firstOrNull { it.id == cardId } ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { pipeline.generateQr(item) }
            if (result !== item) {
                replaceItem(result)
                toast(R.string.toast_qr_generated)
                maybeShowQrDisclaimer() // Change 1: generated QR → verify-before-paying notice
            }
        }
    }

    // ---------------------------------------------------- QR disclaimer (Change 1)

    private var qrDisclaimerDismissed = false

    /** Shown once per session, only for GENERATED QR codes (issuer QR is trusted). */
    fun maybeShowQrDisclaimer() {
        if (!qrDisclaimerDismissed) _state.value = _state.value.copy(showQrDisclaimer = true)
    }

    fun dismissQrDisclaimer() {
        qrDisclaimerDismissed = true
        _state.value = _state.value.copy(showQrDisclaimer = false)
    }

    /** 8d: a live-scanned IPS QR becomes a bill card (generated artifact). */
    fun scanBill(payload: String) {
        viewModelScope.launch {
            val store = storage.store()
            _state.value = _state.value.copy(busy = true)
            val s = _state.value
            val card = withContext(Dispatchers.IO) {
                pipeline.processScannedQr(payload, store, s.addresses, s.providerOverrides, s.spaceBindings)
            }
            _state.value = _state.value.copy(
                items = listOf(card) + _state.value.items,
                busy = false, tab = 0, focusItemId = card.id
            )
            persist(card)
            if (card.qrGenerated) maybeShowQrDisclaimer()
            refreshFiles()
        }
    }

    // ------------------------------------------------------ summary report (Change 5)

    fun toggleReportSelection(cardId: String) {
        applySelection(
            _state.value.reportSelection.let {
                if (cardId in it) it - cardId else it + cardId
            }
        )
    }

    /**
     * Selects exactly one card, discarding whatever was selected before. The
     * „Ukloni" action on an unreadable card uses it: that card offers no other
     * action, so its button has to mean THAT card and nothing that happened to
     * be ticked earlier.
     */
    fun selectOnly(cardId: String) {
        applySelection(setOf(cardId))
    }

    fun clearReport() {
        _state.value = _state.value.copy(reportText = null)
        applySelection(emptySet())
    }

    /** Builds the grouped report from the selected, complete bill cards. */
    fun buildReport() {
        val sel = _state.value.reportSelection
        val lines = _state.value.items
            .filter { it.id in sel && it.mode == CardMode.RACUN && it.month != null && it.amount != null && it.address.isNotEmpty() }
            .map {
                ReportLine(
                    addressLabel = it.address,
                    month = it.month!!,
                    providerDisplay = ProviderNames.display(it.provider),
                    amount = it.amount!!
                )
            }
        if (lines.isEmpty()) return
        val separator = java.text.DecimalFormatSymbols.getInstance().groupingSeparator
        _state.value = _state.value.copy(reportText = Report.buildSummary(lines, separator))
    }

    fun copyReport() {
        val text = _state.value.reportText ?: return
        val app = getApplication<Application>()
        val cm = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Racunko", text))
        toast(R.string.toast_report_copied)
    }

    fun shareReport() {
        val text = _state.value.reportText ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(chooser)
    }

    // -------------------- selection action bar: share / delete (Change 2–4)

    /**
     * v1.4.5: „Podeli" on a single bill card — a PAID bill shares BOTH the bill
     * and its `uplata_…` confirmation (ACTION_SEND_MULTIPLE); an unpaired bill (or
     * a confirmation) shares its one file.
     */
    fun shareBill(cardId: String) {
        val c = _state.value.items.firstOrNull { it.id == cardId } ?: return
        val conf = c.pairedConfUri
        if (c.mode == CardMode.RACUN && c.paired && conf != null) {
            share(listOf(c.uri, conf), listOf(c.currentName, c.pairedConfName ?: ""), "application/pdf")
        } else {
            val mime = if (c.currentName.endsWith(".pdf", true)) "application/pdf" else "image/*"
            share(listOf(c.uri), listOf(c.currentName), mime)
        }
    }

    /**
     * „Podeli" (selected) — replaces „Podeli sve", scoped to selection. Pulls in
     * the paired confirmation of each selected paid bill (v1.4.5) and dedupes by
     * uri (a confirmation selected on its own is not attached twice); keeps each
     * bill adjacent to its confirmation.
     */
    fun shareSelectedCards() {
        val cards = _state.value.items.filter {
            it.id in _state.value.reportSelection && it.status != com.racunko.app.domain.CardStatus.ERROR
        }
        val byUri = LinkedHashMap<String, String>()
        for (c in cards) {
            byUri[c.uri] = c.currentName
            if (c.mode == CardMode.RACUN && c.paired && c.pairedConfUri != null) {
                byUri[c.pairedConfUri!!] = c.pairedConfName ?: ""
            }
        }
        if (byUri.isNotEmpty()) share(byUri.keys.toList(), byUri.values.toList(), "application/pdf")
    }

    /**
     * „Obriši" (selected). List-only by default (file kept, card marked
     * dismissed so backfill won't revive it); with [alsoFiles], deletes the bill
     * file, its paired confirmation, and the generated QR PNG(s) too.
     */
    fun deleteSelected(alsoFiles: Boolean) {
        val ids = _state.value.reportSelection
        if (ids.isEmpty()) return
        val cards = _state.value.items.filter { it.id in ids }
        viewModelScope.launch {
            val store = storage.store()
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                for (c in cards) {
                    if (alsoFiles) {
                        // The card's own file + every QR copy it made (Download + Pictures).
                        runCatching { store.delete(Uri.parse(c.uri)) }
                        Gallery.delete(app, c.qrImageUri ?: cardDao.byName(c.currentName)?.qrImageUri)
                        QrCache.remove(app, QrCache.keyFor(c.roDigits, c.nameBase))
                        // v1.4.8 Change 3: a paired confirmation is cleaned in full too —
                        // its file, its own QR (if any), and its list record.
                        c.pairedConfName?.let { confName ->
                            val confRec = cardDao.byName(confName)
                            c.pairedConfUri?.let { runCatching { store.delete(Uri.parse(it)) } }
                            Gallery.delete(app, confRec?.qrImageUri)
                            cardDao.deleteByName(confName)
                        }
                        cardDao.deleteByName(c.currentName)
                    } else {
                        cardDao.setDismissed(c.currentName)
                        c.pairedConfName?.let { cardDao.setDismissed(it) }
                    }
                }
            }
            applySelection(emptySet())
            reconcileCards()
            refreshFiles()
            toast(R.string.toast_deleted, cards.size)
        }
    }

    // ------------------------------------------- „Isprazni fasciklu" (Change 5)

    /** Deletes ALL bill/confirmation files + generated QR PNGs; clears records. */
    fun purgeAll(alsoPayees: Boolean) {
        viewModelScope.launch {
            val store = storage.store()
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                (store.list(FileKind.RACUN) + store.list(FileKind.POTVRDA)).forEach {
                    runCatching { store.delete(it.uri) }
                }
                cardDao.all().forEach {
                    Gallery.delete(app, it.qrImageUri)
                    QrCache.remove(app, QrCache.keyFor(it.roDigits, it.name.substringBeforeLast('.')))
                }
            }
            cardDao.clear()
            dao.clear()
            if (alsoPayees) payeeDao.clear()
            _state.value = _state.value.copy(items = emptyList(), unpairedBills = emptyList())
            applySelection(emptySet())
            refreshFiles()
            toast(R.string.toast_purged)
        }
    }

    // v1.5.1 Change 5: „Potraži u Download" removed — Racunko subfolders are
    // auto-detected, and Downloads-root files come in via „+ Dodaj račun".

    // -------------------------------------------------------- manual pairing

    /** Layer 3, from a confirmation card: tap a bill-name chip. */
    fun pairConfirmationWith(confId: String, billName: String) {
        val conf = _state.value.items.firstOrNull { it.id == confId } ?: return
        viewModelScope.launch {
            val store = storage.store()
            val confFile = StoredFile(Uri.parse(conf.uri), conf.currentName, 0L)
            val (newUri, newName) = withContext(Dispatchers.IO) {
                pipeline.pairManually(confFile, store, billName)
            }
            val entity = dao.byName(billName)
            replaceItem(
                conf.copy(
                    uri = newUri.toString(), currentName = newName, matched = true,
                    provider = entity?.provider ?: conf.provider,
                    address = entity?.address ?: conf.address,
                    month = entity?.month?.let { m -> entity.year2?.let { com.racunko.app.parser.MonthYear(m, it) } } ?: conf.month,
                    amount = entity?.amount ?: conf.amount,
                    pairCandidates = emptyList()
                )
            )
            markBillCardPaired(billName, newName, newUri.toString())
            toast(R.string.toast_paired, newName)
            refreshFiles()
        }
    }

    /** Layer 3, from a bill card (Change 3): a confirmation from the Potvrde folder. */
    fun attachConfirmationToBill(billId: String, confUriString: String) {
        val bill = _state.value.items.firstOrNull { it.id == billId } ?: return
        viewModelScope.launch {
            val store = storage.store()
            val conf = withContext(Dispatchers.IO) {
                store.list(FileKind.POTVRDA).firstOrNull { it.uri.toString() == confUriString }
            } ?: return@launch
            bindConfToBill(bill, conf)
        }
    }

    /** Change 3 primary flow: "+" on the bill card → system picker at Downloads. */
    fun attachPickedToBill(billId: String, srcUri: Uri) {
        val bill = _state.value.items.firstOrNull { it.id == billId } ?: return
        viewModelScope.launch {
            val store = storage.store()
            val app = getApplication<Application>()
            val name = queryDisplayName(srcUri) ?: "potvrda_${System.currentTimeMillis()}.pdf"
            val mime = app.contentResolver.getType(srcUri)
                ?: if (name.endsWith(".pdf", true)) "application/pdf" else "image/jpeg"
            val imported = withContext(Dispatchers.IO) {
                val i = store.importFile(FileKind.POTVRDA, srcUri, name, mime)
                if (i != null) {
                    runCatching {
                        android.provider.DocumentsContract.deleteDocument(app.contentResolver, srcUri)
                    }
                }
                i
            }
            if (imported == null) {
                toast(R.string.toast_folder_error)
                return@launch
            }
            bindConfToBill(bill, imported)
        }
    }

    private suspend fun bindConfToBill(bill: CardItem, conf: StoredFile) {
        val store = storage.store()
        val (newUri, newName) = withContext(Dispatchers.IO) {
            pipeline.pairManually(conf, store, bill.nameBase)
        }
        replaceItem(
            bill.copy(
                paired = true, qrImageUri = null,
                pairedConfName = newName, pairedConfUri = newUri.toString()
            )
        )
        val confItem = CardItem(
            id = "conf_${bill.id}", mode = CardMode.POTVRDA, origName = conf.name,
            uri = newUri.toString(), provider = bill.provider, address = bill.address,
            month = bill.month, amount = bill.amount, matched = true, currentName = newName
        )
        _state.value = _state.value.copy(items = listOf(confItem) + _state.value.items)
        persist(confItem)
        toast(R.string.toast_paired, newName)
        refreshFiles()
    }

    private fun markBillCardPaired(billName: String, confName: String?, confUri: String?) {
        val items = _state.value.items.map {
            if (it.mode == CardMode.RACUN && it.nameBase == billName) {
                it.copy(
                    paired = true, qrImageUri = null,
                    pairedConfName = confName ?: it.pairedConfName,
                    pairedConfUri = confUri ?: it.pairedConfUri
                )
            } else it
        }
        _state.value = _state.value.copy(items = items)
        items.filter { it.mode == CardMode.RACUN && it.nameBase == billName }.forEach { persist(it) }
    }

    private fun replaceItem(item: CardItem) {
        _state.value = _state.value.copy(
            items = _state.value.items.map { if (it.id == item.id) item else it }
        )
        persist(item)
    }

    // ------------------------------------------------------------------ actions

    /**
     * The QR is derived, never stored, so a card that is about to show one asks
     * for its bytes here. Lazy on purpose: only a card the user can actually see
     * pays for it, and „open only when unpaid" keeps that to the few bills that
     * still need paying rather than the whole list.
     */
    private val qrInFlight = mutableSetOf<String>()

    fun ensureQr(cardId: String) {
        val item = _state.value.items.firstOrNull { it.id == cardId } ?: return
        if (item.qrPng != null || !item.hasQr || item.qrUnavailable) return
        if (!qrInFlight.add(cardId)) return
        viewModelScope.launch {
            val qr = withContext(Dispatchers.IO) { runCatching { pipeline.qrFor(item) }.getOrNull() }
            qrInFlight.remove(cardId)
            val current = _state.value.items.firstOrNull { it.id == cardId } ?: return@launch
            val updated =
                if (qr == null) current.copy(qrUnavailable = true)
                // A code rebuilt from the fields is not the issuer's own — it
                // inherits the verify-before-paying notice.
                else current.copy(qrPng = qr.png, qrGenerated = current.qrGenerated || qr.generated)
            // Deliberately NOT persisted. The record stores no bitmap, so the
            // only thing that would be written is a `qrGenerated` raised by a
            // fallback — and a source file that was briefly unreachable must not
            // mark the bill as generated for good. It also keeps display off the
            // write path entirely: showing a card touches no database.
            _state.value = _state.value.copy(
                items = _state.value.items.map { if (it.id == cardId) updated else it }
            )
        }
    }

    /**
     * Odluka 4: „Podeli QR" — the second of the two equal ways out. The PNG is
     * written to `cacheDir/share` and handed over through FileProvider, so the
     * system owns the copy and cleans it up; nothing lands in the gallery.
     * Intesa takes a shared PNG straight to payment; AIK does not appear in the
     * share sheet at all, which is why the other button exists too.
     */
    fun shareQr(id: String) {
        val item = _state.value.items.firstOrNull { it.id == id } ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val png = item.qrPng ?: withContext(Dispatchers.IO) {
                runCatching { pipeline.qrFor(item) }.getOrNull()?.png
            }
            if (png == null) {
                toast(R.string.qr_missing)
                return@launch
            }
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return@runCatching null
                    val dir = File(app.cacheDir, "share").apply { mkdirs() }
                    val target = File(dir, "${item.nameBase}_QR.png")
                    val ok = Gallery.writeCaptionedPng(bmp, item.nameBase, target)
                    bmp.recycle()
                    if (ok) FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", target)
                    else null
                }.getOrNull()
            }
            if (uri == null) {
                toast(R.string.toast_folder_error)
                return@launch
            }
            runCatching { startShare(listOf(uri), "image/png") }
            if (item.qrGenerated) maybeShowQrDisclaimer()
        }
    }

    fun saveQrToGallery(id: String) {
        val item = _state.value.items.firstOrNull { it.id == id } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            // v1.4.7 Change 1: restored cards have no in-memory bytes — re-derive.
            val png = item.qrPng ?: pipeline.qrFor(item)?.png
            if (png == null) {
                withContext(Dispatchers.Main) { toast(R.string.qr_missing) }
                return@launch
            }
            val bmp = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return@launch
            // Saving is now a repeatable tap rather than something processing did
            // once, so drop the copy booked earlier instead of leaving „(1)"
            // duplicates behind in the gallery.
            pipeline.savedQrUris(item)?.let { Gallery.delete(getApplication(), it) }
            val joined = Gallery.save(getApplication(), bmp, "${item.nameBase}_QR", storage.racunkoStore(), caption = item.nameBase)
            bmp.recycle()
            if (joined != null) {
                // On the BILL record, not just the card: this is the bookkeeping
                // that lets pairing delete the copy again (Odluka 5).
                pipeline.recordSavedQr(item, joined)
                withContext(Dispatchers.Main) {
                    replaceItem(item.copy(qrPng = png, qrImageUri = joined))
                    toast(R.string.toast_qr_saved)
                    // Change 1: only generated QR codes carry the verify notice;
                    // an issuer's own decoded QR is trusted as-is.
                    if (item.qrGenerated) maybeShowQrDisclaimer()
                }
            }
        }
    }

    /**
     * Sharing is never auto-triggered — this runs only from an explicit tap.
     * Content uris are tried directly; if the grant is refused, fall back to
     * copying into app cache and sharing via FileProvider.
     */
    /**
     * v1.7: the archive lives in `filesDir`, so its uris are `file://` — which
     * may never leave the app (`FileUriExposedException`). Hand the receiver a
     * FileProvider uri instead. Anything already a `content://` uri (an optional
     * folder copy, a picked document) passes through untouched.
     */
    private fun shareableUri(uri: Uri): Uri {
        if (uri.scheme != "file") return uri
        val app = getApplication<Application>()
        val f = uri.path?.let { File(it) } ?: return uri
        return runCatching {
            FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", f)
        }.getOrDefault(uri)
    }

    fun share(uriStrings: List<String>, names: List<String>, mime: String) {
        val app = getApplication<Application>()
        val uris = uriStrings.map { shareableUri(Uri.parse(it)) }
        try {
            startShare(uris, mime)
        } catch (e: SecurityException) {
            viewModelScope.launch(Dispatchers.IO) {
                val cached = uris.mapIndexedNotNull { i, uri ->
                    runCatching {
                        val dir = File(app.cacheDir, "share").apply { mkdirs() }
                        val f = File(dir, names.getOrElse(i) { "fajl_$i" })
                        app.contentResolver.openInputStream(uri)?.use { input ->
                            f.outputStream().use { input.copyTo(it) }
                        }
                        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", f)
                    }.getOrNull()
                }
                if (cached.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        try { startShare(cached, mime) } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun startShare(uris: List<Uri>, mime: String) {
        val app = getApplication<Application>()
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uris[0])
                type = mime
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                type = mime
            }
        }
        // The read grant rides on the ClipData, not on the extra. Without this
        // the SYSTEM share sheet cannot open the uri to draw its preview — the
        // QR shows up as a grey rectangle in the very sheet where the user is
        // supposed to recognize what they are sending. Found on device: the
        // resolver logged `SecurityException: Permission Denial: opening
        // provider ... FileProvider` while the send itself still worked.
        // The label is deliberately generic: it can surface in the sheet, and a
        // bill's file name carries the address and the amount.
        intent.clipData = android.content.ClipData.newUri(
            app.contentResolver, app.getString(R.string.app_name), uris[0]
        ).apply { uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(chooser)
    }

    // ----------------------------------------------------------------- settings

    fun saveAddressRows(rows: List<Pair<String, String>>) {
        val order = mutableListOf<String>()
        val map = mutableMapOf<String, MutableList<String>>()
        for ((label, pattern) in rows) {
            val l = label.trim()
            val p = pattern.trim()
            if (l.isEmpty() || p.isEmpty()) continue
            if (l !in map) { map[l] = mutableListOf(); order.add(l) }
            map.getValue(l).add(p)
        }
        val addresses = order.map { AddressEntry(it, map.getValue(it)) }
        viewModelScope.launch {
            settings.saveAddresses(addresses)
            _state.value = _state.value.copy(addresses = addresses)
            toast(R.string.toast_addresses_saved)
        }
    }

    /**
     * v1.6: provider names the user added. These do NOT teach the parser to
     * recognize a new provider — detection lives in `ProviderDetector` and is
     * fixed at build time. They only widen the chip list offered when a provider
     * is set by hand, so an unrecognized one is a tap instead of retyping.
     */
    fun saveCustomProviders(names: List<String>) {
        val clean = names
            .map { BillName.sanitizeToken(it).lowercase() }
            .filter { it.isNotBlank() && it !in ProviderDetector.PROVIDERS }
            .distinct()
        viewModelScope.launch {
            settings.saveCustomProviders(clean)
            _state.value = _state.value.copy(customProviders = clean)
        }
    }

    fun saveProviderOverrides(overrides: Map<String, String>) {
        val clean = overrides.filterValues { it.isNotBlank() }
            .mapValues { BillName.sanitizeToken(it.value).lowercase() }
        viewModelScope.launch {
            settings.saveOverrides(clean)
            _state.value = _state.value.copy(providerOverrides = clean)
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            settings.saveLanguage(language)
            _state.value = _state.value.copy(language = language)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clear()
            _state.value = _state.value.copy(unpairedBills = emptyList())
            toast(R.string.toast_history_cleared)
        }
    }

    /** Change 6: „Obriši zapamćene primaoce" — empties payee memory (disables prefill). */
    fun clearPayees() {
        viewModelScope.launch {
            payeeDao.clear()
            toast(R.string.toast_payees_cleared)
        }
    }

    // --------------------------------------------------------------------- misc

    private fun queryDisplayName(uri: Uri): String? = try {
        getApplication<Application>().contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    private fun toast(resId: Int, vararg args: Any) {
        viewModelScope.launch(Dispatchers.Main) {
            val app = getApplication<Application>()
            val msg = if (args.isEmpty()) app.getString(resId) else app.getString(resId, *args)
            Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val KEY_SELECTION = "selected_card_names"
    }
}
