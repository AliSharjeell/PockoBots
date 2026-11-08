package dev.pocket.agent

import android.content.Context
import android.view.View
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
import androidx.compose.material.icons.rounded.Search
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
    val version: String,
    val docs: String,
    val models: List<SubscriptionModelData>,
    val modelSource: String,
    val installing: Boolean = false,
    val installOutput: String = "",
    val installFailed: Boolean = false
)

/** A provider's mark and the colour it is painted in. */
data class ProviderBrand(val icon: Int, val mark: Color, val tile: Color)

/**
 * The colour of a provider's mark.
 *
 * A provider that has a colour of its own is painted in it; every other mark is painted in the
 * primary text colour, so no mark ever borrows another provider's hue.
 */
fun providerBrand(id: String, dark: Boolean): ProviderBrand {
    val primary = if (dark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    return when (id) {
        "codex" -> ProviderBrand(R.drawable.brand_codex, Color(0xFF10A37F), Color(0xFF10A37F).copy(alpha = 0.16f))
        "claude" -> ProviderBrand(R.drawable.brand_claude, Color(0xFFD97757), Color(0xFFD97757).copy(alpha = 0.16f))
        "gemini" -> ProviderBrand(R.drawable.brand_gemini, Color(0xFF8E75B2), Color(0xFF8E75B2).copy(alpha = 0.16f))
        "grok" -> ProviderBrand(R.drawable.brand_grok, primary, primary.copy(alpha = 0.10f))
        "opencode" -> ProviderBrand(R.drawable.brand_opencode, primary, primary.copy(alpha = 0.10f))
        "cursor" -> ProviderBrand(R.drawable.brand_cursor, primary, primary.copy(alpha = 0.10f))
        else -> ProviderBrand(R.drawable.phosphor_terminal_window_regular, primary, primary.copy(alpha = 0.10f))
    }
}

/** What a provider is doing right now, said the way the marketplace says it. */
private fun statusLine(provider: SubscriptionProviderData): String = when {
    !provider.supported -> "Not available on this phone"
    provider.installing -> "Installing on this phone"
    !provider.installed -> "Not installed on this phone"
    provider.authenticated -> if (provider.version.isEmpty()) "Signed in" else "Signed in · ${provider.version}"
    else -> "Installed · sign-in needed"
}

private fun statusColor(provider: SubscriptionProviderData, dark: Boolean): Color = when {
    !provider.supported -> if (dark) Color(0xFF71717A) else Color(0xFF94A3B8)
    provider.installing -> PockoColors.AmberWorking
    provider.installed && provider.authenticated -> PockoColors.EmeraldReady
    provider.installed -> PockoColors.AmberWorking
    else -> if (dark) Color(0xFF71717A) else Color(0xFF94A3B8)
}

@Composable
private fun ProviderMark(provider: SubscriptionProviderData, size: Int, dark: Boolean, selected: Boolean = false) {
    val brand = providerBrand(provider.id, dark)
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(if (selected) brand.mark.copy(alpha = 0.22f) else brand.tile)
            .border(
                width = 1.dp,
                color = if (selected) brand.mark.copy(alpha = 0.55f) else brand.mark.copy(alpha = 0.22f),
                shape = RoundedCornerShape((size / 3).dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(brand.icon),
            contentDescription = provider.name,
            tint = brand.mark,
            modifier = Modifier.size((size * 0.55f).dp)
        )
    }
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
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
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
            Text(
                text = model.name,
                color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

@Composable
fun ModernSubscriptionsContent(
    providers: List<SubscriptionProviderData>,
    selectedId: String,
    selectedModel: String,
    query: String,
    loading: Boolean,
    error: String,
    dark: Boolean,
    onSelectProvider: (String) -> Unit,
    onQuery: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onInstall: (String) -> Unit,
    onSignIn: (String) -> Unit,
    onUse: (String, String) -> Unit,
    onRetry: () -> Unit,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(PockoColors.PrimaryBlue, PockoColors.AccentCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Subscription models",
                                color = if (dark) PockoColors.TextPrimaryDark else PockoColors.TextPrimaryLight,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Run bots on a vendor CLI you already pay for",
                                color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${providers.size} PROVIDERS",
                        color = PockoColors.AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.06).em
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(providers, key = { it.id }) { provider ->
                    val active = provider.id == selectedId
                    Column(
                        modifier = Modifier
                            .width(78.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) Color(0xFF1D2233) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (active) PockoColors.PrimaryBlue.copy(alpha = 0.5f) else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelectProvider(provider.id) }
                            )
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProviderMark(provider, 40, dark, active)
                        Spacer(modifier = Modifier.height(7.dp))
                        Text(
                            text = provider.name.substringBefore(" /").substringBefore(" CLI"),
                            color = if (active) PockoColors.PrimaryBlue else if (dark) Color(0xFF94A3B8) else Color(0xFF64748B),
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
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading providers…",
                        color = if (dark) PockoColors.TextSecondaryDark else PockoColors.TextSecondaryLight,
                        fontSize = 14.sp
                    )
                }
            }
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
                    ProviderMark(selected, 46, dark)
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

                Spacer(modifier = Modifier.height(14.dp))
                val current = selected.installed && selected.authenticated
                Button(
                    onClick = {
                        when {
                            selected.installing -> Unit
                            !selected.installed -> onInstall(selected.id)
                            !selected.authenticated -> onSignIn(selected.id)
                            else -> onUse(selected.id, selectedModel)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (current) PockoColors.PrimaryBlue.copy(alpha = 0.18f) else PockoColors.PrimaryBlue
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    modifier = Modifier.height(42.dp),
                    enabled = !selected.installing
                ) {
                    Text(
                        text = when {
                            selected.installing -> "Installing…"
                            !selected.installed -> "Install CLI"
                            !selected.authenticated -> "Sign in"
                            else -> "Use this subscription"
                        },
                        color = if (current) PockoColors.PrimaryBlue else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (selected.supported && selected.installed) {
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
                val query = controller.query.value
                val loading = controller.loading.value
                val error = controller.error.value
                ModernSubscriptionsContent(
                    providers = providers,
                    selectedId = selectedId,
                    selectedModel = selectedModel,
                    query = query,
                    loading = loading,
                    error = error,
                    dark = AgentStyle.dark,
                    onSelectProvider = { controller.select(it) },
                    onQuery = { controller.query.value = it },
                    onSelectModel = { controller.selectedModel.value = it },
                    onInstall = { controller.install(it) },
                    onSignIn = { controller.signIn(it) },
                    onUse = { id, model -> controller.use(id, model) },
                    onRetry = { controller.load() },
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
            if (selectedId.value.isEmpty()) {
                selectedId.value = providers.value.firstOrNull { it.id == "claude" }?.id
                    ?: providers.value.firstOrNull { it.supported }?.id
                    ?: providers.value.firstOrNull()?.id
                    ?: ""
            }
            selectedModel.value = providers.value.firstOrNull { it.id == selectedId.value }?.models?.firstOrNull()?.id ?: ""
        }, { message ->
            if (current != epoch) return@marketplaceRequest
            loading.value = false
            error.value = message ?: "Couldn't load your subscriptions."
        })
    }

    /** Picks a provider and asks its CLI which models it can run. */
    fun select(id: String) {
        selectedId.value = id
        query.value = ""
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
            selectedModel.value = parsed.firstOrNull { it.id == selectedModel.value }?.id ?: parsed.firstOrNull()?.id ?: ""
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
                SubscriptionProviderData("claude", "Claude Code", true, "", true, true, "2.1.319", "https://code.claude.com", listOf(
                    SubscriptionModelData("claude-opus-5-5", "Claude Opus 5.5", "The strongest model for hard problems."),
                    SubscriptionModelData("claude-sonnet-5-5", "Claude Sonnet 5.5", "A balanced model for most work."),
                    SubscriptionModelData("claude-haiku-5-5", "Claude Haiku 5.5", "The fastest model for quick edits.")
                ), "cli"),
                SubscriptionProviderData("codex", "Codex / ChatGPT", true, "", false, false, "", "https://developers.openai.com", emptyList(), "fallback")
            ),
            selectedId = "claude",
            selectedModel = "claude-opus-5-5",
            query = "",
            loading = false,
            error = "",
            dark = true,
            onSelectProvider = {},
            onQuery = {},
            onSelectModel = {},
            onInstall = {},
            onSignIn = {},
            onUse = { _, _ -> },
            onRetry = {},
            onOpenDocs = {}
        )
    }
}
