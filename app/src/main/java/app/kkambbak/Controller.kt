package app.kkambbak

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.kkambbak.car.AmbientLight
import app.kkambbak.car.DemoPlayer
import app.kkambbak.car.PleosCarInputs
import app.kkambbak.car.UxRestrictionsMonitor
import app.kkambbak.core.Briefing
import app.kkambbak.core.CrashRecord
import app.kkambbak.core.CrashTrigger
import app.kkambbak.core.DemoScript
import app.kkambbak.core.DriveEvent
import app.kkambbak.core.DriveSession
import app.kkambbak.core.GearState
import app.kkambbak.core.KST
import app.kkambbak.core.ManeuverResult
import app.kkambbak.core.RewardBook
import app.kkambbak.core.RewardKind
import app.kkambbak.core.Side
import app.kkambbak.core.TripSummary
import app.kkambbak.core.Verdict
import app.kkambbak.core.VoiceIntent
import app.kkambbak.core.VoiceIntents
import app.kkambbak.core.Weekly
import app.kkambbak.data.Store
import app.kkambbak.ui.CrashFlow
import app.kkambbak.ui.DemoPanel
import app.kkambbak.ui.SettingsState
import app.kkambbak.ui.SourceStatus
import app.kkambbak.ui.Tab
import app.kkambbak.ui.UiAction
import app.kkambbak.ui.UiState
import app.kkambbak.voice.VoiceIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate

/**
 * 앱의 중심. 차량 입력(실차 또는 시연)을 엔진에 넣고, 결과를 저장하고, 화면 상태를 만든다.
 * 모든 처리는 메인 스레드에서 순서대로 일어난다.
 */
class Controller(private val context: Context) : DriveSession.Listener {

    private val main = Handler(Looper.getMainLooper())
    private val store = Store(context)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var profileId = store.currentProfileId
    private var settings = store.settings(profileId)

    // 파일은 쓸 때만 건드리고, 화면 계산은 메모리 사본으로 한다.
    private var results = store.results(profileId).toMutableList()
    private var trips = store.trips(profileId).toMutableList()
    private var crashes = store.crashes(profileId).toMutableList()

    private fun reload() {
        results = store.results(profileId).toMutableList()
        trips = store.trips(profileId).toMutableList()
        crashes = store.crashes(profileId).toMutableList()
        refreshData()
    }
    private var session = newSession()

    private val pleos = PleosCarInputs(context, ::onCarEvent, ::onVehicleStatus)
    private val ux = UxRestrictionsMonitor(context) { r -> uxRestricted = r; publish() }
    private var uxRestricted = false
    private val ambient = AmbientLight { pleos.vehicle }
    private val demo = DemoPlayer()
    private var demoTitle: String? = null
    private var demoProgress = 0f
    private val demoLog = mutableListOf<ManeuverResult>()

    private var vehicleSources: List<SourceStatus> = emptyList()
    private var voiceSources: List<SourceStatus> = emptyList()

    private var statementMode = false
    private var pendingSuddenStop: CrashRecord? = null

    private val voice = VoiceIO(
        context,
        onSpeaking = { s -> update { copy(speaking = s) } },
        onListening = { l -> update { copy(listening = l, crashFlow = crashFlow?.copy(listening = l)) } },
        onPartial = { t -> if (statementMode) update { copy(crashFlow = crashFlow?.copy(partial = t)) } },
        onFinal = ::onVoiceFinal,
        onStatus = { tts, stt, ttsOk, sttOk ->
            voiceSources = listOf(SourceStatus("TextToSpeech SDK", ttsOk, tts), SourceStatus("SpeechToText SDK", sttOk, stt))
            publish()
        },
    )

    private fun newSession() = DriveSession(this).also {
        it.preAlertEnabled = settings.preAlert
        it.storeSpots = settings.storeSpots
    }

    fun start() {
        pleos.start()
        ux.start()
        voice.init()
        voice.altVoice = settings.altVoice
        refreshData()
    }

    fun stop() {
        demo.stop()
        pleos.stop()
        ux.stop()
        voice.release()
    }

    /** 위치 권한을 받은 뒤 위치 수신을 다시 시작한다. */
    fun onLocationPermissionGranted() = pleos.startLocation()

    // ------------------------------------------------------------ 입력

