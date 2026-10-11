package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(items: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val scope = rememberCoroutineScope()
    LaunchedEffect(list, items.size) {
        snapshotFlow { list.firstVisibleItemIndex.coerceIn(0, items.lastIndex) }
            .distinctUntilChanged().collect { onSelected(it) }
    }
    LazyColumn(modifier.height(200.dp), state = list, contentPadding = PaddingValues(vertical = 80.dp),
        flingBehavior = rememberSnapFlingBehavior(lazyListState = list)) {
        itemsIndexed(items) { index, label ->
            Box(Modifier.fillMaxWidth().height(40.dp).clickable {
                scope.launch { list.animateScrollToItem(index) }
            }, contentAlignment = Alignment.Center) {
                Text(label, style = HType.plex(18, if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (index == selectedIndex) HColors.ink else HColors.textTertiary)
            }
        }
    }
}
