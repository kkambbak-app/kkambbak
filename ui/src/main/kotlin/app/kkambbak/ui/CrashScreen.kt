package app.kkambbak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.kkambbak.core.CrashFacts
import app.kkambbak.core.CrashRecord
import app.kkambbak.core.Signal
import app.kkambbak.core.Snapshot

const val CRASH_DISCLAIMER =
    "이 기록은 차량 신호를 그대로 남긴 사실 기록이며, 과실 비율을 판단하지 않습니다. " +
        "과실 비율은 손해보험협회 「자동차사고 과실비율 인정기준」을 바탕으로 보험사 협의, 과실비율분쟁심의위원회, 법원에서 정해집니다."

@Composable
fun CrashScreen(state: UiState, wide: Boolean, onAction: (UiAction) -> Unit) {
    val list = state.crashes.sortedByDescending { it.createdAtMs }
    val selected = list.firstOrNull { it.id == state.selectedCrashId } ?: list.firstOrNull()
    if (list.isEmpty()) {
        KCard(Modifier.fillMaxWidth()) {
            Text("사고 기록이 없어요", style = K.h2, color = K.c.text)
            Spacer(Modifier.height(8.dp))
            Text("사고가 나면 \"사고 기록\"이라고 말하거나 주행 화면의 사고 기록 버튼을 누르세요. 급정거가 감지되면 먼저 물어봐요. 직전 60초의 방향지시등·브레이크·전조등·와이퍼·속도가 고정돼 남아요.", style = K.body, color = K.c.text2)
            Spacer(Modifier.height(12.dp))
            Text(CRASH_DISCLAIMER, style = K.small, color = K.c.text3)
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (wide) {
            Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.forEach { c ->
                    val sel = c.id == selected?.id
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (sel) K.c.surface2 else K.c.surface)
                            .border(1.dp, if (sel) K.c.bad.copy(alpha = 0.6f) else K.c.line, RoundedCornerShape(16.dp))
                            .clickable { onAction(UiAction.SelectCrash(c.id)) }.padding(16.dp),
                    ) {
                        Text(CrashFacts.kst(c.createdAtMs), style = K.bodyStrong, color = K.c.text)
                        Text(c.trigger.korean, style = K.small, color = K.c.text2)
                    }
                }
            }
        }
        if (selected != null) CrashDetail(selected, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
fun CrashDetail(c: CrashRecord, modifier: Modifier) {
    KCard(modifier) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("사고 기록지", style = K.h2, color = K.c.text)
                    Text("${CrashFacts.kst(c.createdAtMs)} · ${c.trigger.korean}", style = K.small, color = K.c.text2)
                    Spacer(Modifier.height(14.dp))
                    Timeline(c.snapshots, c.createdAtMs, Modifier.fillMaxWidth().height(150.dp))
                    Spacer(Modifier.height(14.dp))
                    c.facts.forEach { f ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text("·", style = K.body, color = K.c.amber, modifier = Modifier.width(16.dp))
                            Text(f, style = K.body, color = K.c.text)
                        }
                    }
                    if (c.statement.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("운전자 진술(음성으로 받아 적음)", style = K.small, color = K.c.text2)
                        Text("\"${c.statement}\"", style = K.body, color = K.c.text)
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCode(qrText(c), 170.dp)
                    Spacer(Modifier.height(6.dp))
                    Text("휴대폰으로 옮기기", style = K.tiny, color = K.c.text2)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("무결성 해시(SHA-256) ${c.hash.take(16)}…${c.hash.takeLast(8)}", style = K.tiny, color = K.c.text3)
            Spacer(Modifier.height(6.dp))
            Text(CRASH_DISCLAIMER, style = K.tiny, color = K.c.text3)
        }
    }
}

fun qrText(c: CrashRecord): String = buildString {
    appendLine("깜빡 사고 기록지")
    c.facts.forEach { appendLine(it) }
    if (c.statement.isNotBlank()) appendLine("진술: ${c.statement}")
    appendLine("SHA-256: ${c.hash}")
}

