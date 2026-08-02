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
     */
    private fun handleShareIntent(intent: Intent?) {
        val i = intent ?: return
        val uris: List<Uri> = when (i.action) {
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
}
