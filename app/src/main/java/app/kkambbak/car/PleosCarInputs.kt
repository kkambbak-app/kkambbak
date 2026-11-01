package app.kkambbak.car

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import app.kkambbak.core.DriveEvent
import app.kkambbak.core.GearState
import app.kkambbak.core.ManeuverKind
import app.kkambbak.core.Signal
import app.kkambbak.ui.SourceStatus

/**
 * Pleos Connect SDK → 깜빡 엔진 이벤트 변환기.
 * - Vehicle SDK: TurnSignal, Gear, Brake, Light(하향등), Wiper
 * - NaviHelper SDK: 다음 회전 안내(TBT)와 남은 거리
 * - FusedLocation SDK: 위치·진행방향(속도는 엔진이 위치 변화로 추정)
 * 각 SDK는 독립적으로 실패할 수 있으므로, 붙은 것만으로 동작하고 상태를 화면에 알린다.
 */
class PleosCarInputs(
    private val context: Context,
    private val emit: (DriveEvent) -> Unit,
    private val onStatus: (List<SourceStatus>) -> Unit,
) {
    private val tag = "kkambbak.pleos"
    private val main = Handler(Looper.getMainLooper())
    private val status = linkedMapOf(
        "Vehicle SDK" to SourceStatus("Vehicle SDK", false, "연결 중"),
        "NaviHelper SDK" to SourceStatus("NaviHelper SDK", false, "연결 중"),
        "FusedLocation SDK" to SourceStatus("FusedLocation SDK", false, "연결 중"),
    )

    var vehicle: Vehicle? = null
        private set
    private var navi: NaviHelper? = null
    private var fused: FusedLocation? = null
    private var routing = false

    private fun now() = System.currentTimeMillis()
    private fun post(e: DriveEvent) = main.post { emit(e) }
    private fun setStatus(name: String, ok: Boolean, detail: String) {
        status[name] = SourceStatus(name, ok, detail)
        main.post { onStatus(status.values.toList()) }
    }

    fun start() {
        startVehicle()
        startNavi()
        startLocation()
    }

    fun stop() {
        main.removeCallbacks(tbtPoll)
        runCatching {
            vehicle?.apply {
                getTurnSignal().unregisterTurnSignalState(turnListener)
                getGear().unregisterGearSelection(gearListener)
                getBrake().unregisterBrakePedalStatus(brakeListener)
                getLight().unregisterLowBeamLightState(lowBeamListener)
                getWiper().unregisterWiperState(frontWindow, wiperListener)
                release()
            }
        }
        runCatching { navi?.removeListener(naviListener); navi?.release() }
        runCatching { fused?.unregisterFusedLocationCallback(locationListener); fused?.release() }
    }

    // ------------------------------------------------------------ Vehicle SDK

    private val frontWindow = WindowArea(WindowValue.FRONT_WINDSHIELD)

    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) { post(DriveEvent.SignalChanged(value.toSignal(), now())) }
        override fun onFailed(e: Exception) = fail("방향지시등", e)
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) { post(DriveEvent.GearChanged(gear.toGear(), now())) }
        override fun onFailed(e: Exception) = fail("기어", e)
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) { post(DriveEvent.BrakeChanged(status == true, now())) }
        override fun onFailed(e: Exception) = fail("브레이크", e)
    }
    private val lowBeamListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) { post(DriveEvent.LowBeamChanged(state == LightState.ON, now())) }
        override fun onFailed(e: Exception) = fail("전조등", e)
    }
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) { post(DriveEvent.WiperChanged(state == WiperState.ON, now())) }
        override fun onFailed(e: Exception) = fail("와이퍼", e)
    }

    private fun fail(what: String, e: Exception?) {
        Log.w(tag, "$what 수신 실패", e)
        setStatus("Vehicle SDK", false, "$what 수신 실패: ${e?.javaClass?.simpleName}")
    }

    private fun startVehicle(attempt: Int = 0) {
        try {
            val v = vehicle ?: Vehicle(context).also { it.initialize(); vehicle = it }
            // 서비스 연결은 비동기라, 초기값 조회가 실패하면 잠시 뒤 다시 시도한다.
            v.getTurnSignal().getCurrentTurnSignalState(
                { value -> post(DriveEvent.SignalChanged(value.toSignal(), now())); registerVehicle(v) },
                { e ->
                    if (attempt < 5) main.postDelayed({ startVehicle(attempt + 1) }, 1_000L)
                    else setStatus("Vehicle SDK", false, "차량 서비스에 연결하지 못함(${e.javaClass.simpleName})")
                },
            )
        } catch (t: Throwable) {
            Log.w(tag, "Vehicle SDK 초기화 실패", t)
            setStatus("Vehicle SDK", false, "이 기기는 Pleos 차량 서비스를 지원하지 않음")
        }
    }

    private var vehicleRegistered = false
    private fun registerVehicle(v: Vehicle) {
        if (vehicleRegistered) return
        vehicleRegistered = true
        v.getTurnSignal().registerTurnSignalState(turnListener)
        v.getGear().registerGearSelection(gearListener)
        v.getGear().getCurrentGearSelectionState({ post(DriveEvent.GearChanged(it.toGear(), now())) }, { fail("기어", it) })
        v.getBrake().registerBrakePedalStatus(brakeListener)
        v.getLight().registerLowBeamLightState(lowBeamListener)
        runCatching { v.getWiper().registerWiperState(frontWindow, wiperListener) }
        setStatus("Vehicle SDK", true, "방향지시등 · 기어 · 브레이크 · 전조등 · 와이퍼")
    }

    // ------------------------------------------------------------ NaviHelper SDK

    private val naviListener = object : NaviHelperEventListener {
        override fun onTBTInfo(tbtList: List<TBTInfo>) {
            val first = tbtList.firstOrNull() ?: return
            val kind = first.type.toManeuver()
            post(DriveEvent.TbtUpdated(kind, first.distance, first.description.orEmpty(), now()))
        }
        override fun onNavigationStatus(navigationStatus: NavigationStatus) {
            routing = navigationStatus == NavigationStatus.ROUTE_DRIVE
            setStatus("NaviHelper SDK", true, if (routing) "경로 안내 중 · 회전 안내 수신" else "경로 안내 대기")
            if (routing) schedulePoll() else main.post { emit(DriveEvent.TbtUpdated(null, 0.0, "", now())) }
        }
        override fun onRouteStarted(routeStartInfo: ai.pleos.playground.navi.data.RouteStartInfo) { routing = true; schedulePoll() }
        override fun onRouteEnded() { routing = false }
        override fun onRouteCancelled() { routing = false }
        override fun onError(errorCode: ai.pleos.playground.navi.constants.NaviErrorCode) {
            Log.w(tag, "NaviHelper 오류 $errorCode")
        }
    }

    /** TBT가 이벤트로 오지 않는 내비 구현도 있어, 경로 안내 중에는 1초마다 요청한다. */
    private val tbtPoll = object : Runnable {
        override fun run() {
            if (!routing) return
            runCatching { navi?.getTBTInfo() }
            main.postDelayed(this, 1_000L)
        }
    }

    private fun schedulePoll() { main.removeCallbacks(tbtPoll); main.post(tbtPoll) }

    private fun startNavi() {
        try {
            val n = NaviHelper(context.applicationContext)
            navi = n
            n.initialize(
                {
                    n.addListener(naviListener)
                    setStatus("NaviHelper SDK", true, "연결됨 · 경로 안내 대기")
                    runCatching { n.getRouteStateInfo() }
                },
                { t -> setStatus("NaviHelper SDK", false, "내비 연결 실패(${t.javaClass.simpleName}) · 내비 없이 회전 감지") },
            )
        } catch (t: Throwable) {
            setStatus("NaviHelper SDK", false, "지원하지 않는 기기 · 내비 없이 회전 감지")
        }
    }

    // ------------------------------------------------------------ FusedLocation SDK

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            post(DriveEvent.LocationUpdated(location.latitude, location.longitude, location.heading.toDouble(), location.timestampMillis.takeIf { it > 0 } ?: now()))
        }
        override fun onError(exception: Exception) {
            setStatus("FusedLocation SDK", false, "위치 수신 실패(${exception.javaClass.simpleName})")
        }
    }

    fun startLocation() {
        try {
            val f = fused ?: FusedLocation(context.applicationContext).also { fused = it }
            if (!f.isSupported()) { setStatus("FusedLocation SDK", false, "지원하지 않는 기기"); return }
            f.initialize()
            f.registerFusedLocationCallback(locationListener)
            setStatus("FusedLocation SDK", true, "위치 · 진행방향")
        } catch (t: SecurityException) {
            setStatus("FusedLocation SDK", false, "위치 권한 필요")
        } catch (t: Throwable) {
            setStatus("FusedLocation SDK", false, "지원하지 않는 기기")
        }
    }
}

