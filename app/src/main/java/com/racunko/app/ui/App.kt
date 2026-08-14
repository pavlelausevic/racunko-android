package com.racunko.app.ui

import android.graphics.BitmapFactory
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.racunko.app.R
import com.racunko.app.domain.CardItem
import com.racunko.app.domain.CardMode
import com.racunko.app.domain.CardStatus
import com.racunko.app.parser.DueDateParser
import com.racunko.app.parser.Months
import com.racunko.app.parser.ProviderDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Newest billing month first; a card with no readable month goes last. The key
 * is `year2 * 100 + month`, which orders correctly inside a century — and the
 * two-digit year is all a bill ever prints.
 */
private val BY_MONTH_DESC: Comparator<CardItem> =
    compareByDescending<CardItem> { c -> c.month?.let { it.year2 * 100 + it.month } ?: Int.MIN_VALUE }
        .thenBy { it.provider }
        .thenBy { it.currentName }

/**
 * v1.3: no onboarding — storage defaults to Download/Racunko and is resolved
 * lazily on first write (Change 1/2), so the app opens straight into the UI.
 */
@Composable
fun App(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Change 1: on return-to-foreground, resync the card list from DB + disk so
    // processed bills are never lost after backgrounding / the share round-trip.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!state.loaded) return
    // v1.7: nothing stands between launch and the list. Storage is the app's own,
    // so there is no folder to grant and no first-run screen to get through.
    MainScreen(vm)
}

// ------------------------------------------------------------ main screen

