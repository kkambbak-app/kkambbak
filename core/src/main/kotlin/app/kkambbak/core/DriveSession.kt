package app.kkambbak.core

/**
 * 한 운전자의 주행을 처리하는 조립 지점.
 * 입력 이벤트 → (속도 추정, 판정 엔진, 사고 버퍼, 급정거 감지, 주행 시작·종료) → 리스너.
 * Pleos SDK 어댑터와 시연 스크립트가 똑같이 이 클래스에 이벤트를 넣는다.
 */
class DriveSession(private val listener: Listener) {

    interface Listener {
        fun onManeuver(result: ManeuverResult) {}
        fun onTripStarted(startMs: Long) {}
        fun onTripEnded(summary: TripSummary) {}
        fun onPreAlert(side: Side, place: String) {}
        fun onSuddenStop(timeMs: Long) {}
        fun onStateChanged(state: LiveState) {}
    }

    data class LiveState(
        val signal: Signal = Signal.OFF,
        val gear: GearState = GearState.UNKNOWN,
        val brake: Boolean = false,
        val lowBeam: Boolean = false,
        val wiper: Boolean = false,
        val speedKmh: Double = 0.0,
        val tripActive: Boolean = false,
        val tripJudged: Int = 0,
        val nextManeuver: String? = null,
        val nextDistanceM: Double? = null,
        val nextLegalM: Double = LegalDistance.GENERAL_M,
        val lastTimeMs: Long = 0L,
    )

    private val tripResults = mutableListOf<ManeuverResult>()
    private var tripStart: Long? = null

    private val judge = SignalJudge(
        onResult = { r ->
            if (tripStart != null) tripResults += r
            listener.onManeuver(r)
        },
        onPreAlert = { side, place -> listener.onPreAlert(side, place) },
    )
    private val speed = SpeedEstimator()
    private val stopDetector = SuddenStopDetector()
    val crashBuffer = CrashBuffer()

    var state = LiveState()
        private set
    private var lat: Double? = null
    private var lon: Double? = null

    var preAlertEnabled: Boolean
        get() = judge.preAlertEnabled
        set(v) { judge.preAlertEnabled = v }
    var storeSpots: Boolean
        get() = judge.storeSpots
        set(v) { judge.storeSpots = v }

    fun handle(e: DriveEvent) {
        when (e) {
            is DriveEvent.SignalChanged -> {
                state = state.copy(signal = e.signal)
                judge.onSignal(e.signal, e.timeMs)
            }
            is DriveEvent.GearChanged -> onGear(e.gear, e.timeMs)
            is DriveEvent.BrakeChanged -> state = state.copy(brake = e.pressed)
            is DriveEvent.LowBeamChanged -> state = state.copy(lowBeam = e.on)
            is DriveEvent.WiperChanged -> state = state.copy(wiper = e.on)
            is DriveEvent.LocationUpdated -> {
                speed.update(e.lat, e.lon, e.timeMs)
                lat = e.lat; lon = e.lon
                state = state.copy(speedKmh = speed.speedKmh)
                // 기어 정보가 없는 차량을 위한 보조 규칙: 시속 10km를 넘으면 주행 시작으로 본다.
                if (tripStart == null && state.gear == GearState.UNKNOWN && speed.speedKmh > 10) startTrip(e.timeMs)
                judge.onLocation(e.lat, e.lon, e.headingDeg, speed.speedKmh, speed.lastStepM, e.timeMs)
                if (stopDetector.update(speed.rawKmh, e.timeMs)) listener.onSuddenStop(e.timeMs)
            }
            is DriveEvent.TbtUpdated -> {
                state = state.copy(
                    nextManeuver = e.kind?.let { if (e.place.isBlank()) it.label else "${e.place} ${it.label}" },
                    nextDistanceM = if (e.kind != null) e.distanceM else null,
                    nextLegalM = if (e.kind?.highwayExit == true || speed.speedKmh >= SignalJudge.HIGHWAY_SPEED_KMH) LegalDistance.HIGHWAY_M else LegalDistance.GENERAL_M,
                )
                judge.onTbt(e.kind, e.distanceM, e.place, e.timeMs)
            }
            is DriveEvent.Tick -> judge.onTick(e.timeMs)
        }
        state = state.copy(tripActive = tripStart != null, tripJudged = tripResults.size, lastTimeMs = e.timeMs)
        crashBuffer.add(
            Snapshot(e.timeMs, state.signal, state.brake, state.lowBeam, state.wiper, state.gear, speed.speedKmh, lat, lon)
        )
        listener.onStateChanged(state)
    }

    private fun onGear(g: GearState, t: Long) {
        val prev = state.gear
        state = state.copy(gear = g)
        judge.setMoving(g == GearState.DRIVE || g == GearState.NEUTRAL || g == GearState.UNKNOWN)
        val wasMoving = prev != GearState.PARK
        if (g == GearState.PARK) {
            if (tripStart != null) endTrip(t)
        } else if (tripStart == null && (g == GearState.DRIVE || g == GearState.REVERSE || g == GearState.NEUTRAL)) {
            if (!wasMoving || prev == GearState.UNKNOWN) startTrip(t)
        }
    }

    private fun startTrip(t: Long) {
        tripStart = t
        tripResults.clear()
        judge.reset()
        speed.reset()
        listener.onTripStarted(t)
    }

    private fun endTrip(t: Long) {
        val start = tripStart ?: return
        judge.onTick(t + 31_000) // 확인 대기 중인 분기 판정을 마무리
        val summary = TripSummary(start, t, tripResults.toList())
        tripStart = null
        tripResults.clear()
        listener.onTripEnded(summary)
    }

    /** 기어 정보가 없는 차량에서 사용자가 주행 종료를 직접 누를 때. */
    fun endTripNow(t: Long) = endTrip(t)

    fun freezeCrash(trigger: CrashTrigger, t: Long): CrashRecord = crashBuffer.freeze(trigger, t)
}
