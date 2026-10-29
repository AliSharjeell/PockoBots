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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

data class MarketplaceItemData(
    val id: String,
    val name: String,
    val description: String,
    val rawJson: JSONObject
)

data class BrandInfo(
    val icon: ImageVector,
    val brandColor: Color,
    val bgGlow: Color,
    val category: String,
    val tagColor: Color
)

fun resolveBrand(name: String, id: String): BrandInfo {
    val lower = "$name $id".lowercase()
    return when {
        lower.contains("git") -> BrandInfo(
            icon = Icons.Rounded.Code,
            brandColor = Color(0xFFF0F6FC),
            bgGlow = Color(0xFF24292E),
            category = "CODING",
            tagColor = Color(0xFF388BFD)
        )
        lower.contains("brave") -> BrandInfo(
            icon = Icons.Rounded.Search,
            brandColor = Color(0xFFFB542B),
            bgGlow = Color(0x33FB542B),
            category = "SEARCH",
            tagColor = Color(0xFFFB542B)
        )
        lower.contains("postgres") || lower.contains("sql") || lower.contains("database") -> BrandInfo(
            icon = Icons.Rounded.Storage,
            brandColor = Color(0xFF336791),
            bgGlow = Color(0x33336791),
            category = "DATABASE",
            tagColor = Color(0xFF336791)
        )
        lower.contains("cloudflare") -> BrandInfo(
            icon = Icons.Rounded.CloudQueue,
            brandColor = Color(0xFFF38020),
            bgGlow = Color(0x33F38020),
            category = "DEVOPS",
            tagColor = Color(0xFFF38020)
        )
        lower.contains("airtable") -> BrandInfo(
            icon = Icons.Rounded.ViewQuilt,
            brandColor = Color(0xFF18BFFF),
            bgGlow = Color(0x3318BFFF),
            category = "DATA",
            tagColor = Color(0xFF18BFFF)
        )
        lower.contains("canva") -> BrandInfo(
            icon = Icons.Rounded.Palette,
            brandColor = Color(0xFF00C4CC),
            bgGlow = Color(0x3300C4CC),
            category = "DESIGN",
            tagColor = Color(0xFF00C4CC)
        )
        lower.contains("atlassian") || lower.contains("jira") -> BrandInfo(
            icon = Icons.Rounded.CheckCircle,
            brandColor = Color(0xFF0052CC),
            bgGlow = Color(0x330052CC),
            category = "MANAGEMENT",
            tagColor = Color(0xFF2684FF)
        )
        lower.contains("slack") -> BrandInfo(
            icon = Icons.Rounded.Forum,
            brandColor = Color(0xFFE01E5A),
            bgGlow = Color(0x334A154B),
            category = "MESSAGING",
            tagColor = Color(0xFFE01E5A)
        )
        lower.contains("browser") || lower.contains("puppeteer") -> BrandInfo(
            icon = Icons.Rounded.Language,
            brandColor = Color(0xFF4285F4),
            bgGlow = Color(0x334285F4),
            category = "BROWSER",
            tagColor = Color(0xFF4285F4)
        )
        lower.contains("apple") || lower.contains("icloud") -> BrandInfo(
            icon = Icons.Rounded.Smartphone,
            brandColor = Color(0xFFA2AAAD),
            bgGlow = Color(0x33A2AAAD),
            category = "ECOSYSTEM",
            tagColor = Color(0xFFA2AAAD)
        )
        lower.contains("aave") -> BrandInfo(
            icon = Icons.Rounded.AccountBalanceWallet,
            brandColor = Color(0xFFB6509E),
            bgGlow = Color(0x33B6509E),
            category = "FINANCE",
            tagColor = Color(0xFFB6509E)
        )
        lower.contains("composio") || lower.contains("cobrain") -> BrandInfo(
            icon = Icons.Rounded.AutoAwesome,
            brandColor = Color(0xFF8B5CF6),
            bgGlow = Color(0x338B5CF6),
            category = "AUTOMATION",
            tagColor = Color(0xFF8B5CF6)
        )
        lower.contains("google") || lower.contains("drive") -> BrandInfo(
            icon = Icons.Rounded.Cloud,
            brandColor = Color(0xFF34A853),
            bgGlow = Color(0x3334A853),
            category = "STORAGE",
            tagColor = Color(0xFF34A853)
        )
        lower.contains("linear") -> BrandInfo(
            icon = Icons.Rounded.TaskAlt,
            brandColor = Color(0xFF5E6AD2),
            bgGlow = Color(0x335E6AD2),
            category = "TRACKING",
            tagColor = Color(0xFF5E6AD2)
        )
        lower.contains("notion") -> BrandInfo(
            icon = Icons.Rounded.Description,
            brandColor = Color(0xFFFFFFFF),
            bgGlow = Color(0x33FFFFFF),
            category = "DOCS",
            tagColor = Color(0xFF94A3B8)
        )
        else -> BrandInfo(
            icon = Icons.Rounded.Extension,
            brandColor = Color(0xFF4F46E5),
            bgGlow = Color(0x334F46E5),
            category = "EXTENSION",
            tagColor = Color(0xFF4F46E5)
        )
    }
}