    private fun onCarEvent(e: DriveEvent) {
        if (demo.running) return // 시연 재생 중에는 실차 입력을 섞지 않는다
        session.handle(e)
    }

    private fun onVehicleStatus(s: List<SourceStatus>) { vehicleSources = s; publish() }

    // ------------------------------------------------------------ 엔진 리스너

    /** '자주 놓치는 곳'이 꺼져 있으면 회전 지점 이름·좌표를 지우고 회전 종류와 판정만 저장한다. */
    private fun forStorage(r: ManeuverResult) = if (settings.storeSpots) r else r.copy(place = "", lat = null, lon = null)

    override fun onManeuver(result: ManeuverResult) {
        val stored = forStorage(result)
        store.appendResult(profileId, stored)
        results += stored
        if (demo.running) demoLog += result
        refreshData()
    }

    override fun onTripStarted(startMs: Long) {
        update { copy(celebration = false, briefing = briefing) }
    }

    override fun onTripEnded(summary: TripSummary) {
        val before = rewards()
        val storedTrip = summary.copy(results = summary.results.map(::forStorage))
        store.appendTrip(profileId, storedTrip)
        trips += storedTrip
        val after = rewards()
        val newly = RewardBook.newlyUnlocked(before, after)
        val text = Briefing.trip(summary, newly)
        val celebrate = summary.perfect && after.any { it.reward.id == "celebrate" && it.unlocked }
        update { copy(briefing = text, tab = Tab.TODAY, celebration = celebrate) }
        if (settings.autoBriefing) main.postDelayed({ voice.speak(text) }, 600)
        refreshData()
        if (celebrate) {
            val amber = RewardBook.all.first { it.id == "celebrate" }.colorArgb ?: 0xFFFFB020
            ambient.celebrate(amber) { }
            main.postDelayed({ update { copy(celebration = false) } }, 4_000)
        }
        publish()
    }

    override fun onPreAlert(side: Side, place: String) = voice.chime()

    override fun onSuddenStop(timeMs: Long) {
        // 버퍼를 지금 고정해 두되, 운전자가 동의할 때만 저장한다.
        pendingSuddenStop = session.freezeCrash(CrashTrigger.SUDDEN_STOP, timeMs)
        update { copy(suddenStopPrompt = true) }
    }

    override fun onStateChanged(state: DriveSession.LiveState) = publish()

    // ------------------------------------------------------------ 화면 동작

    fun onAction(a: UiAction) {
        when (a) {
            is UiAction.SelectTab -> update { copy(tab = a.tab) }
            UiAction.ReplayBriefing -> _state.value.briefing?.let(voice::speak)
            UiAction.WeeklyBriefing -> voice.speak(_state.value.weeklyBriefing)
            UiAction.StartDemoDrive -> startDemo("출근길 시연 · 집 → 회사(가상 경로) · 4배속", DemoScript.drive(DemoScript.commuteLegs, System.currentTimeMillis()), 4.0)
            UiAction.StartPerfectDemo -> startDemo("완벽한 퇴근길 · 회사 → 집(가상 경로) · 4배속", DemoScript.drive(DemoScript.perfectLegs, System.currentTimeMillis()), 4.0)
            UiAction.StartCrashDemo -> startDemo("사고 상황 시연 · 합류 구간 접촉(가상) · 2배속", DemoScript.crash(System.currentTimeMillis()), 2.0)
            UiAction.CrashButton -> recordCrash(CrashTrigger.BUTTON)
            UiAction.VoiceButton -> { statementMode = false; voice.listen() }
            UiAction.AcceptSuddenStop -> {
                val rec = pendingSuddenStop
                pendingSuddenStop = null
                update { copy(suddenStopPrompt = false) }
                if (rec != null) openCrashFlow(rec)
            }
            UiAction.DismissSuddenStop -> { pendingSuddenStop = null; update { copy(suddenStopPrompt = false) } }
            UiAction.StartStatement -> startStatement()
            UiAction.FinishCrashFlow -> finishCrashFlow()
            is UiAction.SelectCrash -> update { copy(selectedCrashId = a.id) }
            is UiAction.ApplyReward -> applyReward(a.id)
            is UiAction.SetPreAlert -> saveSettings(settings.copy(preAlert = a.on))
            is UiAction.SetStoreSpots -> saveSettings(settings.copy(storeSpots = a.on))
            is UiAction.SetAutoBriefing -> saveSettings(settings.copy(autoBriefing = a.on))
            is UiAction.SelectProfile -> switchProfile(store.profiles.first { it.name == a.name }.id)
            UiAction.ClearData -> { store.clear(profileId); reload(); update { copy(briefing = null) }; toast("${store.profile(profileId).name} 기록을 지웠어요") }
            UiAction.DismissCelebration -> update { copy(celebration = false) }
            UiAction.DismissToast -> update { copy(toast = null) }
        }
        publish()
    }

