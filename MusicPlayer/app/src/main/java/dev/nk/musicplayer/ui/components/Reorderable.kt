package dev.nk.musicplayer.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Minimal long-press drag-to-reorder for a LazyColumn. Small enough not to be worth a
 * third-party dependency: it tracks which visible item is under the finger and swaps as the
 * dragged row's midpoint crosses a neighbour.
 *
 * Only works for lists whose LazyColumn contains nothing but the reorderable rows, since it
 * maps list indices straight onto data indices.
 */
class ReorderState(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    var dragOffset by mutableFloatStateOf(0f)
        private set

    private val draggingItem
        get() = draggingIndex?.let { index ->
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        }

    fun onDragStart(offsetY: Float) {
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { offsetY.toInt() in it.offset..(it.offset + it.size) }
            ?.let {
                draggingIndex = it.index
                dragOffset = 0f
            }
    }

    fun onDrag(deltaY: Float) {
        dragOffset += deltaY
        val item = draggingItem ?: return
        val start = item.offset + dragOffset
        val middle = start + item.size / 2f

        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
            candidate.index != item.index &&
                middle.toInt() in candidate.offset..(candidate.offset + candidate.size)
        } ?: return

        onMove(item.index, target.index)
        // Keep the row visually under the finger after the underlying list shifts.
        dragOffset += (item.offset - target.offset).toFloat()
        draggingIndex = target.index
    }

    fun onDragEnd() {
        draggingIndex = null
        dragOffset = 0f
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    onMove: (Int, Int) -> Unit
): ReorderState = remember(listState) { ReorderState(listState, onMove) }

/** Apply to the LazyColumn itself; the drag begins on a long press anywhere in a row. */
fun Modifier.reorderable(state: ReorderState): Modifier = this.pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> state.onDragStart(offset.y) },
        onDrag = { change, dragAmount ->
            change.consume()
            state.onDrag(dragAmount.y)
        },
        onDragEnd = { state.onDragEnd() },
        onDragCancel = { state.onDragEnd() }
    )
}
