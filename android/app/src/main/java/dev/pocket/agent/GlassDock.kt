package dev.pocket.agent

import android.content.Context
import android.view.View
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import java.util.function.IntConsumer

data class DockTabItem(
    val label: String,
    val icon: Int,
    val selectedIcon: Int
)

val DockTabs = listOf(
    DockTabItem("Home", R.drawable.phosphor_house_regular, R.drawable.phosphor_house_fill),
    DockTabItem("Marketplace", R.drawable.phosphor_storefront_regular, R.drawable.phosphor_storefront_fill),
    DockTabItem("Terminal", R.drawable.phosphor_terminal_window_regular, R.drawable.phosphor_terminal_window_fill),
    DockTabItem("Settings", R.drawable.phosphor_gear_six_regular, R.drawable.phosphor_gear_six_fill)
)

val WorkspaceTabs = listOf(
    DockTabItem("Chat", R.drawable.phosphor_chat_circle_regular, R.drawable.phosphor_chat_circle_fill),
    DockTabItem("Terminal", R.drawable.phosphor_terminal_window_regular, R.drawable.phosphor_terminal_window_fill),
    DockTabItem("Files", R.drawable.phosphor_folder_open_regular, R.drawable.phosphor_folder_open_fill)
)

@Composable
fun GlassDockBar(
    dark: Boolean = true,
    selectedIndex: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    tabs: List<DockTabItem> = DockTabs,
    compact: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = if (compact) 6.dp else 14.dp, end = 14.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = if (compact) 216.dp else 300.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = if (compact) 6.dp else if (dark) 16.dp else 8.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = if (dark) Color(0xFF000000) else Color(0x33000000)
                )
                .pockoGlass(dark, 32.dp)
                .padding(4.dp)
                .height(if (compact) 40.dp else 58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = selectedIndex == index
                val tabActiveBg by animateColorAsState(
                    targetValue = when {
                        selected && dark -> Color.White.copy(alpha = 0.09f)
                        selected && !dark -> Color.White.copy(alpha = 0.18f)
                        else -> Color.Transparent
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "tabBg"
                )

                val contentTint = Color.White

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(26.dp))
                        .background(tabActiveBg, RoundedCornerShape(26.dp))
                        .then(
                            if (selected && dark) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(26.dp)
                                )
                            } else Modifier
                        )
                        .semantics {
                            contentDescription = tab.label
                            role = Role.Tab
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.size(if (compact) 20.dp else 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(if (selected) tab.selectedIcon else tab.icon),
                                contentDescription = null,
                                tint = contentTint,
                                modifier = Modifier.size(if (compact) 20.dp else 24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Kyant Backdrop refraction with modern Material 3 icons and live preview support. */
object GlassDock {
    @JvmStatic
    fun create(context: Context, dark: Boolean, selected: Int, action: IntConsumer): View = createDock(context, dark, selected, DockTabs, action)

    @JvmStatic
    fun createWorkspace(context: Context, dark: Boolean, selected: Int, action: IntConsumer): View = createDock(context, dark, selected, WorkspaceTabs, action)

    private fun createDock(context: Context, dark: Boolean, selected: Int, tabs: List<DockTabItem>, action: IntConsumer): View =
        ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PockoTheme(darkTheme = dark) {
                    GlassDockBar(
                        dark = dark,
                        selectedIndex = selected,
                        onTabSelected = { action.accept(it) },
                        tabs = tabs,
                        compact = tabs == WorkspaceTabs
                    )
                }
            }
        }
}

@Preview(name = "Glass Dock - Dark Mode", showBackground = true, backgroundColor = 0xFF090A0F)
@Composable
private fun PreviewGlassDockDark() {
    PockoTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            GlassDockBar(dark = true, selectedIndex = 0)
        }
    }
}

@Preview(name = "Glass Dock - Light Mode", showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
private fun PreviewGlassDockLight() {
    PockoTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            GlassDockBar(dark = false, selectedIndex = 1)
        }
    }
}
