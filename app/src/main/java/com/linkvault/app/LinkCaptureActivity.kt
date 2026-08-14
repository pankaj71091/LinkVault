package com.linkvault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkvault.app.ui.capture.SaveLinkScreen
import com.linkvault.app.ui.capture.SaveLinkViewModel
import com.linkvault.app.ui.capture.extractCapturedLink
import com.linkvault.app.ui.theme.LinkVaultTheme

/**
 * Registered in the manifest for two intent types, both with a dialog
 * theme so this always appears as a small floating window rather than a
 * switch into the full app:
 *  - ACTION_SEND (text/plain): the system share sheet, when another app's
 *    "Share" is used.
 *  - ACTION_VIEW (http/https, browsable): being picked as a link handler —
 *    e.g. LinkSheet's chooser, or Android's own "default browser" list.
 *    LinkVault isn't a browser, so picking it here just means "save this,
 *    don't open it" — the exact same save screen either way.
 *
 * Either way, does one job (save a link) and finishes, handing control
 * straight back rather than taking over the screen.
 */
class LinkCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val capturedLink = extractCapturedLink(intent)
        if (capturedLink == null) {
            // Nothing usable in the intent (e.g. empty shared text) — nothing to show.
            finish()
            return
        }

        val application = application as LinkVaultApplication

        setContent {
            LinkVaultTheme {
                val viewModel: SaveLinkViewModel = viewModel(
                    factory = SaveLinkViewModel.factory(application, capturedLink.url, capturedLink.title)
                )
                SaveLinkScreen(
                    viewModel = viewModel,
                    onSaved = { finish() },
                    onCancelled = { finish() }
                )
            }
        }
    }
}
