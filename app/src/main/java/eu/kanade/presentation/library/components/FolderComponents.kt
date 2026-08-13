package eu.kanade.presentation.library.components

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource as composeStringResource

private val FolderTileWidth = 100.dp

/**
 * A single folder tile: a cover with the folder name below and a lock badge when protected.
 */
@Composable
fun FolderGridItem(
    folder: Category,
    coverModel: Any?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Column(
        modifier = modifier
            .width(FolderTileWidth)
            .clip(MaterialTheme.shapes.small)
            .then(
                if (highlighted) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small,
                    )
                } else {
                    Modifier
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
    ) {
        Box {
            MangaCover.Book(
                modifier = Modifier.fillMaxWidth(),
                data = coverModel,
            )
            if (folder.locked) {
                Surface(
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.TopEnd),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
        }
        Text(
            text = folder.name,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Horizontal strip of folder tiles shown at the top of the library root.
 */
@Composable
fun FolderStrip(
    folders: List<Category>,
    getCoverModel: (Category) -> Any?,
    onFolderClick: (Category) -> Unit,
    onFolderLongClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
    dragState: LibraryDragState? = null,
) {
    Column(modifier = modifier.padding(vertical = MaterialTheme.padding.small)) {
        Text(
            text = composeStringResource(SYMR.strings.folders),
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
            style = MaterialTheme.typography.titleSmall,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            items(items = folders, key = { it.id }) { folder ->
                FolderGridItem(
                    folder = folder,
                    coverModel = getCoverModel(folder),
                    onClick = { onFolderClick(folder) },
                    onLongClick = { onFolderLongClick(folder) },
                    modifier = if (dragState != null) {
                        Modifier.folderDropTarget(folder.id, dragState)
                    } else {
                        Modifier
                    },
                    highlighted = dragState?.hoveredFolderId == folder.id,
                )
            }
        }
    }
}

/**
 * The view shown when browsing inside a folder: a header with back/edit/delete and the folder's items.
 */
@Composable
fun FolderContent(
    folder: Category,
    items: List<LibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onExit: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClickManga: (LibraryManga) -> Unit,
    onLongClickManga: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = composeStringResource(MR.strings.action_bar_up_description),
                )
            }
            Text(
                text = folder.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MaterialTheme.padding.small),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = composeStringResource(MR.strings.action_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = composeStringResource(MR.strings.action_delete))
            }
        }

        LibraryComfortableGrid(
            items = items,
            columns = columns,
            contentPadding = contentPadding,
            selection = selection,
            onClick = onClickManga,
            onLongClick = onLongClickManga,
            onClickContinueReading = onClickContinueReading,
            searchQuery = null,
            onGlobalSearchClicked = {},
        )
    }
}
