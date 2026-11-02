package app.kkambbak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(state: UiState, wide: Boolean, onAction: (UiAction) -> Unit) {
    val left = @Composable { m: Modifier ->
        KCard(m) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionTitle("운전자")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.profiles.forEach { p ->
                        val sel = p == state.profileName
                        Text(
                            p, style = K.bodyStrong, color = if (sel) K.c.text else K.c.text2,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                .background(if (sel) K.c.surface2 else K.c.surface)
                                .border(1.dp, if (sel) K.c.amber else K.c.line, RoundedCornerShape(14.dp))
                                .clickable { onAction(UiAction.SelectProfile(p)) }
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                        )
                    }
                }
                Text("차를 같이 쓰는 사람마다 기록을 따로 저장해요. 운전자를 바꾸면 그 사람의 기록만 보여요.", style = K.small, color = K.c.text3, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = K.c.line)
                ToggleRow("초보 모드 알림음", "회전 지점 35m(고속도로 105m) 앞까지 지시등이 없으면 짧은 알림음을 한 번 울려요.", state.settings.preAlert) { onAction(UiAction.SetPreAlert(it)) }
                HorizontalDivider(color = K.c.line)
                ToggleRow("주차 브리핑 자동 재생", "P단에 놓으면 이번 주행 기록을 10초로 읽어 줘요.", state.settings.autoBriefing) { onAction(UiAction.SetAutoBriefing(it)) }
                HorizontalDivider(color = K.c.line)
                ToggleRow("자주 놓치는 곳 기억하기", "켜면 내비가 알려준 회전 지점 이름과 대략적인 위치를 이 차 안에만 저장해요. 끄면 회전 종류와 판정만 남아요. 기본은 꺼짐.", state.settings.storeSpots) { onAction(UiAction.SetStoreSpots(it)) }
            }
        }
    }
    val right = @Composable { m: Modifier ->
        KCard(m) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionTitle("데이터 원칙")
                listOf(
                    "판정은 모두 차 안에서 끝나요. 서버로 보내는 데이터가 없어요.",
                    "주행 궤적은 저장하지 않아요. 평소에는 회전별 판정 결과만 남아요.",
                    "사고 기록용 60초 버퍼(신호·추정 속도·위치)는 메모리에서 계속 덮어쓰고, 사고 기록을 남길 때만 저장해요.",
                    "보험사 등 외부에 점수를 주는 기능은 없어요. 도입한다면 별도 동의를 받는 것이 전제예요.",
                ).forEach { Row(Modifier.padding(vertical = 4.dp)) { Text("·", color = K.c.amber, style = K.body, modifier = Modifier.width(16.dp)); Text(it, style = K.body, color = K.c.text) } }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = K.c.line)
                Spacer(Modifier.height(12.dp))
                SectionTitle("차량 연결 상태")
                if (state.sources.isEmpty()) Text("확인 중…", style = K.body, color = K.c.text2)
                state.sources.forEach { s ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (s.ok) K.c.good else K.c.text3)
                        Spacer(Modifier.width(10.dp))
                        Text(s.name, style = K.bodyStrong, color = K.c.text, modifier = Modifier.width(200.dp))
                        Text(s.detail, style = K.small, color = K.c.text2)
                    }
                }
                Spacer(Modifier.height(16.dp))
                SmallButton("${state.profileName} 기록 모두 지우기") { onAction(UiAction.ClearData) }
            }
        }
    }
    if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        left(Modifier.weight(1f).fillMaxHeight()); right(Modifier.weight(1f).fillMaxHeight())
    } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { left(Modifier.fillMaxWidth()); right(Modifier.fillMaxWidth()) }
}