@Composable
private fun MainScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // itemId to field
    var attachFor by remember { mutableStateOf<String?>(null) }                // bill item id
    var dueTarget by remember { mutableStateOf<String?>(null) }                // bill item id
    var bannerDismissed by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var purgeStep by remember { mutableStateOf(0) }                            // 0 none, 1 first, 2 final
    val listState = rememberLazyListState()

    // v1.6: come back to the app where you left it. The offset is saved
    // continuously and re-applied ONCE the cards have loaded — on a cold start
    // the list is briefly empty, and restoring into an empty list is a no-op
    // that would silently drop the position.
    var savedIndex by rememberSaveable { mutableStateOf(0) }
    var savedOffset by rememberSaveable { mutableStateOf(0) }
    var scrollRestored by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                // don't let the pre-restore 0,0 overwrite what we are about to restore
                if (scrollRestored || index > 0 || offset > 0) {
                    savedIndex = index
                    savedOffset = offset
                }
            }
    }
    LaunchedEffect(state.items.size) {
        if (!scrollRestored && state.items.isNotEmpty()) {
            if (savedIndex > 0 || savedOffset > 0) listState.scrollToItem(savedIndex, savedOffset)
            scrollRestored = true
        }
    }

    LaunchedEffect(state.focusItemId) {
        if (state.focusItemId != null) {
            listState.animateScrollToItem(0)
            vm.clearFocus()
        }
    }

    val tabItems = state.items.filter {
        (state.tab == 0 && it.mode == CardMode.RACUN) || (state.tab == 1 && it.mode == CardMode.POTVRDA)
    }
    val files = if (state.tab == 0) state.racuniFiles else state.potvrdeFiles
    val mode = if (state.tab == 0) CardMode.RACUN else CardMode.POTVRDA

    // Launchers live above the camera branch so their result callbacks stay
    // registered while the camera screen temporarily replaces the main UI.
    // v1.5.2 Change A: manual adds go through the intake guard (classify first,
    // warn only on a recognized mismatch) — the same path share-into uses.
    val pickLauncher = rememberLauncherForActivityResult(
        OpenDocsAtDownloads()
    ) { uris -> if (uris.isNotEmpty()) vm.intake(uris, mode) }
    // v1.4.2 Change 2: „Dodaj fotografiju" uses the system Photo Picker (gallery),
    // not the document/file explorer.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.intake(listOf(uri), mode) }

    // 8d: full-screen live scanning replaces the main UI until lock/cancel.
    var showCamera by remember { mutableStateOf(false) }
    BackHandler(enabled = showCamera) { showCamera = false }
    if (showCamera) {
        CameraScreen(
            onScanned = { payload ->
                showCamera = false
                vm.scanBill(payload)
            },
            onCancel = { showCamera = false },
            onFallbackPhoto = {
                showCamera = false
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Header(onSettings = { showSettings = true })
        TabRow(
            selectedTabIndex = state.tab,
            containerColor = Palette.Bg,
            contentColor = Palette.Text
        ) {
            Tab(selected = state.tab == 0, onClick = { vm.setTab(0) },
                text = { Text(stringResource(R.string.tab_racuni)) })
            Tab(selected = state.tab == 1, onClick = { vm.setTab(1) },
                text = { Text(stringResource(R.string.tab_potvrde)) })
        }
        if (state.busy) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth(), color = Palette.Blue, trackColor = Palette.Card2
            )
        }

        var selected by rememberSaveable(state.tab) { mutableStateOf(setOf<String>()) }

        // ---- address grouping + the due filter --------------------------------
        // One flat list of nine addresses × several providers is unreadable, so
        // cards are grouped under their address label. Bills with no address yet
        // come FIRST — those are the ones asking for input. The collapse state is
        // deliberately not persisted: every entry into the screen starts open.
        // v1.6: the applied filter is part of „where I left off", so it is saved
        // across process death. The collapse state deliberately is NOT — every
        // entry into the screen starts with the sections open.
        var addressFilter by rememberSaveable(state.tab) { mutableStateOf<String?>(null) }
        var dueOnly by rememberSaveable(state.tab) { mutableStateOf(false) }
        val collapsed = remember(state.tab) { mutableStateMapOf<String, Boolean>() }

        val allLabels = remember(tabItems) {
            tabItems.map { it.address }.filter { it.isNotEmpty() }.distinct().sorted()
        }
        // if the filtered address disappears (deleted/edited), fall back to „Sve"
        val activeFilter = addressFilter?.takeIf { it in allLabels }

        // v1.6: bills that have entered their own reminder window. Recomputed
        // from today's date, so simply opening the app is what surfaces them.
        val today = LocalDate.now()
        val dueIds = remember(state.items, today) {
            state.items.filter { card ->
                card.mode == CardMode.RACUN && !card.paired && card.remindEnabled &&
                    DueDateParser.isDueWithin(
                        card.dueDateEpochDay?.let { LocalDate.ofEpochDay(it) },
                        today,
                        card.remindDaysBefore
                    )
            }.map { it.id }.toSet()
        }
        val overdueCount = remember(state.items, today) {
            state.items.count { card ->
                card.mode == CardMode.RACUN && !card.paired &&
                    (DueDateParser.daysUntil(
                        card.dueDateEpochDay?.let { LocalDate.ofEpochDay(it) }, today
                    ) ?: 1L) < 0L
            }
        }

        val groups = remember(tabItems, activeFilter, dueOnly, dueIds) {
            tabItems
                .filter { activeFilter == null || it.address == activeFilter }
                .filter { !dueOnly || it.id in dueIds }
                .groupBy { it.address }
                .toList()
                .sortedWith(compareBy({ it.first.isNotEmpty() }, { it.first }))
                // v1.6: inside a section the cards run by the month they are FOR,
                // newest first — the same order in the unfiltered list and under an
                // address filter, since the filter only removes whole sections. A
                // bill whose month could not be read sinks to the bottom of its
                // section rather than jumping to the top.
                .map { (label, cards) -> label to cards.sortedWith(BY_MONTH_DESC) }
        }
        /** Exactly what the filters are showing — what „Izaberi sve" now means. */
        val visibleIds = groups.flatMap { it.second }.map { it.id }
        val selectMode = state.reportSelection.isNotEmpty()

        // Change 1: one-time-per-session generated-QR verify notice.
        if (state.showQrDisclaimer) {
            QrDisclaimerBanner(onDismiss = { vm.dismissQrDisclaimer() })
        }
        // v1.6: the reminder itself — on open, dismissible for the session.
        if (!selectMode && state.tab == 0 && dueIds.isNotEmpty() && !dueOnly && !bannerDismissed) {
            DueBanner(
                count = dueIds.size,
                overdue = overdueCount,
                onShow = { dueOnly = true; addressFilter = null },
                onDismiss = { bannerDismissed = true }
            )
        }
        // Change 2–4 / v1.4.4: contextual action bar. v1.6: „Izaberi sve" is
        // scoped to the current view, not to every card in the tab.
        if (selectMode) {
            SelectionActionBar(
                count = state.reportSelection.size,
                showReport = state.tab == 0, // Izveštaj is bill-oriented (Change 3)
                onReport = { vm.buildReport() },
                onShare = { vm.shareSelectedCards() },
                onDelete = { showDeleteDialog = true },
                onSelectAll = { vm.selectAll(visibleIds) },
                onClear = { vm.clearReport() }
            )
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // v1.5.1 Change 3: addresses are the one thing Računko can't derive —
            // an empty address book gets a prominent CTA to build it first.
            if (state.tab == 0 && state.addresses.isEmpty()) {
                item(key = "cta_sifarnik") {
                    SifarnikCta(onOpen = { showSettings = true })
                }
            }
            // Bills only: a confirmation is paid by definition, so a „to pay"
            // total on that tab would be meaningless.
            if (state.tab == 0 && tabItems.isNotEmpty()) {
                item(key = "summary") { SummaryCard(tabItems) }
            }
            // Bills only, like the summary above: a confirmation is paid by
            // definition, so it can never be „pred rokom" and the pill would be
            // an offer to filter by nothing.
            val dueFilterable = if (state.tab == 0) dueIds.size else 0
            // The row earns its place with more than one address OR with a
            // deadline to filter by — with a single address the bell pill is
            // still the only way back out of that filter.
            if (allLabels.size > 1 || dueFilterable > 0) {
                item(key = "addr_filter") {
                    AddressFilterRow(
                        labels = allLabels,
                        counts = tabItems.groupingBy { it.address }.eachCount(),
                        total = tabItems.size,
                        selected = activeFilter,
                        dueCount = dueFilterable,
                        overdue = overdueCount,
                        dueActive = dueOnly,
                        onDue = { dueOnly = !dueOnly; addressFilter = null },
                        onSelect = { addressFilter = it; dueOnly = false }
                    )
                }
            }
            // v1.6: with several addresses the screen opens as a list of section
            // headers — an overview you unfold. With a single address there is
            // nothing to choose between, so folding it away would only add a tap:
            // that one section starts open. Same when a filter is applied — asking
            // for one address and getting a closed section is not an answer.
            val startCollapsed = allLabels.size > 1 && activeFilter == null && !dueOnly
            groups.forEach { (label, cards) ->
                val isOpen = collapsed[label]?.not() ?: !startCollapsed
                item(key = "hdr_$label") {
                    AddressGroupHeader(
                        label = label,
                        cards = cards,
                        expanded = isOpen,
                        onToggle = { collapsed[label] = isOpen }
                    )
                }
                if (isOpen) {
                    items(cards.size, key = { cards[it].id }) { i ->
                        val card = cards[i]
                        Card(
                            item = card,
                            vm = vm,
                            onEdit = { field -> editTarget = card.id to field },
                            onAttach = { attachFor = card.id },
                            onDue = { dueTarget = card.id },
                            selected = card.id in state.reportSelection,
                            selectMode = selectMode,
                            // v1.4.4 Change 3: bills and confirmations alike are
                            // selectable — and since v1.6.2 so are unreadable
                            // cards, which otherwise could not be removed at all.
                            selectable = true,
                            onLongPress = { vm.toggleReportSelection(card.id) },
                            onSelectTap = { vm.toggleReportSelection(card.id) },
                            onRemove = { vm.selectOnly(card.id); showDeleteDialog = true }
                        )
                    }
                }
            }
            item {
                FileSection(
                    files = files,
                    selected = selected,
                    isBills = state.tab == 0,
                    emptyTextRes = if (state.tab == 0) R.string.empty_racuni else R.string.empty_potvrde,
                    pickerLabelRes = if (state.tab == 0) R.string.btn_add_bill else R.string.btn_add_potvrda,
                    onToggle = { uri ->
                        selected = if (uri in selected) selected - uri else selected + uri
                    },
                    onProcess = {
                        vm.processSelected(selected.toList(), mode)
                        selected = emptySet()
                    },
                    onRefresh = { vm.refreshFiles() },
                    // Change 8 unified + menu: PDF vs photo, both opening at Downloads
                    onPickFile = {
                        pickLauncher.launch(
                            if (state.tab == 0) arrayOf("application/pdf")
                            else arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")
                        )
                    },
                    onPickPhoto = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onScan = { showCamera = true }
                )
            }
        }
    }

    // ---- sheets ----
    editTarget?.let { (id, field) ->
        val item = state.items.firstOrNull { it.id == id }
        if (item != null) {
            EditSheet(
                item = item, field = field,
                addressLabels = state.addresses.map { it.label }.distinct(),
                providerLabels = (ProviderDetector.PROVIDERS + state.customProviders).distinct(),
                onSave = { value ->
                    vm.applyEdit(id, field, value)
                    editTarget = null
                },
                onDismiss = { editTarget = null }
            )
        } else editTarget = null
    }

    attachFor?.let { billId ->
        val attachPicker = rememberLauncherForActivityResult(OpenDocAtDownloads()) { uri ->
            if (uri != null) vm.attachPickedToBill(billId, uri)
            attachFor = null
        }
        AttachSheet(
            files = state.potvrdeFiles,
            onPick = { uriString ->
                vm.attachConfirmationToBill(billId, uriString)
                attachFor = null
            },
            onSystemPicker = {
                attachPicker.launch(
                    arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")
                )
            },
            onDismiss = { attachFor = null }
        )
    }

    dueTarget?.let { id ->
        val item = state.items.firstOrNull { it.id == id }
        if (item != null) {
            DueSheet(
                item = item,
                onSave = { due, remind, days, hour ->
                    vm.setDue(id, due, remind, days, hour)
                    dueTarget = null
                },
                onDismiss = { dueTarget = null }
            )
        } else dueTarget = null
    }

    if (showSettings) {
        SettingsScreen(
            vm = vm,
            onPurge = { showSettings = false; purgeStep = 1 },
            onDismiss = { showSettings = false }
        )
    }

    // Change 5: report result — copy or share the plain text.
    state.reportText?.let { text ->
        ReportSheet(
            text = text,
            onCopy = { vm.copyReport() },
            onShare = { vm.shareReport() },
            onDismiss = { vm.clearReport() }
        )
    }

    // Change 4: delete-selected confirmation (list-only, or also files).
    if (showDeleteDialog) {
        val sel = state.items.filter { it.id in state.reportSelection }
        DeleteDialog(
            count = sel.size,
            confirmations = sel.count { it.pairedConfName != null },
            qrs = sel.count { it.qrImageUri != null },
            onConfirm = { alsoFiles ->
                vm.deleteSelected(alsoFiles)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // Change 5: „Isprazni fasciklu" double confirmation.
    if (purgeStep == 1) {
        PurgeFirstDialog(
            bills = state.racuniFiles.size,
            confirmations = state.potvrdeFiles.size,
            qrs = state.items.count { it.qrImageUri != null },
            onContinue = { purgeStep = 2 },
            onDismiss = { purgeStep = 0 }
        )
    }
    if (purgeStep == 2) {
        PurgeFinalDialog(
            onConfirm = { alsoPayees ->
                vm.purgeAll(alsoPayees)
                purgeStep = 0
            },
            onDismiss = { purgeStep = 0 }
        )
    }

    // v1.5.2 Change A: intake guard — the head pending file asks its question:
    // a recognized mismatch warns with a suggestion; UNKNOWN asks the type.
    state.pendingIntake?.firstOrNull()?.let { pending ->
        IntakeDialog(
            pending = pending,
            onPick = { chosen -> vm.resolveIntake(chosen) },
            onCancel = { vm.skipIntake() }
        )
    }
}

/**
 * v1.5.2 Change A1 — three shapes, one dialog: „looks like a confirmation"
 * (suggest routing it there), the mirror for bills, and the neutral type
 * question when nothing matched. Tapping outside / Back skips the file.
 */
@Composable
private fun IntakeDialog(
    pending: PendingIntake,
    onPick: (CardMode) -> Unit,
    onCancel: () -> Unit
) {
    val warn = pending.action != com.racunko.app.parser.registry.IntakeAction.ASK_TYPE
    val titleRes = when (pending.action) {
        com.racunko.app.parser.registry.IntakeAction.WARN_SUGGEST_CONFIRMATION -> R.string.intake_conf_title
        com.racunko.app.parser.registry.IntakeAction.WARN_SUGGEST_BILL -> R.string.intake_bill_title
        else -> R.string.intake_unknown_title
    }
    val bodyRes = when (pending.action) {
        com.racunko.app.parser.registry.IntakeAction.WARN_SUGGEST_CONFIRMATION -> R.string.intake_conf_body
        com.racunko.app.parser.registry.IntakeAction.WARN_SUGGEST_BILL -> R.string.intake_bill_body
        else -> R.string.intake_unknown_body
    }

    // Which button carries the weight. On a WARNING the classifier has a real
    // fingerprint, so its suggestion leads. On UNKNOWN nothing was recognized —
    // and an unrecognized document is far more often a bank confirmation than a
    // bill, because bills come from five known issuers while confirmations come
    // from every bank there is. Leaving both buttons flat, as before, made the
    // user choose with no help at all on exactly the case that offers none.
    val leadMode = when {
        warn -> pending.suggested ?: CardMode.POTVRDA
        else -> CardMode.POTVRDA
    }
    val otherMode = if (leadMode == CardMode.POTVRDA) CardMode.RACUN else CardMode.POTVRDA
    val leadLabel = when {
        !warn -> stringResource(R.string.share_type_conf_short)
        leadMode == CardMode.POTVRDA -> stringResource(R.string.intake_add_as_conf)
        else -> stringResource(R.string.intake_add_as_bill)
    }
    val otherLabel = when {
        !warn -> stringResource(R.string.share_type_bill)
        otherMode == CardMode.RACUN -> stringResource(R.string.intake_keep_bill)
        else -> stringResource(R.string.intake_keep_conf)
    }

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Palette.Card,
        title = {
            DialogText(
                stringResource(titleRes),
                color = Palette.Text, size = 18.sp, lineHeight = 24.sp,
                weight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                DialogText(stringResource(bodyRes), color = Palette.Muted, size = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                DialogText(stringResource(R.string.intake_cancel_hint), color = Palette.Dim, size = 11.sp, lineHeight = 15.sp)
            }
        },
        // Both choices live in confirmButton as one row of equal halves. Left to
        // AlertDialog's own slots they were two text buttons of whatever width
        // their labels happened to need, pushed to the right edge — which reads
        // as two unrelated links rather than as one either/or.
        confirmButton = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DialogButton(otherLabel, fill = null, modifier = Modifier.weight(1f)) { onPick(otherMode) }
                DialogButton(leadLabel, fill = Palette.Blue, modifier = Modifier.weight(1f)) { onPick(leadMode) }
            }
        },
        dismissButton = null
    )
}

/**
 * One dialog button. [fill] null draws it outlined — the way out, the choice
 * that changes nothing — and a colour fills it as the one the dialog is for.
 */
