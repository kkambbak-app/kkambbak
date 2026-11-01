package app.kkambbak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kkambbak.core.Benchmarks
import app.kkambbak.core.WeeklyStats
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WeeklyScreen(state: UiState, wide: Boolean, onAction: (UiAction) -> Unit) {
    val w = state.weekly
    val chart = @Composable { m: Modifier ->
        KCard(m) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("최근 7일 · 하루 회전 중 비율", style = K.small, color = K.c.text2, modifier = Modifier.weight(1f))
                Legend()
            }
            Spacer(Modifier.height(12.dp))
            WeekChart(w, Modifier.fillMaxWidth().weight(1f))
        }
    }
    val side = @Composable { m: Modifier ->
        KCard(m) {
            SectionTitle("이번 주 요약")
            Text("켠 비율 ${pct(w.onTime + w.late, w.judged)}", style = K.h1, color = K.c.text)
            Text(Benchmarks.NATIONAL_SIGNAL_LABEL, style = K.small, color = K.c.text2)
            Spacer(Modifier.height(12.dp))
            StatLine("판정한 회전", "${w.judged}번")
            StatLine("법정 거리 앞에서 켬", pct(w.onTime, w.judged), K.c.good)
            StatLine("늦게 · 안 켬", "${w.late}번 · ${w.missed}번", K.c.warn)
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = K.c.line)
            Spacer(Modifier.height(12.dp))
            Text("자주 놓친 곳", style = K.small, color = K.c.text2)
            Spacer(Modifier.height(6.dp))
            if (w.topMissedPlaces.isEmpty()) Text("없어요", style = K.body, color = K.c.text)
            w.topMissedPlaces.forEachIndexed { i, (place, n) ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = K.bodyStrong, color = K.c.amber, modifier = Modifier.width(24.dp))
                    Text(place, style = K.body, color = K.c.text, modifier = Modifier.weight(1f))
                    Text("${n}번", style = K.body, color = K.c.text2)
                }
            }
            Spacer(Modifier.weight(1f))
            SmallButton(if (state.speaking) "읽는 중…" else "주간 브리핑 듣기", primary = true, modifier = Modifier.fillMaxWidth()) { onAction(UiAction.WeeklyBriefing) }
        }
    }
    if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        chart(Modifier.weight(1.5f).fillMaxHeight()); side(Modifier.weight(1f).fillMaxHeight())
    } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        chart(Modifier.fillMaxWidth().height(320.dp)); side(Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(K.c.good); Text("제때", style = K.tiny, color = K.c.text2)
        Dot(K.c.warn); Text("늦게", style = K.tiny, color = K.c.text2)
        Dot(K.c.bad); Text("안 켬", style = K.tiny, color = K.c.text2)
    }
}

/** 하루 회전을 100%로 놓고 제때·늦게·안 켬을 쌓은 막대. 점선 = 교통문화지수 전국 점등률(켠 비율과 비교). */
@Composable
private fun WeekChart(w: WeeklyStats, modifier: Modifier) {
    val good = K.c.good; val warn = K.c.warn; val bad = K.c.bad
    val grid = K.c.line; val empty = K.c.surface2; val ref = K.c.text2
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val n = w.days.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.5f
            val h = size.height
            for (g in listOf(0.25f, 0.5f, 0.75f, 1f)) drawLine(grid, Offset(0f, h * (1 - g)), Offset(size.width, h * (1 - g)), 1f)
            w.days.forEachIndexed { i, d ->
                val x = slot * i + (slot - bw) / 2
                if (d.judged == 0) {
                    drawRoundRect(empty, Offset(x, h - 6f), Size(bw, 6f), CornerRadius(3f, 3f))
                } else {
                    val fGood = d.onTime / d.judged.toFloat()
                    val fLate = d.late / d.judged.toFloat()
                    val fBad = d.missed / d.judged.toFloat()
                    var top = h
                    fun seg(f: Float, c: androidx.compose.ui.graphics.Color) {
                        if (f <= 0f) return
                        val hh = h * f
                        drawRect(c, Offset(x, top - hh), Size(bw, hh - 2f))
                        top -= hh
                    }
                    seg(fGood, good); seg(fLate, warn); seg(fBad, bad.copy(alpha = 0.85f))
                }
            }
            val y = h * (1 - (Benchmarks.NATIONAL_SIGNAL_RATE / 100.0).toFloat())
            drawLine(ref, Offset(0f, y), Offset(size.width, y), 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            w.days.forEach { d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN), style = K.small, color = K.c.text, textAlign = TextAlign.Center)
                    Text(if (d.judged == 0) "—" else "${d.signalUseRate.roundToInt()}%", style = K.tiny, color = K.c.text2, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("점선: 전국 방향지시등 점등률 75.6% · 요일 아래 숫자: 그날 켠 비율", style = K.tiny, color = K.c.text3)
    }
}
