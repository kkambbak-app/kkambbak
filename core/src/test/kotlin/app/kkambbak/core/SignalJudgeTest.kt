package app.kkambbak.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalJudgeTest {

    private class Collector : DriveSession.Listener {
        val results = mutableListOf<ManeuverResult>()
        val trips = mutableListOf<TripSummary>()
        val alerts = mutableListOf<Pair<Side, String>>()
        var suddenStops = 0
        override fun onManeuver(result: ManeuverResult) { results += result }
        override fun onTripEnded(summary: TripSummary) { trips += summary }
        override fun onPreAlert(side: Side, place: String) { alerts += side to place }
        override fun onSuddenStop(timeMs: Long) { suddenStops++ }
    }

    private fun run(events: List<DriveEvent>, setup: (DriveSession) -> Unit = {}): Pair<Collector, DriveSession> {
        val c = Collector()
        val s = DriveSession(c)
        setup(s)
        events.forEach { s.handle(it) }
        return c to s
    }

    @Test fun commuteDemoGivesExpectedVerdicts() {
        val (c, _) = run(DemoScript.drive(DemoScript.commuteLegs, 1_000_000L))
        assertEquals(
            listOf(Verdict.ON_TIME, Verdict.ON_TIME, Verdict.LATE, Verdict.ON_TIME, Verdict.ON_TIME, Verdict.MISSED),
            c.results.map { it.verdict },
        )
        assertEquals(listOf("아파트 정문", "중앙사거리", "시청 앞 교차로", "한빛대로 진입로", "도심 방면", "회사 앞 사거리"), c.results.map { it.place })
        val late = c.results[2]
        assertTrue("늦게 켠 거리는 30m 미만이어야 한다: ${late.signalDistanceM}", late.signalDistanceM!! < 30.0)
        val exit = c.results[4]
        assertEquals(LegalDistance.HIGHWAY_M, exit.legalDistanceM, 0.0)
        assertTrue(exit.signalDistanceM!! >= 100.0)
        assertEquals(1, c.trips.size)
        assertEquals(6, c.trips[0].judged)
        assertEquals(4, c.trips[0].onTime)
    }

    @Test fun perfectTripIsPerfect() {
        val (c, _) = run(DemoScript.drive(DemoScript.perfectLegs, 5_000_000L))
        assertEquals(3, c.results.size)
        assertTrue(c.results.all { it.verdict == Verdict.ON_TIME })
        assertTrue(c.trips.single().perfect)
    }

    @Test fun noCoordinatesStoredUnlessEnabled() {
        val (c, _) = run(DemoScript.drive(DemoScript.commuteLegs, 0L))
        assertTrue(c.results.all { it.lat == null && it.lon == null })
        val (c2, _) = run(DemoScript.drive(DemoScript.commuteLegs, 0L)) { it.storeSpots = true }
        assertTrue(c2.results.all { it.lat != null && it.lon != null })
    }

    @Test fun preAlertFiresOnlyWhenEnabledAndSignalMissing() {
        val (off, _) = run(DemoScript.drive(DemoScript.commuteLegs, 0L))
        assertTrue(off.alerts.isEmpty())
        val (on, _) = run(DemoScript.drive(DemoScript.commuteLegs, 0L)) { it.preAlertEnabled = true }
        // 50m 앞까지 신호가 없던 곳: 시청 앞 교차로(10m에서 켬), 회사 앞 사거리(안 켬)
        assertEquals(listOf("시청 앞 교차로", "회사 앞 사거리"), on.alerts.map { it.second })
    }

    @Test fun signalTurnedOffBeforeTurnCountsAsMissed() {
        val legs = listOf(
            DemoScript.Leg(300.0, 40.0, ManeuverKind.RIGHT_TURN, "테스트 교차로", 90.0, 80.0),
            DemoScript.Leg(150.0, 25.0, null, "끝", 0.0, null),
        )
        val ev = DemoScript.drive(legs, 0L).toMutableList()
        // 지시등을 켠 직후 끄는 이벤트를 하나 끼워 넣는다
        val on = ev.first { it is DriveEvent.SignalChanged && it.signal == Signal.RIGHT }
        ev += DriveEvent.SignalChanged(Signal.OFF, on.timeMs + 1500)
        val (c, _) = run(ev.sortedBy { it.timeMs })
        assertEquals(Verdict.MISSED, c.results.single().verdict)
    }

    @Test fun wrongSideSignal() {
        val legs = listOf(
            DemoScript.Leg(300.0, 40.0, ManeuverKind.LEFT_TURN, "테스트 교차로", -90.0, 60.0, wrongSide = true),
            DemoScript.Leg(150.0, 25.0, null, "끝", 0.0, null),
        )
        val (c, _) = run(DemoScript.drive(legs, 0L))
        assertEquals(Verdict.WRONG_SIDE, c.results.single().verdict)
    }

    @Test fun goingStraightPastGuidedTurnIsNotJudged() {
        // 안내는 우회전인데 실제로는 직진(turnDeg = 0)
        val legs = listOf(
            DemoScript.Leg(300.0, 40.0, ManeuverKind.RIGHT_TURN, "테스트 교차로", 0.0, 60.0),
            DemoScript.Leg(150.0, 25.0, null, "끝", 0.0, null),
        )
        val (c, _) = run(DemoScript.drive(legs, 0L))
        assertTrue(c.results.isEmpty())
    }

    @Test fun routeChangeFarFromTurnIsNotJudged() {
        val c = Collector()
        val s = DriveSession(c)
        var t = 0L
        s.handle(DriveEvent.GearChanged(GearState.DRIVE, t))
        var lat = 37.5; var lon = 127.0
        s.handle(DriveEvent.LocationUpdated(lat, lon, 0.0, t))
        s.handle(DriveEvent.TbtUpdated(ManeuverKind.LEFT_TURN, 120.0, "A", t))
        repeat(4) {
            t += 1000
            val p = Geo.move(lat, lon, 0.0, 10.0); lat = p.first; lon = p.second
            s.handle(DriveEvent.LocationUpdated(lat, lon, 0.0, t))
        }
        // 80m 남은 상태에서 경로가 바뀌어 다른 안내가 온다
        s.handle(DriveEvent.TbtUpdated(ManeuverKind.RIGHT_TURN, 500.0, "B", t))
        repeat(10) {
            t += 1000
            val p = Geo.move(lat, lon, 0.0, 10.0); lat = p.first; lon = p.second
            s.handle(DriveEvent.LocationUpdated(lat, lon, 0.0, t))
        }
        assertTrue(c.results.isEmpty())
    }

    private fun freeTurn(signalLeadMs: Long?): List<DriveEvent> {
        val ev = mutableListOf<DriveEvent>()
        var t = 0L
        var lat = 37.5; var lon = 127.0; var h = 0.0
        ev += DriveEvent.GearChanged(GearState.DRIVE, t)
        ev += DriveEvent.LocationUpdated(lat, lon, h, t)
        // 직진 10초(시속 30km)
        repeat(20) { t += 500; val p = Geo.move(lat, lon, h, 30 / 3.6 * 0.5); lat = p.first; lon = p.second; ev += DriveEvent.LocationUpdated(lat, lon, h, t) }
        val turnStart = t
        if (signalLeadMs != null) ev += DriveEvent.SignalChanged(Signal.RIGHT, turnStart - signalLeadMs)
        // 4초에 걸쳐 오른쪽으로 90도(시속 15km)
        repeat(8) { t += 500; h += 11.25; val p = Geo.move(lat, lon, h, 15 / 3.6 * 0.5); lat = p.first; lon = p.second; ev += DriveEvent.LocationUpdated(lat, lon, h, t) }
        repeat(6) { t += 500; val p = Geo.move(lat, lon, h, 25 / 3.6 * 0.5); lat = p.first; lon = p.second; ev += DriveEvent.LocationUpdated(lat, lon, h, t) }
        return ev.sortedBy { it.timeMs }
    }

    @Test fun turnWithoutNavigationWithSignal() {
        val (c, _) = run(freeTurn(signalLeadMs = 5_000))
        val r = c.results.single()
        assertEquals(ManeuverKind.RIGHT_TURN, r.kind)
        assertEquals(Verdict.ON_TIME, r.verdict)
        assertNull(r.signalDistanceM)
        assertTrue(!r.viaNavigation)
    }

    @Test fun turnWithoutNavigationWithoutSignal() {
        val (c, _) = run(freeTurn(signalLeadMs = null))
        assertEquals(Verdict.MISSED, c.results.single().verdict)
    }

    @Test fun crashScenarioDetectsSuddenStopAndDescribesFacts() {
        val ev = DemoScript.crash(10_000_000L)
        val (c, s) = run(ev)
        assertEquals(1, c.suddenStops)
        val rec = s.freezeCrash(CrashTrigger.SUDDEN_STOP, ev.last().timeMs)
        assertTrue(rec.snapshots.isNotEmpty())
        val signalFact = rec.facts.first { "방향지시등" in it }
        assertTrue(signalFact, signalFact.startsWith("오른쪽 방향지시등"))
        assertTrue(rec.facts.any { it.startsWith("브레이크: 기록 시점") && "전부터 밟음" in it })
        // 해시 체인: 같은 입력이면 같고, 한 줄만 바뀌어도 달라진다
        assertEquals(rec.hash, Hashing.chain(rec.snapshots))
        val tampered = rec.snapshots.toMutableList().also { it[0] = it[0].copy(signal = Signal.LEFT) }
        assertNotEquals(rec.hash, Hashing.chain(tampered))
        val withStatement = rec.withStatement("합류하다가 앞차와 부딪혔어요")
        assertNotNull(withStatement.statementHash)
        assertEquals(64, withStatement.statementHash.length)
    }

    @Test fun briefingSentences() {
        val (c, _) = run(DemoScript.drive(DemoScript.commuteLegs, 0L))
        val text = Briefing.trip(c.trips.single())
        assertTrue(text, text.startsWith("회전 6번 중 4번은 제때 켰어요. 늦게 켠 게 1번, 안 켠 게 1번 있었어요."))
        assertTrue(text, "회사 앞 사거리 좌회전에서 깜빡이가 없었어요." in text)
        val (p, _) = run(DemoScript.drive(DemoScript.perfectLegs, 0L))
        assertEquals("이번 주행 회전 3번, 모두 제때 켰어요.", Briefing.trip(p.trips.single()))
        assertEquals("은", Briefing.topic("우회전"))
        assertEquals("는", Briefing.topic("오른쪽 진출입로"))
    }

    @Test fun weeklyAndRewards() {
        val now = 1_793_000_000_000L
        val (results, trips) = DemoScript.history(now)
        val w = Weekly.compute(results, now)
        assertEquals(58, w.judged)
        assertEquals(7, w.days.size)
        val top2 = w.topMissedPlaces.take(2).map { it.first }
        assertTrue(w.topMissedPlaces.toString(), "시청 앞 교차로 우회전" in top2 && "회사 앞 사거리 좌회전" in top2)
        val inputs = RewardBook.inputs(results, trips)
        assertTrue(inputs.toString(), inputs.perfectTrips >= 1)
        val prog = RewardBook.evaluate(inputs)
        assertTrue(prog.first { it.reward.id == "celebrate" }.unlocked)
        assertTrue(!prog.first { it.reward.id == "voice" }.unlocked)
    }

    @Test fun voiceIntents() {
        assertEquals(VoiceIntent.CRASH_RECORD, VoiceIntents.parse("사고 기록"))
        assertEquals(VoiceIntent.WEEKLY, VoiceIntents.parse("이번 주 깜빡이 어땠어?"))
        assertEquals(VoiceIntent.TODAY, VoiceIntents.parse("오늘 어땠어"))
        assertEquals(VoiceIntent.UNKNOWN, VoiceIntents.parse("노래 틀어줘"))
    }
}
