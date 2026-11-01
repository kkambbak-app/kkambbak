package app.kkambbak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kkambbak.core.ManeuverKind
import app.kkambbak.core.Signal
import app.kkambbak.core.Verdict

@Composable
fun KCard(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(K.c.surface)
            .border(1.dp, K.c.line, RoundedCornerShape(20.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = K.small, color = K.c.text2, modifier = modifier.padding(bottom = 10.dp))
}

@Composable
fun VerdictChip(v: Verdict) {
    val col = verdictColor(v)
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(col.copy(alpha = 0.16f))
            .border(1.dp, col.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Text(v.korean, style = K.tiny, color = col) }
}

@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    sub: String? = null,
    onClick: () -> Unit,
) {
    val bg = when {
        danger -> K.c.bad.copy(alpha = 0.18f)
        primary -> K.c.amber
        else -> K.c.surface2
    }
    val fg = when {
        danger -> K.c.bad
        primary -> Color(0xFF1A1200)
        else -> K.c.text
    }
    Column(
        modifier
            .heightIn(min = 76.dp) // 차량 화면 최소 터치 영역보다 넉넉하게
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, if (danger) K.c.bad.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = K.h2, color = fg, textAlign = TextAlign.Center)
        if (sub != null) Text(sub, style = K.tiny, color = fg.copy(alpha = 0.75f), textAlign = TextAlign.Center)
    }
}

@Composable
fun SmallButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (primary) K.c.amber else K.c.surface2
    val fg = if (primary) Color(0xFF1A1200) else K.c.text
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else K.c.surface2.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = K.bodyStrong, color = if (enabled) fg else K.c.text3) }
}

/** 깜빡 로고: 바깥을 향한 좌우 방향지시등 화살표. lit 으로 어느 쪽이 켜졌는지 보여 준다. */
@Composable
fun SignalMark(size: Dp, signal: Signal = Signal.OFF, both: Boolean = false, modifier: Modifier = Modifier) {
    val amber = K.c.amber
    val dim = K.c.line
    Canvas(modifier.size(width = size * 2.1f, height = size)) {
        val w = this.size.width
        val h = this.size.height
        fun arrow(left: Boolean, color: Color) {
            // 꼬리가 짧은 삼각 화살표(계기판 방향지시등 모양)
            val p = Path()
            val tip = if (left) w * 0.04f else w * 0.96f
            val base = if (left) w * 0.40f else w * 0.60f
            val neck = if (left) w * 0.22f else w * 0.78f
            p.moveTo(tip, h * 0.5f)
            p.lineTo(neck, h * 0.08f)
            p.lineTo(neck, h * 0.30f)
            p.lineTo(base, h * 0.30f)
            p.lineTo(base, h * 0.70f)
            p.lineTo(neck, h * 0.70f)
            p.lineTo(neck, h * 0.92f)
            p.close()
            drawPath(p, color)
        }
        arrow(true, if (both || signal == Signal.LEFT) amber else dim)
        arrow(false, if (both || signal == Signal.RIGHT) amber else dim)
    }
}

/** 회전 종류 아이콘(간단한 화살표). */
@Composable
fun ManeuverGlyph(kind: ManeuverKind, color: Color, size: Dp = 34.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        val st = Stroke(width = s * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val p = Path()
        val right = kind.side == app.kkambbak.core.Side.RIGHT
        val sign = if (right) 1f else -1f
        val cx = s / 2
        when (kind) {
            ManeuverKind.U_TURN -> {
                p.moveTo(cx + s * 0.18f, s * 0.85f); p.lineTo(cx + s * 0.18f, s * 0.38f)
                p.cubicTo(cx + s * 0.18f, s * 0.08f, cx - s * 0.22f, s * 0.08f, cx - s * 0.22f, s * 0.38f)
                p.lineTo(cx - s * 0.22f, s * 0.6f)
                drawPath(p, color, style = st)
                val a = Path(); a.moveTo(cx - s * 0.36f, s * 0.48f); a.lineTo(cx - s * 0.22f, s * 0.64f); a.lineTo(cx - s * 0.08f, s * 0.48f)
                drawPath(a, color, style = st)
            }
            ManeuverKind.LEFT_TURN, ManeuverKind.RIGHT_TURN -> {
                p.moveTo(cx - sign * s * 0.12f, s * 0.88f); p.lineTo(cx - sign * s * 0.12f, s * 0.42f)
                p.lineTo(cx + sign * s * 0.30f, s * 0.42f)
                drawPath(p, color, style = st)
                val a = Path(); a.moveTo(cx + sign * s * 0.16f, s * 0.26f); a.lineTo(cx + sign * s * 0.32f, s * 0.42f); a.lineTo(cx + sign * s * 0.16f, s * 0.58f)
                drawPath(a, color, style = st)
            }
            else -> {
                p.moveTo(cx - sign * s * 0.10f, s * 0.88f); p.lineTo(cx - sign * s * 0.10f, s * 0.56f)
                p.lineTo(cx + sign * s * 0.22f, s * 0.18f)
                drawPath(p, color, style = st)
                val a = Path(); a.moveTo(cx + sign * s * 0.02f, s * 0.16f); a.lineTo(cx + sign * s * 0.24f, s * 0.16f); a.lineTo(cx + sign * s * 0.26f, s * 0.38f)
                drawPath(a, color, style = st)
            }
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    Box(modifier.height(height).fillMaxWidth().clip(RoundedCornerShape(50)).background(K.c.surface2)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
fun Ring(fraction: Float, color: Color, size: Dp, stroke: Dp = 16.dp, content: @Composable () -> Unit) {
    val track = K.c.surface2
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val d = this.size.minDimension - sw
            val tl = Offset(sw / 2, sw / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = K.bodyStrong, color = K.c.text)
            Text(desc, style = K.small, color = K.c.text2)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = K.c.amber, checkedThumbColor = Color(0xFF1A1200)),
        )
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun StatLine(label: String, value: String, color: Color = K.c.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = K.body, color = K.c.text2, modifier = Modifier.weight(1f))
        Text(value, style = K.bodyStrong, color = color)
    }
}
