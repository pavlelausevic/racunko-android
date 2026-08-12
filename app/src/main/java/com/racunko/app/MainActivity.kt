package com.racunko.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import com.racunko.app.ui.App
import com.racunko.app.ui.MainViewModel
import com.racunko.app.ui.RacunkoTheme

class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // rc2 Change 2: go edge-to-edge so Compose sees real IME insets — this is
        // what makes imePadding()/isImeVisible actually work (with adjustResize).
        // The screens already apply statusBars/navigationBars padding at their roots.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            RacunkoTheme {
                App(vm)
            }
        }
        // launchMode=singleTask: subsequent shares arrive through onNewIntent
        addOnNewIntentListener { handleShareIntent(it) }
        handleShareIntent(intent)
    }

    /**
     * Share target: a file shared from another app. v1.4.8 Change 2 — we no
     * longer assume "confirmation"; the ViewModel stages the uris and the UI
     * asks „Račun ili Potvrda?" in THIS same (singleTask) window via onNewIntent.
     *
     * v1.6: the share no longer arrives here straight from the sending app — it
     * comes through [ShareTargetActivity], which relaunches this window from our
     * own process so the sender's launch flags cannot spawn a second task. The
     * raw SEND actions are still read, because an older shortcut or a direct
     * component start may still deliver one.
     */
    private fun handleShareIntent(intent: Intent?) {
        val i = intent ?: return
        val uris: List<Uri> = when (i.action) {
            ACTION_SHARED_IN ->
                IntentCompat.getParcelableArrayListExtra(i, EXTRA_SHARED_URIS, Uri::class.java)
                    ?.filterNotNull() ?: emptyList()
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.filterNotNull() ?: emptyList()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            i.action = Intent.ACTION_MAIN // consume, so recreation doesn't reprocess
            vm.onSharedIn(uris)
        }
    }

    companion object {
        /** Internal hand-off from [ShareTargetActivity]; never in an intent-filter. */
        const val ACTION_SHARED_IN = "com.racunko.app.action.SHARED_IN"
        const val EXTRA_SHARED_URIS = "com.racunko.app.extra.SHARED_URIS"
    }
}