private fun TurnSignalValue?.toSignal() = when (this) {
    TurnSignalValue.LEFT -> Signal.LEFT
    TurnSignalValue.RIGHT -> Signal.RIGHT
    else -> Signal.OFF
}

private fun VehicleGear?.toGear() = when (this) {
    VehicleGear.GEAR_PARK -> GearState.PARK
    VehicleGear.GEAR_REVERSE -> GearState.REVERSE
    VehicleGear.GEAR_NEUTRAL -> GearState.NEUTRAL
    VehicleGear.GEAR_UNKNOWN, null -> GearState.UNKNOWN
    else -> GearState.DRIVE // D, 1~9단
}

/** 내비 TBT 종류 → 판정 대상. 직진·톨게이트·터널·도착·회전교차로 등은 판정하지 않는다. */
internal fun TurnType.toManeuver(): ManeuverKind? = when (this) {
    TurnType.TURN_LEFT, TurnType.TURN_SHARP_L -> ManeuverKind.LEFT_TURN
    TurnType.TURN_RIGHT, TurnType.TURN_SHARP_R -> ManeuverKind.RIGHT_TURN
    TurnType.TURN_UTURN -> ManeuverKind.U_TURN
    TurnType.TURN_KEEP_L, TurnType.TURN_ENTER_UNDER_L, TurnType.TURN_AVOID_UNDER_L,
    TurnType.TURN_ENTER_OVER_L, TurnType.TURN_AVOID_OVER_L -> ManeuverKind.KEEP_LEFT
    TurnType.TURN_KEEP_R, TurnType.TURN_ENTER_UNDER_R, TurnType.TURN_AVOID_UNDER_R,
    TurnType.TURN_ENTER_OVER_R, TurnType.TURN_AVOID_OVER_R -> ManeuverKind.KEEP_RIGHT
    TurnType.TURN_ENTER_IC_L -> ManeuverKind.RAMP_LEFT
    TurnType.TURN_ENTER_IC_R -> ManeuverKind.RAMP_RIGHT
    TurnType.TURN_EXIT_IC_L -> ManeuverKind.HIGHWAY_EXIT_LEFT
    TurnType.TURN_EXIT_IC_R -> ManeuverKind.HIGHWAY_EXIT_RIGHT
    else -> null
}
