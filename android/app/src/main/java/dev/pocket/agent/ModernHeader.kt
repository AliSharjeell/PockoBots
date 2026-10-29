package dev.pocket.agent

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ModernTopBar(
    title: String,
    subtitle: String,
    isInbox: Boolean,
    botAvatar: String = "",
    botAccent: String = "#3B82F6",
    statusText: String = "",
    dark: Boolean = true,
    onLeftClick: () -> Unit = {},
    onRightClick: () -> Unit = {}
) {
    val accentColor = try {
        Color(android.graphics.Color.parseColor(botAccent))
    } catch (_: Exception) {
        PockoColors.PrimaryBlue
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Action Button (Back or New)
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (dark) Color(0xFF181B26).copy(alpha = 0.85f)
                    else Color(0xFFFFFFFF).copy(alpha = 0.9f)
                )
                .border(
                    width = 1.dp,
                    color = if (dark) Color(0xFF2B3042) else Color(0xFFE2E8F0),
                    shape = CircleShape
                )
                .semantics {
                    contentDescription = if (isInbox) "New conversation" else "Back to messages"
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onLeftClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isInbox) Icons.Rounded.Add else Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Center Identity
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isInbox && botAvatar.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(accentColor, accentColor.copy(alpha = 0.7f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = botAvatar,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isInbox) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PockoColors.PrimaryBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "4 KB",
                                color = PockoColors.PrimaryBlue,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (statusText.isNotEmpty()) {
                        val statusColor = when (statusText.lowercase()) {
                            "ready" -> PockoColors.EmeraldReady
                            "working" -> PockoColors.AmberWorking
                            else -> Color(0xFF94A3B8)
                        }
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = subtitle,
                        color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Right Action Button (Tools / Menu)
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (dark) Color(0xFF181B26).copy(alpha = 0.85f)
                    else Color(0xFFFFFFFF).copy(alpha = 0.9f)
                )
                .border(
                    width = 1.dp,
                    color = if (dark) Color(0xFF2B3042) else Color(0xFFE2E8F0),
                    shape = CircleShape
                )
                .semantics {
                    contentDescription = "Conversation tools"
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onRightClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Open sidebar",
                tint = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

object ModernHeader {
    @JvmStatic
    fun create(
        context: Context,
        title: String,
        subtitle: String,
        isInbox: Boolean,
        botAvatar: String,
        botAccent: String,
        statusText: String,
        dark: Boolean,
        onLeftClick: Runnable,
        onRightClick: Runnable
    ): View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            PockoTheme(darkTheme = dark) {
                ModernTopBar(
                    title = title,
                    subtitle = subtitle,
                    isInbox = isInbox,
                    botAvatar = botAvatar,
                    botAccent = botAccent,
                    statusText = statusText,
                    dark = dark,
                    onLeftClick = { onLeftClick.run() },
                    onRightClick = { onRightClick.run() }
                )
            }
        }
    }
}

@Preview(name = "Top Bar - Inbox Dark", showBackground = true, backgroundColor = 0xFF090A0F)
@Composable
private fun PreviewTopBarInboxDark() {
    PockoTheme(darkTheme = true) {
        ModernTopBar(
            title = "PockoBots",
            subtitle = "Messages · Local AI",
            isInbox = true,
            dark = true
        )
    }
}

@Preview(name = "Top Bar - Chat Bot Dark", showBackground = true, backgroundColor = 0xFF090A0F)
@Composable
private fun PreviewTopBarChatDark() {
    PockoTheme(darkTheme = true) {
        ModernTopBar(
            title = "Code Assistant",
            subtitle = "Ready on this phone",
            isInbox = false,
            botAvatar = "CA",
            botAccent = "#4F46E5",
            statusText = "Ready",
            dark = true
        )
    }
}
