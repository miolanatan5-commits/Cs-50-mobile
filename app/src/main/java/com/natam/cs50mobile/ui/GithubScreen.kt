package com.natam.cs50mobile.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.natam.cs50mobile.R

private const val KEYS_URL = "https://github.com/settings/ssh/new"
private const val SUBMIT_URL = "https://submit.cs50.io"

@Composable
fun GithubScreen(vm: GithubViewModel, onSentToTerminal: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val publicKey by vm.publicKey.collectAsStateWithLifecycle()

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf("") }
    var slug by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }

    val copiedText = stringResource(R.string.gh_copied)
    val noBrowserText = stringResource(R.string.gh_no_browser)
    val invalidText = stringResource(R.string.gh_invalid)
    val needNameText = stringResource(R.string.gh_need_name_email)
    val noKeyText = stringResource(R.string.gh_no_key)

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.gh_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(stringResource(R.string.gh_intro), style = MaterialTheme.typography.bodyMedium)

        if (message.isNotEmpty()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        Text(
            text = stringResource(R.string.gh_step_key),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.gh_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.gh_email_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None
            ),
            modifier = Modifier.fillMaxWidth()
        )
        ActionButton(R.string.gh_setup, R.string.gh_setup_desc, Modifier.fillMaxWidth()) {
            if (name.isBlank() || email.isBlank()) {
                message = needNameText
            } else {
                message = ""
                vm.setup(name, email)
                onSentToTerminal()
            }
        }

        Text(
            text = stringResource(R.string.gh_step_paste),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(
                if (publicKey != null) R.string.gh_key_ready else R.string.gh_key_missing
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        ActionButton(R.string.gh_copy, R.string.gh_copy_desc, Modifier.fillMaxWidth()) {
            vm.refreshKey()
            val key = publicKey
            if (key == null) {
                message = noKeyText
            } else {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ssh", key))
                message = copiedText
            }
        }
        ActionButton(R.string.gh_open_keys, R.string.gh_open_keys_desc, Modifier.fillMaxWidth()) {
            if (!openUrl(context, KEYS_URL)) message = noBrowserText
        }

        Text(
            text = stringResource(R.string.gh_step_test),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        ActionButton(R.string.gh_test, R.string.gh_test_desc, Modifier.fillMaxWidth()) {
            message = ""
            vm.testConnection()
            onSentToTerminal()
        }
        ActionButton(R.string.gh_open_submit, R.string.gh_open_submit_desc, Modifier.fillMaxWidth()) {
            if (!openUrl(context, SUBMIT_URL)) message = noBrowserText
        }

        Text(
            text = stringResource(R.string.gh_send_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(stringResource(R.string.gh_send_intro), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = folder,
            onValueChange = { folder = it },
            label = { Text(stringResource(R.string.gh_folder_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None
            ),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = slug,
            onValueChange = { slug = it },
            label = { Text(stringResource(R.string.gh_slug_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None
            ),
            modifier = Modifier.fillMaxWidth()
        )
        val send = { tool: String ->
            val cleanFolder = folder.trim()
            val cleanSlug = slug.trim()
            if (cleanSlug.isEmpty() || !GithubViewModel.isSafePath(cleanFolder) ||
                !GithubViewModel.isSafePath(cleanSlug)
            ) {
                message = invalidText
            } else {
                message = ""
                vm.runTool(tool, cleanFolder, cleanSlug)
                onSentToTerminal()
            }
        }
        ActionButton(R.string.gh_check, R.string.gh_check_desc, Modifier.fillMaxWidth()) { send("check50") }
        ActionButton(R.string.gh_submit, R.string.gh_submit_desc, Modifier.fillMaxWidth()) { send("submit50") }
    }
}

private fun openUrl(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}
