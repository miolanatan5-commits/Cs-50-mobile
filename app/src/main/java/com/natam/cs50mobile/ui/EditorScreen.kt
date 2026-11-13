package com.natam.cs50mobile.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.natam.cs50mobile.R
import com.natam.cs50mobile.core.editor.LineText

@Composable
fun EditorScreen(
    vm: EditorViewModel,
    onRunStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val file by vm.open.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    val announcement by vm.announcement.collectAsStateWithLifecycle()
    val diagnostics by vm.diagnostics.collectAsStateWithLifecycle()
    val result by vm.buildResult.collectAsStateWithLifecycle()
    val spell by vm.spellSymbols.collectAsStateWithLifecycle()

    // Se o app for para o fundo, o trabalho já está salvo.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.saveQuietly() }

    var lineInput by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    val current = file ?: return
    val lineIndex = LineText.lineOfOffset(text.text, text.selection.start)
    val lineTotal = LineText.lineCount(text.text)

    val goToLine = {
        val number = lineInput.trim().toIntOrNull()
        if (number != null) vm.goToLine(number)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(R.string.btn_close_file, R.string.desc_close_file, Modifier, vm::closeFile)
            Text(
                text = current.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            ActionButton(R.string.btn_save, R.string.desc_save, Modifier, vm::save)
        }

        val runDescription = stringResource(R.string.desc_run)
        Button(
            onClick = { vm.run(onRunStarted) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .semantics { contentDescription = runDescription }
        ) {
            Text(text = stringResource(R.string.btn_run), style = MaterialTheme.typography.titleMedium)
        }

        if (announcement.text.isNotEmpty()) LiveText(announcement, maxLines = 4)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(R.string.btn_prev_line, R.string.desc_prev_line, Modifier.weight(1f)) {
                vm.moveLine(-1)
            }
            ActionButton(R.string.btn_read_line, R.string.desc_read_line, Modifier.weight(1f), vm::readLine)
            ActionButton(R.string.btn_next_line, R.string.desc_next_line, Modifier.weight(1f)) {
                vm.moveLine(1)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = lineInput,
                onValueChange = { lineInput = it.filter(Char::isDigit).take(6) },
                label = { Text(stringResource(R.string.line_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { goToLine() }),
                modifier = Modifier.weight(1f)
            )
            ActionButton(R.string.btn_go_line, R.string.desc_go_line, Modifier) { goToLine() }
        }

        Text(
            text = stringResource(R.string.line_status, lineIndex + 1, lineTotal),
            style = MaterialTheme.typography.bodySmall
        )

        // O rótulo é o nome acessível do campo (contentDescription esconderia o texto digitado).
        OutlinedTextField(
            value = text,
            onValueChange = vm::onTextChange,
            label = { Text(stringResource(R.string.editor_label, current.name)) },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 560.dp)
        )

        // Digitar { } ; # no teclado do celular com TalkBack é lento: atalhos de um toque.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val indentDescription = stringResource(R.string.desc_indent)
            OutlinedButton(
                onClick = { vm.insert("    ") },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = indentDescription }
            ) { Text(stringResource(R.string.btn_indent)) }

            for ((symbol, spokenId) in LineText.SYMBOLS) {
                val spokenName = stringResource(spokenId)
                OutlinedButton(
                    onClick = { vm.insert(symbol) },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = spokenName }
                ) { Text(symbol) }
            }
        }

        val spellLabel = stringResource(R.string.spell_switch)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = spell, role = Role.Switch, onValueChange = vm::setSpellSymbols)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = spellLabel,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = spell, onCheckedChange = null)
        }

        result?.let {
            Text(
                text = stringResource(R.string.build_result, it.errors, it.warnings),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        if (diagnostics.isNotEmpty()) {
            val errorsDescription = stringResource(R.string.desc_errors, diagnostics.size)
            OutlinedButton(
                onClick = { showErrors = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = errorsDescription }
            ) { Text(stringResource(R.string.btn_errors, diagnostics.size)) }
        }
    }

    if (showErrors) {
        AlertDialog(
            onDismissRequest = { showErrors = false },
            title = { Text(stringResource(R.string.errors_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    for (d in diagnostics) {
                        val label = stringResource(
                            if (d.isError) R.string.errors_item_error else R.string.errors_item_warning,
                            d.line,
                            d.file.name,
                            d.message
                        )
                        TextButton(
                            onClick = {
                                showErrors = false
                                vm.openDiagnostic(d)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = label,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showErrors = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}
