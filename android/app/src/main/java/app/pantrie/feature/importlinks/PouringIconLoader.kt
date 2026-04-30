package app.pantrie.feature.importlinks

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pantrie.R

/**
 * Brand-on-theme loading indicator for the link-import flow.
 *
 *   [ Speakeater S logo, 72dp ]
 *           ↓ ↓ ↓                ← three brass drops falling
 *           ↓ ↓ ↓
 *      ╲ ▒▒▒▒▒ ╱                 ← coupe glass filling with liquid
 *
 * The S icon is the actual launcher PNG (Speakeater logo). Drops are pure
 * Compose Canvas — vector, 60+fps, no network, perfect loop. Glass fills over
 * 4 seconds then resets via the inherent loop, reading as a continuous pour.
 *
 * Decision note: the user proposed generating a Veo video for the loop, but
 * native Compose is the right call here — no network, no per-loop API cost,
 * crisp at any density, perfect seam, and the file size delta is zero.
 */
@Composable
fun PouringIconLoader(
  label: String,
  sublabel: String? = null,
) {
  val transition = rememberInfiniteTransition(label = "pour")

  // Brass palette — matches the Speakeater theme (BrassBright #D4A04A).
  val brass = Color(0xFFD4A04A)
  val brassDeep = Color(0xFFB5853A)
  val brassDim = Color(0xFFA67D38)

  // Three drops staggered 33% out of phase, looping every 1200ms. Each drop
  // travels from the icon bottom (y=0) to the glass rim (y=POUR_HEIGHT).
  val drop1 = transition.animateFloat(
    0f, 1f,
    infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
    label = "drop1",
  )
  val drop2 = transition.animateFloat(
    -0.33f, 0.67f,
    infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
    label = "drop2",
  )
  val drop3 = transition.animateFloat(
    -0.66f, 0.34f,
    infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
    label = "drop3",
  )

  // Glass fill: 0 → 1 over 4.5s, then snaps to 0 via Restart. Reads as "filled,
  // served, refilling" without needing a separate drain animation.
  val fillLevel = transition.animateFloat(
    0f, 1f,
    infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Restart),
    label = "fill",
  )

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Image(
      // Flat PNG copy at drawable/speakeater_logo.png. The mipmap launcher is an
      // adaptive icon (XML wrapper in mipmap-anydpi-v26) which painterResource
      // can't load — only VectorDrawables or rasterized assets work here.
      painter = painterResource(R.drawable.speakeater_logo),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)),
    )

    // Pour gap — three falling drops + faint stream connector between icon and glass.
    Canvas(modifier = Modifier.width(72.dp).height(38.dp)) {
      val centerX = size.width / 2f
      val pourHeight = size.height
      val dropWidth = 5.dp.toPx()
      val dropHeight = 9.dp.toPx()

      // Stream connector: thin brass line from icon bottom fading down ~30%.
      drawLine(
        brush = Brush.verticalGradient(
          colors = listOf(brass.copy(alpha = 0.65f), brass.copy(alpha = 0f)),
          startY = 0f, endY = pourHeight * 0.3f,
        ),
        start = Offset(centerX, 0f),
        end = Offset(centerX, pourHeight * 0.3f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
      )

      listOf(drop1.value, drop2.value, drop3.value).forEachIndexed { i, raw ->
        val p = ((raw % 1f) + 1f) % 1f
        val xJitter = (i - 1) * 2f.dp.toPx() * (1f - p)
        val cx = centerX + xJitter
        val cy = p * pourHeight
        val alpha = when {
          p < 0.1f -> p / 0.1f
          p > 0.9f -> (1f - p) / 0.1f
          else -> 1f
        }
        drawOval(
          brush = Brush.verticalGradient(
            colors = listOf(brass.copy(alpha = alpha), brassDeep.copy(alpha = alpha * 0.8f)),
            startY = cy - dropHeight / 2f,
            endY = cy + dropHeight / 2f,
          ),
          topLeft = Offset(cx - dropWidth / 2f, cy - dropHeight / 2f),
          size = Size(dropWidth, dropHeight),
        )
      }
    }

    // Coupe glass — wide bowl, narrow stem. Drops land in the bowl, liquid level rises.
    Canvas(modifier = Modifier.size(width = 80.dp, height = 70.dp)) {
      val w = size.width
      val h = size.height
      val bowlH = h * 0.55f      // bowl is the top 55%
      val stemH = h * 0.30f      // stem is the next 30%
      val baseH = h * 0.15f      // base is the bottom 15%

      // Bowl path — gentle V (coupe profile): wide at top, narrows to stem.
      val bowlPath = Path().apply {
        moveTo(w * 0.05f, 0f)             // top-left rim
        lineTo(w * 0.95f, 0f)             // top-right rim
        cubicTo(
          w * 0.85f, bowlH * 0.5f,
          w * 0.65f, bowlH * 0.95f,
          w * 0.55f, bowlH,                // bottom-right of bowl, joins stem
        )
        lineTo(w * 0.45f, bowlH)           // bottom-left of bowl
        cubicTo(
          w * 0.35f, bowlH * 0.95f,
          w * 0.15f, bowlH * 0.5f,
          w * 0.05f, 0f,                   // back to top-left
        )
        close()
      }

      // Liquid fill — clipped to bowl, level rises with fillLevel.
      // Liquid surface is slightly wavy via two nearly-overlapping ellipses,
      // giving a subtle shimmer at the meniscus.
      val liquidLevelY = bowlH * (1f - fillLevel.value)
      clipPath(bowlPath) {
        // Body of the liquid
        drawRect(
          color = brass.copy(alpha = 0.78f),
          topLeft = Offset(0f, liquidLevelY),
          size = Size(w, bowlH - liquidLevelY + 2f),
        )
        // Meniscus highlight (thin lighter band at the surface)
        if (fillLevel.value > 0.05f && fillLevel.value < 0.98f) {
          drawRect(
            color = Color(0xFFE8C475).copy(alpha = 0.55f),
            topLeft = Offset(0f, liquidLevelY),
            size = Size(w, 2.dp.toPx()),
          )
        }
      }

      // Bowl outline — drawn AFTER liquid so it sits on top, crisp brass edge.
      drawPath(
        path = bowlPath,
        color = brassDim,
        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
      )

      // Stem — a thin brass vertical line.
      drawLine(
        color = brassDim,
        start = Offset(w / 2f, bowlH),
        end = Offset(w / 2f, bowlH + stemH),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
      )

      // Base — brass disc.
      drawLine(
        color = brassDim,
        start = Offset(w * 0.30f, bowlH + stemH + baseH * 0.3f),
        end = Offset(w * 0.70f, bowlH + stemH + baseH * 0.3f),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round,
      )
    }

    Spacer(Modifier.height(10.dp))

    Text(
      label,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    if (sublabel != null) {
      Spacer(Modifier.height(4.dp))
      Text(
        sublabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
      )
    }
  }
}
