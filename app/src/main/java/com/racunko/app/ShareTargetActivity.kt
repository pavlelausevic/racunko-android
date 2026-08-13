package com.racunko.app

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat

/**
 * The one door another app knocks on when it shares a file into Računko.
 *
 * Why a separate, invisible activity instead of a second intent-filter on
 * [MainActivity]: when the sender starts our activity, the *sender's* launch
 * flags decide where it lands. `launchMode="singleTask"` is supposed to find the
 * existing Računko task and reuse it, but that lookup can be defeated from the
 * outside (FLAG_ACTIVITY_MULTIPLE_TASK / NEW_DOCUMENT, a task whose recorded
 * affinity no longer matches the manifest, a `startActivityForResult` share) —
 * and when it is defeated the system builds a fresh task that shows up in
 * Recents underneath the sending app. That is the "second Računko" bug.
 *
 * This activity removes the sender from that decision entirely. It is a
 * trampoline: it takes the shared uris, then starts [MainActivity] itself. The
 * launch now comes from our own process with flags we control, and before it we
 * explicitly pull our own task to the front through [ActivityManager.AppTask],
 * which addresses the task by id and so does not depend on affinity matching at
 * all.
 *
 * It is invisible (translucent theme), kept out of Recents and finished in the
 * same frame, so it never appears anywhere the user can see.
 *
 * `taskAffinity=""` **is** set on this activity in the manifest — deliberately,
 * and only here. It keeps the trampoline out of the real Računko task. Do not
 * copy it back onto [MainActivity]: an empty affinity there is precisely what
 * broke singleTask in the first place.
 */
class ShareTargetActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = incomingUris(intent)
        if (uris.isNotEmpty()) {
            bringOwnTaskForward()
            deliver(uris)
        }
        finish()
    }

    /**
     * Hand the payload to [MainActivity], and never die trying.
     *
     * Re-granting a uri through ClipData makes the system verify that WE hold a
     * grant for it. A sender that hands over a uri we cannot pass on — a stale
     * one, or a provider that refused the grant — makes `startActivity` throw
     * SecurityException, which used to take the whole app down before the user
     * saw anything. So the grant is an attempt, not a requirement: without it
     * the uri still travels in the extras, and if the read then fails the
     * pipeline reports a bad file, which is a far better answer than a crash.
     */
    private fun deliver(uris: List<Uri>) {
        runCatching { startActivity(forwardIntent(uris, grant = true)) }
            .recoverCatching { startActivity(forwardIntent(uris, grant = false)) }
            // Last resort: no payload survived, but the user asked for Računko,
            // so at least open it instead of appearing to do nothing.
            .recoverCatching { startActivity(forwardIntent(emptyList(), grant = false)) }
    }

    /** The shared payload, whichever of the two SEND actions delivered it. */
    private fun incomingUris(intent: Intent?): List<Uri> {
        val i = intent ?: return emptyList()
        return when (i.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.filterNotNull() ?: emptyList()
            else -> emptyList()
        }
    }

    /**
     * Move the existing Računko task to the foreground by its own id. This is
     * the belt to singleTask's braces: it works even if the system would not
     * have matched the task by affinity. A no-op when the app isn't running —
     * the NEW_TASK launch below then creates the one and only task.
     */
    private fun bringOwnTaskForward() {
        val am = getSystemService(ActivityManager::class.java) ?: return
        val own = runCatching {
            am.appTasks.firstOrNull { task ->
                task.taskInfo.baseActivity?.className == MainActivity::class.java.name
            }
        }.getOrNull() ?: return
        runCatching { own.moveToFront() }
    }

    /**
     * With [grant] the uris are re-granted to [MainActivity] through ClipData —
     * extras alone carry no permission, so the receiving window would otherwise
     * see a uri it may not read. The grant we hold lasts as long as this
     * activity, which is long enough: it is created inside `startActivity`,
     * before `finish()`. Without [grant] the uris travel in the extras only —
     * see [deliver] for when that is worth trying.
     */
    private fun forwardIntent(uris: List<Uri>, grant: Boolean): Intent =
        Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHARED_IN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (uris.isNotEmpty()) {
                putParcelableArrayListExtra(MainActivity.EXTRA_SHARED_URIS, ArrayList(uris))
                if (grant) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newRawUri(null, uris[0]).apply {
                        for (i in 1 until uris.size) addItem(ClipData.Item(uris[i]))
                    }
                }
            }
        }
}
