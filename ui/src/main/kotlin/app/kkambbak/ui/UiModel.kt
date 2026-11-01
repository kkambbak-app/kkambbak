package app.kkambbak.ui

import app.kkambbak.core.CrashRecord
import app.kkambbak.core.DriveSession
import app.kkambbak.core.ManeuverResult
import app.kkambbak.core.RewardProgress
import app.kkambbak.core.TripSummary
import app.kkambbak.core.WeeklyStats

enum class Tab(val label: String) { TODAY("오늘"), WEEKLY("주간 리포트"), REWARDS("리워드"), CRASH("사고 기록"), SETTINGS("설정") }

data class SourceStatus(val name: String, val ok: Boolean, val detail: String)

data class SettingsState(
    val preAlert: Boolean = false,
    val storeSpots: Boolean = false,
    val autoBriefing: Boolean = true,
    val altVoice: Boolean = false,
    val nightTheme: Boolean = false,
    val ambientRewardId: String? = null,
)

/** 사고 기록 직후의 안내·진술 단계. */
data class CrashFlow(
    val record: CrashRecord,
    val listening: Boolean = false,
    val partial: String = "",
    val saved: Boolean = false,
)

/** 시연 모드에서만 보이는 관찰 패널(실제 주행 중에는 표시하지 않는다). */
data class DemoPanel(
    val title: String,
    val progress: Float,
    val log: List<ManeuverResult>,
)

data class UiState(
    val tab: Tab = Tab.TODAY,
    val profileName: String = "운전자 1",
    val profiles: List<String> = listOf("운전자 1", "운전자 2", "시연 운전자"),
    val demoProfile: Boolean = false,
    val driving: Boolean = false,
    val live: DriveSession.LiveState = DriveSession.LiveState(),
    val todayResults: List<ManeuverResult> = emptyList(),
    val lastTrip: TripSummary? = null,
    val briefing: String? = null,
    val weekly: WeeklyStats = WeeklyStats(emptyList(), 0, 0, 0, 0, emptyList()),
    val weeklyBriefing: String = "",
    val rewards: List<RewardProgress> = emptyList(),
    val crashes: List<CrashRecord> = emptyList(),
    val selectedCrashId: String? = null,
    val suddenStopPrompt: Boolean = false,
    val crashFlow: CrashFlow? = null,
    val celebration: Boolean = false,
    val settings: SettingsState = SettingsState(),
    val sources: List<SourceStatus> = emptyList(),
    val demo: DemoPanel? = null,
    val speaking: Boolean = false,
    val listening: Boolean = false,
    val toast: String? = null,
    val nowMs: Long = 0L,
)

sealed interface UiAction {
    data class SelectTab(val tab: Tab) : UiAction
    data object ReplayBriefing : UiAction
    data object WeeklyBriefing : UiAction
    data object StartDemoDrive : UiAction
    data object StartPerfectDemo : UiAction
    data object StartCrashDemo : UiAction
    data object CrashButton : UiAction
    data object VoiceButton : UiAction
    data object AcceptSuddenStop : UiAction
    data object DismissSuddenStop : UiAction
    data object StartStatement : UiAction
    data object FinishCrashFlow : UiAction
    data class SelectCrash(val id: String) : UiAction
    data class ApplyReward(val id: String) : UiAction
    data class SetPreAlert(val on: Boolean) : UiAction
    data class SetStoreSpots(val on: Boolean) : UiAction
    data class SetAutoBriefing(val on: Boolean) : UiAction
    data class SelectProfile(val name: String) : UiAction
    data object ClearData : UiAction
    data object DismissCelebration : UiAction
    data object DismissToast : UiAction
}
