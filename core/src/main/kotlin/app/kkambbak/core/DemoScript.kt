package app.kkambbak.core

import kotlin.math.max
import kotlin.math.min

/**
 * 시연용 주행 스크립트. 실차·에뮬레이터 없이도 판정 엔진 전체를 같은 입력 형식으로 돌려 볼 수 있다.
 * 장소 이름은 모두 가상의 이름이다.
 */
object DemoScript {

    /** 한 구간: 직진 length 미터 뒤 maneuver 를 수행한다. signalAtM = 남은 거리 몇 m에서 지시등을 켤지(null = 안 켬). */
    data class Leg(
        val lengthM: Double,
        val speedKmh: Double,
        val kind: ManeuverKind?,
        val place: String,
        val turnDeg: Double,
        val signalAtM: Double?,
        val wrongSide: Boolean = false,
    )

    private const val DT_MS = 500L
    private const val ORIGIN_LAT = 37.5050
    private const val ORIGIN_LON = 127.0240

    /** 출근길: 회전 6번 — 제때 4, 늦게 1, 미점등 1. */
    val commuteLegs = listOf(
        Leg(250.0, 30.0, ManeuverKind.RIGHT_TURN, "아파트 정문", 90.0, 55.0),
        Leg(400.0, 45.0, ManeuverKind.LEFT_TURN, "중앙사거리", -90.0, 40.0),
        Leg(350.0, 40.0, ManeuverKind.RIGHT_TURN, "시청 앞 교차로", 90.0, 10.0),
        Leg(300.0, 50.0, ManeuverKind.RAMP_RIGHT, "한빛대로 진입로", 25.0, 45.0),
        Leg(1200.0, 90.0, ManeuverKind.HIGHWAY_EXIT_RIGHT, "도심 방면", 30.0, 160.0),
        Leg(400.0, 40.0, ManeuverKind.LEFT_TURN, "회사 앞 사거리", -90.0, null),
        Leg(150.0, 25.0, null, "회사 주차장", 0.0, null),
    )

    /** 모든 회전을 제때 켠 짧은 퇴근길(리워드 시연용). */
    val perfectLegs = listOf(
        Leg(300.0, 35.0, ManeuverKind.LEFT_TURN, "회사 앞 사거리", -90.0, 50.0),
        Leg(500.0, 45.0, ManeuverKind.RIGHT_TURN, "시청 앞 교차로", 90.0, 45.0),
        Leg(400.0, 40.0, ManeuverKind.RIGHT_TURN, "중앙사거리", 90.0, 60.0),
        Leg(200.0, 25.0, null, "집", 0.0, null),
    )

