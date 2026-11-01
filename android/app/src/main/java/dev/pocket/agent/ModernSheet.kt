package dev.pocket.agent

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SheetMenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: Color,
    val iconResource: Int? = null,
    val textOnly: Boolean = false,
    val action: () -> Unit
)

@Composable
fun ModernSheetContent(
    title: String,
    subtitle: String,
    items: List<SheetMenuItem>,
    dark: Boolean = true,
    onClose: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(if (dark) Color(0xFF121214) else Color(0xFFFFFFFF))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        if (dark) Color(0xFF38383B) else Color(0xFFE2E8F0),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .navigationBarsPadding()
    ) {
        // Drag Handle Pill
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(42.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(if (dark) Color(0xFF525255) else Color(0xFFCBD5E1))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Sheet Header
        Text(
            text = title,
            color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Menu Items List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.forEach { item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pockoGlass(dark, 24.dp, pressed = pressed)
                        .clickable(
                            interactionSource = interaction,
                            indication = null
                        ) {
                            item.action()
                        }
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!item.textOnly) {
                    // Icon Chip
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(item.iconColor.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if(item.iconResource != null) Icon(painterResource(item.iconResource),null,tint=item.iconColor,modifier=Modifier.size(25.dp))
                        else Icon(imageVector=item.icon,contentDescription=null,tint=item.iconColor,modifier=Modifier.size(25.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    }

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = item.title,
                            color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (item.subtitle.isNotEmpty()) {
                            Text(
                                text = item.subtitle,
                                color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    if (!item.textOnly) Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

object ModernSheet {
    @JvmStatic
    fun newConversation(activity: BotsActivity, createBot: Runnable, createGroup: Runnable) {
        val tint = Color(0xFF007AFF)
        show(activity, "New conversation", "", listOf(
            SheetMenuItem("New bot", "", Icons.Rounded.SmartToy, tint, textOnly = true) { createBot.run() },
            SheetMenuItem("New group", "", Icons.Rounded.Groups, tint, textOnly = true) { createGroup.run() }
        ), AgentStyle.dark)
    }
    @JvmStatic
    fun show(
        context: Context,
        title: String,
        subtitle: String,
        items: List<SheetMenuItem>,
        dark: Boolean
    ) {
        val dialog = Dialog(context)
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(context as? androidx.lifecycle.LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(context as? androidx.savedstate.SavedStateRegistryOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                PockoTheme(darkTheme = dark) {
                    ModernSheetContent(
                        title = title,
                        subtitle = subtitle,
                        items = items.map { item ->
                            item.copy(action = {
                                dialog.dismiss()
                                item.action()
                            })
                        },
                        dark = dark,
                        onClose = { dialog.dismiss() }
                    )
                }
            }
        }

        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.apply {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(this, false)
            decorView.setPadding(0,0,0,0)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            attributes?.windowAnimations = android.R.style.Animation_InputMethod
            navigationBarColor = android.graphics.Color.TRANSPARENT
            if(android.os.Build.VERSION.SDK_INT>=29)isNavigationBarContrastEnforced=false
            androidx.core.view.WindowInsetsControllerCompat(this,decorView).setAppearanceLightNavigationBars(!dark)
            setDimAmount(0.35f)
        }
    }
}

@Preview(name = "Modern Sheet - Dark", showBackground = true, backgroundColor = 0xFF090A0F)
@Composable
private fun PreviewModernSheetDark() {
    PockoTheme(darkTheme = true) {
        ModernSheetContent(
            title = "Conversation Tools",
            subtitle = "Active bot tools and workspace actions",
            items = listOf(
                SheetMenuItem("Bot profile", "Inspect or edit identity", Icons.Rounded.AccountCircle, Color(0xFF8B5CF6)) {},
                SheetMenuItem("Project terminal", "Terminal shell in workspace", Icons.Rounded.Terminal, Color(0xFF10B981)) {},
                SheetMenuItem("Project files", "Browse and edit project source", Icons.Rounded.Folder, Color(0xFFF59E0B)) {},
                SheetMenuItem("Appearance", "Toggle Light, Dark or System", Icons.Rounded.DarkMode, Color(0xFF3B82F6)) {}
            ),
            dark = true
        )
    }
}
