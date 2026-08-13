package eu.kanade.presentation.library.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover as MangaCoverComposable
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover
import kotlin.math.roundToInt

/**
 * Shared state for dragging a library series onto a folder tile.
 *
 * Coordinates are all expressed in the window/root space so the grid (drag source)
 * and the folder strip (drop targets) can hit-test against each other even though
 * they live in separate lazy layouts.
 */
@Stable
class LibraryDragState {
    var dragging by mutableStateOf(false)
        private set
    var moved by mutableStateOf(false)
        private set
    var draggedItem by mutableStateOf<LibraryItem?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var hoveredFolderId by mutableStateOf<Long?>(null)
        private set

    private val folderBounds = mutableMapOf<Long, Rect>()

    fun setFolderBounds(id: Long, rect: Rect) {
        folderBounds[id] = rect
    }

    fun start(item: LibraryItem, at: Offset) {
        draggedItem = item
        pointer = at
        dragging = true
        moved = false
        hoveredFolderId = null
    }

    fun update(at: Offset) {
        pointer = at
        moved = true
        hoveredFolderId = folderBounds.entries.firstOrNull { it.value.contains(at) }?.key
    }

    /** Ends the drag and returns the folder id under the pointer, if any. */
    fun end(): Long? {
        val target = hoveredFolderId
        dragging = false
        moved = false
        draggedItem = null
        hoveredFolderId = null
        return target
    }
}

/** Registers a folder tile as a drop target reporting its bounds in root space. */
fun Modifier.folderDropTarget(folderId: Long, state: LibraryDragState): Modifier =
    this.onGloballyPositioned { coords ->
        if (coords.isAttached) state.setFolderBounds(folderId, coords.boundsInRoot())
    }

/**
 * Makes a library grid/list item draggable onto a folder after a long press.
 *
 * A stationary long press (no movement) falls back to [onLongPress] so normal
 * selection keeps working; a long press followed by a drag onto a folder tile
 * triggers [onDrop]. Vertical scrolling is unaffected (it starts before the
 * long-press timeout).
 */
fun Modifier.libraryDragSource(
    item: LibraryItem,
    state: LibraryDragState,
    onDrop: (Long, LibraryManga) -> Unit,
    onLongPress: () -> Unit,
): Modifier = composed {
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { rootOffset = it.positionInRoot() }
        .pointerInput(item.libraryManga.manga.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { local -> state.start(item, rootOffset + local) },
                onDrag = { change, amount ->
                    change.consume()
                    state.update(state.pointer + amount)
                },
                onDragEnd = {
                    val moved = state.moved
                    val target = state.end()
                    when {
                        target != null -> onDrop(target, item.libraryManga)
                        !moved -> onLongPress()
                    }
                },
                onDragCancel = { state.end() },
            )
        }
}

/** Floating cover that follows the pointer while dragging a series. */
@androidx.compose.runtime.Composable
fun DragGhost(
    state: LibraryDragState,
    containerOrigin: Offset,
) {
    val item = state.draggedItem
    if (!state.dragging || !state.moved || item == null) return
    val manga = item.libraryManga.manga
    val ghostSize = 72.dp
    val local = state.pointer - containerOrigin
    MangaCoverComposable.Book(
        modifier = Modifier
            .size(ghostSize)
            .offset { IntOffset((local.x - 36.dp.toPx()).roundToInt(), (local.y - 36.dp.toPx()).roundToInt()) }
            .alpha(0.85f),
        data = MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            ogUrl = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        ),
    )
}
