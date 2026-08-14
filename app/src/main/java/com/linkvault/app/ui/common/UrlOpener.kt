package com.linkvault.app.ui.common

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.linkvault.app.LinkCaptureActivity

/**
 * Opens [url] via the system's default handler (ACTION_VIEW).
 *
 * Now that LinkVault registers its own ACTION_VIEW/browsable intent-filter
 * (LinkCaptureActivity — so it can be picked as a link handler, e.g. in
 * LinkSheet or Android's default-browser list), a plain implicit intent
 * here is *technically* eligible to resolve back to LinkVault itself. In
 * practice that's essentially never what happens — if there's a configured
 * default browser (which is the normal case, and exactly the setup
 * described when this was added), intent resolution goes straight to it
 * silently, same as before, no regression. This only falls back to
 * building a filtered chooser in the narrow case where resolution would
 * otherwise land on LinkVault itself, so tapping "open" on an
 * already-saved bookmark can never just loop back into LinkVault's own
 * save screen.
 */
fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    val resolvesToSelf = intent.resolveActivity(context.packageManager)?.packageName == context.packageName

    val intentToLaunch = if (resolvesToSelf) {
        Intent.createChooser(intent, null).apply {
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(context, LinkCaptureActivity::class.java))
            )
        }
    } else {
        intent
    }

    try {
        context.startActivity(intentToLaunch)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this link", Toast.LENGTH_SHORT).show()
    }
}
