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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
import com.racunko.app.parser.Months
import com.racunko.app.parser.ProviderDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    // rc1 Change 1: no working folder yet → first-run grant screen before anything.
    if (state.needsOnboarding) { OnboardingScreen(vm); return }
    MainScreen(vm)
}

// ------------------------------------------------------------ onboarding (rc2)

/**
 * v1.5.0-rc2 Change 1: Računko needs ONE folder to store bills/confirmations/QRs.
 * Android 11+ greys out "USE THIS FOLDER" on the Downloads ROOT, so we pre-point
 * the SAF tree picker at the `Download/Racunko` SUBFOLDER (allowed) and explain,
 * via an info popup, how to confirm it (and that files from other apps come in
 * through „Dodaj iz fajla", a no-permission file picker). This is the only
 * permission prompt in the whole flow.
 */
@Composable
private fun OnboardingScreen(vm: MainViewModel) {
    // Show the how-to popup on entry; the ⓘ button reopens it, and a cancelled
    // grant re-shows it so the user never dead-ends.
    var showInfo by remember { mutableStateOf(true) }
    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) vm.onTreeGranted(uri) else showInfo = true }
    val launchGrant = {
        treeLauncher.launch(
            android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", "primary:Download/Racunko"
            )
        )
    }
    Surface(color = Palette.Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.app_name),
                fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.Blue
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.onboarding_title),
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = Palette.Text, textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.onboarding_body),
                fontSize = 15.sp, color = Palette.Muted, lineHeight = 22.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = launchGrant,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Blue, contentColor = Palette.Bg
                )
            ) { Text(stringResource(R.string.onboarding_grant), fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { showInfo = true }) {
                Text("ⓘ " + stringResource(R.string.onboarding_info_btn), color = Palette.Blue, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.onboarding_hint),
                fontSize = 12.sp, color = Palette.Muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            containerColor = Palette.Card,
            title = { Text(stringResource(R.string.onboarding_info_title), color = Palette.Text, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    stringResource(R.string.onboarding_info),
                    color = Palette.Muted, fontSize = 14.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false; launchGrant() }) {
                    Text(stringResource(R.string.onboarding_grant), color = Palette.Blue, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(R.string.btn_ok), color = Palette.Muted)
                }
            }
        )
    }
}

// ------------------------------------------------------------ main screen