/** -60초 ~ 0초 타임라인: 방향지시등(앰버 띠), 브레이크(붉은 띠), 속도(선). */
@Composable
private fun Timeline(snaps: List<Snapshot>, endMs: Long, modifier: Modifier) {
    val amber = K.c.amber; val bad = K.c.bad; val line = K.c.text; val grid = K.c.line; val text3 = K.c.text3
    Column(modifier) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.width(78.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                Text("방향지시등", style = K.tiny, color = K.c.text2)
                Text("브레이크", style = K.tiny, color = K.c.text2)
                Text("속도", style = K.tiny, color = K.c.text2)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                if (snaps.isEmpty()) return@Canvas
                val w = size.width; val h = size.height
                val start = endMs - 60_000L
                fun x(t: Long) = ((t - start).coerceIn(0, 60_000L) / 60_000f) * w
                val rowH = h / 3
                for (i in 0..3) drawLine(grid, Offset(0f, rowH * i), Offset(w, rowH * i), 1f)
                for (s in listOf(0, 15, 30, 45, 60)) drawLine(grid, Offset(w * s / 60f, 0f), Offset(w * s / 60f, h), 1f)
                // 구간 띠: 같은 상태가 이어지는 구간을 하나의 막대로 그린다
                fun band(row: Int, color: Color, on: (Snapshot) -> Boolean) {
                    var runStart: Long? = null
                    for (i in snaps.indices) {
                        val a = snaps[i]
                        if (on(a) && runStart == null) runStart = a.timeMs
                        val nextOn = snaps.getOrNull(i + 1)?.let(on) ?: false
                        if (runStart != null && !nextOn) {
                            val end = snaps.getOrNull(i + 1)?.timeMs ?: endMs
                            drawRect(color, Offset(x(runStart), rowH * (row + 0.25f)), Size((x(end) - x(runStart)).coerceAtLeast(2f), rowH * 0.5f))
                            runStart = null
                        }
                    }
                }
                band(0, amber) { it.signal != Signal.OFF }
                band(1, bad) { it.brake }
                val maxV = (snaps.maxOf { it.speedKmh }).coerceAtLeast(30.0)
                val p = Path()
                snaps.forEachIndexed { i, s ->
                    val y = rowH * 2 + rowH * (1 - (s.speedKmh / maxV).toFloat()) * 0.9f + rowH * 0.05f
                    if (i == 0) p.moveTo(x(s.timeMs), y) else p.lineTo(x(s.timeMs), y)
                }
                drawPath(p, line, style = Stroke(3f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 78.dp)) {
            listOf("-60초", "-45초", "-30초", "-15초", "기록").forEachIndexed { i, s ->
                Box(Modifier.weight(1f), contentAlignment = if (i == 4) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(s, style = K.tiny, color = text3)
                }
            }
        }
    }
}

/** 사고 직후 안내·진술 화면(정차 상태에서만 열린다). */
@Composable
fun CrashFlowPanel(flow: CrashFlow, demo: Boolean, onAction: (UiAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(K.c.surface).border(2.dp, K.c.bad.copy(alpha = 0.7f), RoundedCornerShape(24.dp)).padding(28.dp),
    ) {
        Text("직전 60초 차량 신호를 고정했어요", style = K.h1, color = K.c.text)
        Spacer(Modifier.height(10.dp))
        Text("다친 사람이 있으면 먼저 119에 신고하세요. 비상등을 켜고 트렁크를 열어 둔 뒤, 가드레일 밖처럼 안전한 곳으로 대피하세요.", style = K.body, color = K.c.text)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                flow.record.facts.take(4).forEach { Text("· $it", style = K.small, color = K.c.text2) }
            }
            Column(Modifier.weight(1f)) {
                Text("기억이 생생할 때 경위를 말로 남겨 두세요", style = K.small, color = K.c.text2)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        flow.partial.isNotBlank() -> "\"${flow.partial}\""
                        flow.listening -> "듣고 있어요…"
                        else -> "버튼을 누르고 말씀하세요"
                    },
                    style = K.bodyStrong, color = if (flow.partial.isNotBlank()) K.c.text else K.c.text3,
                )
                if (demo && flow.partial.isNotBlank()) Text("(시연 입력)", style = K.tiny, color = K.c.text3)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(if (flow.listening) "듣는 중…" else "말로 남기기", Modifier.weight(1f), primary = true) { onAction(UiAction.StartStatement) }
            BigButton(if (flow.partial.isBlank()) "건너뛰기" else "저장하고 닫기", Modifier.weight(1f)) { onAction(UiAction.FinishCrashFlow) }
        }
    }
}

@Composable
fun SuddenStopPanel(onAction: (UiAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(K.c.surface).border(2.dp, K.c.amber.copy(alpha = 0.7f), RoundedCornerShape(24.dp)).padding(28.dp),
    ) {
        Text("급정거가 감지됐어요", style = K.h1, color = K.c.text)
        Spacer(Modifier.height(8.dp))
        Text("직전 60초의 차량 신호를 사고 기록으로 남길까요? 남기지 않으면 저장하지 않아요.", style = K.body, color = K.c.text2)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("기록 남기기", Modifier.weight(1f), danger = true) { onAction(UiAction.AcceptSuddenStop) }
            BigButton("괜찮아요", Modifier.weight(1f)) { onAction(UiAction.DismissSuddenStop) }
        }
    }
}

@Suppress("unused")
private val transparent = Color.Transparent