@Composable
private fun DialogButton(
    label: String,
    fill: Color?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier
            .clip(shape)
            .background(fill ?: Color.Transparent, shape)
            .border(1.dp, fill ?: Palette.Line, shape)
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (fill != null) Palette.Bg else Palette.Text,
            fontSize = 13.sp,
            fontWeight = if (fill != null) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Every dialog's buttons: one row, two equal halves, the acting choice on the
 * right. Left to AlertDialog's own slots they are two text buttons of whatever
 * width their labels happen to need, pushed against the right edge — which
 * reads as two unrelated links rather than as one decision, and gives a
 * two-word label five times the target of a one-word one.
 */
@Composable
private fun DialogChoices(
    cancelLabel: String,
    actionLabel: String,
    actionFill: Color,
    onCancel: () -> Unit,
    onAction: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DialogButton(cancelLabel, fill = null, modifier = Modifier.weight(1f)) { onCancel() }
        DialogButton(actionLabel, fill = actionFill, modifier = Modifier.weight(1f)) { onAction() }
    }
}

/**
 * An opt-in inside a dialog, wearing the same disc the lists select with — a
 * square Material checkbox here was the app's second way of saying „chosen".
 * Mark and label are centred as one group, so the dialog has a single axis
 * instead of a left-aligned row under a centred title.
 */
@Composable
private fun DialogCheck(checked: Boolean, label: String, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        SelectMark(selected = checked, modifier = Modifier.padding(end = 11.dp))
        Text(
            label,
            color = Palette.Text, fontSize = 13.sp, lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Dialog copy that sets its own alignment from how it actually wrapped: one line
 * is centred, more than one is justified to both margins. A centred paragraph
 * gives every line a different ragged start and reads as decoration; a justified
 * one holds the block square against the dialog's own edges.
 */
@Composable
private fun DialogText(
    text: String,
    color: Color,
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight = FontWeight.Normal
) {
    var multiline by remember(text) { mutableStateOf(false) }
    Text(
        text,
        color = color, fontSize = size, lineHeight = lineHeight, fontWeight = weight,
        textAlign = if (multiline) TextAlign.Justify else TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
        onTextLayout = { multiline = it.lineCount > 1 }
    )
}

@Composable
private fun DeleteDialog(
    count: Int,
    confirmations: Int,
    qrs: Int,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var alsoFiles by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Card,
        title = {
            DialogText(
                stringResource(R.string.delete_title, count),
                color = Palette.Text, size = 18.sp, lineHeight = 24.sp,
                weight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                DialogCheck(
                    checked = alsoFiles,
                    label = stringResource(R.string.delete_also_files)
                ) { alsoFiles = !alsoFiles }
                if (alsoFiles) {
                    Spacer(Modifier.height(6.dp))
                    DialogText(
                        stringResource(R.string.delete_counts, count, confirmations, qrs),
                        color = Palette.Amber, size = 12.sp, lineHeight = 17.sp
                    )
                }
            }
        },
        confirmButton = {
            DialogChoices(
                cancelLabel = stringResource(R.string.btn_otkazi),
                actionLabel = stringResource(R.string.action_delete),
                actionFill = Palette.Red,
                onCancel = onDismiss,
                onAction = { onConfirm(alsoFiles) }
            )
        },
        dismissButton = null
    )
}

@Composable
private fun PurgeFirstDialog(
    bills: Int,
    confirmations: Int,
    qrs: Int,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Card,
        title = {
            DialogText(
                stringResource(R.string.purge_title),
                color = Palette.Text, size = 18.sp, lineHeight = 24.sp,
                weight = FontWeight.SemiBold
            )
        },
        text = {
            DialogText(
                stringResource(R.string.purge_text, bills, confirmations, qrs),
                color = Palette.Muted, size = 13.sp, lineHeight = 19.sp
            )
        },
        confirmButton = {
            DialogChoices(
                cancelLabel = stringResource(R.string.btn_otkazi),
                actionLabel = stringResource(R.string.purge_continue),
                actionFill = Palette.Red,
                onCancel = onDismiss,
                onAction = onContinue
            )
        },
        dismissButton = null
    )
}

@Composable
private fun PurgeFinalDialog(onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var alsoPayees by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Card,
        title = {
            DialogText(
                stringResource(R.string.purge_final_title),
                color = Palette.Red, size = 18.sp, lineHeight = 24.sp,
                weight = FontWeight.Bold
            )
        },
        text = {
            DialogCheck(
                checked = alsoPayees,
                label = stringResource(R.string.purge_also_payees)
            ) { alsoPayees = !alsoPayees }
        },
        confirmButton = {
            DialogChoices(
                cancelLabel = stringResource(R.string.btn_otkazi),
                actionLabel = stringResource(R.string.purge_final_confirm),
                actionFill = Palette.Red,
                onCancel = onDismiss,
                onAction = { onConfirm(alsoPayees) }
            )
        },
        dismissButton = null
    )
}

@Composable
private fun QrDisclaimerBanner(onDismiss: () -> Unit) {
    // v1.5.1 Change 4: the banner dismisses itself after ~4 s (long enough to be
    // read) or on tap; the permanent short caption under the QR stays either way.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(4000)
        onDismiss()
    }
    Surface(
        color = Palette.Amber.copy(alpha = 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onDismiss() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.qr_disclaimer_title),
                color = Palette.Amber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.qr_disclaimer_text),
                color = Palette.Text, fontSize = 12.5.sp, lineHeight = 17.sp
            )
        }
    }
}

