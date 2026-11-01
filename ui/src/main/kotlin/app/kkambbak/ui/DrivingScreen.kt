package app.kkambbak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kkambbak.core.GearState
import app.kkambbak.core.LegalDistance
import kotlin.math.roundToInt

/**
 * 주행 중 화면. 운전자 주의 분산 가이드에 따라 움직이는 요소·목록·판정 결과를 띄우지 않는다.
 * 큰 버튼 두 개(음성, 사고 기록)만 둔다. 판정 결과는 주차 후에 들려준다.
 */
@Composable
fun DrivingScreen(state: UiState, onAction: (UiAction) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(K.c.bg)) {
        val wide = maxWidth > 900.dp
        Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SignalMark(40.dp, both = true)
                        Spacer(Modifier.width(14.dp))
                        Text("깜빡", style = K.h1, color = K.c.text)
                    }
                    Spacer(Modifier.height(28.dp))
                    Text("주행 중 · 깜빡이 기록 중", style = K.h1, color = K.c.text)
                    Spacer(Modifier.height(8.dp))
                    Text("결과는 주차한 뒤에 들려드릴게요.", style = K.body, color = K.c.text2)
                    if (state.listening) {
                        Spacer(Modifier.height(16.dp))
                        Text("듣고 있어요… \"사고 기록\"이라고 말하면 직전 60초를 남겨요.", style = K.body, color = K.c.amber)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BigButton("음성", Modifier.weight(1f), sub = "\"사고 기록\" · \"오늘 어땠어?\"") { onAction(UiAction.VoiceButton) }
                    BigButton("사고 기록", Modifier.weight(1f), danger = true, sub = "직전 60초 신호 고정") { onAction(UiAction.CrashButton) }
                }
            }
            if (state.demo != null && wide) DemoObserver(state, Modifier.weight(1.05f).fillMaxHeight())
        }
    }
}

/** 시연 관찰 패널: 심사·시연에서 엔진이 무엇을 보고 있는지 보여 주기 위한 화면. */
@Composable
private fun DemoObserver(state: UiState, modifier: Modifier) {
    val live = state.live
    val demo = state.demo ?: return
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(K.c.surface)
            .border(1.dp, K.c.amber.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("시연 관찰 패널", style = K.bodyStrong, color = K.c.amber, modifier = Modifier.weight(1f))
            Text("실제 주행 중에는 표시하지 않음", style = K.tiny, color = K.c.text3)
        }
        Spacer(Modifier.height(4.dp))
        Text(demo.title, style = K.small, color = K.c.text2)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            SignalMark(30.dp, signal = live.signal)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(live.nextManeuver ?: "안내 없음", style = K.h2, color = K.c.text)
                val d = live.nextDistanceM
                Text(
                    if (d != null) "남은 거리 ${d.coerceAtLeast(0.0).roundToInt()}m" else "직진",
                    style = K.small, color = K.c.text2,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${live.speedKmh.roundToInt()} km/h", style = K.h2, color = K.c.text)
                Text(gearLabel(live.gear) + (if (live.brake) " · 브레이크" else ""), style = K.small, color = K.c.text2)
            }
        }
        Spacer(Modifier.height(12.dp))
        DistanceBar(live.nextDistanceM, live.nextLegalM, live.signal != app.kkambbak.core.Signal.OFF)
        Spacer(Modifier.height(16.dp))
        Text("판정 기록", style = K.small, color = K.c.text2)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            demo.log.takeLast(4).forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ManeuverGlyph(r.kind, K.c.text2, 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("${r.place} ${r.kind.label}", style = K.small, color = K.c.text, modifier = Modifier.weight(1f))
                    Text(r.signalDistanceM?.let { "${it.roundToInt()}m 앞" } ?: "—", style = K.small, color = K.c.text2)
                    Spacer(Modifier.width(10.dp))
                    VerdictChip(r.verdict)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        ProgressBar(demo.progress, K.c.amber.copy(alpha = 0.6f), height = 6.dp)
    }
}

/** 남은 거리 막대: 일반 0~150m(법정 30m), 고속도로 0~300m(법정 100m). 지시등이 켜지면 앰버. */
@Composable
private fun DistanceBar(distance: Double?, legal: Double, signalOn: Boolean) {
    val amber = K.c.amber
    val track = K.c.surface2
    val line = K.c.text2
    val good = K.c.good
    Column {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val w = size.width
            val h = size.height
            drawRoundRect(track, Offset(0f, h * 0.3f), Size(w, h * 0.4f), CornerRadius(h, h))
            val maxM = if (legal >= LegalDistance.HIGHWAY_M) 300.0 else 150.0
            if (distance != null && distance in 0.0..maxM) {
                val x = (w * (1 - distance / maxM)).toFloat()
                drawRoundRect(if (signalOn) amber else line.copy(alpha = 0.5f), Offset(0f, h * 0.3f), Size(x, h * 0.4f), CornerRadius(h, h))
                drawCircle(if (signalOn) amber else line, h * 0.42f, Offset(x, h / 2))
            }
            val lx = (w * (1 - legal / maxM)).toFloat()
            drawLine(good, Offset(lx, 0f), Offset(lx, h), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
        }
        val maxM = if (legal >= LegalDistance.HIGHWAY_M) 300.0 else 150.0
        val f = (legal / maxM).toFloat()
        Row(Modifier.fillMaxWidth()) {
            Text(if (legal >= LegalDistance.HIGHWAY_M) "300m" else "150m", style = K.tiny, color = K.c.text3, modifier = Modifier.weight(1f - f))
            Row(Modifier.weight(f)) {
                Text("법정 ${legal.roundToInt()}m", style = K.tiny, color = K.c.good, modifier = Modifier.weight(1f))
                Text("회전", style = K.tiny, color = K.c.text3)
            }
        }
    }
}

fun gearLabel(g: GearState) = when (g) {
    GearState.PARK -> "P"
    GearState.DRIVE -> "D"
    GearState.REVERSE -> "R"
    GearState.NEUTRAL -> "N"
    GearState.UNKNOWN -> "—"
}

@Composable
internal fun FullCenter(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
