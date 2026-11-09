package dev.pocket.agent

import android.content.Context
import android.view.View
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** One model a provider can run. */
data class SubscriptionModelData(val id: String, val name: String, val description: String)

/** One vendor CLI, with the state of its install and the models it offers. */
data class SubscriptionProviderData(
    val id: String,
    val name: String,
    val supported: Boolean,
    val reason: String,
    val installed: Boolean,
    val authenticated: Boolean,
    val signInRequired: Boolean = true,
    val version: String,
    val docs: String,
    val models: List<SubscriptionModelData>,
    val modelSource: String,
    val installing: Boolean = false,
    val installOutput: String = "",
    val installFailed: Boolean = false
)

/** A provider's mark and the colour it is painted in. */
data class ProviderBrand(val icon: Int, val mark: Color)

/** One limit window a provider reported, such as its five-hour window or its weekly one. */
data class UsageWindowData(
    val label: String,
    val usedPercent: Double,
    val windowDurationMins: Int?,
    /** When the window fills up again, in unix seconds. */
    val resetsAt: Long?
)

/** The plan limits a provider reported, or the reason it reported none. */
data class ProviderUsageData(
    val providerId: String,
    val providerName: String,
    val reported: Boolean,
    val windows: List<UsageWindowData>,
    val note: String
)

/**
 * The colour of a provider's mark.
 *
 * A provider that has a colour of its own is painted in it. The rest are painted in white, or in
 * the dark zinc of the light theme, so a mark reads the same way in both themes.
 */
fun providerBrand(id: String, dark: Boolean): ProviderBrand {
    val ink = if (dark) Color(0xFFFFFFFF) else Color(0xFF18181B)
    return when (id) {
        "codex" -> ProviderBrand(R.drawable.brand_codex, ink)
        "claude" -> ProviderBrand(R.drawable.brand_claude, Color(0xFFD97757))
        "gemini" -> ProviderBrand(R.drawable.brand_gemini, Color(0xFF8E75B2))
        "grok" -> ProviderBrand(R.drawable.brand_grok, ink)
        "opencode" -> ProviderBrand(R.drawable.brand_opencode, ink)
        "cursor" -> ProviderBrand(R.drawable.brand_cursor, ink)
        else -> ProviderBrand(R.drawable.phosphor_terminal_window_regular, ink)
    }
}

/** The colour of the text that is chosen. */
private fun selectedInk(dark: Boolean): Color = if (dark) Color(0xFFFFFFFF) else Color(0xFF18181B)

/** A model that costs nothing to run is marked as such. */
private fun isFree(model: SubscriptionModelData): Boolean =
    model.name.contains("free", ignoreCase = true) || model.id.contains("free", ignoreCase = true)

/** A placeholder block that shines while the real content is on its way. */
@Composable
private fun ShimmerBlock(dark: Boolean, shape: RoundedCornerShape, modifier: Modifier = Modifier) {
    val shift = rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shift"
    )
    val base = if (dark) Color(0xFF1D2233) else Color(0xFFE2E8F0)
    val shine = if (dark) Color(0xFF2A2F42) else Color(0xFFF1F5F9)
    val start = shift.value * 420f - 210f
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, shine, base),
                    start = Offset(start, 0f),
                    end = Offset(start + 210f, 0f)
                )
            )
    )
}

