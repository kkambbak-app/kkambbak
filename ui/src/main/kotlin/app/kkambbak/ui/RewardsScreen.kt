package app.kkambbak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.kkambbak.core.RewardKind
import app.kkambbak.core.RewardProgress

@Composable
fun RewardsScreen(state: UiState, wide: Boolean, onAction: (UiAction) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("깜빡 리워드", style = K.h2, color = K.c.text)
                Text("신호 습관을 지키면 내 차에서 쓸 수 있는 보상이 열려요. 진행 상황은 주차 중에만 보여요.", style = K.small, color = K.c.text2)
            }
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (wide) 3 else 1),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.rewards) { p -> RewardCard(p, applied = state.settings.ambientRewardId == p.reward.id || (p.reward.kind == RewardKind.THEME && state.settings.nightTheme) || (p.reward.kind == RewardKind.VOICE && state.settings.altVoice), onAction) }
        }
    }
}

@Composable
private fun RewardCard(p: RewardProgress, applied: Boolean, onAction: (UiAction) -> Unit) {
    val r = p.reward
    val accent = r.colorArgb?.let { Color(it) } ?: K.c.amber
    KCard(Modifier.fillMaxWidth().heightIn(min = 250.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RewardBadge(r.kind, accent, p.unlocked)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.kind.korean, style = K.tiny, color = K.c.text3)
                Text(r.title, style = K.bodyStrong, color = if (p.unlocked) K.c.text else K.c.text2)
            }
            Pill(if (p.unlocked) "열림" else "잠김", if (p.unlocked) K.c.good else K.c.text3)
        }
        Spacer(Modifier.height(12.dp))
        Text(r.description, style = K.small, color = K.c.text2)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(12.dp))
        Text("조건 · ${r.requirement}", style = K.tiny, color = K.c.text3)
        Spacer(Modifier.height(6.dp))
        ProgressBar(p.progress, if (p.unlocked) K.c.good else accent.copy(alpha = 0.7f), height = 8.dp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.progressText, style = K.tiny, color = K.c.text2, modifier = Modifier.weight(1f))
            if (p.unlocked && r.kind != RewardKind.PARK_CELEBRATION) {
                SmallButton(
                    when {
                        applied -> "사용 중"
                        r.kind == RewardKind.AMBIENT_COLOR -> "무드등에 적용"
                        else -> "켜기"
                    },
                    primary = !applied,
                ) { onAction(UiAction.ApplyReward(r.id)) }
            }
        }
    }
}

@Composable
private fun RewardBadge(kind: RewardKind, accent: Color, unlocked: Boolean) {
    val c = if (unlocked) accent else K.c.text3
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
            .background(if (unlocked) Brush.radialGradient(listOf(c.copy(alpha = 0.55f), c.copy(alpha = 0.08f))) else Brush.linearGradient(listOf(K.c.surface2, K.c.surface2)))
            .border(1.dp, c.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (kind) {
            RewardKind.AMBIENT_COLOR, RewardKind.PARK_CELEBRATION -> Canvas(Modifier.size(26.dp)) {
                drawCircle(c, size.minDimension / 2.6f)
                drawCircle(c.copy(alpha = 0.35f), size.minDimension / 2f, style = Stroke(3f))
            }
            RewardKind.VOICE -> Canvas(Modifier.size(26.dp)) {
                val w = size.width; val h = size.height
                listOf(0.3f, 0.7f, 1f, 0.6f, 0.35f).forEachIndexed { i, f ->
                    val x = w * (0.1f + i * 0.2f)
                    drawLine(c, Offset(x, h * (0.5f - f / 2)), Offset(x, h * (0.5f + f / 2)), 5f)
                }
            }
            RewardKind.THEME -> SignalMark(18.dp, both = unlocked)
        }
    }
}