    private fun saveSettings(s: SettingsState) {
        settings = s
        store.saveSettings(profileId, s)
        session.preAlertEnabled = s.preAlert
        session.storeSpots = s.storeSpots
        voice.altVoice = s.altVoice
        publish()
    }

    private fun switchProfile(id: String) {
        if (id == profileId) return
        demo.stop()
        profileId = id
        store.currentProfileId = id
        settings = store.settings(id)
        session = newSession()
        voice.altVoice = settings.altVoice
        update { copy(briefing = null, selectedCrashId = null, celebration = false) }
        if (id == "demo") seedDemoHistory()
        reload()
    }

    private fun startDemo(title: String, events: List<DriveEvent>, speed: Double) {
        switchProfile("demo")
        session = newSession()
        demoLog.clear()
        demoTitle = title
        demoProgress = 0f
        update { copy(suddenStopPrompt = false, crashFlow = null, celebration = false) }
        demo.play(events, speed, onEvent = { e, p -> demoProgress = p; session.handle(e) }, onDone = {
            demoTitle = null
            publish()
        })
        publish()
    }

    /** 시연 운전자에 지난 6일치 가상 기록을 넣는다(한 번만). */
    private fun seedDemoHistory() {
        if (!store.isEmpty("demo")) return
        val (hist, histTrips) = DemoScript.history(System.currentTimeMillis())
        hist.forEach { store.appendResult("demo", it) }
        histTrips.forEach { store.appendTrip("demo", it) }
    }

    private fun recordCrash(trigger: CrashTrigger) {
        val rec = session.freezeCrash(trigger, session.state.lastTimeMs.takeIf { it > 0 } ?: System.currentTimeMillis())
        openCrashFlow(rec)
        if (isDriving()) toast("직전 60초를 고정했어요. 정차하면 안내해 드릴게요.")
    }

    private fun openCrashFlow(rec: CrashRecord) {
        saveCrash(rec)
        update { copy(crashFlow = CrashFlow(rec), selectedCrashId = rec.id) }
        voice.speak("직전 60초 차량 신호를 고정했어요. 다친 사람이 있으면 먼저 119에 신고하세요. 비상등을 켜고 안전한 곳으로 대피한 뒤, 경위를 말씀해 주시면 받아 적을게요.")
    }

    private fun startStatement() {
        val flow = _state.value.crashFlow ?: return
        statementMode = true
        if (voice.canListen && !demo.running && profileId != "demo") {
            voice.stopSpeaking()
            voice.listen()
        } else {
            // 음성 인식이 없는 시연 환경: 예시 진술을 글자 단위로 보여 준다(화면에 '시연 입력'으로 표시).
            val sample = "2차로로 합류하려고 오른쪽 깜빡이를 켜고 들어가는데 앞차가 갑자기 서서 부딪혔어요"
            update { copy(crashFlow = flow.copy(listening = true, partial = "")) }
            sample.forEachIndexed { i, _ ->
                main.postDelayed({ update { copy(crashFlow = crashFlow?.copy(partial = sample.substring(0, i + 1))) } }, 60L * i)
            }
            main.postDelayed({ update { copy(crashFlow = crashFlow?.copy(listening = false)) } }, 60L * sample.length + 100)
        }
    }

    private fun finishCrashFlow() {
        val flow = _state.value.crashFlow ?: return
        statementMode = false
        voice.stopListening()
        val rec = if (flow.partial.isNotBlank()) flow.record.withStatement(flow.partial) else flow.record
        saveCrash(rec)
        update { copy(crashFlow = null, tab = Tab.CRASH, selectedCrashId = rec.id) }
        toast("사고 기록지를 저장했어요")
    }