@Composable
private fun MainScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // itemId to field
    var attachFor by remember { mutableStateOf<String?>(null) }                // bill item id
    var showSettings by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var purgeStep by remember { mutableStateOf(0) }                            // 0 none, 1 first, 2 final
    val listState = rememberLazyListState()

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
                Modifier.fillMaxWidth(), color = Palette.Amber, trackColor = Palette.Card2
            )
        }

        // Change 1: one-time-per-session generated-QR verify notice.
        if (state.showQrDisclaimer) {
            QrDisclaimerBanner(onDismiss = { vm.dismissQrDisclaimer() })
        }
        // Change 2–4 / v1.4.4: contextual action bar on either tab when selected.
        if (state.reportSelection.isNotEmpty()) {
            SelectionActionBar(
                count = state.reportSelection.size,
                showReport = state.tab == 0, // Izveštaj is bill-oriented (Change 3)
                onReport = { vm.buildReport() },
                onShare = { vm.shareSelectedCards() },
                onDelete = { showDeleteDialog = true },
                onSelectAll = { vm.selectAll(tabItems.map { it.id }) },
                onClear = { vm.clearReport() }
            )
        }

        var selected by rememberSaveable(state.tab) { mutableStateOf(setOf<String>()) }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // v1.5.1 Change 3: addresses are the one thing Računko can't derive —
            // an empty address book gets a prominent CTA to build it first.
            if (state.tab == 0 && state.addresses.isEmpty()) {
                item(key = "cta_sifarnik") {
                    SifarnikCta(onOpen = { showSettings = true })
                }
            }
            items(tabItems.size, key = { tabItems[it].id }) { i ->
                val card = tabItems[i]
                // v1.4.4 Change 3: both bill and confirmation cards are selectable.
                val selectable = card.status != CardStatus.ERROR
                Card(
                    item = card,
                    vm = vm,
                    onEdit = { field -> editTarget = card.id to field },
                    onAttach = { attachFor = card.id },
                    reportSelected = card.id in state.reportSelection,
                    onReportToggle = if (selectable) ({ vm.toggleReportSelection(card.id) }) else null
                )
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
    // suggested choice first (highlighted), the override second
    val suggested = pending.suggested
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Palette.Card,
        title = {
            Text(stringResource(titleRes), color = Palette.Text, fontSize = 16.sp, lineHeight = 22.sp)
        },
        text = {
            Text(
                stringResource(R.string.intake_cancel_hint),
                color = Palette.Dim, fontSize = 11.sp
            )
        },
        confirmButton = {
            val confirmMode = when {
                warn -> pending.suggested ?: CardMode.POTVRDA
                else -> CardMode.RACUN
            }
            val confirmLabel = when {
                !warn -> stringResource(R.string.share_type_bill)
                confirmMode == CardMode.POTVRDA -> stringResource(R.string.intake_add_as_conf)
                else -> stringResource(R.string.intake_add_as_bill)
            }
            val highlight = warn || suggested == CardMode.RACUN
            TextButton(onClick = { onPick(confirmMode) }) {
                Text(
                    confirmLabel,
                    color = if (highlight) Palette.Green else Palette.Muted,
                    fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
                )
            }
        },
        dismissButton = {
            val dismissMode = when {
                warn -> if ((pending.suggested ?: CardMode.POTVRDA) == CardMode.POTVRDA) CardMode.RACUN else CardMode.POTVRDA
                else -> CardMode.POTVRDA
            }
            val dismissLabel = when {
                !warn -> stringResource(R.string.share_type_conf)
                dismissMode == CardMode.RACUN -> stringResource(R.string.intake_keep_bill)
                else -> stringResource(R.string.intake_keep_conf)
            }
            val highlight = !warn && suggested == CardMode.POTVRDA
            TextButton(onClick = { onPick(dismissMode) }) {
                Text(
                    dismissLabel,
                    color = if (highlight) Palette.Green else Palette.Muted,
                    fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
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
        title = { Text(stringResource(R.string.delete_title, count), color = Palette.Text) },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { alsoFiles = !alsoFiles },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = alsoFiles, onCheckedChange = { alsoFiles = it })
                    Text(stringResource(R.string.delete_also_files), color = Palette.Text, fontSize = 13.sp)
                }
                if (alsoFiles) {
                    Text(
                        stringResource(R.string.delete_counts, count, confirmations, qrs),
                        color = Palette.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(alsoFiles) }) {
                Text(stringResource(R.string.action_delete), color = Palette.Red, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_otkazi), color = Palette.Muted) }
        }
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
        title = { Text(stringResource(R.string.purge_title), color = Palette.Text) },
        text = {
            Text(
                stringResource(R.string.purge_text, bills, confirmations, qrs),
                color = Palette.Muted, fontSize = 13.sp, lineHeight = 18.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.purge_continue), color = Palette.Red) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_otkazi), color = Palette.Muted) }
        }
    )
}