/** Change 2–4 / v1.4.4: contextual action bar shown when ≥1 card is selected. */
@Composable
private fun SelectionActionBar(
    count: Int,
    showReport: Boolean,
    onReport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        color = Palette.Card2,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Palette.Blue.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction(RIcons.Close, Palette.Muted, stringResource(R.string.btn_otkazi), 18) { onClear() }
            Text(
                "$count",
                color = Palette.Blue, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(10.dp))
            // „Izaberi sve" stays a word: an icon for it would be a guess.
            Text(
                stringResource(R.string.select_all),
                color = Palette.Muted, fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .clickable { onSelectAll() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            if (showReport) {
                IconAction(RIcons.Report, Palette.Blue, stringResource(R.string.report_title)) { onReport() }
            }
            IconAction(RIcons.Share, Palette.Blue, stringResource(R.string.btn_podeli)) { onShare() }
            IconAction(RIcons.Delete, Palette.Red, stringResource(R.string.action_delete)) { onDelete() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(text: String, onCopy: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Card) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 26.dp)) {
            Text(
                stringResource(R.string.report_title),
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                color = Palette.Card2,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // v1.6: NOT monospace. The report's columns are now spaced for a
                // proportional font, because that is what every app it gets pasted
                // into uses — so the preview has to be rendered the same way, or
                // it would show an alignment the user never receives.
                Text(
                    text,
                    color = Palette.Text, fontSize = 13.sp,
                    lineHeight = 19.sp, modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(stringResource(R.string.report_copy), Palette.Muted, Modifier.weight(1f), RIcons.Copy) { onCopy() }
                ActionButton(stringResource(R.string.btn_podeli), Palette.Blue, Modifier.weight(1f), RIcons.Share) { onShare() }
            }
        }
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row {
                Text("Računko", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                // the one mark that stays ours — orange, not the new gold
                Text(".", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Dot)
            }
            Text(stringResource(R.string.subtitle), fontSize = 12.sp, color = Palette.Muted)
        }
        IconAction(RIcons.Settings, Palette.Muted, stringResource(R.string.settings_title), 20) {
            onSettings()
        }
    }
}

// ------------------------------------------------------------ file picker

@Composable
private fun FileSection(
    files: List<FileRow>,
    selected: Set<String>,
    isBills: Boolean,
    emptyTextRes: Int,
    pickerLabelRes: Int,
    onToggle: (String) -> Unit,
    onProcess: () -> Unit,
    onRefresh: () -> Unit,
    onPickFile: () -> Unit,
    onPickPhoto: () -> Unit,
    onScan: () -> Unit
) {
    val df = remember { SimpleDateFormat("dd.MM.yyyy.", Locale.getDefault()) }
    var menuOpen by remember { mutableStateOf(false) }
    // v1.6: „Fajlovi u fascikli" is the raw folder listing — useful when adding
    // something, noise the rest of the time, and it sits under every card. It
    // now folds, and starts folded. The „+" stays reachable in the header, so
    // adding a file never needs the section open.
    var expanded by rememberSaveable { mutableStateOf(false) }
    // An empty folder has nothing to fold away — only the paragraph explaining
    // how bills get in, which is exactly what a first run needs to see. So the
    // chevron appears once there are files, and the text stands on its own.
    val foldable = files.isNotEmpty()
    val open = expanded && foldable
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (foldable) Modifier.clickable { expanded = !expanded } else Modifier)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (foldable) {
                    Ico(if (open) RIcons.ExpandMore else RIcons.ChevronRight, Palette.Dim, 17)
                    Spacer(Modifier.width(7.dp))
                }
                // v1.6.2: both texts are pinned to one line. On the confirmations
                // tab the two trailing buttons are wider than on bills („Osveži"
                // plus the longer picker label), which left this row too little
                // room and wrapped the count onto a second line — on that tab
                // only, which is why it read as a bug rather than as a squeeze.
                // The title now gives way first, and the count never wraps.
                Text(
                    stringResource(R.string.files_in_folder),
                    color = Palette.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (foldable) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        pluralStringResource(R.plurals.item_count, files.size, files.size),
                        color = Palette.Dim, fontSize = 11.sp,
                        maxLines = 1, softWrap = false
                    )
                }
            }
            // v1.6.2: „osveži" as a word cost this row about a fifth of its
            // width and pushed the title into an ellipsis on the confirmations
            // tab. It is a secondary action on an already-visible list, so an
            // icon carries it — and the title fits again.
            if (open) {
                IconButton(onClick = onRefresh, modifier = Modifier.size(34.dp)) {
                    Ico(RIcons.Refresh, Palette.Dim, 17)
                }
            }
            Box {
                // v1.6.2: the label is gone and the „+" carries it alone. It sits
                // on a row already titled „Fajlovi u fascikli", so the word said
                // what the position already says — and it was the widest thing
                // here, which is what squeezed the title. The label survives as
                // the content description, so screen readers still hear it.
                val pickerLabel = stringResource(pickerLabelRes)
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier
                        .size(38.dp)
                        .semantics { contentDescription = pickerLabel }
                ) {
                    Ico(RIcons.Add, Palette.Blue, 20)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Palette.Card2
                ) {
                    // v1.6.2: both tabs now open the same menu. Confirmations
                    // used to jump straight into the file picker, which meant
                    // the gallery was simply unreachable there — a photographed
                    // receipt could only be added by first saving it as a file.
                    // Scanning stays bills-only: it reads a live IPS QR, and
                    // that always produces a bill, never a confirmation.
                    if (isBills) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_scan), color = Palette.Text, fontSize = 13.sp) },
                            leadingIcon = { Ico(RIcons.Camera, Palette.Blue) },
                            onClick = { menuOpen = false; onScan() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_from_file), color = Palette.Text, fontSize = 13.sp) },
                        leadingIcon = { Ico(RIcons.Document, Palette.Blue) },
                        onClick = { menuOpen = false; onPickFile() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_photo), color = Palette.Text, fontSize = 13.sp) },
                        leadingIcon = { Ico(RIcons.Image, Palette.Blue) },
                        onClick = { menuOpen = false; onPickPhoto() }
                    )
                }
            }
        }
        if (files.isEmpty()) {
            Text(
                stringResource(emptyTextRes),
                color = Palette.Dim, fontSize = 13.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(vertical = 20.dp)
            )
        }
        if (open) files.forEach { row ->
            // v1.5.1 Change 2: an already-processed file cannot be checked again —
            // tag „već obrađen", checkbox disabled, row not clickable.
            val rowModifier =
                if (row.processed) Modifier.fillMaxWidth().padding(vertical = 2.dp)
                else Modifier.fillMaxWidth().clickable { onToggle(row.uriString) }.padding(vertical = 2.dp)
            Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                // v1.6.2: the same teal disc the card list uses. Two different
                // marks for the same act — „this one" — was the app speaking two
                // dialects; a file picker is still picking.
                SelectMark(
                    selected = row.uriString in selected,
                    enabled = !row.processed,
                    modifier = Modifier.padding(start = 2.dp, end = 12.dp)
                )
                Text(
                    row.name,
                    color = if (row.processed) Palette.Dim else Palette.Text,
                    fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (row.processed) {
                    Text(
                        stringResource(R.string.tag_processed),
                        color = Palette.Green, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .background(Palette.Green.copy(alpha = 0.13f), RoundedCornerShape(99.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    df.format(Date(row.lastModified)),
                    color = Palette.Dim, fontSize = 11.sp
                )
            }
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onProcess,
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                // v1.6.2: teal, not amber. This is the primary action of the
                // screen, and amber now means „something here is unproven" —
                // a full-width amber slab read as a warning about the files
                // rather than as the button that processes them.
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Blue, contentColor = Palette.Bg,
                    disabledContainerColor = Palette.Card2, disabledContentColor = Palette.Dim
                )
            ) {
                Text(stringResource(R.string.btn_obradi), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(60.dp))
    }
}

// ------------------------------------------------------------------ card

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Card(
    item: CardItem,
    vm: MainViewModel,
    onEdit: (String) -> Unit,
    onAttach: () -> Unit,
    onDue: () -> Unit,
    selected: Boolean = false,
    selectMode: Boolean = false,
    selectable: Boolean = true,
    onLongPress: () -> Unit = {},
    onSelectTap: () -> Unit = {},
    onRemove: () -> Unit = {}
) {
    // v1.6: no per-card checkbox. A long press picks the card up and puts the
    // list into select mode; from then on a plain tap adds or removes cards.
    // While selecting, taps must NOT fall through to the editable segments.
    val cardShape = RoundedCornerShape(16.dp)
    Surface(
        color = if (selected) Palette.Card2 else Palette.Card,
        shape = cardShape,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) Palette.Blue else Palette.Line
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            // The press ripple belongs to the clickable, which sits OUTSIDE the
            // Surface's own clip — without this it painted a hard-cornered
            // rectangle over the rounded card on every long press. Clipping to
            // the card's own shape first makes the highlight follow the card.
            .clip(cardShape)
            .then(
                if (selectable) Modifier.combinedClickable(
                    onLongClick = onLongPress,
                    onClick = { if (selectMode) onSelectTap() }
                ) else Modifier
            )
    ) {
        Column(Modifier.padding(14.dp)) {
            val edit: ((String) -> Unit)? = if (selectMode) null else onEdit

            // v1.6.2: the card leads with what a person came to read — WHO is
            // asking and HOW MUCH — instead of with the file name. The four
            // fields are still the file name and still tappable to edit; they
            // are simply laid out by importance rather than strung together in
            // one coloured row.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectMode && selectable) {
                    SelectMark(selected)
                    Spacer(Modifier.width(11.dp))
                }
                Column(Modifier.weight(1f)) {
                    // An unreadable card has no provider to show and asking for
                    // one is pointless — nothing was read. What identifies it is
                    // the file name, so that leads instead.
                    if (item.status == CardStatus.ERROR) {
                        Text(
                            item.currentName,
                            color = Palette.Text, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold, lineHeight = 20.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Seg(
                            item.provider.ifEmpty { stringResource(R.string.seg_provider_missing) },
                            missing = item.provider.isEmpty(),
                            suggested = item.providerSuggested,
                            size = 16.sp,
                            weight = FontWeight.SemiBold,
                            onClick = edit?.let { { it("provider") } }
                        )
                    }
                    if (item.status != CardStatus.ERROR) {
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Seg(
                                item.address.ifEmpty { stringResource(R.string.seg_address_missing) },
                                missing = item.address.isEmpty(),
                                suggested = item.addressSuggested,
                                size = 13.sp,
                                color = Palette.Muted,
                                onClick = edit?.let { { it("address") } }
                            )
                            Dot()
                            Seg(
                                item.month?.let { Months.token(it) }
                                    ?: stringResource(R.string.seg_month_missing),
                                missing = item.month == null,
                                size = 13.sp,
                                color = Palette.Muted,
                                onClick = edit?.let { { it("month") } }
                            )
                        }
                    }
                }
                if (item.status != CardStatus.ERROR) {
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Seg(
                                item.amount?.let { fmtAmount(it) }
                                    ?: stringResource(R.string.seg_amount_missing),
                                missing = item.amount == null,
                                size = 19.sp,
                                weight = FontWeight.Bold,
                                onClick = edit?.let { { it("amount") } }
                            )
                            // Change 5: recipient-account checksum result.
                            if (item.accountVerified) VerifiedTick()
                        }
                        Spacer(Modifier.height(3.dp))
                        Badge(item)
                    }
                }
            }
            if (item.status == CardStatus.ERROR) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.error_read),
                    color = Palette.Muted, fontSize = 13.sp, lineHeight = 19.sp
                )
                // v1.6.2: an unreadable card used to be a dead end — nothing to
                // edit, no action, and not even selectable, so the only way out
                // was to delete the file outside the app. It offers its own exit
                // now, and it is selectable like any other card.
                Spacer(Modifier.height(11.dp))
                ActionButton(
                    stringResource(R.string.btn_ukloni_karticu),
                    Palette.Red,
                    Modifier.fillMaxWidth(),
                    RIcons.Delete
                ) { onRemove() }
                return@Column
            }

            // The file name itself, once, as the quiet record of what the four
            // fields above produced — no longer the headline it used to be.
            Spacer(Modifier.height(9.dp))
            Text(
                item.currentName,
                color = Palette.Dim, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )

            // v1.6: deadline + reminder. Empty and optional when the bill
            // prints no deadline — never invented.
            if (item.mode == CardMode.RACUN) {
                Spacer(Modifier.height(9.dp))
                DueRow(item, enabled = !selectMode, onOpen = onDue)
            }

            // Change 6: legend for any payee-memory-prefilled segment above.
            if (item.providerSuggested || item.addressSuggested) {
                Text(
                    stringResource(R.string.suggested),
                    color = Palette.Amber, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }

            // ambiguous address chips
            if (item.addrAmbiguous.size > 1) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.addrAmbiguous.forEach { label ->
                        Chip(label, Palette.Blue) { vm.applyEdit(item.id, "address", label) }
                    }
                }
            }

            // v1.5.2 Change B2: identical final name — ask for a space tag
            if (item.mode == CardMode.RACUN && item.needsSpaceTag) {
                Spacer(Modifier.height(8.dp))
                SpaceTagPrompt(
                    canRemember = item.spaceId.isNotBlank(),
                    onApply = { sub, remember -> vm.applySpaceTag(item.id, sub, remember) }
                )
            }

            // pair-candidate chips on an unpaired confirmation (secondary flow)
            if (item.mode == CardMode.POTVRDA && !item.matched && item.pairCandidates.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.pick_bill_to_pair), color = Palette.Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.pairCandidates.forEach { name ->
                        Chip(name, Palette.Green) { vm.pairConfirmationWith(item.id, name) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // QR preview. An UNPAID bill leads with its code open — that is the
            // moment it is needed — while a paid one keeps it folded, so the
            // height of the card marks the state on its own.
            val opensQr = item.mode == CardMode.RACUN && !item.paired
            // Keyed on the default too, not just the id: when a confirmation
            // arrives the card keeps its id, and without this the QR of a bill
            // that was just paid would stay open until the next list rebuild.
            var showQr by remember(item.id, opensQr) { mutableStateOf(opensQr) }
            // Nothing is stored: the bytes are derived on demand, and only for a
            // card that is actually going to show them.
            LaunchedEffect(item.id, showQr, item.hasQr) {
                if (showQr && item.hasQr && item.qrPng == null) vm.ensureQr(item.id)
            }
            val qrBitmap = remember(item.qrPng) {
                item.qrPng?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
            }
            if (showQr && qrBitmap != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        qrBitmap, contentDescription = "NBS IPS QR",
                        modifier = Modifier
                            .size(150.dp)
                            .background(Color.White, RoundedCornerShape(10.dp))
                            .padding(6.dp)
                    )
                }
                // Change 1: permanent verify line under a GENERATED QR only.
                // Set with the same rule as the dialogs — one line centred, more
                // than one justified — so a block of text under the code reads as
                // a block and not as a ragged afterthought.
                if (item.qrGenerated) {
                    Spacer(Modifier.height(6.dp))
                    DialogText(
                        stringResource(R.string.qr_disclaimer_short),
                        Palette.Amber, 11.sp, 15.sp
                    )
                }
                // Two ways out, side by side and equal. Empirically: Intesa takes
                // a shared PNG straight to payment, AIK never appears in the
                // share sheet — and the app cannot know which bank this is. The
                // user tries once and then knows which button is theirs.
                if (item.mode == CardMode.RACUN) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(
                            stringResource(R.string.share_qr), Palette.Blue,
                            Modifier.weight(1f), RIcons.Share
                        ) { vm.shareQr(item.id) }
                        ActionButton(
                            stringResource(R.string.btn_qr_slika), Palette.Blue,
                            Modifier.weight(1f), RIcons.QrCode
                        ) { vm.saveQrToGallery(item.id) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            } else if (showQr && item.hasQr && item.qrUnavailable) {
                // An open slot must never be silently empty — 1.6.2 already cost
                // one device pass to a card that offered no way forward.
                DialogText(
                    stringResource(R.string.qr_regen_failed),
                    Palette.Dim, 11.sp, 15.sp
                )
                Spacer(Modifier.height(10.dp))
            }

            // v1.3 Change 3: attaching a confirmation is THE primary action on a bill
            if (item.mode == CardMode.RACUN && !item.paired) {
                ActionButton(
                    stringResource(R.string.btn_add_potvrda),
                    Palette.Green,
                    Modifier.fillMaxWidth(),
                    RIcons.Add
                ) { onAttach() }
                Spacer(Modifier.height(8.dp))
            }

            // Change 5b: generate an IPS QR for a bill that has none. The gate is
            // the core's (verified account + amount); when unmet the button is
            // disabled AND says why, rather than being silently grey.
            if (item.mode == CardMode.RACUN && !item.hasQr) {
                val canGenerate = item.accountVerified && item.amount != null
                val reason = when {
                    !item.accountVerified -> stringResource(R.string.make_qr_need_account)
                    item.amount == null -> stringResource(R.string.make_qr_need_amount)
                    else -> ""
                }
                MakeQrAction(enabled = canGenerate, reason = reason) { vm.generateQr(item.id) }
                Spacer(Modifier.height(8.dp))
            }

            // actions — „QR slika" now sits next to the code itself, so this row
            // is only about the bill FILE.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    stringResource(R.string.btn_podeli), Palette.Blue,
                    Modifier.weight(1f), RIcons.Share
                ) { vm.shareBill(item.id) } // v1.4.5: paid bill shares both files
            }

            // v1.3 Change 3: the attached confirmation as a sub-row under its bill
            val confName = item.pairedConfName
            val confUri = item.pairedConfUri
            if (item.mode == CardMode.RACUN && item.paired && confName != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Palette.Card2,
                    shape = RoundedCornerShape(11.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, Palette.Green.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Ico(RIcons.Check, Palette.Green, 14)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            confName,
                            color = Palette.Green, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (confUri != null) {
                            IconAction(RIcons.Share, Palette.Blue, stringResource(R.string.btn_podeli), 16) {
                                val mime = if (confName.endsWith(".pdf", true))
                                    "application/pdf" else "image/*"
                                vm.share(listOf(confUri), listOf(confName), mime)
                            }
                        }
                    }
                }
            }

            // links row. Only a card that STARTS folded needs the link: an unpaid
            // bill already shows its code, so „prikaži QR" would point at what is
            // on screen. With neither link nor notice there is no row at all —
            // that is where the height an unpaid card gains is partly paid back.
            if (item.mode == CardMode.RACUN && (!item.hasQr || !opensQr)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (item.hasQr && !opensQr) {
                        LinkButton(stringResource(if (showQr) R.string.hide_qr else R.string.show_qr)) {
                            showQr = !showQr
                        }
                    } else if (!item.hasQr) {
                        Text(
                            stringResource(R.string.qr_missing),
                            color = Palette.Dim, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(item: CardItem) {
    val (textRes, color) = when {
        item.status == CardStatus.ERROR -> R.string.badge_err to Palette.Red
        item.mode == CardMode.POTVRDA && item.matched -> R.string.badge_paired to Palette.Green
        item.mode == CardMode.POTVRDA ->
            (if (item.isComplete) R.string.badge_ok else R.string.badge_warn) to
                (if (item.isComplete) Palette.Green else Palette.Amber)
        !item.isComplete -> R.string.badge_warn to Palette.Amber
        item.hasQr -> R.string.badge_qr_ok to Palette.Green
        else -> R.string.badge_ok to Palette.Green
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (item.mode == CardMode.RACUN && item.paired) {
            Text(
                stringResource(R.string.badge_placeno),
                color = Palette.Green, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(Palette.Green.copy(alpha = 0.13f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            stringResource(textRes),
            color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(color.copy(alpha = 0.13f), RoundedCornerShape(99.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * One editable field of the file name, rendered as plain text rather than as a
 * boxed chip.
 *
 * Every segment used to be a bordered box in its own hue, which made a card look
 * like a form even when nothing was wrong with it. A value we can prove needs no
 * decoration at all; only the two states that want a person get marked, and both
 * get the SAME mark, because "look at this" is one idea:
 *
 *  - **missing** — the placeholder in amber, underlined. We could not prove it
 *    and will not guess it.
 *  - **suggested** — a real value, underlined. It came from payee memory rather
 *    than from this document, so it is worth a glance before it becomes a name.
 */
@Composable
private fun Seg(
    text: String,
    missing: Boolean,
    onClick: (() -> Unit)?,
    suggested: Boolean = false,
    size: TextUnit = 13.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Palette.Text
) {
    val marked = missing || suggested
    var m = Modifier.clip(RoundedCornerShape(6.dp))
    if (onClick != null) m = m.clickable { onClick() }
    Text(
        text,
        color = if (missing) Palette.Amber else color,
        fontSize = size,
        fontWeight = weight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (marked) TextDecoration.Underline else null,
        modifier = m.padding(horizontal = 3.dp, vertical = 2.dp)
    )
}

/** The „·" that separates address from month. */
@Composable
private fun Dot() {
    Text("·", color = Palette.Dim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 5.dp))
}

/** „Napravi QR" — enabled only when the core gate holds; otherwise says why. */
@Composable
private fun MakeQrAction(enabled: Boolean, reason: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(11.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.Green.copy(alpha = 0.14f),
                contentColor = Palette.Green,
                disabledContainerColor = Palette.Card2,
                disabledContentColor = Palette.Dim
            )
        ) {
            Ico(RIcons.QrCode, if (enabled) Palette.Green else Palette.Dim, 15)
            Spacer(Modifier.width(7.dp))
            Text(
                stringResource(R.string.btn_make_qr),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        }
        if (!enabled && reason.isNotEmpty()) {
            Text(
                reason,
                color = Palette.Amber, fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp, start = 4.dp)
            )
        }
    }
}

/** Small green check shown next to the amount when the recipient account passed the checksum. */
@Composable
private fun VerifiedTick() {
    Icon(
        RIcons.Check,
        contentDescription = null,
        tint = Palette.Green,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(14.dp)
            .semantics { contentDescription = "verified" }
    )
}


@Composable
private fun Chip(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(Palette.Card2, RoundedCornerShape(11.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(11.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

/** Palette-tinted icon at a consistent inline size. */
@Composable
private fun Ico(
    icon: ImageVector,
    tint: Color,
    size: Int = 16,
    description: String? = null
) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size.dp))
}

/** Icon-only tap target — used where the glyph alone is unambiguous. */
@Composable
private fun IconAction(
    icon: ImageVector,
    tint: Color,
    description: String,
    size: Int = 19,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Ico(icon, tint, size, description)
    }
}

@Composable
private fun ActionButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.14f), contentColor = color
        )
    ) {
        if (icon != null) {
            Ico(icon, color, 15)
            Spacer(Modifier.width(7.dp))
        }
        // Two of these share a row under the QR, so a long label must taper off
        // rather than be cut mid-glyph.
        Text(
            text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text, color = Palette.Muted, fontSize = 12.sp,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
        )
    }
}

// ----------------------------------------------------------- edit sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    item: CardItem,
    field: String,
    addressLabels: List<String>,
    providerLabels: List<String>,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val titleRes = when (field) {
        "provider" -> R.string.edit_provider
        "address" -> R.string.edit_address
        "month" -> R.string.edit_month
        else -> R.string.edit_amount
    }
    var value by remember {
        mutableStateOf(
            when (field) {
                "provider" -> item.provider
                "address" -> item.address
                "month" -> item.month?.let { Months.token(it) } ?: ""
                else -> item.amount?.toString() ?: ""
            }
        )
    }
    // Change 6: Done commits the value and dismisses the IME
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Card) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 26.dp).imePadding()) {
            Text(stringResource(titleRes), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
            Spacer(Modifier.height(12.dp))
            if (field == "address" && addressLabels.isNotEmpty()) {
                ChipFlow(addressLabels, Palette.Blue) { value = it; onSave(it) }
            }
            if (field == "provider") {
                // built-in (detected) names first, then the user's own (v1.6)
                ChipFlow(providerLabels, Palette.Amber) { value = it; onSave(it) }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    if (value.isNotBlank()) onSave(value)
                }),
                placeholder = {
                    val hint = when (field) {
                        "month" -> stringResource(R.string.month_hint)
                        "amount" -> stringResource(R.string.amount_hint)
                        else -> ""
                    }
                    if (hint.isNotEmpty()) Text(hint, color = Palette.Dim)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
                    focusedBorderColor = Palette.Blue, unfocusedBorderColor = Palette.Line,
                    cursorColor = Palette.Blue
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(stringResource(R.string.btn_otkazi), Palette.Muted, Modifier.weight(1f)) { onDismiss() }
                ActionButton(stringResource(R.string.btn_sacuvaj), Palette.Blue, Modifier.weight(1f)) { onSave(value) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(labels: List<String>, color: Color, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { label -> Chip(label, color) { onPick(label) } }
    }
}

// --------------------------------------------------------- attach sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachSheet(
    files: List<FileRow>,
    onPick: (String) -> Unit,
    onSystemPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Card) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 26.dp)) {
            Text(
                stringResource(R.string.pick_confirmation),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text
            )
            Spacer(Modifier.height(10.dp))
            // v1.3 Change 3: primary — pick straight from Downloads
            ActionButton(
                stringResource(R.string.system_picker),
                Palette.Blue,
                Modifier.fillMaxWidth(),
                RIcons.Add
            ) { onSystemPicker() }
            Spacer(Modifier.height(10.dp))
            if (files.isEmpty()) {
                Text(stringResource(R.string.empty_folder), color = Palette.Dim, fontSize = 13.sp)
            }
            files.forEach { row ->
                Text(
                    row.name,
                    color = Palette.Blue, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(row.uriString) }
                        .padding(vertical = 10.dp)
                )
                HorizontalDivider(color = Palette.Line)
            }
        }
    }
}