    private fun onVoiceFinal(text: String) {
        if (statementMode) {
            update { copy(crashFlow = crashFlow?.copy(partial = text, listening = false)) }
            return
        }
        when (VoiceIntents.parse(text)) {
            VoiceIntent.CRASH_RECORD -> recordCrash(CrashTrigger.VOICE)
            VoiceIntent.TODAY -> voice.speak(todaySentence())
            VoiceIntent.WEEKLY -> voice.speak(_state.value.weeklyBriefing)
            VoiceIntent.REPEAT -> _state.value.briefing?.let(voice::speak)
            VoiceIntent.UNKNOWN -> voice.speak("\"사고 기록\", \"오늘 어땠어\", \"이번 주 어땠어\" 중에서 말씀해 주세요.")
        }
        publish()
    }

    private fun todaySentence(): String {
        val rs = todayResults()
        if (rs.isEmpty()) return "오늘은 아직 판정한 회전이 없어요."
        val ok = rs.count { it.verdict == Verdict.ON_TIME }
        return "오늘 회전 ${rs.size}번 중 ${ok}번을 제때 켰어요."
    }

    private fun applyReward(id: String) {
        val p = rewards().firstOrNull { it.reward.id == id } ?: return
        if (!p.unlocked) return
        if (isDriving()) { toast("주차한 뒤에 바꿀 수 있어요"); return }
        when (p.reward.kind) {
            RewardKind.AMBIENT_COLOR -> {
                saveSettings(settings.copy(ambientRewardId = id))
                ambient.apply(p.reward.colorArgb ?: 0xFFFFB020) { ok ->
                    toast(if (ok) "${p.reward.title}을 내 차 무드등에 적용했어요" else "이 기기에서는 무드등을 바꿀 수 없어요 · 실차에서 적용돼요")
                }
            }
            RewardKind.THEME -> saveSettings(settings.copy(nightTheme = !settings.nightTheme))
            RewardKind.VOICE -> { saveSettings(settings.copy(altVoice = !settings.altVoice)); voice.speak("브리핑 목소리를 바꿨어요.") }
            RewardKind.PARK_CELEBRATION -> {}
        }
    }

    // ------------------------------------------------------------ 상태 계산

    private fun isDriving(): Boolean {
        val live = session.state
        val inGear = live.gear == GearState.DRIVE || live.gear == GearState.REVERSE || live.gear == GearState.NEUTRAL
        val stoppedForCrash = live.speedKmh < 1 && (_state.value.suddenStopPrompt || _state.value.crashFlow != null)
        if (stoppedForCrash) return false
        return if (ux.available && !demo.running) uxRestricted else inGear || live.speedKmh >= 5
    }

    private fun saveCrash(rec: CrashRecord) {
        store.saveCrash(profileId, rec)
        crashes.removeAll { it.id == rec.id }
        crashes += rec
        refreshData()
    }

    private fun todayResults(): List<ManeuverResult> {
        val today = LocalDate.now(KST)
        return results.filter { Instant.ofEpochMilli(it.timeMs).atZone(KST).toLocalDate() == today }
    }

    private fun rewards() = RewardBook.evaluate(RewardBook.inputs(results, trips))

    /** 기록이 바뀌었을 때만 다시 계산하는 부분(오늘·주간·리워드·사고 기록). */
    private fun refreshData() {
        val now = System.currentTimeMillis()
        val weekly = Weekly.compute(results, now)
        _state.value = _state.value.copy(
            todayResults = todayResults(),
            lastTrip = trips.lastOrNull(),
            weekly = weekly,
            weeklyBriefing = Briefing.weekly(weekly),
            rewards = rewards(),
            crashes = crashes.toList(),
        )
        publish()
    }

    /** 매 이벤트마다 갱신하는 가벼운 부분. */
    private fun publish() {
        _state.value = _state.value.copy(
            profileName = store.profile(profileId).name,
            profiles = store.profiles.map { it.name },
            demoProfile = profileId == "demo",
            driving = isDriving(),
            live = session.state,
            settings = settings,
            sources = vehicleSources + voiceSources,
            demo = demoTitle?.let { DemoPanel(it, demoProgress, demoLog.toList()) },
            nowMs = System.currentTimeMillis(),
        )
    }

    private inline fun update(f: UiState.() -> UiState) { _state.value = _state.value.f() }

    private fun toast(text: String) {
        update { copy(toast = text) }
        main.postDelayed({ if (_state.value.toast == text) update { copy(toast = null) } }, 3_500)
    }
}