@Composable
private fun PurgeFinalDialog(onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var alsoPayees by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Card,
        title = { Text(stringResource(R.string.purge_final_title), color = Palette.Red, fontWeight = FontWeight.Bold) },
        text = {
            Row(
                Modifier.fillMaxWidth().clickable { alsoPayees = !alsoPayees },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = alsoPayees, onCheckedChange = { alsoPayees = it })
                Text(stringResource(R.string.purge_also_payees), color = Palette.Text, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(alsoPayees) }) {
                Text(stringResource(R.string.purge_final_confirm), color = Palette.Red, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_otkazi), color = Palette.Muted) }
        }
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
    Surface(color = Palette.Card2, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClear) { Text("✕", color = Palette.Muted, fontSize = 16.sp) }
            Text(
                "$count",
                color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 2.dp)
            )
            TextButton(onClick = onSelectAll) {
                Text(stringResource(R.string.select_all), color = Palette.Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            if (showReport) {
                TextButton(onClick = onReport) {
                    Text("📄", color = Palette.Blue, fontSize = 15.sp)
                }
            }
            TextButton(onClick = onShare) {
                Text("📤 " + stringResource(R.string.btn_podeli), color = Palette.Blue, fontSize = 13.sp)
            }
            TextButton(onClick = onDelete) {
                Text("🗑 " + stringResource(R.string.action_delete), color = Palette.Red, fontSize = 13.sp)
            }
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
                Text(
                    text,
                    color = Palette.Text, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                    lineHeight = 19.sp, modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("⧉ " + stringResource(R.string.report_copy), Palette.Muted, Modifier.weight(1f)) { onCopy() }
                ActionButton("📤 " + stringResource(R.string.btn_podeli), Palette.Blue, Modifier.weight(1f)) { onShare() }
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
                Text(".", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Amber)
            }
            Text(stringResource(R.string.subtitle), fontSize = 12.sp, color = Palette.Muted)
        }
        TextButton(onClick = onSettings) {
            Text("⚙", color = Palette.Muted, fontSize = 18.sp)
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
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.files_in_folder),
                color = Palette.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.refresh), color = Palette.Dim, fontSize = 12.sp)
            }
            Box {
                TextButton(onClick = {
                    // Bills: a unified menu (scan / file / photo). Confirmations:
                    // straight to the file picker (PDF or image).
                    if (isBills) menuOpen = true else onPickFile()
                }) {
                    Text("➕ " + stringResource(pickerLabelRes), color = Palette.Blue, fontSize = 12.sp)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    // 8d: live camera scanning.
                    DropdownMenuItem(
                        text = {
                            Text("📷 " + stringResource(R.string.add_scan), color = Palette.Text, fontSize = 13.sp)
                        },
                        onClick = { menuOpen = false; onScan() }
                    )
                    DropdownMenuItem(
                        text = { Text("📄 " + stringResource(R.string.add_from_file), color = Palette.Text, fontSize = 13.sp) },
                        onClick = { menuOpen = false; onPickFile() }
                    )
                    DropdownMenuItem(
                        text = { Text("🖼 " + stringResource(R.string.add_photo), color = Palette.Text, fontSize = 13.sp) },
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
        files.forEach { row ->
            // v1.5.1 Change 2: an already-processed file cannot be checked again —
            // tag „već obrađen", checkbox disabled, row not clickable.
            val rowModifier =
                if (row.processed) Modifier.fillMaxWidth().padding(vertical = 2.dp)
                else Modifier.fillMaxWidth().clickable { onToggle(row.uriString) }.padding(vertical = 2.dp)
            Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = row.uriString in selected,
                    onCheckedChange = { onToggle(row.uriString) },
                    enabled = !row.processed
                )
                Text(
                    row.name,
                    color = if (row.processed) Palette.Dim else Palette.Text,
                    fontSize = 13.sp, fontFamily = FontFamily.Monospace,
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
        if (files.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onProcess,
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Amber, contentColor = Palette.Bg,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Card(
    item: CardItem,
    vm: MainViewModel,
    onEdit: (String) -> Unit,
    onAttach: () -> Unit,
    reportSelected: Boolean = false,
    onReportToggle: (() -> Unit)? = null
) {
    Surface(
        color = Palette.Card,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Palette.Line),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            // top row: (report checkbox) + original name + badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onReportToggle != null) {
                    Checkbox(checked = reportSelected, onCheckedChange = { onReportToggle() })
                }
                Text(
                    item.origName,
                    color = Palette.Dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Badge(item)
            }
            if (item.status == CardStatus.ERROR) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.error_read), color = Palette.Muted, fontSize = 13.sp)
                return@Column
            }
            Spacer(Modifier.height(8.dp))

            // filename segments
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (item.mode == CardMode.POTVRDA) {
                    Seg("uplata", Palette.Green, missing = false, onClick = null)
                    Underscore()
                }
                Seg(item.provider.ifEmpty { stringResource(R.string.seg_provider_missing) },
                    Palette.Amber, item.provider.isEmpty(), suggested = item.providerSuggested) { onEdit("provider") }
                Underscore()
                Seg(item.address.ifEmpty { stringResource(R.string.seg_address_missing) },
                    Palette.Blue, item.address.isEmpty(), suggested = item.addressSuggested) { onEdit("address") }
                Underscore()
                Seg(item.month?.let { Months.token(it) } ?: stringResource(R.string.seg_month_missing),
                    Palette.Violet, item.month == null) { onEdit("month") }
                Underscore()
                Seg(item.amount?.toString() ?: stringResource(R.string.seg_amount_missing),
                    Palette.Green, item.amount == null) { onEdit("amount") }
                // Change 5: recipient-account checksum result, next to the amount.
                if (item.accountVerified) VerifiedTick()
                Text(
                    "." + item.currentName.substringAfterLast('.', "pdf"),
                    color = Palette.Dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            // Change 6: legend for any payee-memory-prefilled segment above.
            if (item.providerSuggested || item.addressSuggested) {
                Text(
                    "• " + stringResource(R.string.suggested),
                    color = Palette.Violet, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp)
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

            // QR preview
            var showQr by remember(item.id) { mutableStateOf(false) }
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
                if (item.qrGenerated) {
                    Text(
                        stringResource(R.string.qr_disclaimer_short),
                        color = Palette.Amber, fontSize = 11.sp, lineHeight = 15.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // v1.3 Change 3: attaching a confirmation is THE primary action on a bill
            if (item.mode == CardMode.RACUN && !item.paired) {
                ActionButton(
                    "➕ " + stringResource(R.string.btn_add_potvrda),
                    Palette.Green,
                    Modifier.fillMaxWidth()
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

            // actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.mode == CardMode.RACUN && item.hasQr) {
                    ActionButton("🔳 " + stringResource(R.string.btn_qr_slika), Palette.Violet, Modifier.weight(1f)) {
                        vm.saveQrToGallery(item.id)
                    }
                }
                ActionButton("📤 " + stringResource(R.string.btn_podeli), Palette.Blue, Modifier.weight(1f)) {
                    vm.shareBill(item.id) // v1.4.5: paid bill shares both files
                }
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
                        Text(
                            "✅ $confName",
                            color = Palette.Green, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (confUri != null) {
                            TextButton(onClick = {
                                val mime = if (confName.endsWith(".pdf", true))
                                    "application/pdf" else "image/*"
                                vm.share(listOf(confUri), listOf(confName), mime)
                            }) {
                                Text(
                                    "📤 " + stringResource(R.string.btn_podeli),
                                    color = Palette.Blue, fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // links row
            if (item.mode == CardMode.RACUN) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (item.hasQr) {
                        LinkButton(stringResource(if (showQr) R.string.hide_qr else R.string.show_qr)) {
                            showQr = !showQr
                        }
                    } else {
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

@Composable
private fun Seg(
    text: String,
    color: Color,
    missing: Boolean,
    suggested: Boolean = false,
    onClick: (() -> Unit)?
) {
    val shape = RoundedCornerShape(9.dp)
    // A "predloženo" (payee-memory) value is tinted violet with a violet border
    // so the user sees it was filled for them and can safely overwrite it.
    val borderColor = when {
        missing -> Palette.Red
        suggested -> Palette.Violet
        else -> Palette.Line
    }
    var m = Modifier
        .background(Palette.Card2, shape)
        .border(1.dp, borderColor, shape)
    if (onClick != null) m = m.clickable { onClick() }
    Text(
        text,
        color = if (missing) Palette.Red else if (suggested) Palette.Violet else color,
        fontSize = 14.sp, fontFamily = FontFamily.Monospace,
        modifier = m.padding(horizontal = 9.dp, vertical = 5.dp)
    )
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
            Text(
                "🔳 " + stringResource(R.string.btn_make_qr),
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
    Text(
        "✓",
        color = Palette.Green,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(start = 4.dp)
            .semantics { contentDescription = "verified" }
    )
}

@Composable
private fun Underscore() {
    Text("_", color = Palette.Dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
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

@Composable
private fun ActionButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.14f), contentColor = color
        )
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
                ChipFlow(ProviderDetector.PROVIDERS, Palette.Amber) { value = it; onSave(it) }
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
                "➕ " + stringResource(R.string.system_picker),
                Palette.Blue,
                Modifier.fillMaxWidth()
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
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
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
                TextButton(onClick = { dismissIme(); onDismiss() }) {
                    Text("✕", color = Palette.Muted, fontSize = 17.sp)
                }
            }
            Spacer(Modifier.height(10.dp))

            // language
            Text(stringResource(R.string.settings_language), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LangChip(stringResource(R.string.lang_sr), state.language == "sr") {
                    vm.setLanguage("sr")
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("sr"))
                }
                LangChip(stringResource(R.string.lang_en), state.language == "en") {
                    vm.setLanguage("en")
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                }
                LangChip(stringResource(R.string.lang_system), state.language == "system") {
                    vm.setLanguage("system")
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                }
            }
            Spacer(Modifier.height(18.dp))

            // storage location
            Text(stringResource(R.string.settings_folder), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted)
            Spacer(Modifier.height(4.dp))
            Text(
                state.locationLabel,
                fontSize = 12.sp, color = Palette.Blue, fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(8.dp))
            ActionButton(
                stringResource(R.string.settings_choose_location),
                Palette.Muted, Modifier.fillMaxWidth()
            ) { treeLauncher.launch(null) }
            Spacer(Modifier.height(18.dp))

            // addresses
            Text(stringResource(R.string.settings_addresses), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted)
            Spacer(Modifier.height(4.dp))
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
                    TextButton(onClick = { rows.removeAt(i) }) {
                        Text("✕", color = Palette.Dim, fontSize = 15.sp)
                    }
                }
            }
            TextButton(onClick = {
                dismissIme() // no lingering keyboard after adding a row
                rows.add("" to "")
            }) {
                Text(stringResource(R.string.btn_add_address), color = Palette.Blue, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))

            // provider label overrides
            Text(stringResource(R.string.settings_providers), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted)
            Spacer(Modifier.height(6.dp))
            for (i in overrides.indices) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(
                        overrides[i].first,
                        color = Palette.Amber, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
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
            Spacer(Modifier.height(18.dp))
            ActionButton(
                stringResource(R.string.settings_clear_history),
                Palette.Red, Modifier.fillMaxWidth()
            ) { vm.clearHistory() }
            Spacer(Modifier.height(8.dp))
            ActionButton(
                stringResource(R.string.settings_clear_payees),
                Palette.Red, Modifier.fillMaxWidth()
            ) { vm.clearPayees() }
            Spacer(Modifier.height(8.dp))
            // Change 5: destructive purge — tucked in Settings, double-confirmed.
            ActionButton(
                stringResource(R.string.settings_purge),
                Palette.Red, Modifier.fillMaxWidth()
            ) { onPurge() }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(stringResource(R.string.btn_otkazi), Palette.Muted, Modifier.weight(1f)) { onDismiss() }
                ActionButton(stringResource(R.string.btn_sacuvaj), Palette.Blue, Modifier.weight(1f)) {
                    vm.saveAddressRows(rows.toList())
                    vm.saveProviderOverrides(overrides.toMap())
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
                    Modifier.fillMaxWidth().clickable { rememberBind = !rememberBind },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = rememberBind, onCheckedChange = { rememberBind = it })
                    Text(stringResource(R.string.space_tag_remember), color = Palette.Text, fontSize = 12.sp)
                }
            } else {
                Spacer(Modifier.height(6.dp))
            }
            ActionButton(
                stringResource(R.string.btn_sacuvaj), Palette.Amber, Modifier.fillMaxWidth()
            ) { if (value.isNotBlank()) onApply(value, canRemember && rememberBind) }
        }
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

@Composable
private fun LangChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 12.sp) }
    )
}

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
