package app.kkambbak.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.kkambbak.core.Verdict

/** 깜빡 디자인 토큰. 차량 화면에 맞춰 어두운 바탕, 높은 대비, 방향지시등과 같은 앰버를 브랜드 색으로 쓴다. */
data class KPalette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val amber: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
)

val DefaultPalette = KPalette(
    bg = Color(0xFF0D0F12),
    surface = Color(0xFF161A1F),
    surface2 = Color(0xFF1F242B),
    line = Color(0xFF2C323B),
    text = Color(0xFFF2F4F6),
    text2 = Color(0xFFA9B1BB),
    text3 = Color(0xFF6E7781),
    amber = Color(0xFFFFB020),
    good = Color(0xFF3DDC97),
    warn = Color(0xFFFFB020),
    bad = Color(0xFFFF6B5B),
)

/** 리워드 '야간 시그널' 테마: 바탕에 앰버가 살짝 섞인다. */
val NightSignalPalette = DefaultPalette.copy(
    bg = Color(0xFF120E08),
    surface = Color(0xFF1C1610),
    surface2 = Color(0xFF261E14),
    line = Color(0xFF3A2E1E),
)

val LocalPalette = staticCompositionLocalOf { DefaultPalette }

object K {
    val c: KPalette @Composable get() = LocalPalette.current

    val display = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold, lineHeight = 62.sp)
    val h1 = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
    val h2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp)
    val body = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp)
    val bodyStrong = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp)
    val small = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp)
    val tiny = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
}

@Composable
fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.ON_TIME -> K.c.good
    Verdict.LATE -> K.c.warn
    Verdict.MISSED, Verdict.WRONG_SIDE -> K.c.bad
}

@Composable
fun KkambbakTheme(night: Boolean = false, content: @Composable () -> Unit) {
    val p = if (night) NightSignalPalette else DefaultPalette
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = p.amber, onPrimary = Color(0xFF1A1200),
                background = p.bg, surface = p.surface, onSurface = p.text, onBackground = p.text,
            ),
            content = content,
        )
    }
}