// ------------------------------------------------- settings (full screen, v1.5.1)

/**
 * v1.5.1 Change 6 (BLOCKER): Settings moved from ModalBottomSheet to a FULL
 * SCREEN. The sheet's separate dialog window kept swallowing IME insets on
 * device (regressed twice); in the activity's own window, edge-to-edge +
 * adjustResize + imePadding are guaranteed to work: the scrollable content
 * shrinks above the keyboard and the focused field is auto-scrolled into view.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(vm: MainViewModel, onPurge: () -> Unit, onDismiss: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val rows = remember {
        state.addresses.flatMap { a -> a.patterns.map { p -> a.label to p } }.toMutableStateList()
    }
    val overrides = remember {
        ProviderDetector.PROVIDERS.map { it to (state.providerOverrides[it] ?: "") }.toMutableStateList()
    }
    val custom = remember { state.customProviders.toMutableStateList() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.exportArchive(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.importArchive(uri)
    }
    val mirrorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.chooseCustomLocation(uri)
    }
    // Belt and braces: clear focus + controller hide + raw InputMethodManager.
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val dismissIme = {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        val imm = view.context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
        Unit
    }
    val imeVisible = WindowInsets.isImeVisible
    // First Back closes the IME, second Back closes the screen.
    BackHandler { if (imeVisible) dismissIme() else onDismiss() }

    Surface(color = Palette.Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    stringResource(R.string.settings_title),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text,
                    modifier = Modifier.weight(1f)
                )
                IconAction(RIcons.Close, Palette.Muted, stringResource(R.string.btn_otkazi), 18) {
                    dismissIme(); onDismiss()
                }
            }
            Spacer(Modifier.height(10.dp))

            // language
            FieldLabel(stringResource(R.string.settings_language))
            FlowRowChips(
                options = listOf("sr", "en", "ru", "system"),
                selected = state.language,
                label = { code ->
                    when (code) {
                        "sr" -> stringResource(R.string.lang_sr)
                        "en" -> stringResource(R.string.lang_en)
                        "ru" -> stringResource(R.string.lang_ru)
                        else -> stringResource(R.string.lang_system)
                    }
                },
                onSelect = { code ->
                    vm.setLanguage(code)
                    AppCompatDelegate.setApplicationLocales(
                        if (code == "system") LocaleListCompat.getEmptyLocaleList()
                        else LocaleListCompat.forLanguageTags(code)
                    )
                }
            )
            Spacer(Modifier.height(18.dp))

            // storage location. v1.7: the archive is the app's own — there is no
            // folder to choose and no permission to give. Saying so plainly beats
            // a row that opens a picker for something that is no longer storage.
            FieldLabel(stringResource(R.string.settings_folder))
            SheetRow(icon = RIcons.Document, text = stringResource(R.string.settings_storage_private))
            Text(
                stringResource(R.string.settings_storage_private_note),
                fontSize = 10.5.sp, color = Palette.Dim, lineHeight = 15.sp,
                modifier = Modifier.padding(top = 5.dp, start = 2.dp)
            )
            Spacer(Modifier.height(18.dp))

            // v1.7: export and import, side by side and equally weighted — the
            // archive is inside the app, so getting it out has to be as ordinary
            // as putting it in.
            FieldLabel(stringResource(R.string.settings_backup))
            SheetRow(
                icon = RIcons.Share,
                text = stringResource(R.string.settings_export)
            ) { exportLauncher.launch(null) }
            Spacer(Modifier.height(8.dp))
            SheetRow(
                icon = RIcons.Add,
                text = stringResource(R.string.settings_import)
            ) { importLauncher.launch(null) }
            Text(
                stringResource(R.string.settings_backup_note),
                fontSize = 10.5.sp, color = Palette.Dim, lineHeight = 15.sp,
                modifier = Modifier.padding(top = 5.dp, start = 2.dp)
            )
            Spacer(Modifier.height(18.dp))

            // v1.7 step 4: the visible folder, back as a CHOICE. Tapping the row
            // picks a folder; tapping it again while one is bound turns it off.
            FieldLabel(stringResource(R.string.settings_mirror))
            val mirrorOn = state.downloadsTreeUri != null
            SheetRow(
                icon = RIcons.Document,
                text = if (mirrorOn) state.locationLabel
                else stringResource(R.string.settings_mirror_off),
                tint = if (mirrorOn) Palette.Text else Palette.Muted
            ) { if (mirrorOn) vm.resetLocation() else mirrorLauncher.launch(null) }
            Text(
                stringResource(R.string.settings_mirror_note),
                fontSize = 10.5.sp, color = Palette.Dim, lineHeight = 15.sp,
                modifier = Modifier.padding(top = 5.dp, start = 2.dp)
            )
            Spacer(Modifier.height(18.dp))

            // addresses
            FieldLabel(stringResource(R.string.settings_addresses))
            Text(stringResource(R.string.settings_addresses_note), fontSize = 11.sp, color = Palette.Dim, lineHeight = 16.sp)
            Spacer(Modifier.height(8.dp))
            for (i in rows.indices) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    OutlinedTextField(
                        value = rows[i].first,
                        onValueChange = { rows[i] = it to rows[i].second },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { dismissIme() }),
                        placeholder = { Text(stringResource(R.string.addr_label_hint), color = Palette.Dim, fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        colors = settingsFieldColors(),
                        modifier = focusIntoViewModifier().width(92.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = rows[i].second,
                        onValueChange = { rows[i] = rows[i].first to it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { dismissIme() }),
                        placeholder = { Text(stringResource(R.string.addr_pattern_hint), color = Palette.Dim, fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        colors = settingsFieldColors(),
                        modifier = focusIntoViewModifier().weight(1f)
                    )
                    IconAction(RIcons.Close, Palette.Dim, stringResource(R.string.action_delete), 15) {
                        rows.removeAt(i)
                    }
                }
            }
            AddRowButton(stringResource(R.string.btn_add_address)) {
                dismissIme() // no lingering keyboard after adding a row
                rows.add("" to "")
            }
            Spacer(Modifier.height(18.dp))

            // provider labels: the five detected ones can be RENAMED …
            FieldLabel(stringResource(R.string.settings_providers))
            Text(
                stringResource(R.string.settings_providers_note),
                fontSize = 11.sp, color = Palette.Dim, lineHeight = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            for (i in overrides.indices) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    // „sz" is the filename token, not a word anybody recognizes on
                    // sight — the row is labelled with what it stands for.
                    // Muted, not amber: these five are what Računko already
                    // recognizes, which is the calmest fact on the screen. Amber
                    // now means „something here wants you", and five steady
                    // labels wearing it made the section look like a problem.
                    Text(
                        providerRowLabel(overrides[i].first),
                        color = Palette.Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp,
                        modifier = Modifier.width(92.dp)
                    )
                    OutlinedTextField(
                        value = overrides[i].second,
                        onValueChange = { overrides[i] = overrides[i].first to it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { dismissIme() }),
                        placeholder = { Text(overrides[i].first, color = Palette.Dim, fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        colors = settingsFieldColors(),
                        modifier = focusIntoViewModifier().weight(1f)
                    )
                }
            }

            // „Moji pružaoci" (a list of extra names offered as chips when the
            // provider is set by hand) is HIDDEN for now — the half-feature raised
            // more questions than it answered, and it is not being taken further
            // in this round. Whatever the user already entered is still loaded
            // into `custom` and written straight back on Save, so nothing is lost
            // and the section can be restored from git.

            Spacer(Modifier.height(20.dp))
            FieldLabel(stringResource(R.string.settings_danger))
            DangerRow(stringResource(R.string.settings_clear_history)) { vm.clearHistory() }
            Spacer(Modifier.height(6.dp))
            DangerRow(stringResource(R.string.settings_clear_payees)) { vm.clearPayees() }
            Spacer(Modifier.height(6.dp))
            // Change 5: destructive purge — tucked in Settings, double-confirmed.
            DangerRow(stringResource(R.string.settings_purge)) { onPurge() }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(stringResource(R.string.btn_otkazi), Palette.Muted, Modifier.weight(1f)) { onDismiss() }
                ActionButton(stringResource(R.string.btn_sacuvaj), Palette.Blue, Modifier.weight(1f), RIcons.Check) {
                    vm.saveAddressRows(rows.toList())
                    vm.saveProviderOverrides(overrides.toMap())
                    vm.saveCustomProviders(custom.toList())
                    onDismiss()
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/**
 * v1.5.1 Change 6: scrolls the focused field into view above the keyboard.
 * The small delay lets the IME animate in before the scroll target is measured.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun focusIntoViewModifier(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return Modifier
        .bringIntoViewRequester(requester)
        .onFocusEvent { focusState ->
            if (focusState.isFocused) {
                scope.launch {
                    delay(250)
                    requester.bringIntoView()
                }
            }
        }
}

/**
 * v1.5.2 Change B2: „Isti naziv već postoji — dodaj oznaku prostora". Chips
 * offer common tags; „Zapamti za ovaj prostor" binds the tag to the bill's
 * spaceId (IDENT) so from next month it applies automatically. Providers with
 * no per-space id can still tag the file — it just can't auto-bind.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpaceTagPrompt(canRemember: Boolean, onApply: (String, Boolean) -> Unit) {
    var value by remember { mutableStateOf("") }
    var rememberBind by remember { mutableStateOf(canRemember) }
    Surface(
        color = Palette.Amber.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Palette.Amber.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                stringResource(R.string.space_tag_title),
                color = Palette.Amber, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("G1", "G2", "STAN", "LOKAL").forEach { tag ->
                    Chip(tag, Palette.Amber) { value = tag }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.space_tag_hint), color = Palette.Dim, fontSize = 12.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            if (canRemember) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { rememberBind = !rememberBind }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The last square checkbox in the app. It sits inline on a
                    // card rather than in a dialog, so it stays left-aligned —
                    // only the mark itself is unified.
                    SelectMark(selected = rememberBind, modifier = Modifier.padding(end = 11.dp))
                    Text(stringResource(R.string.space_tag_remember), color = Palette.Text, fontSize = 12.sp)
                }
            } else {
                Spacer(Modifier.height(6.dp))
            }
            ActionButton(
                stringResource(R.string.btn_sacuvaj), Palette.Blue, Modifier.fillMaxWidth()
            ) { if (value.isNotBlank()) onApply(value, canRemember && rememberBind) }
        }
    }
}

// ------------------------------------------------------ sheet form language
//
// The four primitives every sheet and the Settings screen are built from, so
// one change of mind about the form changes the whole app: a sheet heading, the
// small uppercase field label, the „icon · text · ›" row, and the teal toggle.

@Composable
private fun SheetTitle(text: String) {
    Text(
        text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text,
        modifier = Modifier.padding(bottom = 14.dp)
    )
}

@Composable
private fun FieldLabel(text: String, required: Boolean = false) {
    Row(Modifier.padding(bottom = 6.dp)) {
        Text(
            text.uppercase(), color = Palette.Muted, fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp
        )
        if (required) Text(" *", color = Palette.Red, fontSize = 10.sp)
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector?,
    text: String,
    tint: Color = Palette.Text,
    // v1.7: null = the row states a fact rather than offering an action. It then
    // takes no ripple and shows no chevron, so nothing invites a tap that does
    // nothing.
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Card2, shape)
            .border(1.dp, Palette.Line, shape)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Ico(icon, Palette.Blue, 17)
            Spacer(Modifier.width(11.dp))
        }
        Text(
            text, color = tint, fontSize = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (onClick != null) Ico(RIcons.ChevronRight, Palette.Dim, 16)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Card2, shape)
            .border(1.dp, Palette.Line, shape)
            .clip(shape)
            .clickable { onChange(!checked) }
            .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = Palette.Text, fontSize = 13.5.sp, lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Bg,
                checkedTrackColor = Palette.Blue,
                checkedBorderColor = Palette.Blue,
                uncheckedThumbColor = Palette.Dim,
                uncheckedTrackColor = Palette.Card,
                uncheckedBorderColor = Palette.Line
            )
        )
    }
}

/** The „Na dan · 1 dana ranije · 3 dana ranije" selector. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FlowRowChips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            val active = option == selected
            val shape = RoundedCornerShape(99.dp)
            Text(
                label(option),
                color = if (active) Palette.Blue else Palette.Muted,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .background(if (active) Palette.Blue.copy(alpha = 0.16f) else Palette.Card2, shape)
                    .border(1.dp, if (active) Palette.Blue else Palette.Line, shape)
                    .clip(shape)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

// ------------------------------------------------ selection · due · reminder

/**
 * The teal disc that replaced every checkbox in the app — on a card and in the
 * folder list alike, so selecting means one thing and looks like one thing.
 *
 * [enabled] false is the already-processed file: still visible, so the row does
 * not jump when it becomes selectable again, but drawn as an outline the eye
 * skips.
 */
