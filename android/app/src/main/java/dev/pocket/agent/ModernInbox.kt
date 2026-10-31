package dev.pocket.agent

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class BotItemData(
    val id: String,
    val name: String,
    val role: String,
    val avatar: String,
    val accent: String,
    val isEnabled: Boolean,
    val isWorking: Boolean,
    val lastMessage: String,
    val queued: Int
)

data class GroupItemData(
    val id: String,
    val name: String
)

@Composable
fun ModernInboxContent(
    bots: List<BotItemData>,
    groups: List<GroupItemData>,
    dark: Boolean = true,
    onCreateBot: () -> Unit = {},
    onCreateGroup: () -> Unit = {},
    onBotClick: (String) -> Unit = {},
    onGroupClick: (String) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Quick Action Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                if (dark) Color(0xFF141724) else Color(0xFFEFF6FF),
                                if (dark) Color(0xFF1C2236) else Color(0xFFF1F5F9)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                PockoColors.PrimaryBlue.copy(alpha = 0.4f),
                                PockoColors.VioletAccent.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PockoColors.PrimaryBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SmartToy,
                                contentDescription = null,
                                tint = PockoColors.PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Personal AI Assistants",
                                color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Code, terminal, phone apps & MCP on device",
                                color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onCreateBot,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PockoColors.PrimaryBlue
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create bot", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = onCreateGroup,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (dark) Color(0xFF23283A) else Color(0xFFE2E8F0)
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Group,
                                contentDescription = null,
                                tint = if (dark) Color.White else Color(0xFF1E293B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "New group",
                                color = if (dark) Color.White else Color(0xFF1E293B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Groups Header and list if any exist
        if (groups.isNotEmpty()) {
            item {
                Text(
                    text = "GROUPS",
                    color = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.06).em,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                )
            }

            items(groups) { group ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (dark) Color(0xFF141724) else Color(0xFFFFFFFF))
                        .border(
                            width = 1.dp,
                            color = if (dark) Color(0xFF22273A) else Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onGroupClick(group.id)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PockoColors.VioletAccent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Group,
                            contentDescription = null,
                            tint = PockoColors.VioletAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Group: " + group.name,
                            color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Collaborative multi-bot conversation",
                            color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                            fontSize = 11.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Bots Header
        item {
            Text(
                text = "YOUR BOTS",
                color = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.06).em,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }

        // Empty state when 0 bots
        if (bots.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (dark) Color(0xFF141724) else Color(0xFFFFFFFF))
                        .border(
                            width = 1.dp,
                            color = if (dark) Color(0xFF22273A) else Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(PockoColors.PrimaryBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SmartToy,
                            contentDescription = null,
                            tint = PockoColors.PrimaryBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Meet your local bots",
                        color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Give a bot a name and instructions. Every bot can code, browse and use your phone.",
                        color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onCreateBot,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PockoColors.PrimaryBlue)
                    ) {
                        Text("Create my first bot", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Bot Cards List
        items(bots) { bot ->
            val accentColor = try {
                Color(android.graphics.Color.parseColor(bot.accent))
            } catch (_: Exception) {
                PockoColors.PrimaryBlue
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (dark) Color(0xFF131520) else Color(0xFFFFFFFF))
                    .border(
                        width = 1.dp,
                        color = if (dark) Color(0xFF222738) else Color(0xFFE2E8F0),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onBotClick(bot.id)
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with accent gradient
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(accentColor, accentColor.copy(alpha = 0.72f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (bot.avatar.isNotEmpty()) bot.avatar else bot.name.take(1).uppercase(Locale.ROOT),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Info Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = bot.name,
                            color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Status Badge Pill
                        val (statusText, statusBg, statusColor) = when {
                            bot.isWorking -> Triple("Working", PockoColors.AmberWorking.copy(alpha = 0.16f), PockoColors.AmberWorking)
                            bot.isEnabled -> Triple("Ready", PockoColors.EmeraldReady.copy(alpha = 0.16f), PockoColors.EmeraldReady)
                            else -> Triple("Paused", Color(0xFF94A3B8).copy(alpha = 0.16f), Color(0xFF94A3B8))
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusBg)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = statusText,
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = if (bot.lastMessage.isNotEmpty()) bot.lastMessage else bot.role,
                        color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )

                    if (bot.queued > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PockoColors.PrimaryIndigo.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${bot.queued} queued",
                                color = Color(0xFF818CF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

object ModernInbox {
    @JvmStatic
    fun create(
        context: Context,
        stateJson: JSONObject,
        groupsJson: JSONArray?,
        dark: Boolean,
        onCreateBot: Runnable,
        onCreateGroup: Runnable,
        onBotClick: java.util.function.Consumer<String>,
        onGroupClick: java.util.function.Consumer<String>
    ): View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        val botsList = mutableListOf<BotItemData>()
        val botsArray = stateJson.optJSONArray("bots")
        if (botsArray != null) {
            for (i in 0 until botsArray.length()) {
                val b = botsArray.optJSONObject(i) ?: continue
                val lastMsg = b.optJSONObject("lastMessage")?.optString("text") ?: ""
                botsList.add(
                    BotItemData(
                        id = b.optString("id"),
                        name = b.optString("name"),
                        role = b.optString("role"),
                        avatar = b.optString("avatar"),
                        accent = b.optString("accent", "#3B82F6"),
                        isEnabled = b.optBoolean("enabled", true),
                        isWorking = b.optJSONObject("active") != null,
                        lastMessage = lastMsg,
                        queued = b.optInt("queued", 0)
                    )
                )
            }
        }

        val groupsList = mutableListOf<GroupItemData>()
        if (groupsJson != null) {
            for (i in 0 until groupsJson.length()) {
                val g = groupsJson.optJSONObject(i) ?: continue
                groupsList.add(
                    GroupItemData(
                        id = g.optString("id"),
                        name = g.optString("name")
                    )
                )
            }
        }

        setContent {
            PockoTheme(darkTheme = dark) {
                ModernInboxContent(
                    bots = botsList,
                    groups = groupsList,
                    dark = dark,
                    onCreateBot = { onCreateBot.run() },
                    onCreateGroup = { onCreateGroup.run() },
                    onBotClick = { onBotClick.accept(it) },
                    onGroupClick = { onGroupClick.accept(it) }
                )
            }
        }
    }
}

@Preview(name = "Modern Inbox - Dark", showBackground = true, backgroundColor = 0xFF090A0F)
@Composable
private fun PreviewModernInboxDark() {
    PockoTheme(darkTheme = true) {
        ModernInboxContent(
            bots = listOf(
                BotItemData(
                    id = "1",
                    name = "Coding Assistant",
                    role = "Fix bugs, implement features and write tests",
                    avatar = "CA",
                    accent = "#3B82F6",
                    isEnabled = true,
                    isWorking = true,
                    lastMessage = "Refactoring user auth and setting up unit tests in workspace.",
                    queued = 1
                ),
                BotItemData(
                    id = "2",
                    name = "Phone Navigator",
                    role = "Operate apps and browse web pages on device",
                    avatar = "PN",
                    accent = "#10B981",
                    isEnabled = true,
                    isWorking = false,
                    lastMessage = "Ready to browse or check phone settings.",
                    queued = 0
                )
            ),
            groups = listOf(
                GroupItemData("g1", "Engineering Team")
            ),
            dark = true
        )
    }
}
