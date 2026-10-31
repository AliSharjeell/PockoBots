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
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import java.util.function.IntConsumer

data class DockTabItem(
    val label: String,
    val icon: Int
)

val DockTabs = listOf(
    DockTabItem("Home", R.drawable.lucide_house),
    DockTabItem("Marketplace", R.drawable.lucide_store),
    DockTabItem("Terminal", R.drawable.lucide_square_terminal),
    DockTabItem("Settings", R.drawable.lucide_settings_2)
)

@Composable
fun GlassDockBar(
    dark: Boolean = true,
    selectedIndex: Int = 0,
    onTabSelected: (Int) -> Unit = {}
) {
    val backdrop = rememberCanvasBackdrop {
        drawRect(
            Brush.linearGradient(
                listOf(
                    if (dark) Color(0xFF454547) else Color(0xFF6B6B6E),
                    if (dark) Color(0xFF38383A) else Color(0xFF555558),
                    if (dark) Color(0xFF404042) else Color(0xFF626265)
                )
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (dark) 16.dp else 8.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = if (dark) Color(0xFF000000) else Color(0x33000000)
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(32.dp) },
                    effects = {
                        blur(10.dp.toPx())
                        lens(18.dp.toPx(), 26.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(
                            if (dark) Color.White.copy(alpha = 0.08f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                    }
                )
                .border(
                    width = 1.dp,
                    brush = if (dark) {
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.12f)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFFFFFFF).copy(alpha = 0.9f),
                                Color(0xFFDDE3EC).copy(alpha = 0.6f)
                            )
                        )
                    },
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(4.dp)
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockTabs.forEachIndexed { index, tab ->
                val selected = selectedIndex == index
                val tabActiveBg by animateColorAsState(
                    targetValue = when {
                        selected && dark -> Color.White.copy(alpha = 0.12f)
                        selected && !dark -> Color.White.copy(alpha = 0.18f)
                        else -> Color.Transparent
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "tabBg"
                )

                val contentTint by animateColorAsState(
                    targetValue = when {
                        selected -> Color(0xFF007AFF)
                        dark -> Color.White
                        else -> Color.White
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "tabTint"
                )

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
                        Icon(
                            painter = painterResource(tab.icon),
                            contentDescription = null,
                            tint = contentTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            color = contentTint,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }
        }
    }
}

/** Kyant Backdrop refraction with modern Material 3 icons and live preview support. */
object GlassDock {
    @JvmStatic
    fun create(context: Context, dark: Boolean, selected: Int, action: IntConsumer): View =
        ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PockoTheme(darkTheme = dark) {
                    GlassDockBar(
                        dark = dark,
                        selectedIndex = selected,
                        onTabSelected = { action.accept(it) }
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
