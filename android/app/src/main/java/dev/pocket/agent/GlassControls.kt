package dev.pocket.agent

import android.app.Dialog
import androidx.activity.ComponentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

/** The same glass material for Compose navigation and native editable controls. */
internal fun Modifier.pockoGlass(dark: Boolean, corner: Dp, primary: Boolean = false, pressed: Boolean = false): Modifier = composed {
    val highlight by animateFloatAsState(if (pressed) .09f else 0f, tween(160), label = "glassPress")
    val backdrop = rememberCanvasBackdrop {
        drawRect(Brush.linearGradient(when {
            primary -> listOf(Color(0xFF005DD6), Color(0xFF168CFF), Color(0xFF0063DE))
            dark -> listOf(Color(0xFF171719), Color(0xFF202022), Color(0xFF141416))
            else -> listOf(Color(0xFFF4F4F5), Color(0xFFFFFFFF), Color(0xFFE4E4E7))
        }))
    }
    this.clip(RoundedCornerShape(corner)).drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedCornerShape(corner) },
        effects = { blur(10.dp.toPx()); lens((if (dark) 18.dp else 8.dp).toPx(), (if (dark) 26.dp else 12.dp).toPx()) },
        onDrawSurface = { drawRect(Color.White.copy(alpha = (if (dark) .035f else .12f) + highlight)) }
    ).border(
        1.dp,
        Brush.verticalGradient(if (dark) listOf(Color.White.copy(alpha = .06f), Color.White.copy(alpha = .02f))
            else listOf(Color.White.copy(alpha = .16f), Color(0xFFDDE3EC).copy(alpha = .08f))),
        RoundedCornerShape(corner)
    )
}

object GlassControls {
    @JvmStatic
    fun bindDialog(dialog: Dialog, owner: ComponentActivity) {
        dialog.window?.decorView?.apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
        }
    }

    /** Keep native text input, accessibility and click handling above a decorative glass layer. */
    @JvmStatic
    fun wrap(content: View, corner: Float, primary: Boolean): View {
        val outerParams = content.layoutParams
        content.background = null
        content.elevation = 0f
        content.translationZ = 0f
        content.stateListAnimator = null
        if (content is AgentIcon) content.setIconTint(if (primary) android.graphics.Color.WHITE else AgentStyle.TEXT)
        return GlassControlFrame(content, corner, primary).apply { if (outerParams != null) layoutParams = outerParams }
    }
}

private class GlassControlFrame(content: View, corner: Float, primary: Boolean) : FrameLayout(content.context) {
    private val pressed = mutableStateOf(false)
    private val glass = ComposeView(context).apply {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isFocusable = false
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        setContent { Box(Modifier.fillMaxSize().pockoGlass(AgentStyle.dark, corner.dp, primary, pressed.value)) }
    }
    init {
        elevation = 0f
        translationZ = 0f
        stateListAnimator = null
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        // A zero-sized layout param keeps the decorative layer out of wrap-content sizing.
        addView(glass, LayoutParams(0, 0))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        glass.measure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressed.value = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressed.value = false
        }
        return super.dispatchTouchEvent(event)
    }
}
