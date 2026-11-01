package app.kkambbak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 앱 루트. 주행 중이면 주행 화면만, 주차 중이면 탭 화면을 보여 준다. */
@Composable
fun KkambbakApp(state: UiState, onAction: (UiAction) -> Unit) {
    KkambbakTheme(night = state.settings.nightTheme) {
        Box(Modifier.fillMaxSize().background(K.c.bg)) {
            if (state.driving) {
                DrivingScreen(state, onAction)
            } else {
                ParkedScreen(state, onAction)
            }
            // 사고·급정거 패널은 정차 상태에서만(주행 화면에서는 버튼만 받는다)
            if (!state.driving && (state.crashFlow != null || state.suddenStopPrompt)) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    if (state.crashFlow != null) CrashFlowPanel(state.crashFlow, state.demoProfile, onAction, Modifier.widthIn(max = 980.dp).padding(28.dp))
                    else SuddenStopPanel(onAction, Modifier.widthIn(max = 760.dp).padding(28.dp))
                }
            }
            if (state.celebration && !state.driving) Celebration(onAction)
            state.toast?.let { Toast(it, onAction) }
        }
    }
}

@Composable
private fun ParkedScreen(state: UiState, onAction: (UiAction) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 900.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
            TopBar(state, wide, onAction)
            Spacer(Modifier.height(14.dp))
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavRail(state.tab, onAction)
                    Spacer(Modifier.width(20.dp))
                    Box(Modifier.weight(1f).fillMaxHeight()) { TabContent(state, true, onAction) }
                }
            } else {
                NavRow(state.tab, onAction)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.weight(1f)) { TabContent(state, false, onAction) }
            }
        }
    }
}

@Composable
private fun TabContent(state: UiState, wide: Boolean, onAction: (UiAction) -> Unit) = when (state.tab) {
    Tab.TODAY -> TodayScreen(state, wide, onAction)
    Tab.WEEKLY -> WeeklyScreen(state, wide, onAction)
    Tab.REWARDS -> RewardsScreen(state, wide, onAction)
    Tab.CRASH -> CrashScreen(state, wide, onAction)
    Tab.SETTINGS -> SettingsScreen(state, wide, onAction)
}

@Composable
private fun TopBar(state: UiState, wide: Boolean, onAction: (UiAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SignalMark(24.dp, both = true)
        Spacer(Modifier.width(12.dp))
        Text("깜빡", style = K.h2, color = K.c.text, maxLines = 1)
        Spacer(Modifier.width(14.dp))
        if (wide) Text("깜빡했다면, 차가 기억합니다", style = K.small, color = K.c.text2, maxLines = 1, modifier = Modifier.weight(1f))
        else Spacer(Modifier.weight(1f))
        if (state.demoProfile) {
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                if (wide) { Text("시연", style = K.tiny, color = K.c.amber); Spacer(Modifier.width(8.dp)) }
                DemoChip("출근길 ▶", primary = true) { onAction(UiAction.StartDemoDrive) }
                Spacer(Modifier.width(6.dp))
                DemoChip(if (wide) "완벽한 퇴근길 ▶" else "퇴근길 ▶") { onAction(UiAction.StartPerfectDemo) }
                Spacer(Modifier.width(6.dp))
                DemoChip(if (wide) "사고 상황 ▶" else "사고 ▶") { onAction(UiAction.StartCrashDemo) }
            }
            Spacer(Modifier.width(if (wide) 14.dp else 8.dp))
        }
        if (wide) {
            Pill(state.profileName, K.c.text2)
            Spacer(Modifier.width(8.dp))
            Pill("P · 주차 중", K.c.good)
        }
    }
}

@Composable
private fun DemoChip(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (primary) K.c.amber else K.c.surface2)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = K.tiny, color = if (primary) Color(0xFF1A1200) else K.c.text) }
}

@Composable
private fun NavRail(tab: Tab, onAction: (UiAction) -> Unit) {
    Column(Modifier.width(150.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Tab.entries.forEach { t -> NavItem(t, t == tab, Modifier.fillMaxWidth(), onAction) }
    }
}

@Composable
private fun NavRow(tab: Tab, onAction: (UiAction) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(Tab.entries) { t -> NavItem(t, t == tab, Modifier, onAction) } }
}

@Composable
private fun NavItem(t: Tab, selected: Boolean, modifier: Modifier, onAction: (UiAction) -> Unit) {
    Box(
        modifier.heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) K.c.surface2 else Color.Transparent)
            .border(1.dp, if (selected) K.c.amber.copy(alpha = 0.7f) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable { onAction(UiAction.SelectTab(t)) }.padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(t.label, style = K.bodyStrong, color = if (selected) K.c.text else K.c.text2) }
}

@Composable
private fun Celebration(onAction: (UiAction) -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFFFFB020).copy(alpha = 0.45f), Color.Transparent)))
            .clickable { onAction(UiAction.DismissCelebration) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(K.c.surface).border(2.dp, K.c.amber, RoundedCornerShape(28.dp)).padding(horizontal = 44.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SignalMark(48.dp, both = true)
            Spacer(Modifier.height(14.dp))
            Text("모든 회전을 제때 켰어요", style = K.h1, color = K.c.text)
            Spacer(Modifier.height(6.dp))
            Text("주차 축하등 · 실내 무드등이 3초간 앰버색으로 켜져요", style = K.body, color = K.c.amber)
        }
    }
}

@Composable
private fun Toast(text: String, onAction: (UiAction) -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            text, style = K.bodyStrong, color = K.c.text,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(K.c.surface2).border(1.dp, K.c.line, RoundedCornerShape(16.dp))
                .clickable { onAction(UiAction.DismissToast) }.padding(horizontal = 22.dp, vertical = 14.dp),
        )
    }
}
