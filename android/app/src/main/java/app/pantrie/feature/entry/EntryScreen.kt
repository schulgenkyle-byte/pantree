package app.pantrie.feature.entry

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantrie.R
import app.pantrie.ui.theme.Brass
import app.pantrie.ui.theme.BrassBright
import app.pantrie.ui.theme.Ink
import app.pantrie.ui.theme.InkFaint
import app.pantrie.ui.theme.Mono
import app.pantrie.ui.theme.Paper
import app.pantrie.ui.theme.SerifDisplay
import androidx.compose.material3.Text

/**
 * Post-login home. Three photographic rooms — Sip · Eat · Solve — mirroring the
 * verbs in the Speakeater tagline. Replaces the previous "deck-as-home" landing.
 *
 * Design contract: see `speakeater-mock/entry-cards-demo.html` (canonical mock)
 * and `DESIGN_SYSTEM.md` §1, §2, §6.
 *
 * Each card routes into its room via the supplied callbacks. The room screens
 * own their own internal navigation; back from a room returns here.
 */
@Composable
fun EntryScreen(
  onPickSip: () -> Unit,
  onPickEat: () -> Unit,
  onPickSolve: () -> Unit,
  onOpenYou: () -> Unit,
) {
  val scroll = rememberScrollState()
  val cfg = LocalConfiguration.current
  val isWide = cfg.screenWidthDp >= 600

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Paper),
  ) {

    // Speakeasy haze — two soft radial brass washes for atmosphere.
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(Brass.copy(alpha = 0.05f), Color.Transparent),
            radius = 800f,
          ),
        ),
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scroll)
        .padding(horizontal = 20.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {

      // ----- Top bar: Speakeater wordmark + You pill -----
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = buildAnnotatedString {
            append("Speakeater")
            withStyle(SpanStyle(color = Brass)) { append(".") }
          },
          color = Ink,
          fontFamily = SerifDisplay,
          fontWeight = FontWeight.Normal,
          fontSize = 20.sp,
        )
        Spacer(Modifier.weight(1f))
        Box(
          modifier = Modifier
            .border(1.dp, InkFaint.copy(alpha = 0.4f))
            .clickable { onOpenYou() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
          Text(
            text = "YOU",
            color = InkFaint,
            fontFamily = Mono,
            fontWeight = FontWeight.Medium,
            fontSize = 9.sp,
            letterSpacing = 2.7.sp,
          )
        }
      }

      Spacer(Modifier.height(28.dp))

      // ----- Headline block -----
      Eyebrow("The Door")
      Spacer(Modifier.height(14.dp))
      Text(
        text = buildAnnotatedString {
          append("Why are you here. ")
          withStyle(
            SpanStyle(color = BrassBright, fontStyle = FontStyle.Italic),
          ) {
            append("Pick a room.")
          }
        },
        color = Ink,
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp,
      )

      Spacer(Modifier.height(28.dp))

      // ----- Three rooms -----
      val cardSpacing = 16.dp
      if (isWide) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(cardSpacing),
        ) {
          RoomCard(
            pip = "N° 01",
            verb = "Sip",
            name = "The Cellar",
            imageRes = R.drawable.room_sip,
            onClick = onPickSip,
            modifier = Modifier.weight(1f),
          )
          RoomCard(
            pip = "N° 02",
            verb = "Eat",
            name = "The Table",
            imageRes = R.drawable.room_eat,
            onClick = onPickEat,
            modifier = Modifier.weight(1f),
          )
          RoomCard(
            pip = "N° 03",
            verb = "Solve",
            name = "The Parlor",
            imageRes = R.drawable.room_solve,
            onClick = onPickSolve,
            modifier = Modifier.weight(1f),
          )
        }
      } else {
        RoomCard(
          pip = "N° 01",
          verb = "Sip",
          name = "The Cellar",
          imageRes = R.drawable.room_sip,
          onClick = onPickSip,
        )
        Spacer(Modifier.height(cardSpacing))
        RoomCard(
          pip = "N° 02",
          verb = "Eat",
          name = "The Table",
          imageRes = R.drawable.room_eat,
          onClick = onPickEat,
        )
        Spacer(Modifier.height(cardSpacing))
        RoomCard(
          pip = "N° 03",
          verb = "Solve",
          name = "The Parlor",
          imageRes = R.drawable.room_solve,
          onClick = onPickSolve,
        )
      }

      Spacer(Modifier.height(24.dp))

      // ----- Legend -----
      Row(verticalAlignment = Alignment.CenterVertically) {
        LegendWord("Sip")
        LegendDot()
        LegendWord("Eat")
        LegendDot()
        LegendWord("Solve")
      }

      Spacer(Modifier.height(20.dp))

      // Pro upsell card — self-hides for Pro users.
      app.pantrie.billing.ProUpgradeCard(vintageMode = false)

      Spacer(Modifier.height(12.dp))

      // Banner ad — self-hides for Pro users.
      app.pantrie.billing.BannerAd()

      Spacer(Modifier.height(8.dp))
    }
  }
}

@Composable
private fun Eyebrow(text: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    EyebrowHairline()
    Spacer(Modifier.width(12.dp))
    Text(
      text = text.uppercase(),
      color = Brass,
      fontFamily = Mono,
      fontWeight = FontWeight.Medium,
      fontSize = 10.sp,
      letterSpacing = 3.2.sp,
    )
    Spacer(Modifier.width(12.dp))
    EyebrowHairline()
  }
}