@Composable
private fun SelectMark(
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val ring = when {
        !enabled -> Palette.Line
        selected -> Palette.Blue
        else -> Palette.Dim
    }
    Box(
        modifier
            .size(20.dp)
            .background(if (selected && enabled) Palette.Blue else Color.Transparent, CircleShape)
            .border(1.5.dp, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected && enabled) Ico(RIcons.Check, Palette.Bg, 13)
    }
}

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy.")

private fun fmtDate(date: LocalDate): String = date.format(DATE_FMT)

/**
 * The bill's deadline, or an invitation to set one. A bill that prints no
 * deadline shows „rok?" and stays perfectly usable without it — the field is
 * optional by design, never guessed.
 */
@Composable
private fun DueRow(item: CardItem, enabled: Boolean, onOpen: () -> Unit) {
    val due = item.dueDateEpochDay?.let { LocalDate.ofEpochDay(it) }
    val today = LocalDate.now()
    val days = DueDateParser.daysUntil(due, today)
    val shape = RoundedCornerShape(9.dp)
    Row(
        Modifier
            .clip(shape)
            .then(if (enabled) Modifier.clickable { onOpen() } else Modifier)
            .padding(vertical = 3.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ico(RIcons.Calendar, if (due == null) Palette.Dim else Palette.Muted, 14)
        Spacer(Modifier.width(7.dp))
        if (due == null) {
            Text(stringResource(R.string.due_missing), color = Palette.Dim, fontSize = 11.5.sp)
        } else {
            Text(
                fmtDate(due), color = Palette.Muted,
                fontSize = 11.5.sp, fontFamily = FontFamily.Monospace
            )
            if (!item.paired && days != null) {
                Spacer(Modifier.width(7.dp))
                DueChip(days)
            }
        }
        Spacer(Modifier.weight(1f))
        if (item.remindEnabled && due != null && !item.paired) {
            Ico(RIcons.Bell, Palette.Blue.copy(alpha = 0.7f), 13)
        }
    }
}

/** „za 3d" while there is time, „kasni 2d" in red once the date has passed. */
@Composable
private fun DueChip(days: Long) {
    val overdue = days < 0
    val color = when {
        overdue -> Palette.Red
        days <= 3 -> Palette.Amber
        else -> Palette.Muted
    }
    Text(
        if (overdue) stringResource(R.string.due_overdue, -days) else stringResource(R.string.due_in, days),
        color = color, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(99.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

/**
 * Per-bill deadline and reminder — the „planirani troškovi" mechanic, scoped
 * to what Računko can honestly deliver today: the reminder surfaces in the app
 * when you open it. The chosen hour is stored for the system notification that
 * will use it later, and the sheet says so rather than implying it works now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueSheet(
    item: CardItem,
    onSave: (Long?, Boolean, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var due by remember { mutableStateOf(item.dueDateEpochDay) }
    var remind by remember { mutableStateOf(item.remindEnabled) }
    var daysBefore by remember { mutableStateOf(item.remindDaysBefore) }
    var hour by remember { mutableStateOf(item.remindHour) }
    var showPicker by remember { mutableStateOf(false) }
    var hourMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Card) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 26.dp)) {
            SheetTitle(stringResource(R.string.due_title))

            FieldLabel(stringResource(R.string.due_date_label))
            SheetRow(
                icon = RIcons.Calendar,
                text = due?.let { fmtDate(LocalDate.ofEpochDay(it)) }
                    ?: stringResource(R.string.due_not_set),
                tint = if (due == null) Palette.Dim else Palette.Text,
                onClick = { showPicker = true }
            )
            if (due != null) {
                TextButton(onClick = { due = null }) {
                    Text(stringResource(R.string.due_clear), color = Palette.Red, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(14.dp))
            ToggleRow(
                label = stringResource(R.string.due_remind),
                checked = remind,
                onChange = { remind = it }
            )

            if (remind) {
                Spacer(Modifier.height(12.dp))
                FieldLabel(stringResource(R.string.due_days_label))
                FlowRowChips(
                    options = listOf(0, 1, 3, 7, 10, 15),
                    selected = daysBefore,
                    label = { d ->
                        if (d == 0) stringResource(R.string.due_on_day)
                        else pluralStringResource(R.plurals.day_count, d, d)
                    },
                    onSelect = { daysBefore = it }
                )

                Spacer(Modifier.height(12.dp))
                FieldLabel(stringResource(R.string.due_time_label))
                Box {
                    SheetRow(
                        icon = RIcons.Clock,
                        text = "%02d:00".format(hour),
                        tint = Palette.Text,
                        onClick = { hourMenu = true }
                    )
                    DropdownMenu(
                        expanded = hourMenu,
                        onDismissRequest = { hourMenu = false },
                        containerColor = Palette.Card2
                    ) {
                        (0..23).forEach { h ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "%02d:00".format(h),
                                        color = if (h == hour) Palette.Blue else Palette.Text,
                                        fontSize = 13.sp, fontFamily = FontFamily.Monospace
                                    )
                                },
                                onClick = { hour = h; hourMenu = false }
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.due_time_note),
                    color = Palette.Dim, fontSize = 10.5.sp, lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(stringResource(R.string.btn_otkazi), Palette.Muted, Modifier.weight(1f)) { onDismiss() }
                ActionButton(stringResource(R.string.btn_sacuvaj), Palette.Blue, Modifier.weight(1f)) {
                    onSave(due, remind, daysBefore, hour)
                }
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = due?.let { it * 86_400_000L }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            colors = DatePickerDefaults.colors(containerColor = Palette.Card),
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { due = it / 86_400_000L }
                    showPicker = false
                }) { Text(stringResource(R.string.btn_sacuvaj), color = Palette.Blue) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.btn_otkazi), color = Palette.Muted)
                }
            }
        ) {
            DatePicker(state = pickerState, colors = DatePickerDefaults.colors(containerColor = Palette.Card))
        }
    }
}

/**
 * The reminder itself: bills whose own window has opened, collected above the
 * list every time the app is opened. Tapping it narrows the list to exactly
 * those bills, so the notice leads somewhere instead of just informing.
 */
@Composable
private fun DueBanner(count: Int, overdue: Int, onShow: () -> Unit, onDismiss: () -> Unit) {
    val accent = if (overdue > 0) Palette.Red else Palette.Amber
    Surface(
        color = accent.copy(alpha = 0.10f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(RIcons.Bell, accent, 17)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.due_banner, count, count),
                    color = accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
                )
                if (overdue > 0) {
                    Text(
                        pluralStringResource(R.plurals.due_banner_overdue, overdue, overdue),
                        color = Palette.Muted, fontSize = 10.5.sp
                    )
                }
            }
            TextButton(onClick = onShow) {
                Text(stringResource(R.string.due_banner_show), color = accent, fontSize = 12.sp)
            }
            IconAction(RIcons.Close, Palette.Dim, stringResource(R.string.btn_otkazi), 15) { onDismiss() }
        }
    }
}

// ------------------------------------------- summary · filter · group header

/** Thousands-separated integer amount, in the device's locale. */
private fun fmtAmount(value: Long): String =
    java.text.NumberFormat.getIntegerInstance().format(value)

/**
 * The screen's focal point: what is still to be paid, and what already is.
 * Both figures cover the bills currently in the list (i.e. the batch in the
 * folder) — not a running all-time balance, which Računko has no way to know.
 */
@Composable
private fun SummaryCard(bills: List<CardItem>) {
    val unpaid = bills.filter { !it.paired }
    val paid = bills.filter { it.paired }
    Surface(
        color = Palette.Card,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Palette.Line),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // v1.6.2: one number leads. What is still owed is the only figure a
        // person opens this app to see, so it gets the size; what is already
        // paid is reassurance and sits under a rule, quiet. The two used to be
        // near-equal rows behind a clock icon, which made the eye choose.
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    fmtAmount(unpaid.sumOf { it.amount ?: 0L }),
                    color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "RSD",
                    color = Palette.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.summary_unpaid) + "  ·  " +
                    pluralStringResource(R.plurals.bill_count, unpaid.size, unpaid.size),
                color = Palette.Muted, fontSize = 13.sp
            )
            Spacer(Modifier.height(13.dp))
            HorizontalDivider(color = Palette.Line)
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.summary_paid),
                    color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f)
                )
                Text(
                    fmtAmount(paid.sumOf { it.amount ?: 0L }),
                    color = Palette.Green, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    pluralStringResource(R.plurals.bill_count, paid.size, paid.size),
                    color = Palette.Dim, fontSize = 11.sp, maxLines = 1, softWrap = false
                )
            }
        }
    }
}

