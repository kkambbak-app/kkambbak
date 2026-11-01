package app.kkambbak.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.kkambbak.core.Briefing
import app.kkambbak.core.CrashTrigger
import app.kkambbak.core.DemoScript
import app.kkambbak.core.DriveEvent
import app.kkambbak.core.DriveSession
import app.kkambbak.core.ManeuverResult
import app.kkambbak.core.RewardBook
import app.kkambbak.core.TripSummary
import app.kkambbak.core.Weekly
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * 시연 영상용 프레임. 시연 스크립트를 실제 엔진(DriveSession)에 넣고, 그 순간의 화면을 실제 Compose 코드로 렌더링한다.
 * 프레임 목록(frames.tsv)에는 프레임 이름과 그 순간에 일어난 일을 함께 적어 자막 작업에 쓴다.
 * 실행: ./gradlew :ui:recordPaparazziDebug --tests '*VideoFramesTest*'
 */
class VideoFramesTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920, screenHeight = 880, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        theme = "android:Theme.Material.NoActionBar",
        useDeviceResolution = true,
        snapshotHandler = app.kkambbak.tools.FullResHandler(java.io.File("build/video-frames")),
        maxPercentDifference = 1.0,
    )

    private val log = StringBuilder()
    private var n = 0

    private fun shot(state: UiState, note: String) {
        n++
        val name = "f%04d".format(n)
        log.append(name).append('\t').append(note.replace('\t', ' ')).append('\n')
        paparazzi.snapshot(name) { KkambbakApp(state) {} }
    }

    private class Rec(val onState: (DriveSession.LiveState) -> Unit = {}) : DriveSession.Listener {
        val results = mutableListOf<ManeuverResult>()
        val trips = mutableListOf<TripSummary>()
        var last = DriveSession.LiveState()
        var suddenStop = false
        override fun onManeuver(result: ManeuverResult) { results += result }
        override fun onTripEnded(summary: TripSummary) { trips += summary }
        override fun onSuddenStop(timeMs: Long) { suddenStop = true }
        override fun onStateChanged(state: DriveSession.LiveState) { last = state }
    }

    /** 스크립트를 재생하며 everyMs(스크립트 시간)마다 주행 화면 프레임을 찍는다. */
    private fun drive(
        title: String, events: List<DriveEvent>, everyMs: Long, base: UiState, histResults: List<ManeuverResult>,
        session: DriveSession, rec: Rec,
    ) {
        val t0 = events.first().timeMs
        val total = (events.last().timeMs - t0).toFloat()
        var nextShot = t0
        var lastCount = 0
        for (e in events) {
            session.handle(e)
            val newResult = rec.results.size > lastCount
            if (rec.last.tripActive && (e.timeMs >= nextShot || newResult)) {
                val note = if (newResult) {
                    val r = rec.results.last()
                    "판정\t${r.place} ${r.kind.label}\t${r.verdict.korean}\t${r.signalDistanceM ?: ""}"
                } else "주행\t${rec.last.nextManeuver ?: ""}\t${rec.last.nextDistanceM?.toInt() ?: ""}\t${rec.last.signal}"
                shot(base.copy(driving = true, live = rec.last, demo = DemoPanel(title, (e.timeMs - t0) / total, rec.results.toList()),
                    todayResults = rec.results.toList()), note)
                nextShot = e.timeMs + everyMs
                lastCount = rec.results.size
            }
        }
    }

    @Test
    fun frames() {
        val now = SampleStates.NOW
        val (hist, histTrips) = DemoScript.history(now)
        val base0 = UiState(
            profileName = "시연 운전자", demoProfile = true, nowMs = now,
            settings = SettingsState(preAlert = false, autoBriefing = true),
            sources = SampleStates.base().sources,
        )
        fun derived(results: List<ManeuverResult>, trips: List<TripSummary>, today: List<ManeuverResult>, briefing: String?): UiState {
            val w = Weekly.compute(results, now)
            return base0.copy(
                todayResults = today, lastTrip = trips.lastOrNull(), briefing = briefing, weekly = w,
                weeklyBriefing = Briefing.weekly(w), rewards = RewardBook.evaluate(RewardBook.inputs(results, trips)),
            )
        }

        // 1) 출근길
        val before = derived(hist, histTrips, emptyList(), null)
        shot(before, "시작\t주차 상태 · 오늘 기록 없음")
        val rec = Rec()
        val session = DriveSession(rec)
        drive("출근길 시연 · 집 → 회사(가상 경로)", DemoScript.drive(DemoScript.commuteLegs, SampleStates.commuteStart()), 4_000, before, hist, session, rec)
        val trip = rec.trips.single()
        val brief = Briefing.trip(trip)
        val afterCommute = derived(hist + rec.results, histTrips + rec.trips, rec.results.toList(), brief)
        shot(afterCommute.copy(speaking = true), "주차\t$brief")
        shot(afterCommute.copy(tab = Tab.WEEKLY), "주간\t${afterCommute.weeklyBriefing}")
        shot(afterCommute.copy(tab = Tab.REWARDS), "리워드\t")

        // 2) 완벽한 퇴근길 → 주차 축하등
        val rec2 = Rec()
        val s2 = DriveSession(rec2)
        DemoScript.drive(DemoScript.perfectLegs, now + 9 * 3_600_000L).forEach { s2.handle(it) }
        val allResults = hist + rec.results + rec2.results
        val allTrips = histTrips + rec.trips + rec2.trips
        val afterPerfect = derived(allResults, allTrips, rec.results + rec2.results, Briefing.trip(rec2.trips.single()))
        shot(afterPerfect.copy(celebration = true), "축하\t${afterPerfect.briefing}")
        shot(afterPerfect.copy(tab = Tab.SETTINGS), "설정\t")

        // 3) 사고 상황
        val rec3 = Rec()
        val s3 = DriveSession(rec3)
        val crashEvents = DemoScript.crash(now + 10 * 3_600_000L)
        val t0 = crashEvents.first().timeMs
        var next = t0
        for (e in crashEvents) {
            s3.handle(e)
            if (rec3.last.tripActive && e.timeMs >= next && !rec3.suddenStop) {
                shot(afterPerfect.copy(driving = true, live = rec3.last, demo = DemoPanel("사고 상황 시연 · 합류 구간(가상)", (e.timeMs - t0) / (crashEvents.last().timeMs - t0).toFloat(), emptyList())),
                    "사고주행\t${rec3.last.signal}\t${rec3.last.brake}\t${rec3.last.speedKmh.toInt()}")
                next = e.timeMs + 3_000
            }
        }
        shot(afterPerfect.copy(suddenStopPrompt = true), "급정거\t")
        val record = s3.freezeCrash(CrashTrigger.SUDDEN_STOP, crashEvents.last().timeMs)
        shot(afterPerfect.copy(crashFlow = CrashFlow(record)), "사고안내\t")
        val statement = "2차로로 합류하려고 오른쪽 깜빡이를 켜고 들어가는데 앞차가 갑자기 서서 부딪혔어요"
        listOf(14, 30, statement.length).forEach { k ->
            shot(afterPerfect.copy(crashFlow = CrashFlow(record, listening = k < statement.length, partial = statement.take(k))), "진술\t${statement.take(k)}")
        }
        val saved = record.withStatement(statement)
        shot(afterPerfect.copy(tab = Tab.CRASH, crashes = listOf(saved), selectedCrashId = saved.id), "기록지\t${saved.hash}")

        File("build/video-frames.tsv").writeText(log.toString())
    }
}
