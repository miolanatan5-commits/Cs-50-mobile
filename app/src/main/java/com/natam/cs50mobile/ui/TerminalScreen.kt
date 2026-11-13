package com.natam.cs50mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.natam.cs50mobile.R
import com.natam.cs50mobile.core.terminal.TerminalSession

@Composable
fun TerminalScreen(vm: TerminalViewModel, modifier: Modifier = Modifier) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val status by vm.terminalStatus.collectAsStateWithLifecycle()
    val announcement by vm.announcement.collectAsStateWithLifecycle()
    val announceOn by vm.announceEnabled.collectAsStateWithLifecycle()
    val cs50Installed by vm.cs50Installed.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.ensureStarted() }

    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }

    // Só acompanha o fim da saída enquanto o usuário não tiver rolado para cima.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canForward) -> if (scrolling) follow = !canForward }
    }
    val itemCount = snapshot.lines.size + 1
    LaunchedEffect(snapshot.version) {
        if (follow) listState.scrollToItem(itemCount - 1)
    }

    val submit = {
        val text = input
        input = ""
        vm.sendLine(text)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = statusText(status),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )

        if (cs50Installed) {
            ActionButton(
                R.string.btn_cs50_update, R.string.desc_cs50_update,
                Modifier.fillMaxWidth(), vm::installCs50Tools
            )
        } else {
            ActionButton(
                R.string.btn_cs50_install, R.string.desc_cs50_install,
                Modifier.fillMaxWidth(), vm::installCs50Tools
            )
        }

        // Saída completa: cada linha é um item que o TalkBack percorre com deslizes.
        // NÃO é região ao vivo (isso falaria tudo). Quem fala é o cartão "Última saída".
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(snapshot.lines, key = { index, _ -> snapshot.firstIndex + index }) { _, line ->
                Text(
                    text = line.ifEmpty { " " },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "partial") {
                Text(
                    text = snapshot.partial.ifEmpty { " " },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        AnnouncementCard(
            announcement = announcement,
            title = stringResource(R.string.last_output_title),
            emptyText = stringResource(R.string.no_output)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // O rótulo é o nome acessível do campo (contentDescription esconderia o texto digitado).
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.input_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None
                ),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier.weight(1f)
            )
            val sendDescription = stringResource(R.string.desc_send)
            Button(
                onClick = { submit() },
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = sendDescription }
            ) { Text(stringResource(R.string.btn_send)) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(R.string.btn_interrupt, R.string.desc_interrupt, Modifier.weight(1f), vm::interrupt)
            ActionButton(R.string.btn_eof, R.string.desc_eof, Modifier.weight(1f), vm::endOfInput)
            ActionButton(R.string.btn_read_last, R.string.desc_read_last, Modifier.weight(1f), vm::readLast)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(R.string.btn_clear, R.string.desc_clear, Modifier.weight(1f), vm::clearOutput)
            ActionButton(R.string.btn_restart, R.string.desc_restart, Modifier.weight(1f), vm::restartSession)
        }

        val announceLabel = stringResource(R.string.announce_switch)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = announceOn,
                    role = Role.Switch,
                    onValueChange = vm::setAnnounceEnabled
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = announceLabel,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = announceOn, onCheckedChange = null)
        }
    }
}

@Composable
private fun statusText(status: TerminalSession.Status): String = when (status) {
    TerminalSession.Status.Idle -> stringResource(R.string.status_idle)
    TerminalSession.Status.Starting -> stringResource(R.string.status_starting)
    TerminalSession.Status.Running -> stringResource(R.string.status_running)
    is TerminalSession.Status.Exited -> stringResource(R.string.status_exited, status.code)
}
