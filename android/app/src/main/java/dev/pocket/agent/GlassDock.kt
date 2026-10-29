package dev.pocket.agent

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import java.util.function.IntConsumer

/** Kyant Backdrop refraction with a readable tint fallback on older Android. */
object GlassDock {
 @JvmStatic fun create(context: Context, dark: Boolean, selected: Int, action: IntConsumer): View = ComposeView(context).apply {
  setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  setContent {
   val backdrop = rememberCanvasBackdrop {
    drawRect(Brush.linearGradient(listOf(if(dark) Color(0xFF202535) else Color(0xFFE2ECFF), if(dark) Color(0xFF3B3051) else Color(0xFFF2E6FA),if(dark) Color(0xFF183A41) else Color(0xFFD7F0ED))))
   }
   Row(Modifier.fillMaxWidth().padding(5.dp).drawBackdrop(backdrop,shape={RoundedCornerShape(32.dp)},effects={blur(8.dp.toPx());lens(18.dp.toPx(),24.dp.toPx())},onDrawSurface={drawRect(if(dark)Color.Black.copy(alpha=.28f) else Color.White.copy(alpha=.42f))}).padding(5.dp).height(54.dp),verticalAlignment=Alignment.CenterVertically) {
    listOf("Bots","Market","Terminal","Settings").forEachIndexed { index, label ->
     Box(Modifier.weight(1f).fillMaxHeight().background(if(selected==index)if(dark)Color.White.copy(alpha=.16f)else Color.White.copy(alpha=.78f)else Color.Transparent,RoundedCornerShape(26.dp)).clickable { action.accept(index) },contentAlignment=Alignment.Center) {
      BasicText(label,style=TextStyle(color=if(selected==index)Color(0xFF4596FF) else if(dark)Color(0xFFE9ECF5)else Color(0xFF343B49),fontSize=12.sp,fontWeight=if(selected==index)FontWeight.Bold else FontWeight.Normal))
     }
    }
   }
  }
 }
}
