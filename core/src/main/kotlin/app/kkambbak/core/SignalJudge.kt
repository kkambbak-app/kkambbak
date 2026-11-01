package app.kkambbak.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 방향지시등 판정 엔진.
 *
 * 1) 내비 안내(TBT)가 있을 때: 다음 회전·분기 지점까지의 남은 거리를 추적하다가
 *    지시등이 켜진 순간의 남은 거리를 적고, 지점을 통과한 뒤 실제로 방향을 틀었는지 확인하고 판정한다.
 *    법정 거리(일반도로 30m, 고속도로 100m) 이상 앞에서 켜서 유지했으면 '제때'.
 * 2) 내비 안내가 없을 때: 저속에서 진행방향이 크게 바뀐 구간을 회전으로 보고, 켰는지만 판정한다(거리 판정 없음).
 *
 * 저장하는 것은 판정 결과뿐이다. 위치는 판정에만 쓰고 버린다.
 */
class SignalJudge(
    private val onResult: (ManeuverResult) -> Unit,
    private val onPreAlert: (Side, String) -> Unit = { _, _ -> },
) {
    /** 초보 모드: 법정 거리 직전(일반 35m, 고속도로 105m)까지 신호가 없으면 짧게 한 번 알려준다. */
    var preAlertEnabled = false
    /** '자주 놓치는 곳' 기능. 꺼져 있으면 결과에 좌표를 남기지 않는다. */
    var storeSpots = false

    companion object {
        const val OPEN_WINDOW_GENERAL_M = 150.0
        const val OPEN_WINDOW_HIGHWAY_M = 300.0
        const val HIGHWAY_SPEED_KMH = 80.0
        const val PRE_ALERT_GENERAL_M = 35.0   // 법정 거리(30m) 직전
        const val PRE_ALERT_HIGHWAY_M = 105.0  // 법정 거리(100m) 직전
        const val SWITCH_REACHED_M = 40.0        // TBT가 다음 안내로 넘어갈 때 이 거리 안이면 '지점 도달'로 본다
        const val TURN_CONFIRM_DEG = 45.0
        const val TURN_ABORT_AFTER_M = 80.0
        const val NON_TURN_CONFIRM_AFTER_M = 30.0
        const val SIGNAL_OFF_GRACE_M = 3.0

        // 내비 없는 회전 감지
        const val FB_WINDOW_MS = 12_000L
        const val FB_TURN_DEG = 60.0
        const val FB_MAX_AVG_SPEED = 35.0
        const val FB_MIN_PEAK_SPEED = 12.0
        const val FB_COOLDOWN_MS = 15_000L
    }

    private class Target(
        val kind: ManeuverKind?,
        val place: String,
        var tbtDistance: Double,
        var traveledSinceTbt: Double = 0.0,
        var opened: Boolean = false,
        var legal: Double = LegalDistance.GENERAL_M,
        var onSinceDist: Double? = null,
        var wrongSide: Boolean = false,
        var openHeading: Double? = null,
        var preAlerted: Boolean = false,
        var openedLat: Double? = null,
        var openedLon: Double? = null,
    ) {
        val estDistance: Double get() = tbtDistance - traveledSinceTbt
    }

    private class Pending(val t: Target, val passedAt: Long, var traveledAfter: Double = 0.0)

    private var target: Target? = null
    private var pending: Pending? = null
    /** 방금 통과한 안내 지점. 내비가 같은 안내를 잠깐 더 보내도 두 번 판정하지 않게 한다. */
    private var lastPassedKey: String? = null

    private var signal = Signal.OFF
    private var heading: Double? = null
    private var lat: Double? = null
    private var lon: Double? = null
    private var speedKmh = 0.0
    private var moving = true

    // 내비 없는 회전 감지용 기록
    private data class HSample(val t: Long, val heading: Double, val speed: Double, val step: Double)
    private val headingHist = ArrayDeque<HSample>()
    private val signalLog = ArrayDeque<Pair<Long, Signal>>()
    private var lastFallbackAt = Long.MIN_VALUE / 2

    /** 주행 중일 때만 판정한다(P단·후진에서는 멈춤). */
    fun setMoving(isMoving: Boolean) {
        moving = isMoving
        if (!isMoving) headingHist.clear()
    }

    fun reset() {
        target = null; pending = null; lastPassedKey = null; headingHist.clear(); signalLog.clear()
    }

    // ---------------------------------------------------------------- 입력

    fun onSignal(newSignal: Signal, t: Long) {
        val old = signal
        signal = newSignal
        signalLog.addLast(t to newSignal)
        while (signalLog.size > 64) signalLog.removeFirst()

        val tg = target ?: return
        val kind = tg.kind ?: return
        if (!tg.opened) return
        val side = kind.side.signal
        if (newSignal == side && old != side) {
            tg.onSinceDist = max(0.0, tg.estDistance)
        } else if (newSignal != side && old == side) {
            // 회전 지점 전에 꺼 버렸다면 유지하지 않은 것으로 본다.
            if (tg.estDistance > SIGNAL_OFF_GRACE_M) tg.onSinceDist = null
        }
        if (newSignal != Signal.OFF && newSignal != side) tg.wrongSide = true
    }

    fun onTbt(kind: ManeuverKind?, distanceM: Double, place: String, t: Long) {
        val key = "${kind?.name}|$place"
        if (key == lastPassedKey) return
        lastPassedKey = null
        val cur = target
        if (cur != null && (cur.kind != kind || cur.place != place)) {
            // 안내가 다음 지점으로 넘어갔다: 직전 지점에 거의 도달했으면 통과 확인 단계로, 아니면(경로 변경) 버린다.
            if (cur.kind != null && cur.opened && cur.estDistance <= SWITCH_REACHED_M) moveToPending(cur, t)
            target = null
        }
        val tg = target
        if (tg == null) {
            target = Target(kind, place, distanceM)
        } else {
            tg.tbtDistance = distanceM
            tg.traveledSinceTbt = 0.0
        }
        checkOpenAndPass(t)
    }

    fun onLocation(newLat: Double, newLon: Double, headingDeg: Double, speed: Double, stepM: Double, t: Long) {
        lat = newLat; lon = newLon; heading = headingDeg; speedKmh = speed
        if (!moving) return

        target?.let { it.traveledSinceTbt += stepM }
        pending?.let { p ->
            p.traveledAfter += stepM
            tryConfirm(p, t)
        }
        checkOpenAndPass(t)

        // 내비 없는 회전 감지
        headingHist.addLast(HSample(t, headingDeg, speed, stepM))
        while (headingHist.isNotEmpty() && t - headingHist.first().t > FB_WINDOW_MS) headingHist.removeFirst()
        if (!navigationCovers()) detectFallbackTurn(t)
    }

    fun onTick(t: Long) {
        // 통과 후 오래 확인이 안 되면 정리한다.
        val p = pending ?: return
        if (t - p.passedAt > 30_000L) {
            if (p.t.kind?.headingCheck == false) judge(p.t, t) // 분기류는 통과만으로 판정
            pending = null
        }
    }

    // ---------------------------------------------------------------- 내비 기반 판정

    private fun openWindow(kind: ManeuverKind): Double =
        if (kind.highwayExit || speedKmh >= HIGHWAY_SPEED_KMH) OPEN_WINDOW_HIGHWAY_M else OPEN_WINDOW_GENERAL_M

    private fun checkOpenAndPass(t: Long) {
        val tg = target ?: return
        val kind = tg.kind ?: return
        if (!moving) return
        val est = tg.estDistance
        if (!tg.opened && est <= openWindow(kind)) {
            tg.opened = true
            tg.legal = if (kind.highwayExit || speedKmh >= HIGHWAY_SPEED_KMH) LegalDistance.HIGHWAY_M else LegalDistance.GENERAL_M
            tg.openHeading = heading
            tg.openedLat = lat; tg.openedLon = lon
            if (signal == kind.side.signal) tg.onSinceDist = est
            else if (signal != Signal.OFF) tg.wrongSide = true
        }
        if (!tg.opened) return

        val alertAt = if (tg.legal >= LegalDistance.HIGHWAY_M) PRE_ALERT_HIGHWAY_M else PRE_ALERT_GENERAL_M
        if (preAlertEnabled && !tg.preAlerted && tg.onSinceDist == null && est in 0.0..alertAt) {
            tg.preAlerted = true
            onPreAlert(kind.side, tg.place)
        }

        if (est <= 0.0) {
            moveToPending(tg, t)
            target = null
        }
    }

    private fun moveToPending(tg: Target, t: Long) {
        lastPassedKey = "${tg.kind?.name}|${tg.place}"
        // 이전 대기 건이 있으면 먼저 정리
        pending?.let { if (it.t.kind?.headingCheck == false) judge(it.t, t) }
        pending = Pending(tg, t)
        tryConfirm(pending!!, t)
    }

    private fun tryConfirm(p: Pending, t: Long) {
        val kind = p.t.kind ?: run { pending = null; return }
        if (!kind.headingCheck) {
            if (p.traveledAfter >= NON_TURN_CONFIRM_AFTER_M) { judge(p.t, t); pending = null }
            return
        }
        val h0 = p.t.openHeading
        val h1 = heading
        if (h0 == null || h1 == null) { judge(p.t, t); pending = null; return }
        val d = Geo.headingDelta(h0, h1)
        val directional = if (kind.side == Side.RIGHT) d else -d
        if (directional >= TURN_CONFIRM_DEG) {
            judge(p.t, t); pending = null
        } else if (p.traveledAfter >= TURN_ABORT_AFTER_M) {
            pending = null // 안내대로 돌지 않았다(직진 등) → 판정하지 않는다
        }
    }

    private fun judge(tg: Target, t: Long) {
        val kind = tg.kind ?: return
        // 같은 회전을 '내비 없는 회전 감지'가 한 번 더 세지 않도록 기록을 비운다.
        headingHist.clear()
        lastFallbackAt = t
        val on = tg.onSinceDist
        val verdict = when {
            on != null && on >= tg.legal - 0.5 -> Verdict.ON_TIME
            on != null -> Verdict.LATE
            tg.wrongSide -> Verdict.WRONG_SIDE
            else -> Verdict.MISSED
        }
        onResult(
            ManeuverResult(
                timeMs = t,
                kind = kind,
                place = tg.place,
                verdict = verdict,
                signalDistanceM = on?.let { (it * 10).roundToInt() / 10.0 },
                legalDistanceM = tg.legal,
                viaNavigation = true,
                lat = if (storeSpots) tg.openedLat?.round3() else null,
                lon = if (storeSpots) tg.openedLon?.round3() else null,
            )
        )
    }

    private fun navigationCovers(): Boolean {
        val tg = target
        if (pending != null) return true
        if (tg?.kind != null && tg.estDistance <= OPEN_WINDOW_HIGHWAY_M + 50) return true
        return false
    }

    // ---------------------------------------------------------------- 내비 없는 회전

    private fun detectFallbackTurn(t: Long) {
        if (t - lastFallbackAt < FB_COOLDOWN_MS) return
        if (headingHist.size < 4) return
        val first = headingHist.first()
        val now = headingHist.last()
        val d = Geo.headingDelta(first.heading, now.heading)
        if (abs(d) < FB_TURN_DEG) return
        val avg = headingHist.map { it.speed }.average()
        val peak = headingHist.maxOf { it.speed }
        val traveled = headingHist.sumOf { it.step }
        if (avg > FB_MAX_AVG_SPEED || peak < FB_MIN_PEAK_SPEED || traveled < 15.0 || now.speed < 3.0) return

        val side = if (d > 0) Side.RIGHT else Side.LEFT
        val deviationStart = headingHist.firstOrNull { Geo.absHeadingDelta(first.heading, it.heading) >= 15.0 }?.t ?: first.t
        val verdict = fallbackVerdict(side.signal, deviationStart, t)
        lastFallbackAt = t
        headingHist.clear()
        onResult(
            ManeuverResult(
                timeMs = t,
                kind = if (side == Side.RIGHT) ManeuverKind.RIGHT_TURN else ManeuverKind.LEFT_TURN,
                place = "",
                verdict = verdict,
                signalDistanceM = null,
                legalDistanceM = LegalDistance.GENERAL_M,
                viaNavigation = false,
                lat = if (storeSpots) lat?.round3() else null,
                lon = if (storeSpots) lon?.round3() else null,
            )
        )
    }

    /** 방향을 틀기 시작한 시점 2초 전에 이미 켜져 있었으면 제때, 그 뒤에 켰으면 늦게, 없으면 미점등. */
    private fun fallbackVerdict(side: Signal, deviationStart: Long, now: Long): Verdict {
        val early = deviationStart - 2_000L
        if (signalAt(early) == side) return Verdict.ON_TIME
        val anyAfter = signalLog.any { (time, s) -> s == side && time in early..now } || signalAt(early) == side
        return if (anyAfter) Verdict.LATE else Verdict.MISSED
    }

    private fun signalAt(time: Long): Signal {
        var s = Signal.OFF
        for ((t, v) in signalLog) { if (t <= time) s = v else break }
        return s
    }
}

internal fun Double.round3(): Double = (this * 1000).roundToInt() / 1000.0