    fun drive(legs: List<Leg>, startMs: Long): List<DriveEvent> {
        val ev = mutableListOf<DriveEvent>()
        var t = startMs
        var lat = ORIGIN_LAT
        var lon = ORIGIN_LON
        var heading = 0.0
        var signal = Signal.OFF

        fun emit(e: DriveEvent) { ev += e }
        emit(DriveEvent.GearChanged(GearState.PARK, t))
        emit(DriveEvent.LowBeamChanged(true, t))
        emit(DriveEvent.LocationUpdated(lat, lon, heading, t))
        t += 1000
        emit(DriveEvent.BrakeChanged(true, t))
        emit(DriveEvent.GearChanged(GearState.DRIVE, t + 300))
        emit(DriveEvent.BrakeChanged(false, t + 800))
        t += 1000

        legs.forEachIndexed { idx, leg ->
            var remaining = leg.lengthM
            var lastTbtAt = Long.MIN_VALUE
            val tbtKind = leg.kind
            emit(DriveEvent.TbtUpdated(tbtKind, remaining, leg.place, t))
            lastTbtAt = t
            while (remaining > 0.0) {
                // 회전 지점 40m 앞부터 감속
                val target = if (remaining < 40 && leg.kind != null && leg.kind.headingCheck) min(leg.speedKmh, 20.0)
                    else if (leg.kind == null && remaining < 60) max(8.0, leg.speedKmh * remaining / 60.0)
                    else leg.speedKmh
                val step = target / 3.6 * DT_MS / 1000.0
                val move = min(step, remaining)
                val p = Geo.move(lat, lon, heading, move)
                lat = p.first; lon = p.second
                remaining -= move
                t += DT_MS
                emit(DriveEvent.LocationUpdated(lat, lon, heading, t))
                if (t - lastTbtAt >= 1000) {
                    emit(DriveEvent.TbtUpdated(tbtKind, remaining, leg.place, t)); lastTbtAt = t
                }
                val plan = leg.signalAtM
                if (plan != null && signal == Signal.OFF && remaining <= plan && leg.kind != null) {
                    val side = if (leg.wrongSide) (if (leg.kind.side == Side.LEFT) Signal.RIGHT else Signal.LEFT) else leg.kind.side.signal
                    signal = side
                    emit(DriveEvent.SignalChanged(side, t + 1))
                }
                if (leg.kind?.headingCheck == true && remaining < 25 && remaining > 0) {
                    // 정지선 부근에서 잠깐 브레이크
                    if (remaining < 15) emit(DriveEvent.BrakeChanged(true, t + 2)) else emit(DriveEvent.BrakeChanged(false, t + 2))
                }
            }
            emit(DriveEvent.BrakeChanged(false, t + 3))
            // 회전 수행: 8개 샘플에 걸쳐 방향을 튼다.
            if (leg.kind != null) {
                val steps = 8
                val v = if (leg.kind.headingCheck) 18.0 else leg.speedKmh
                for (i in 1..steps) {
                    heading = (heading + leg.turnDeg / steps + 360) % 360
                    val p = Geo.move(lat, lon, heading, v / 3.6 * DT_MS / 1000.0)
                    lat = p.first; lon = p.second
                    t += DT_MS
                    emit(DriveEvent.LocationUpdated(lat, lon, heading, t))
                }
                // 직진 복귀 후 지시등 자동 해제
                for (i in 1..6) {
                    val p = Geo.move(lat, lon, heading, v / 3.6 * DT_MS / 1000.0)
                    lat = p.first; lon = p.second
                    t += DT_MS
                    emit(DriveEvent.LocationUpdated(lat, lon, heading, t))
                }
                if (signal != Signal.OFF) { signal = Signal.OFF; emit(DriveEvent.SignalChanged(Signal.OFF, t + 1)) }
            } else if (idx == legs.lastIndex) {
                // 도착: 정차 후 P단
                emit(DriveEvent.TbtUpdated(null, 0.0, leg.place, t))
                t += 800
                emit(DriveEvent.BrakeChanged(true, t))
                emit(DriveEvent.LocationUpdated(lat, lon, heading, t))
                t += 1200
                emit(DriveEvent.LocationUpdated(lat, lon, heading, t))
                emit(DriveEvent.GearChanged(GearState.PARK, t + 500))
                emit(DriveEvent.BrakeChanged(false, t + 1500))
                t += 2000
            }
            emit(DriveEvent.Tick(t))
        }
        return ev.sortedBy { it.timeMs }
    }

    /**
     * 사고 상황 시연: 합류 구간에서 오른쪽 지시등을 켜고 진로를 바꾸다 앞차와 접촉, 급정거.
     * 급정거가 감지되면 앱이 사고 기록을 제안한다.
     */
    fun crash(startMs: Long): List<DriveEvent> {
        val ev = mutableListOf<DriveEvent>()
        var t = startMs
        var lat = ORIGIN_LAT + 0.01
        var lon = ORIGIN_LON + 0.01
        val heading = 45.0
        ev += DriveEvent.GearChanged(GearState.PARK, t)
        ev += DriveEvent.LowBeamChanged(true, t)
        ev += DriveEvent.WiperChanged(false, t)
        ev += DriveEvent.LocationUpdated(lat, lon, heading, t)
        t += 1000
        ev += DriveEvent.GearChanged(GearState.DRIVE, t)
        t += 500
        // 30초간 시속 45km 주행
        val total = 60
        for (i in 1..total) {
            val p = Geo.move(lat, lon, heading, 45 / 3.6 * DT_MS / 1000.0)
            lat = p.first; lon = p.second
            t += DT_MS
            ev += DriveEvent.LocationUpdated(lat, lon, heading, t)
            if (i == total - 8) ev += DriveEvent.SignalChanged(Signal.RIGHT, t + 1)        // 충돌 약 4초 전 오른쪽 지시등
            if (i == total - 2) ev += DriveEvent.BrakeChanged(true, t + 1)               // 약 1초 전 브레이크
        }
        // 1.5초 만에 정지
        for (v in listOf(25.0, 8.0, 0.0)) {
            val p = Geo.move(lat, lon, heading, v / 3.6 * DT_MS / 1000.0)
            lat = p.first; lon = p.second
            t += DT_MS
            ev += DriveEvent.LocationUpdated(lat, lon, heading, t)
        }
        t += DT_MS
        ev += DriveEvent.LocationUpdated(lat, lon, heading, t)
        ev += DriveEvent.Tick(t)
        return ev.sortedBy { it.timeMs }
    }

