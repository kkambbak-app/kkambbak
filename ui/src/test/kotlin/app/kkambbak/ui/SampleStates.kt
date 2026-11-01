package app.kkambbak.ui

import app.kkambbak.core.Briefing
import app.kkambbak.core.CrashTrigger
import app.kkambbak.core.DemoScript
import app.kkambbak.core.DriveEvent
import app.kkambbak.core.DriveSession
import app.kkambbak.core.ManeuverResult
import app.kkambbak.core.RewardBook
import app.kkambbak.core.TripSummary
import app.kkambbak.core.Weekly

/** 화면 렌더링용 상태. 모두 실제 엔진(DriveSession)에 시연 스크립트를 넣어 만든다. */
object SampleStates {
    // 2026. 11. 2.(월) 08:40 KST
    const val NOW = 1_793_576_400_000L

    class Rec : DriveSession.Listener {
        val results = mutableListOf<ManeuverResult>()
        val trips = mutableListOf<TripSummary>()
        var last: DriveSession.LiveState = DriveSession.LiveState()
        override fun onManeuver(result: ManeuverResult) { results += result }
        override fun onTripEnded(summary: TripSummary) { trips += summary }
        override fun onStateChanged(state: DriveSession.LiveState) { last = state }
    }

    fun commuteStart(): Long = NOW - 10 * 60_000L

    fun runCommute(): Rec {
        val rec = Rec()
        val s = DriveSession(rec)
        DemoScript.drive(DemoScript.commuteLegs, commuteStart()).forEach { s.handle(it) }
        return rec
    }

    fun base(): UiState {
        val (hist, histTrips) = DemoScript.history(NOW)
        val today = runCommute()
        val all = hist + today.results
        val trips = histTrips + today.trips
        val weekly = Weekly.compute(all, NOW)
        val rewards = RewardBook.evaluate(RewardBook.inputs(all, trips))
        return UiState(
            profileName = "시연 운전자",
            demoProfile = true,
            todayResults = today.results,
            lastTrip = today.trips.last(),
            briefing = Briefing.trip(today.trips.last()),
            weekly = weekly,
            weeklyBriefing = Briefing.weekly(weekly),
            rewards = rewards,
            settings = SettingsState(preAlert = true, autoBriefing = true),
            sources = listOf(
                SourceStatus("Vehicle SDK", true, "방향지시등 · 기어 · 브레이크 · 전조등 · 와이퍼"),
                SourceStatus("NaviHelper SDK", true, "회전 안내(TBT) 수신 중"),
                SourceStatus("FusedLocation SDK", true, "위치 · 진행방향"),
                SourceStatus("TextToSpeech SDK", true, "한국어 · 온디바이스"),
                SourceStatus("SpeechToText SDK", true, "한국어 · 온디바이스"),
            ),
            nowMs = NOW,
        )
    }

    /** 출근길 시연 도중(시청 앞 교차로 접근, 지시등 아직 꺼짐) 상태. */
    fun drivingMidway(): UiState {
        val rec = Rec()
        val s = DriveSession(rec)
        val events = DemoScript.drive(DemoScript.commuteLegs, commuteStart())
        // 세 번째 회전 지점까지 45m 남은 순간까지 재생
        var stop = events.size
        var seenThird = false
        for ((i, e) in events.withIndex()) {
            s.handle(e)
            if (e is DriveEvent.TbtUpdated && e.place == "시청 앞 교차로") seenThird = true
            if (seenThird && e is DriveEvent.TbtUpdated && e.distanceM < 45) { stop = i; break }
        }
        return base().copy(
            driving = true,
            live = rec.last,
            demo = DemoPanel("출근길 시연 · 집 → 회사(가상 경로)", stop / events.size.toFloat(), rec.results.toList()),
        )
    }

    fun crashRecord() = run {
        val rec = Rec()
        val s = DriveSession(rec)
        val ev = DemoScript.crash(NOW - 3 * 60_000L)
        ev.forEach { s.handle(it) }
        s.freezeCrash(CrashTrigger.SUDDEN_STOP, ev.last().timeMs)
            .withStatement("2차로로 합류하려고 오른쪽 깜빡이를 켜고 들어가는데 앞차가 갑자기 서서 부딪혔어요")
    }
}