/** The shape of the subscriptions screen, shown while its providers are on their way. */
@Composable
private fun SubscriptionsSkeleton(dark: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(5) {
                ShimmerBlock(dark, RoundedCornerShape(10.dp), Modifier.size(26.dp))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (dark) Color(0xFF131520) else Color(0xFFFFFFFF))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerBlock(dark, RoundedCornerShape(8.dp), Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    ShimmerBlock(dark, RoundedCornerShape(6.dp), Modifier.width(120.dp).height(15.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerBlock(dark, RoundedCornerShape(6.dp), Modifier.width(86.dp).height(11.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            ShimmerBlock(dark, RoundedCornerShape(14.dp), Modifier.fillMaxWidth().height(42.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (dark) Color(0xFF131520) else Color(0xFFFFFFFF))
                .padding(14.dp)
        ) {
            repeat(4) { index ->
                Row(
                    modifier = Modifier.padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBlock(dark, CircleShape, Modifier.size(21.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    ShimmerBlock(dark, RoundedCornerShape(6.dp), Modifier.width((150 - index * 14).dp).height(14.dp))
                }
            }
        }
    }
}

/** What a provider is doing right now, said the way the marketplace says it. */
private fun statusLine(provider: SubscriptionProviderData): String = when {
    !provider.supported -> "Not available on this phone"
    provider.installing -> "Installing on this phone"
    !provider.installed -> "Not installed on this phone"
    provider.authenticated -> if (provider.version.isEmpty()) "Signed in" else "Signed in · ${provider.version}"
    !provider.signInRequired -> if (provider.version.isEmpty()) "Installed · free models" else "Installed ${provider.version} · free models"
    else -> "Installed · sign-in needed"
}

private fun statusColor(provider: SubscriptionProviderData, dark: Boolean): Color = when {
    !provider.supported -> if (dark) Color(0xFF71717A) else Color(0xFF94A3B8)
    provider.installing -> PockoColors.AmberWorking
    provider.installed && provider.authenticated -> PockoColors.EmeraldReady
    provider.installed && !provider.signInRequired -> PockoColors.EmeraldReady
    provider.installed -> PockoColors.AmberWorking
    else -> if (dark) Color(0xFF71717A) else Color(0xFF94A3B8)
}

@Composable
private fun ProviderMark(provider: SubscriptionProviderData, size: Int, dark: Boolean) {
    val brand = providerBrand(provider.id, dark)
    Icon(
        painter = painterResource(brand.icon),
        contentDescription = provider.name,
        tint = brand.mark,
        modifier = Modifier.size(size.dp)
    )
}

@Composable
private fun SearchField(value: String, placeholder: String, dark: Boolean, onChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (dark) Color(0xFF1D2233) else Color(0xFFF1F5F9))
            .border(1.dp, if (dark) Color(0xFF222738) else Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                    fontSize = 14.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** A model in the list, with the tick that says it is the one in use. */
@Composable
private fun ModelRow(
    model: SubscriptionModelData,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 9.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(21.dp)
                .clip(CircleShape)
                .background(if (selected) PockoColors.PrimaryBlue else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (selected) PockoColors.PrimaryBlue else if (dark) Color(0xFF3F3F46) else Color(0xFFCBD5E1),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.name,
                    color = if (selected) selectedInk(dark) else if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isFree(model)) {
                    Spacer(modifier = Modifier.width(7.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (dark) Color(0xFF27272A) else Color(0xFFE2E8F0))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Free",
                            color = if (dark) Color(0xFFA1A1AA) else Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            if (model.description.isNotEmpty()) {
                Text(
                    text = model.description,
                    color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The colour a window is painted in, by how much of it is spent. */
private fun usageTone(used: Double, dark: Boolean): Color = when {
    used >= 90 -> PockoColors.RoseAttention
    used >= 70 -> PockoColors.AmberWorking
    else -> if (dark) Color(0xFF60A5FA) else PockoColors.PrimaryBlue
}

/** The time left in a window, in short units. */
private fun resetsIn(resetsAt: Long?): String? {
    if (resetsAt == null || resetsAt <= 0) return null
    val minutes = (resetsAt * 1000 - System.currentTimeMillis()) / 60000
    if (minutes <= 0) return null
    val days = minutes / (60 * 24)
    val hours = (minutes % (60 * 24)) / 60
    return when {
        days > 0 -> "${days}d ${hours}h left"
        hours > 0 -> "${hours}h ${minutes % 60}m left"
        else -> "${minutes % 60}m left"
    }
}

/** The moment a window fills up again, as a person would read it. */
private fun resetsAtText(resetsAt: Long?): String? {
    if (resetsAt == null || resetsAt <= 0) return null
    val format = java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault())
    return format.format(java.util.Date(resetsAt * 1000))
}

/**
 * How much of the plan is left.
 *
 * The provider's own CLI is asked, so a reading here is the plan the owner signed in to.
 */
@Composable
private fun UsageSection(
    usage: ProviderUsageData?,
    loading: Boolean,
    dark: Boolean,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "USAGE",
                color = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.06).em,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh usage",
                tint = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRefresh
                    )
                    .padding(7.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (dark) Color(0xFF131520) else Color(0xFFFFFFFF))
                .border(1.dp, if (dark) Color(0xFF222738) else Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            when {
                loading -> {
                    ShimmerBlock(dark, RoundedCornerShape(8.dp), Modifier.fillMaxWidth().height(14.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    ShimmerBlock(dark, RoundedCornerShape(8.dp), Modifier.fillMaxWidth(0.6f).height(14.dp))
                }
                usage == null -> Text(
                    text = "Your provider's CLI is asked how much of the plan is left.",
                    color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                    fontSize = 13.sp
                )
                !usage.reported -> Text(
                    text = usage.note.ifEmpty { "This provider does not report usage limits." },
                    color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                    fontSize = 13.sp
                )
                else -> usage.windows.forEachIndexed { index, window ->
                    if (index > 0) Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = window.label,
                            color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${window.usedPercent.toInt()}% used",
                            color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(7.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (dark) Color(0xFF1F2433) else Color(0xFFE2E8F0))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((window.usedPercent / 100.0).coerceIn(0.0, 1.0).toFloat())
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(usageTone(window.usedPercent, dark))
                        )
                    }
                    val left = resetsIn(window.resetsAt)
                    if (left != null) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "$left · resets ${resetsAtText(window.resetsAt)}",
                            color = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernSubscriptionsContent(
    providers: List<SubscriptionProviderData>,
    selectedId: String,
    selectedModel: String,
    activeProvider: String,
    query: String,
    loading: Boolean,
    error: String,
    dark: Boolean,
    usage: ProviderUsageData?,
    usageLoading: Boolean,
    onSelectProvider: (String) -> Unit,
    onQuery: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onInstall: (String) -> Unit,
    onSignIn: (String) -> Unit,
    onUse: (String, String) -> Unit,
    onRetry: () -> Unit,
    onRefreshUsage: () -> Unit,
    onOpenDocs: (String) -> Unit
) {
    val selected = providers.firstOrNull { it.id == selectedId }
    val filtered = remember(selected, query) {
        val needle = query.trim()
        if (needle.isEmpty()) selected?.models.orEmpty()
        else selected?.models.orEmpty().filter {
            it.name.contains(needle, ignoreCase = true) || it.id.contains(needle, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(providers, key = { it.id }) { provider ->
                    val active = provider.id == selectedId
                    Column(
                        modifier = Modifier
                            .width(78.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) (if (dark) Color(0xFF1D2233) else Color(0xFFE2E8F0)) else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelectProvider(provider.id) }
                            )
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProviderMark(provider, 26, dark)
                        Spacer(modifier = Modifier.height(7.dp))
                        Text(
                            text = provider.name,
                            color = if (active) selectedInk(dark) else if (dark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (loading) {
            item { SubscriptionsSkeleton(dark) }
            return@LazyColumn
        }

        if (error.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (dark) Color(0xFF131520) else Color(0xFFFFFFFF))
                        .border(1.dp, if (dark) Color(0xFF222738) else Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = error,
                        color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PockoColors.PrimaryBlue.copy(alpha = 0.18f)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, null, tint = PockoColors.PrimaryBlue, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Try again", color = PockoColors.PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            return@LazyColumn
        }

        if (selected == null) return@LazyColumn

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (dark) Color(0xFF131520) else Color(0xFFFFFFFF))
                    .border(1.dp, if (dark) Color(0xFF222738) else Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderMark(selected, 28, dark)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selected.name,
                            color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor(selected, dark))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusLine(selected),
                                color = statusColor(selected, dark),
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (selected.docs.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = "Open ${selected.name} documentation",
                            tint = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onOpenDocs(selected.id) }
                                )
                                .padding(7.dp)
                        )
                    }
                }

                if (!selected.supported) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = selected.reason,
                        color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                        fontSize = 12.sp
                    )
                    return@Column
                }

                if (selected.installOutput.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = selected.installOutput,
                        color = if (selected.installFailed) Color(0xFFF43F5E) else if (dark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        fontSize = 11.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (activeProvider == "byok") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your own key is the model every bot runs on",
                        color = PockoColors.EmeraldReady,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                val usable = selected.installed && (selected.authenticated || !selected.signInRequired)
                Button(
                    onClick = {
                        when {
                            selected.installing -> Unit
                            !selected.installed -> onInstall(selected.id)
                            !selected.authenticated && selected.signInRequired -> onSignIn(selected.id)
                            else -> onUse(selected.id, selectedModel)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (usable) PockoColors.PrimaryBlue.copy(alpha = 0.18f) else PockoColors.PrimaryBlue
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    modifier = Modifier.height(42.dp),
                    enabled = !selected.installing
                ) {
                    Text(
                        text = when {
                            selected.installing -> "Installing…"
                            !selected.installed -> "Install CLI"
                            !selected.authenticated && selected.signInRequired -> "Sign in"
                            else -> "Use this subscription"
                        },
                        color = if (usable) PockoColors.PrimaryBlue else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (selected.installed && !selected.authenticated && !selected.signInRequired) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sign in to add the models your plan includes",
                        color = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSignIn(selected.id) }
                            )
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    )
                }
            }
        }

        if (selected.supported && selected.installed) {
            // A provider that did not answer has no reading, so there is nothing to show.
            if (usage != null && usage.reported && usage.windows.isNotEmpty()) {
                item {
                    UsageSection(usage, usageLoading, dark, onRefreshUsage)
                }
            }

            item {
                Column {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "MODELS",
                        color = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.06).em,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SearchField(query, "Search models", dark, onQuery)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (selected.modelSource == "cli") "Asked of your ${selected.name} CLI" else "Built-in list · ask the CLI for a fresh one",
                        color = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = if (query.isEmpty()) "No models reported by this provider." else "No models match \"$query\".",
                        color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(filtered, key = { it.id }) { model ->
                    ModelRow(model, model.id == selectedModel, dark) { onSelectModel(model.id) }
                }
            }
        }
    }
}

object ModernSubscriptions {
    @JvmStatic
    fun create(
        context: Context,
        controller: SubscriptionsController
    ): View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            PockoTheme(darkTheme = AgentStyle.dark) {
                val providers = controller.providers.value
                val selectedId = controller.selectedId.value
                val selectedModel = controller.selectedModel.value
                val activeProvider = controller.activeProvider.value
                val query = controller.query.value
                val loading = controller.loading.value
                val error = controller.error.value
                val usage = controller.usage.value
                val usageLoading = controller.usageLoading.value
                ModernSubscriptionsContent(
                    providers = providers,
                    selectedId = selectedId,
                    selectedModel = selectedModel,
                    activeProvider = activeProvider,
                    query = query,
                    loading = loading,
                    error = error,
                    dark = AgentStyle.dark,
                    usage = usage,
                    usageLoading = usageLoading,
                    onSelectProvider = { controller.select(it) },
                    onQuery = { controller.query.value = it },
                    onSelectModel = { controller.selectedModel.value = it },
                    onInstall = { controller.install(it) },
                    onSignIn = { controller.signIn(it) },
                    onUse = { id, model -> controller.use(id, model) },
                    onRetry = { controller.load() },
                    onRefreshUsage = { controller.loadUsage(controller.selectedId.value, true) },
                    onOpenDocs = { controller.openDocs(it) }
                )
            }
        }
    }
}

/** Reads the provider list the backend reports and keeps the screen in step with it. */
class SubscriptionsController(
    private val app: BotsActivity,
    private val onSignInTerminal: java.util.function.Consumer<JSONObject>
) {
    val providers = mutableStateOf(emptyList<SubscriptionProviderData>())
    val selectedId = mutableStateOf("")
    val selectedModel = mutableStateOf("")
    val query = mutableStateOf("")
    val loading = mutableStateOf(true)
    val error = mutableStateOf("")
    /** The provider every bot runs on, and the model it runs on. */
    val activeProvider = mutableStateOf("")
    val activeModel = mutableStateOf("")
    /** How much of the plan is left, as the provider's own CLI reported it. */
    val usage = mutableStateOf<ProviderUsageData?>(null)
    val usageLoading = mutableStateOf(false)

    private var epoch = 0

    fun load() {
        val current = ++epoch
        loading.value = true
        error.value = ""
        app.marketplaceRequest("GET", "/providers", null, { value ->
            if (current != epoch) return@marketplaceRequest
            loading.value = false
            val list = (value as? JSONArray) ?: JSONArray()
            providers.value = (0 until list.length()).mapNotNull { index ->
                (list.optJSONObject(index) ?: return@mapNotNull null).let(::parseProvider)
            }
            val settings = app.currentState().optJSONObject("settings")
            activeProvider.value = settings?.optString("executionProvider", "byok") ?: "byok"
            activeModel.value = settings?.optString("providerModel", "") ?: ""
            if (selectedId.value.isEmpty()) {
                // The provider already in use is the one to open on.
                selectedId.value = providers.value.firstOrNull { it.id == activeProvider.value }?.id
                    ?: providers.value.firstOrNull { it.supported }?.id
                    ?: providers.value.firstOrNull()?.id
                    ?: ""
            }
            // A model is ticked only when it is the model in use, or one the owner has just ticked.
            val models = providers.value.firstOrNull { it.id == selectedId.value }?.models.orEmpty()
            val chosen = selectedModel.value.ifEmpty {
                if (activeProvider.value == selectedId.value) activeModel.value else ""
            }
            selectedModel.value = if (models.any { it.id == chosen }) chosen else ""
            if (selectedId.value.isNotEmpty()) loadUsage(selectedId.value)
        }, { message ->
            if (current != epoch) return@marketplaceRequest
            loading.value = false
            error.value = message ?: "Couldn't load your subscriptions."
        })
    }

    /**
     * Asks a provider's CLI how much of the plan is left.
     *
     * The provider's own CLI is the one that knows, so the reading is asked of it and not guessed.
     */
    fun loadUsage(id: String, refresh: Boolean = false) {
        if (id.isEmpty()) return
        usageLoading.value = true
        app.marketplaceRequest("GET", "/providers/$id/usage" + if (refresh) "?refresh=1" else "", null, { value ->
            usageLoading.value = false
            val json = value as? JSONObject ?: return@marketplaceRequest
            val windows = json.optJSONArray("windows")
            usage.value = ProviderUsageData(
                providerId = json.optString("providerId", id),
                providerName = json.optString("providerName"),
                reported = json.optBoolean("reported"),
                windows = (0 until (windows?.length() ?: 0)).mapNotNull { index ->
                    val window = windows?.optJSONObject(index) ?: return@mapNotNull null
                    UsageWindowData(
                        label = window.optString("label"),
                        usedPercent = window.optDouble("usedPercent", 0.0),
                        windowDurationMins = if (window.has("windowDurationMins") && !window.isNull("windowDurationMins"))
                            window.optInt("windowDurationMins") else null,
                        resetsAt = if (window.has("resetsAt") && !window.isNull("resetsAt"))
                            window.optLong("resetsAt") else null
                    )
                },
                note = json.optString("note")
            )
        }, { message ->
            usageLoading.value = false
            usage.value = ProviderUsageData(id, "", false, emptyList(), message ?: "Couldn't read the usage limits.")
        })
    }

    /** Picks a provider and asks its CLI which models it can run. */
    fun select(id: String) {
        selectedId.value = id
        query.value = ""
        loadUsage(id)
        val current = ++epoch
        app.marketplaceRequest("GET", "/providers/$id/models", null, { value ->
            if (current != epoch) return@marketplaceRequest
            val models = (value as? JSONObject)?.optJSONArray("models")
            val parsed = (0 until (models?.length() ?: 0)).mapNotNull { index ->
                val model = models?.optJSONObject(index) ?: return@mapNotNull null
                SubscriptionModelData(
                    id = model.optString("id"),
                    name = model.optString("name").ifEmpty { model.optString("id") },
                    description = model.optString("description")
                )
            }.filter { it.id.isNotEmpty() }
            providers.value = providers.value.map { provider ->
                if (provider.id != id) provider
                else provider.copy(models = parsed, modelSource = (value as? JSONObject)?.optString("source") ?: "fallback")
            }
            // A model is ticked only when it is the model in use, or one the owner has just ticked.
            val chosen = selectedModel.value.ifEmpty { if (activeProvider.value == id) activeModel.value else "" }
            selectedModel.value = parsed.firstOrNull { it.id == chosen }?.id ?: ""
        }, { })
    }

    fun install(id: String) {
        val current = ++epoch
        update(id) { it.copy(installing = true, installOutput = "", installFailed = false) }
        app.marketplaceRequest("POST", "/providers/$id/install", app.json(), { value ->
            if (current != epoch) return@marketplaceRequest
            val job = value as? JSONObject ?: return@marketplaceRequest
            pollInstall(id, job.optString("id"), current)
        }, { message ->
            if (current != epoch) return@marketplaceRequest
            update(id) { it.copy(installing = false, installFailed = true, installOutput = message ?: "Installation failed.") }
        })
    }

    private fun pollInstall(id: String, jobId: String, current: Int) {
        if (jobId.isEmpty()) return
        app.marketplaceRequest("GET", "/providers/$id/install/$jobId", null, { value ->
            if (current != epoch) return@marketplaceRequest
            val job = value as? JSONObject ?: return@marketplaceRequest
            val output = job.optString("output")
            if (job.optString("status") == "running") {
                update(id) { it.copy(installing = true, installOutput = output.takeLast(400)) }
                app.mainHandler().postDelayed({ pollInstall(id, jobId, current) }, 1200)
                return@marketplaceRequest
            }
            val ok = job.optInt("exitCode", -1) == 0
            update(id) {
                it.copy(
                    installing = false,
                    installFailed = !ok,
                    installOutput = if (ok) "Installation complete. Sign in to finish." else output.takeLast(400)
                )
            }
            if (ok) load()
        }, { message ->
            if (current != epoch) return@marketplaceRequest
            update(id) { it.copy(installing = true, installOutput = message ?: "Reconnecting to installation…") }
            app.mainHandler().postDelayed({ pollInstall(id, jobId, current) }, 2000)
        })
    }

    /** Opens the vendor's own sign-in flow in a terminal the owner can read. */
    fun signIn(id: String) {
        app.marketplaceRequest("POST", "/providers/$id/login", app.json(), { value ->
            val terminal = value as? JSONObject ?: return@marketplaceRequest
            onSignInTerminal.accept(terminal)
        }, { message ->
            update(id) { it.copy(installOutput = message ?: "Sign-in could not start.") }
        })
    }

    /** Makes this provider the one every bot runs on, with the model that was chosen. */
    fun use(id: String, model: String) {
        val current = ++epoch
        app.marketplaceRequest(
            "PUT",
            "/settings",
            app.json("executionProvider", id, "providerModel", model.ifEmpty { "default" }),
            { value ->
                if (current != epoch) return@marketplaceRequest
                (value as? JSONObject)?.let { app.currentState().put("settings", it) }
                load()
            },
            { message -> update(id) { it.copy(installOutput = message ?: "Couldn't save that choice.") } }
        )
    }

    fun openDocs(id: String) {
        val docs = providers.value.firstOrNull { it.id == id }?.docs ?: return
        if (docs.isNotEmpty()) app.openUrl(docs)
    }

    private fun update(id: String, change: (SubscriptionProviderData) -> SubscriptionProviderData) {
        providers.value = providers.value.map { if (it.id == id) change(it) else it }
    }

    private fun parseProvider(json: JSONObject): SubscriptionProviderData {
        val models = json.optJSONArray("models")
        return SubscriptionProviderData(
            id = json.optString("id"),
            name = json.optString("name"),
            supported = json.optBoolean("supported"),
            reason = json.optString("reason"),
            installed = json.optBoolean("installed"),
            authenticated = json.optBoolean("authenticated"),
            signInRequired = json.optBoolean("signInRequired", true),
            version = json.optString("version"),
            docs = json.optString("docs"),
            models = (0 until (models?.length() ?: 0)).mapNotNull { index ->
                val model = models?.optJSONObject(index) ?: return@mapNotNull null
                SubscriptionModelData(
                    id = model.optString("id"),
                    name = model.optString("name").ifEmpty { model.optString("id") },
                    description = model.optString("description")
                )
            }.filter { it.id.isNotEmpty() },
            modelSource = json.optString("modelSource")
        )
    }
}

@Preview(name = "Subscriptions - Dark", showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun PreviewSubscriptionsDark() {
    PockoTheme(darkTheme = true) {
        ModernSubscriptionsContent(
            providers = listOf(
                SubscriptionProviderData(
                    id = "claude", name = "Claude", supported = true, reason = "", installed = true, authenticated = true,
                    version = "2.1.319", docs = "https://code.claude.com", modelSource = "cli",
                    models = listOf(
                        SubscriptionModelData("claude-opus-5-5", "Claude Opus 5.5", "The strongest model for hard problems."),
                        SubscriptionModelData("claude-sonnet-5-5", "Claude Sonnet 5.5", "A balanced model for most work."),
                        SubscriptionModelData("claude-haiku-5-5", "Claude Haiku 5.5", "The fastest model for quick edits.")
                    )
                ),
                SubscriptionProviderData(
                    id = "opencode", name = "OpenCode", supported = true, reason = "", installed = true, authenticated = false,
                    signInRequired = false, version = "1.19.5", docs = "https://opencode.ai/docs/cli/", modelSource = "fallback",
                    models = listOf(
                        SubscriptionModelData("opencode/big-pickle", "Big Pickle", "Free OpenCode model."),
                        SubscriptionModelData("opencode/glm-5.3-flash-free", "GLM-5.3-Flash Free", "Free OpenCode model.")
                    )
                )
            ),
            selectedId = "claude",
            activeProvider = "claude",
            selectedModel = "claude-opus-5-5",
            query = "",
            loading = false,
            error = "",
            dark = true,
            usage = ProviderUsageData(
                providerId = "claude",
                providerName = "Claude",
                reported = true,
                windows = listOf(
                    UsageWindowData("5-hour limit", 42.0, 300, System.currentTimeMillis() / 1000 + 3600),
                    UsageWindowData("Weekly limit", 18.0, 10080, System.currentTimeMillis() / 1000 + 86400 * 3)
                ),
                note = ""
            ),
            usageLoading = false,
            onSelectProvider = {},
            onQuery = {},
            onSelectModel = {},
            onInstall = {},
            onSignIn = {},
            onUse = { _, _ -> },
            onRetry = {},
            onRefreshUsage = {},
            onOpenDocs = {}
        )
    }
}