/**
 * „Sve · pred rokom · KD7 · SG26 …" — narrows the list without collapsing the rest.
 *
 * v1.7: the deadline filter is a pill in THIS row rather than a separate notice.
 * It used to be a bare text link under the list, quieter than the banner that
 * opened it, so the way back out was hard to find (reported on device
 * 14.08.2026). As a pill it carries its own on/off state and „Sve" clears it,
 * which is the way every other filter here already behaves. The two axes stay
 * mutually exclusive: picking an address drops the deadline filter and vice
 * versa, so „Sve" always means all.
 */
@Composable
private fun AddressFilterRow(
    labels: List<String>,
    counts: Map<String, Int>,
    total: Int,
    selected: String?,
    dueCount: Int,
    overdue: Int,
    dueActive: Boolean,
    onDue: () -> Unit,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "all") {
            FilterPill(
                stringResource(R.string.filter_all), total,
                selected == null && !dueActive
            ) { onSelect(null) }
        }
        if (dueCount > 0) {
            item(key = "due") {
                FilterPill(
                    stringResource(R.string.filter_due), dueCount, dueActive,
                    accent = if (overdue > 0) Palette.Red else Palette.Amber,
                    icon = RIcons.Bell
                ) { onDue() }
            }
        }
        items(labels.size, key = { labels[it] }) { i ->
            val label = labels[i]
            FilterPill(label, counts[label] ?: 0, selected == label) {
                onSelect(if (selected == label) null else label)
            }
        }
    }
}