@Composable
fun ModernMarketplaceContent(
    plugins: List<MarketplaceItemData>,
    dark: Boolean = true,
    onConnectionsClick: () -> Unit = {},
    onInstallClick: (JSONObject) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    val categories = remember(plugins) {
        listOf("ALL", "CODING", "SEARCH", "DATABASE", "AUTOMATION", "DESIGN")
    }

    val filtered = remember(searchQuery, selectedCategory, plugins) {
        plugins.filter { plugin ->
            val brand = resolveBrand(plugin.name, plugin.id)
            val matchCategory = selectedCategory == "ALL" || brand.category.equals(selectedCategory, ignoreCase = true)
            val matchQuery = searchQuery.isEmpty() ||
                plugin.name.contains(searchQuery, ignoreCase = true) ||
                plugin.description.contains(searchQuery, ignoreCase = true) ||
                brand.category.contains(searchQuery, ignoreCase = true)
            matchCategory && matchQuery
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Hero Banner
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
                                if (dark) Color(0xFF1B2338) else Color(0xFFF1F5F9)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                PockoColors.AccentCyan.copy(alpha = 0.4f),
                                PockoColors.PrimaryBlue.copy(alpha = 0.25f),
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(PockoColors.PrimaryBlue, PockoColors.AccentCyan)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Hub,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MCP marketplace",
                                color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Supercharge bots with native Model Context Protocol tools",
                                color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (dark) Color(0xFF1D2233) else Color(0xFFE2E8F0))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onConnectionsClick
                                )
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = PockoColors.PrimaryBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Installed connections",
                                    color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = "${plugins.size} CONNECTORS",
                            color = PockoColors.AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // Category Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) PockoColors.PrimaryBlue.copy(alpha = 0.25f)
                                else if (dark) Color(0xFF131520) else Color(0xFFE2E8F0)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) PockoColors.PrimaryBlue
                                else if (dark) Color(0xFF222738) else Color(0xFFCBD5E1),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) PockoColors.PrimaryBlue
                            else if (dark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Extension Counter Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AVAILABLE PROTOCOLS (${filtered.size})",
                    color = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Connector Cards with Brand Logos & Colors
        items(filtered) { plugin ->
            val brand = resolveBrand(plugin.name, plugin.id)

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
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Icon Container with Logo Glow
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(brand.bgGlow)
                        .border(
                            width = 1.dp,
                            color = brand.brandColor.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = brand.icon,
                        contentDescription = plugin.name,
                        tint = brand.brandColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = plugin.name,
                            color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Category Pill Tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(brand.tagColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = brand.category,
                                color = brand.tagColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = plugin.description,
                        color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { onInstallClick(plugin.rawJson) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PockoColors.PrimaryBlue.copy(alpha = 0.18f)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = "Set up",
                        color = PockoColors.PrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

object ModernMarketplace {
    @JvmStatic
    fun create(
        context: Context,
        rawPlugins: JSONArray?,
        dark: Boolean,
        onConnectionsClick: Runnable,
        onInstallClick: java.util.function.Consumer<JSONObject>
    ): View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        val list = mutableListOf<MarketplaceItemData>()
        if (rawPlugins != null) {
            for (i in 0 until rawPlugins.length()) {
                val item = rawPlugins.optJSONObject(i) ?: continue
                list.add(
                    MarketplaceItemData(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        description = item.optString("description"),
                        rawJson = item
                    )
                )
            }
        }

        setContent {
            PockoTheme(darkTheme = dark) {
                ModernMarketplaceContent(
                    plugins = list,
                    dark = dark,
                    onConnectionsClick = { onConnectionsClick.run() },
                    onInstallClick = { onInstallClick.accept(it) }
                )
            }
        }
    }
}

@Preview(name = "Modern Marketplace - Dark", showBackground = true, backgroundColor = 0xFF090A0F)
@Composable
private fun PreviewModernMarketplaceDark() {
    PockoTheme(darkTheme = true) {
        ModernMarketplaceContent(
            plugins = listOf(
                MarketplaceItemData("1", "GitHub MCP", "Manage repositories, branches and pull requests", JSONObject()),
                MarketplaceItemData("2", "Browser Navigator", "Inspect DOM trees and capture web snapshots", JSONObject()),
                MarketplaceItemData("3", "PostgreSQL Server", "Query databases with read-only permission safeguards", JSONObject()),
                MarketplaceItemData("4", "Brave Search", "Privacy-first search API integration", JSONObject())
            ),
            dark = true
        )
    }
}
