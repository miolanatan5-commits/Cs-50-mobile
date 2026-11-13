package com.natam.cs50mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.natam.cs50mobile.R
import com.natam.cs50mobile.core.runtime.RuntimeState

@Composable
fun SetupScreen(vm: TerminalViewModel, state: RuntimeState, modifier: Modifier = Modifier) {
    var url by rememberSaveable { mutableStateOf(vm.rootfsUrl) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )

        when (state) {
            RuntimeState.Checking, RuntimeState.Ready ->
                Text(stringResource(R.string.setup_checking))

            RuntimeState.MissingProot -> {
                Text(stringResource(R.string.setup_missing_proot))
                OutlinedButton(
                    onClick = vm::refreshRuntime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) { Text(stringResource(R.string.setup_check_again)) }
            }

            RuntimeState.NotInstalled, is RuntimeState.Failed -> {
                if (state is RuntimeState.Failed) {
                    Text(
                        text = stringResource(R.string.setup_failed, state.message),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                    )
                }
                Text(stringResource(R.string.setup_intro))
                // O rótulo do campo é o nome acessível. Um contentDescription aqui esconderia do
                // TalkBack o texto digitado.
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.setup_url_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { vm.installRuntime(url) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (state is RuntimeState.Failed) R.string.setup_retry else R.string.setup_install
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            is RuntimeState.Downloading -> {
                // O texto muda em degraus (10% ou 5 MB): o TalkBack fala poucas vezes, não a cada byte.
                val label = if (state.total > 0) {
                    val percent = (state.bytes * 100 / state.total).toInt() / 10 * 10
                    stringResource(R.string.setup_downloading_percent, percent)
                } else {
                    val mb = (state.bytes / (1024 * 1024)).toInt() / 5 * 5
                    stringResource(R.string.setup_downloading_mb, mb)
                }
                Text(
                    text = label,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                if (state.total > 0) {
                    LinearProgressIndicator(
                        progress = { state.bytes.toFloat() / state.total },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                CancelButton(vm)
            }

            is RuntimeState.Extracting -> {
                Text(
                    text = stringResource(R.string.setup_extracting, state.entries / 500 * 500),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                CancelButton(vm)
            }
        }
    }
}

@Composable
private fun CancelButton(vm: TerminalViewModel) {
    OutlinedButton(
        onClick = vm::cancelInstall,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) { Text(stringResource(R.string.setup_cancel)) }
}