    /**
     * 주간 리포트·리워드 화면 시연용 지난 6일 기록. 저장소에는 '시연 운전자' 프로필로만 들어간다.
     * 제때 비율이 62%에서 88%로 오르는 흐름.
     */
    fun history(nowMs: Long): Pair<List<ManeuverResult>, List<TripSummary>> {
        val plans = listOf( // (며칠 전, 회전 수, 제때, 늦게, 미점등)
            Triple(6, 8, listOf(5, 2, 1)),
            Triple(5, 10, listOf(6, 2, 2)),
            Triple(4, 9, listOf(6, 2, 1)),
            Triple(3, 11, listOf(8, 2, 1)),
            Triple(2, 8, listOf(8, 0, 0)),
            Triple(1, 12, listOf(10, 1, 1)),
        )
        val places = listOf(
            ManeuverKind.RIGHT_TURN to "아파트 정문", ManeuverKind.LEFT_TURN to "중앙사거리",
            ManeuverKind.RIGHT_TURN to "시청 앞 교차로", ManeuverKind.RAMP_RIGHT to "한빛대로 진입로",
            ManeuverKind.HIGHWAY_EXIT_RIGHT to "도심 방면", ManeuverKind.LEFT_TURN to "회사 앞 사거리",
        )
        val results = mutableListOf<ManeuverResult>()
        val trips = mutableListOf<TripSummary>()
        for ((ago, n, split) in plans) {
            val date = java.time.Instant.ofEpochMilli(nowMs).atZone(KST).toLocalDate().minusDays(ago.toLong())
            val base = date.atTime(8, 10).atZone(KST).toInstant().toEpochMilli()
            val verdicts = List(split[0]) { Verdict.ON_TIME } + List(split[1]) { Verdict.LATE } + List(split[2]) { Verdict.MISSED }
            // 늦게·미점등은 '회사 앞 사거리'와 '시청 앞 교차로'에 몰리게 해서 '자주 놓치는 곳'이 드러나게 한다.
            val ordered = verdicts.shuffled(kotlin.random.Random(ago))
            val rs = ordered.mapIndexed { i, v ->
                val (kind, place) = when {
                    v == Verdict.MISSED -> ManeuverKind.LEFT_TURN to "회사 앞 사거리"
                    v == Verdict.LATE && i % 2 == 0 -> ManeuverKind.RIGHT_TURN to "시청 앞 교차로"
                    else -> places[i % places.size]
                }
                val legal = if (kind.highwayExit) LegalDistance.HIGHWAY_M else LegalDistance.GENERAL_M
                ManeuverResult(
                    timeMs = base + i * 90_000L, kind = kind, place = place, verdict = v,
                    signalDistanceM = when (v) { Verdict.ON_TIME -> legal + 15 + i; Verdict.LATE -> 12.0 + i; else -> null },
                    legalDistanceM = legal, viaNavigation = true,
                )
            }
            check(rs.size == n)
            results += rs
            // 하루 두 번 주행으로 나눈다
            val half = n / 2
            trips += TripSummary(base, base + half * 90_000L, rs.take(half))
            trips += TripSummary(base + half * 90_000L, base + n * 90_000L, rs.drop(half))
        }
        return results to trips
    }
}
