package com.natam.cs50mobile.ui

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.natam.cs50mobile.R
import com.natam.cs50mobile.core.files.WorkspaceFiles

private sealed interface FileDialog {
    data object NewFile : FileDialog
    data object NewFolder : FileDialog
    data class Delete(val entry: WorkspaceFiles.Entry) : FileDialog
}

@Composable
fun FilesScreen(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val dir by vm.dir.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val announcement by vm.announcement.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var dialog by remember { mutableStateOf<FileDialog?>(null) }
    val atRoot = vm.isRoot(dir)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.files_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.files_path, vm.label(dir)),
            style = MaterialTheme.typography.bodyMedium
        )
        if (announcement.text.isNotEmpty()) LiveText(announcement, maxLines = 3)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!atRoot) {
                ActionButton(R.string.btn_up, R.string.desc_up, Modifier.weight(1f), vm::goUp)
            }
            ActionButton(
                R.string.btn_new_file, R.string.desc_new_file, Modifier.weight(1f)
            ) { dialog = FileDialog.NewFile }
            ActionButton(
                R.string.btn_new_folder, R.string.desc_new_folder, Modifier.weight(1f)
            ) { dialog = FileDialog.NewFolder }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.files_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            items(entries, key = { it.file.path }) { entry ->
                val openLabel = stringResource(R.string.action_open)
                val deleteLabel = stringResource(R.string.action_delete)
                val description = if (entry.isDirectory) {
                    stringResource(R.string.entry_folder_desc, entry.name)
                } else {
                    stringResource(
                        R.string.entry_file_desc,
                        entry.name,
                        Formatter.formatShortFileSize(context, entry.size)
                    )
                }
                Text(
                    text = if (entry.isDirectory) entry.name + "/" else entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = openLabel) {
                            if (entry.isDirectory) vm.openDirectory(entry.file) else vm.openFile(entry.file)
                        }
                        .semantics {
                            contentDescription = description
                            // Excluir fica no menu de ações do TalkBack, sem um botão a mais em cada linha.
                            customActions = listOf(
                                CustomAccessibilityAction(deleteLabel) {
                                    dialog = FileDialog.Delete(entry)
                                    true
                                }
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 14.dp)
                )
            }
        }
    }

    when (val d = dialog) {
        FileDialog.NewFile -> NameDialog(
            title = R.string.dialog_new_file_title,
            onConfirm = { name ->
                dialog = null
                vm.createFile(name)
            },
            onDismiss = { dialog = null }
        )
        FileDialog.NewFolder -> NameDialog(
            title = R.string.dialog_new_folder_title,
            onConfirm = { name ->
                dialog = null
                vm.createFolder(name)
            },
            onDismiss = { dialog = null }
        )
        is FileDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.dialog_delete_title, d.entry.name)) },
            text = { Text(stringResource(R.string.dialog_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    vm.delete(d.entry)
                }) { Text(stringResource(R.string.dialog_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
        null -> Unit
    }
}

@Composable
private fun NameDialog(
    @StringRes title: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // O rótulo é o nome acessível do campo (contentDescription esconderia o texto digitado).
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dialog_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                )
                Text(
                    text = stringResource(R.string.dialog_name_hint),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text(stringResource(R.string.dialog_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}
