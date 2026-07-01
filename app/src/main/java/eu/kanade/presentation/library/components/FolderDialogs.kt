package eu.kanade.presentation.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Create/edit folder dialog: name, optional biometric lock, and (edit only) a cover picker.
 */
@Composable
fun FolderEditDialog(
    folder: Category?,
    biometricSupported: Boolean,
    coverModel: Any?,
    onDismissRequest: () -> Unit,
    onPickCover: () -> Unit,
    onConfirm: (name: String, locked: Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(folder?.name ?: "") }
    var locked by rememberSaveable { mutableStateOf(folder?.locked ?: false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(if (folder == null) SYMR.strings.folder_new else SYMR.strings.folder_edit),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(SYMR.strings.folder_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name, locked) }),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (folder != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MangaCover.Book(
                            data = coverModel,
                            modifier = Modifier.width(64.dp),
                        )
                        OutlinedButton(
                            onClick = onPickCover,
                            modifier = Modifier.padding(start = MaterialTheme.padding.medium),
                        ) {
                            Text(stringResource(SYMR.strings.folder_cover_edit))
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = biometricSupported) { locked = !locked },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(SYMR.strings.folder_lock))
                        if (!biometricSupported) {
                            Text(
                                text = stringResource(SYMR.strings.folder_lock_unsupported),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = locked && biometricSupported,
                        onCheckedChange = { locked = it },
                        enabled = biometricSupported,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, locked) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

/**
 * Single-select folder chooser used by the "Move to folder" selection action. Choosing "None"
 * moves the manga back to the library root.
 */
@Composable
fun MoveToFolderDialog(
    folders: List<Category>,
    onDismissRequest: () -> Unit,
    onConfirm: (folderId: Long?) -> Unit,
) {
    var selectedId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(SYMR.strings.action_move_to_folder)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FolderRadioRow(
                    label = stringResource(SYMR.strings.move_to_root),
                    selected = selectedId == null,
                    onClick = { selectedId = null },
                )
                folders.forEach { folder ->
                    FolderRadioRow(
                        label = folder.name,
                        selected = selectedId == folder.id,
                        onClick = { selectedId = folder.id },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedId) }) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun FolderRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = MaterialTheme.padding.small))
    }
}
