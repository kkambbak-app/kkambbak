package app.kkambbak.core

/**
 * 깜빡의 판정 엔진은 안드로이드에 의존하지 않는 순수 Kotlin이다.
 * Pleos SDK 어댑터(실차)와 시연 스크립트(데모)가 같은 이벤트를 넣고, 같은 결과를 받는다.
 */

enum class Signal { OFF, LEFT, RIGHT }

enum class Side { LEFT, RIGHT;
    val signal: Signal get() = if (this == LEFT) Signal.LEFT else Signal.RIGHT
    val korean: String get() = if (this == LEFT) "왼쪽" else "오른쪽"
}

enum class GearState { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }

/**
 * 판정 대상이 되는 진로 변경 종류.
 * [headingCheck] 가 참이면 실제로 방향을 크게 틀었는지(헤딩 변화)를 확인한 뒤에만 판정한다.
 * 회전교차로는 진입·진출 신호 규칙이 달라 MVP 판정 대상에서 뺀다.
 */
enum class ManeuverKind(val side: Side, val label: String, val headingCheck: Boolean, val highwayExit: Boolean = false) {
    LEFT_TURN(Side.LEFT, "좌회전", true),
    RIGHT_TURN(Side.RIGHT, "우회전", true),
    U_TURN(Side.LEFT, "유턴", true),
    KEEP_LEFT(Side.LEFT, "왼쪽 방향", false),
    KEEP_RIGHT(Side.RIGHT, "오른쪽 방향", false),
    RAMP_LEFT(Side.LEFT, "왼쪽 진출입로", false),
    RAMP_RIGHT(Side.RIGHT, "오른쪽 진출입로", false),
    HIGHWAY_EXIT_LEFT(Side.LEFT, "왼쪽 고속도로 출구", false, highwayExit = true),
    HIGHWAY_EXIT_RIGHT(Side.RIGHT, "오른쪽 고속도로 출구", false, highwayExit = true),
}

enum class Verdict(val korean: String) {
    ON_TIME("제때"),       // 법정 거리 이상 앞에서 켜고 회전까지 유지
    LATE("늦게"),          // 켰지만 법정 거리 안쪽에서 켬
    MISSED("미점등"),      // 켜지 않음
    WRONG_SIDE("반대쪽"),  // 반대 방향 지시등만 켬
}

/** 도로교통법 시행령 별표 2: 일반도로 30m, 고속도로 100m 이상 앞에서 신호. */
object LegalDistance {
    const val GENERAL_M = 30.0
    const val HIGHWAY_M = 100.0
}

/** 한 번의 진로 변경에 대한 판정 결과. 저장되는 유일한 주행 데이터다(궤적은 저장하지 않는다). */
data class ManeuverResult(
    val timeMs: Long,
    val kind: ManeuverKind,
    val place: String,
    val verdict: Verdict,
    /** 지시등을 켠 시점의 남은 거리(m). 내비 안내 없이 감지한 회전이면 null. */
    val signalDistanceM: Double?,
    val legalDistanceM: Double,
    val viaNavigation: Boolean,
    /** 사용자가 '자주 놓치는 곳' 기능을 켰을 때만 채워진다(소수 셋째 자리 반올림). */
    val lat: Double? = null,
    val lon: Double? = null,
)

/** 사고 기록용 차량 신호 스냅샷. */
data class Snapshot(
    val timeMs: Long,
    val signal: Signal,
    val brake: Boolean,
    val lowBeam: Boolean,
    val wiper: Boolean,
    val gear: GearState,
    val speedKmh: Double,
    val lat: Double?,
    val lon: Double?,
)

/** 엔진으로 들어오는 입력. 모든 이벤트는 발생 시각(ms)을 가진다. */
sealed class DriveEvent {
    abstract val timeMs: Long

    data class SignalChanged(val signal: Signal, override val timeMs: Long) : DriveEvent()
    data class GearChanged(val gear: GearState, override val timeMs: Long) : DriveEvent()
    data class BrakeChanged(val pressed: Boolean, override val timeMs: Long) : DriveEvent()
    data class LowBeamChanged(val on: Boolean, override val timeMs: Long) : DriveEvent()
    data class WiperChanged(val on: Boolean, override val timeMs: Long) : DriveEvent()
    data class LocationUpdated(val lat: Double, val lon: Double, val headingDeg: Double, override val timeMs: Long) : DriveEvent()

    /** 내비가 알려준 다음 안내 지점. 판정 대상이 아니면 kind = null. */
    data class TbtUpdated(val kind: ManeuverKind?, val distanceM: Double, val place: String, override val timeMs: Long) : DriveEvent()
    data class Tick(override val timeMs: Long) : DriveEvent()
}
