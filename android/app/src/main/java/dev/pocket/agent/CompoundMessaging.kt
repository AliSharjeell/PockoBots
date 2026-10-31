package dev.pocket.agent

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CompoundMessaging provides Cupertino / Telegram styling for chat messages,
 * modeled directly after 6xingyv/compound:
 * - Continuous curved tails and grouped bubbles (BubbleContinuousShape)
 * - Bot avatars alongside incoming messages
 * - Message timestamps
 * - Cupertino floating reaction bar (👍, ❤️, 🔥, 😂, 💡, 👏) on tap / long press
 * - Cupertino reply preview quoting
 * - Cupertino glass '+' attachment menu & pill composer
 */
object CompoundMessaging {

    fun interface ReplyCallback {
        fun onReply(author: String, snippet: String)
    }

    fun interface ActionCallback {
        fun run()
    }

    fun interface PromptCallback {
        fun onSelect(prompt: String)
    }

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    private fun dp(context: Context, value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }

    /**
     * Renders a Cupertino message bubble row into the thread container.
     */
    @JvmStatic
    fun renderBubble(
        activity: BotsActivity,
        thread: LinearLayout,
        text: String,
        isUser: Boolean,
        isFirstInGroup: Boolean,
        isLastInGroup: Boolean,
        botName: String,
        botAvatar: String,
        botAccent: String,
        markdown: ChatMarkdown,
        timestamp: Long,
        onReply: ReplyCallback
    ) {
        val ctx = activity
        val isDark = AgentStyle.dark

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isUser) Gravity.RIGHT or Gravity.BOTTOM else Gravity.LEFT or Gravity.BOTTOM
            val startPad = if (isUser) dp(ctx, 56) else dp(ctx, 10)
            val endPad = if (isUser) dp(ctx, 10) else dp(ctx, 56)
            val topPad = if (isFirstInGroup) dp(ctx, 6) else dp(ctx, 2)
            val bottomPad = if (isLastInGroup) dp(ctx, 8) else dp(ctx, 2)
            setPadding(startPad, topPad, endPad, bottomPad)
        }

        // Incoming Bot Avatar (placed on bottom-left, only shown on last message of cluster)
        if (!isUser) {
            val avatarSlot = FrameLayout(ctx)
            val avatarSize = dp(ctx, 32)
            if (isLastInGroup) {
                if (botAvatar.startsWith("bloub:")) {
                    val parts = botAvatar.split(":")
                    val bloub = BloubView(ctx).apply {
                        if (parts.size > 1) shapeId = parts[1]
                        colorHex = botAccent
                        if (parts.size > 2) expressionId = parts[2]
                    }
                    avatarSlot.addView(bloub, FrameLayout.LayoutParams(avatarSize, avatarSize))
                } else {
                    val fallback = TextView(ctx).apply {
                letterSpacing = -0.06f
                typeface = AgentStyle.font(ctx)
                        val initial = if (botAvatar.isNotEmpty()) botAvatar.take(2).uppercase() else botName.take(1).uppercase()
                        this.text = initial
                        setTextColor(0xFFFFFFFF.toInt())
                        textSize = 12f
                        typeface = Typeface.create(AgentStyle.font(ctx), Typeface.BOLD)
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(try { Color.parseColor(botAccent) } catch (e: Exception) { 0xFF4F46E5.toInt() })
                        }
                    }
                    avatarSlot.addView(fallback, FrameLayout.LayoutParams(avatarSize, avatarSize))
                }
            }
            val lpAvatar = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                rightMargin = dp(ctx, 8)
                gravity = Gravity.BOTTOM
            }
            row.addView(avatarSlot, lpAvatar)
        }

        // Bubble Content Column
        val contentCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (isUser) Gravity.RIGHT else Gravity.LEFT
        }

        // Bubble Container Frame
        val bubbleFrame = FrameLayout(ctx).apply {
            isClickable = true
            isFocusable = true
        }

        // Background Drawable with Cupertino curve & tail
        val primaryColor = if (isUser) {
            0xFF007AFF.toInt() // Cupertino Telegram blue
        } else {
            if (isDark) 0xFF242429.toInt() else 0xFFE9E9EB.toInt()
        }
        val secondaryColor = if (isUser) 0xFF0062D6.toInt() else primaryColor

        bubbleFrame.background = CupertinoChatBubbleDrawable(
            isOutgoing = isUser,
            hasTail = isLastInGroup,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            cornerRadiusPx = dp(ctx, 18).toFloat()
        )

        // Inside the bubble
        val bubbleLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val bPad = if (isLastInGroup) dp(ctx, 10) else dp(ctx, 8)
            setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), bPad)
        }

        // Message text
        val messageText = TextView(ctx).apply {
                letterSpacing = -0.06f
                typeface = AgentStyle.font(ctx)
            textSize = 14f
            typeface = AgentStyle.font(ctx)
            if (isUser) {
                setTextColor(0xFFFFFFFF.toInt())
                this.text = text
            } else {
                setTextColor(if (isDark) 0xFFF4F4F5.toInt() else 0xFF18181B.toInt())
                markdown.set(this, text)
            }
        }
        if (isUser || !ChatCards.render(activity, bubbleLayout, text, markdown)) bubbleLayout.addView(messageText)

        // Bottom metadata row: Timestamp
        val metaRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            setPadding(0, dp(ctx, 2), 0, 0)
        }

        val timeView = TextView(ctx).apply {
                letterSpacing = -0.06f
                typeface = AgentStyle.font(ctx)
            typeface = AgentStyle.font(ctx)
            this.text = timeFormat.format(Date(timestamp))
            textSize = 10f
            if (isUser) {
                setTextColor(0xAAFFFFFF.toInt())
            } else {
                setTextColor(if (isDark) 0x88A1A1AA.toInt() else 0x8871717A.toInt())
            }
        }
        metaRow.addView(timeView)

        bubbleLayout.addView(metaRow)
        bubbleFrame.addView(bubbleLayout)

        // Reaction Bar on click or long press
        val showReactionBar = View.OnClickListener {
            showCupertinoPopup(activity, bubbleFrame, text, isUser, onReply)
        }
        val showReactionLong = View.OnLongClickListener {
            showCupertinoPopup(activity, bubbleFrame, text, isUser, onReply)
            true
        }
        bubbleFrame.setOnClickListener(showReactionBar)
        bubbleFrame.setOnLongClickListener(showReactionLong)
        bubbleLayout.setOnClickListener(showReactionBar)
        bubbleLayout.setOnLongClickListener(showReactionLong)
        messageText.setOnClickListener(showReactionBar)
        messageText.setOnLongClickListener(showReactionLong)

        contentCol.addView(bubbleFrame)
        row.addView(contentCol, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        thread.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    /**
     * Displays a Cupertino Telegram style floating reaction bar for the tapped bubble.
     */
    private fun showCupertinoPopup(
        activity: BotsActivity,
        anchor: View,
        text: String,
        isUser: Boolean,
        onReply: ReplyCallback
    ) {
        val ctx = activity
        val isDark = AgentStyle.dark

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val bg = GradientDrawable().apply {
                cornerRadius = dp(ctx, 24).toFloat()
                setColor(if (isDark) 0xF01C1C1E.toInt() else 0xF8FFFFFF.toInt())
                setStroke(dp(ctx, 1), if (isDark) 0x33FFFFFF.toInt() else 0x1A000000.toInt())
            }
            background = bg
            setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
            elevation = dp(ctx, 16).toFloat()
        }

        // Emoji Reaction Row (👍, ❤️, 🔥, 😂, 💡, 👏)
        val emojiRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 10))
        }

        val emojis = arrayOf("👍", "❤️", "🔥", "😂", "💡", "👏")
        for (emoji in emojis) {
            val emojiBtn = TextView(ctx).apply {
                letterSpacing = -0.06f
                typeface = AgentStyle.font(ctx)
                this.text = emoji
                textSize = 26f
                setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 4))
                isClickable = true
                setOnClickListener {
                    Toast.makeText(ctx, "Reacted $emoji", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            emojiRow.addView(emojiBtn)
        }
        card.addView(emojiRow)

        val divider = View(ctx).apply {
            setBackgroundColor(if (isDark) 0x22FFFFFF.toInt() else 0x15000000.toInt())
        }
        card.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1)))

        // Actions Row (Reply, Copy)
        val actionRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 10), 0, dp(ctx, 4))
        }

        val btnReply = TextView(ctx).apply {
                letterSpacing = -0.06f
                typeface = AgentStyle.font(ctx)
            this.text = "↩  Reply"
            textSize = 14f
            typeface = Typeface.create(AgentStyle.font(ctx), Typeface.BOLD)
            setTextColor(0xFF007AFF.toInt())
            setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
            isClickable = true
            setOnClickListener {
                dialog.dismiss()
                onReply.onReply(if (isUser) "You" else "Bot", text)
            }
        }
        actionRow.addView(btnReply)

        val btnCopy = TextView(ctx).apply {
                letterSpacing = -0.06f
                typeface = AgentStyle.font(ctx)
            this.text = "📋  Copy"
            textSize = 14f
            typeface = Typeface.create(AgentStyle.font(ctx), Typeface.BOLD)
            setTextColor(if (isDark) 0xFFD4D4D8.toInt() else 0xFF3F3F46.toInt())
            setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
            isClickable = true
            setOnClickListener {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Message", text))
                Toast.makeText(ctx, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        actionRow.addView(btnCopy)
        card.addView(actionRow)

        dialog.setContentView(card)
        dialog.window?.let { win ->
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.setDimAmount(0.35f)
            win.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setGravity(Gravity.CENTER)
        }
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }

    /**
     * Displays the Cupertino '+' attachment modal sheet matching 6xingyv/compound.
     */
    @JvmStatic
    fun showAttachmentSheet(
        activity: BotsActivity,
        onQuickPrompt: PromptCallback,
        onMcpConnect: ActionCallback,
        onClearChat: ActionCallback
    ) {
        val ctx = activity
        val isDark = AgentStyle.dark

        val options = arrayOf(
            "⚡ Quick Prompts (Code & Review)",
            "🔌 Connect MCP Tools",
            "📋 Copy Current Conversation",
            "🗑️ Clear Messages"
        )

        AlertDialog.Builder(ctx)
            .setTitle("Conversation Actions")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showQuickPromptDialog(activity, onQuickPrompt)
                    1 -> onMcpConnect.run()
                    2 -> {
                        activity.copyConversation()
                    }
                    3 -> onClearChat.run()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showQuickPromptDialog(activity: BotsActivity, onSelect: PromptCallback) {
        val prompts = arrayOf(
            "Summarize project status and recent changes",
            "Review files for bugs, security and optimizations",
            "Create unit tests for latest features",
            "Explain step-by-step how the architecture works"
        )
        AlertDialog.Builder(activity)
            .setTitle("Quick Prompts")
            .setItems(prompts) { _, which ->
                onSelect.onSelect(prompts[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