@Composable
private fun EyebrowHairline() {
  Box(
    modifier = Modifier
      .width(28.dp)
      .height(1.dp)
      .background(Brass.copy(alpha = 0.4f)),
  )
}

@Composable
private fun LegendWord(text: String) {
  Text(
    text = text.uppercase(),
    color = InkFaint,
    fontFamily = Mono,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    letterSpacing = 3.2.sp,
  )
}

@Composable
private fun LegendDot() {
  Text(
    text = "  ·  ",
    color = Brass,
    fontFamily = Mono,
    fontSize = 10.sp,
  )
}

/**
 * One photographic room card. 16:11 aspect, photographic background with sepia
 * scrim, art-deco corner brackets, top-left numeral pip, verb + section name
 * centered along the bottom.
 */
@Composable
private fun RoomCard(
  pip: String,
  verb: String,
  name: String,
  imageRes: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(16f / 11f)
      .border(1.dp, Brass.copy(alpha = 0.35f), RectangleShape)
      .clickable { onClick() },
  ) {
    // Photographic background — sepia-toned via overlay scrim
    Image(
      painter = painterResource(id = imageRes),
      contentDescription = name,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )

    // Sepia + darken wash. Three-stop vertical gradient: clear → mid-dark → black.
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colorStops = arrayOf(
              0.0f to Color.Transparent,
              0.5f to Color(0x59000000),
              1.0f to Color(0xEB000000),
            ),
          ),
        ),
    )
    // Brass top wash (very subtle)
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(Brass.copy(alpha = 0.06f), Color.Transparent),
          ),
        ),
    )

    // Inset double frame — 8.dp inside the outer border
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(8.dp)
        .border(1.dp, Brass.copy(alpha = 0.22f), RectangleShape),
    )

    // Art-deco corner brackets — four L-shaped brass marks
    CornerBracket(Alignment.TopStart)
    CornerBracket(Alignment.TopEnd)
    CornerBracket(Alignment.BottomStart)
    CornerBracket(Alignment.BottomEnd)

    // Top-left pip (mono)
    Text(
      text = pip,
      color = Brass,
      fontFamily = Mono,
      fontWeight = FontWeight.Medium,
      fontSize = 9.sp,
      letterSpacing = 2.7.sp,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(start = 26.dp, top = 20.dp),
    )

    // Bottom-center verb + section name
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 22.dp, start = 24.dp, end = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = buildAnnotatedString {
          withStyle(SpanStyle(color = Ink, fontStyle = FontStyle.Italic)) {
            append(verb)
          }
          withStyle(SpanStyle(color = Brass, fontStyle = FontStyle.Normal)) {
            append(".")
          }
        },
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.8).sp,
      )
      Spacer(Modifier.height(6.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .width(16.dp)
            .height(1.dp)
            .background(Brass.copy(alpha = 0.4f)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
          text = name.uppercase(),
          color = Brass,
          fontFamily = Mono,
          fontWeight = FontWeight.Medium,
          fontSize = 10.sp,
          letterSpacing = 3.6.sp,
        )
        Spacer(Modifier.width(10.dp))
        Box(
          modifier = Modifier
            .width(16.dp)
            .height(1.dp)
            .background(Brass.copy(alpha = 0.4f)),
        )
      }
    }
  }
}

/**
 * L-shaped art-deco corner mark, drawn with a stepped pip at the exact corner.
 * Placement via Alignment (TopStart/TopEnd/BottomStart/BottomEnd) on the parent Box.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.CornerBracket(alignment: Alignment) {
  val inset = 14.dp
  val armLen = 18.dp

  Box(
    modifier = Modifier
      .align(alignment)
      .padding(inset)
      .size(armLen)
      .drawBehind {
        val strokeW = 1f * density
        val tlArms = (alignment == Alignment.TopStart)
        val trArms = (alignment == Alignment.TopEnd)
        val blArms = (alignment == Alignment.BottomStart)
        val brArms = (alignment == Alignment.BottomEnd)

        when {
          tlArms -> {
            // top + left arms
            drawLine(SolidColor(Brass), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = strokeW)
            drawLine(SolidColor(Brass), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), strokeWidth = strokeW)
          }
          trArms -> {
            drawLine(SolidColor(Brass), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = strokeW)
            drawLine(SolidColor(Brass), androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = strokeW)
          }
          blArms -> {
            drawLine(SolidColor(Brass), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = strokeW)
            drawLine(SolidColor(Brass), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), strokeWidth = strokeW)
          }
          brArms -> {
            drawLine(SolidColor(Brass), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = strokeW)
            drawLine(SolidColor(Brass), androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = strokeW)
          }
        }
        // Stepped pip at the exact corner of the bracket
        val pipSize = 5f * density
        val pipOffset = when {
          tlArms -> androidx.compose.ui.geometry.Offset(-pipSize / 2, -pipSize / 2)
          trArms -> androidx.compose.ui.geometry.Offset(size.width - pipSize / 2, -pipSize / 2)
          blArms -> androidx.compose.ui.geometry.Offset(-pipSize / 2, size.height - pipSize / 2)
          brArms -> androidx.compose.ui.geometry.Offset(size.width - pipSize / 2, size.height - pipSize / 2)
          else -> androidx.compose.ui.geometry.Offset.Zero
        }
        drawRect(
          color = Brass.copy(alpha = 0.85f),
          topLeft = pipOffset,
          size = androidx.compose.ui.geometry.Size(pipSize, pipSize),
        )
      },
  )
}