@Composable
private fun FilterPill(
    text: String,
    count: Int,
    active: Boolean,
    accent: Color = Palette.Blue,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(99.dp)
    Row(
        Modifier
            .background(if (active) accent.copy(alpha = 0.16f) else Palette.Card2, shape)
            .border(1.dp, if (active) accent else Palette.Line, shape)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A glyph instead of a word where the glyph already says it: the bell is
        // the same mark the deadline banner leads with, so the pill reads as that
        // banner's filter without spending a word on it. `text` stays the
        // accessible label.
        if (icon != null) {
            Ico(icon, if (active) accent else Palette.Muted, 14, text)
        } else {
            Text(
                text,
                color = if (active) accent else Palette.Muted,
                fontSize = 12.5.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            "$count",
            color = if (active) accent.copy(alpha = 0.75f) else Palette.Dim,
            fontSize = 10.sp
        )
    }
}

/**
 * Section header per address: how many bills sit under it, how many are still
 * unpaid, and their total. Tapping anywhere on the row collapses the section.
 */
@Composable
private fun AddressGroupHeader(
    label: String,
    cards: List<CardItem>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    // A bill is settled when its confirmation arrives (`paired`); a confirmation
    // is settled when it finds its bill (`matched`). Counting `paired` for both
    // made every confirmation on the Potvrde tab read „neplaćeno" — including the
    // ones sitting under a bill already marked paid.
    val unpaid = cards.count { if (it.mode == CardMode.POTVRDA) !it.matched else !it.paired }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Ico(if (expanded) RIcons.ExpandMore else RIcons.ChevronRight, Palette.Dim, 17)
            Spacer(Modifier.width(7.dp))
            // The address label is a heading, not a value: it reads as type, in
            // the ordinary text colour. Teal here made every section header look
            // like a link to somewhere else.
            Text(
                label.ifEmpty { stringResource(R.string.group_no_address) },
                color = if (label.isEmpty()) Palette.Amber else Palette.Text,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(9.dp))
            Text(
                pluralStringResource(R.plurals.item_count, cards.size, cards.size),
                color = Palette.Dim, fontSize = 11.sp, maxLines = 1, softWrap = false
            )
            Spacer(Modifier.weight(1f))
            if (unpaid > 0) {
                Text(
                    stringResource(R.string.group_unpaid, unpaid),
                    color = Palette.Amber, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(Palette.Amber.copy(alpha = 0.13f), RoundedCornerShape(99.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(7.dp))
            }
            // Sum only what was actually read. `it.amount ?: 0` turned „we could
            // not read a single amount here" into „this section costs 0", which
            // is the one thing the app must never do: state a number it does not
            // have. With nothing proven, the column stays empty.
            val known = cards.mapNotNull { it.amount }
            if (known.isNotEmpty()) {
                Text(
                    fmtAmount(known.sum()),
                    color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, softWrap = false
                )
            }
        }
        HorizontalDivider(color = Palette.Line, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

/** v1.5.1 Change 3: empty address book → prominent „Napravi šifarnik" CTA. */
@Composable
private fun SifarnikCta(onOpen: () -> Unit) {
    Surface(
        color = Palette.Blue.copy(alpha = 0.10f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Palette.Blue.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.cta_sifarnik_title),
                color = Palette.Blue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.cta_sifarnik_text),
                color = Palette.Muted, fontSize = 12.5.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Blue, contentColor = Palette.Bg
                )
            ) {
                Text(
                    stringResource(R.string.cta_sifarnik_btn),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** „+ Dodaj …" — the single way a row is added anywhere in Settings. */
@Composable
private fun AddRowButton(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ico(RIcons.Add, Palette.Blue, 15)
        Spacer(Modifier.width(6.dp))
        Text(text, color = Palette.Blue, fontSize = 13.sp)
    }
}

/** Destructive action, kept visually apart from everything else. */
@Composable
private fun DangerRow(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Red.copy(alpha = 0.07f), shape)
            .border(1.dp, Palette.Red.copy(alpha = 0.28f), shape)
            .clip(shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Palette.Red, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Ico(RIcons.Delete, Palette.Red, 16)
    }
}

/**
 * What the left column of „Nazivi pružalaca" says. Four of the five tokens are
 * the brand itself and read fine; „sz" is an abbreviation the app invented for
 * the filename, so that row is spelled out. The token underneath is unchanged —
 * this is a label, not a rename.
 */
@Composable
private fun providerRowLabel(token: String): String =
    if (token == "sz") stringResource(R.string.provider_label_sz) else token

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
    focusedBorderColor = Palette.Blue, unfocusedBorderColor = Palette.Line,
    cursorColor = Palette.Blue
)

/**
 * System pickers that open at the public Downloads folder (Change 1) —
 * bills and confirmations downloaded from portals/banking apps land there.
 */
private class OpenDocsAtDownloads : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: android.content.Context, input: Array<String>): android.content.Intent {
        return super.createIntent(context, input).apply {
            putExtra(
                android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                android.provider.DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:Download"
                )
            )
        }
    }
}

private class OpenDocAtDownloads : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: android.content.Context, input: Array<String>): android.content.Intent {
        return super.createIntent(context, input).apply {
            putExtra(
                android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                android.provider.DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:Download"
                )
            )
        }
    }
}
