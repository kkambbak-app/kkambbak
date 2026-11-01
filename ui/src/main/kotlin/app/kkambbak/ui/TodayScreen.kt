package app.kkambbak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kkambbak.core.KST
import app.kkambbak.core.ManeuverResult
import app.kkambbak.core.Verdict
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun TodayScreen(state: UiState, wide: Boolean, onAction: (UiAction) -> Unit) {
    val rs = state.todayResults
    val judged = rs.size
    val onTime = rs.count { it.verdict == Verdict.ON_TIME }
    val late = rs.count { it.verdict == Verdict.LATE }
    val missed = judged - onTime - late

    val summary = @Composable { m: Modifier ->
        KCard(m) {
            SectionTitle("오늘의 깜빡이")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(if (judged == 0) 0f else onTime / judged.toFloat(), K.c.good, 150.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (judged == 0) "—" else "$onTime/$judged", style = K.h1, color = K.c.text)
                        Text("제때", style = K.small, color = K.c.text2)
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    StatLine("켠 비율", pct(onTime + late, judged))
                    StatLine("법정 거리 앞에서 켬", pct(onTime, judged), K.c.good)
                    StatLine("늦게 켬", "${late}번", if (late > 0) K.c.warn else K.c.text)
                    StatLine("켜지 않음", "${missed}번", if (missed > 0) K.c.bad else K.c.text)
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = K.c.line)
            Spacer(Modifier.height(14.dp))
            Text("주차 브리핑", style = K.small, color = K.c.text2)
            Spacer(Modifier.height(6.dp))
            Text(state.briefing ?: "주차하면 이번 주행의 깜빡이 기록을 10초로 들려드려요.", style = K.body, color = K.c.text)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallButton(if (state.speaking) "읽는 중…" else "브리핑 다시 듣기", primary = true, enabled = state.briefing != null) { onAction(UiAction.ReplayBriefing) }
                SmallButton(if (state.listening) "듣고 있어요…" else "음성으로 묻기") { onAction(UiAction.VoiceButton) }
            }
        }
    }

    val list = @Composable { m: Modifier ->
        KCard(m) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("오늘 판정한 회전", style = K.small, color = K.c.text2, modifier = Modifier.weight(1f))
                Text("${judged}건", style = K.small, color = K.c.text3)
            }
            Spacer(Modifier.height(8.dp))
            if (rs.isEmpty()) {
                Text("아직 오늘 기록이 없어요. 운전하면 내비 안내 회전마다 자동으로 기록돼요.", style = K.body, color = K.c.text2)
                if (!state.demoProfile) {
                    Spacer(Modifier.height(16.dp))
                    SmallButton("시연 모드로 둘러보기", primary = true) { onAction(UiAction.SelectProfile("시연 운전자")) }
                    Text("가상 주행 기록과 시연 주행으로 모든 화면을 볼 수 있어요. 실제 기록과 섞이지 않아요.", style = K.small, color = K.c.text3, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(rs.reversed()) { r -> ResultRow(r) }
                }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            summary(Modifier.weight(1f))
            list(Modifier.weight(1.15f).fillMaxHeight())
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            summary(Modifier.fillMaxWidth())
            list(Modifier.fillMaxWidth().weight(1f))
        }
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun ResultRow(r: ManeuverResult) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Instant.ofEpochMilli(r.timeMs).atZone(KST).format(timeFmt), style = K.small, color = K.c.text3, modifier = Modifier.width(56.dp))
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { ManeuverGlyph(r.kind, verdictColor(r.verdict), 30.dp) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(if (r.place.isBlank()) r.kind.label else "${r.place} ${r.kind.label}", style = K.bodyStrong, color = K.c.text)
            Text(detail(r), style = K.small, color = K.c.text2)
        }
        VerdictChip(r.verdict)
    }
}

fun detail(r: ManeuverResult): String {
    val legal = "기준 ${r.legalDistanceM.roundToInt()}m"
    val d = r.signalDistanceM
    return when {
        !r.viaNavigation -> when (r.verdict) {
            Verdict.ON_TIME -> "방향을 틀기 전에 켬 · 내비 안내 없이 감지"
            Verdict.LATE -> "방향을 틀면서 켬 · 내비 안내 없이 감지"
            else -> "켜지 않음 · 내비 안내 없이 감지"
        }
        d != null -> "회전 ${d.roundToInt()}m 앞에서 켬 · $legal"
        r.verdict == Verdict.WRONG_SIDE -> "반대쪽 지시등 · $legal"
        else -> "켜지 않음 · $legal"
    }
}

fun pct(a: Int, b: Int): String = if (b == 0) "—" else "${(a * 100.0 / b).roundToInt()}%"

@Composable
internal fun Pill(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.14f), androidx.compose.foundation.shape.RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Text(text, style = K.tiny, color = color) }
}
