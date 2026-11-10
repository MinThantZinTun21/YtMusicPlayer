package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics

data class BottomTab(
    val label: String,
    val icon: ImageVector,
)

/**
 * The gap between the pill's glass edge and the tabs inside it.
 *
 * Shared with [GlassNavBar], which is meant to measure the same as this bar
 * rather than merely near it when Liquid Glass is on.
 */
internal val PILL_INSET = 6.dp

/**
 * Each tab's own vertical padding, and the counterweight to [PILL_INSET].
 */
internal val TAB_VERTICAL_PADDING = 9.dp

/** The gap between a tab's glyph and its label, in both bars. */
internal val TAB_ICON_LABEL_GAP = 2.dp

/**
 * Material 3 navigation bar: full-width, tonal container, pill indicator
 * behind the selected destination.
 */
@Composable
fun FloatingBottomBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = colors.surfaceContainer,
        contentColor = colors.onSurfaceVariant,
        tonalElevation = 3.dp,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) haptics.play(Haptic.Select)
                    onTabSelected(index)
                },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onSecondaryContainer,
                    selectedTextColor = colors.onSurface,
                    indicatorColor = colors.secondaryContainer,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
            )
        }
    }
}
