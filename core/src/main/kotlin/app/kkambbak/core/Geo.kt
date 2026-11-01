package app.kkambbak.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_R = 6_371_000.0

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_R * atan2(sqrt(a), sqrt(1 - a))
    }

    /** 진행방향(0=북, 시계방향) 기준으로 distance 만큼 이동한 좌표. 시연 스크립트에서 쓴다. */
    fun move(lat: Double, lon: Double, headingDeg: Double, distanceM: Double): Pair<Double, Double> {
        val h = Math.toRadians(headingDeg)
        val dLat = distanceM * cos(h) / EARTH_R
        val dLon = distanceM * sin(h) / (EARTH_R * cos(Math.toRadians(lat)))
        return Pair(lat + Math.toDegrees(dLat), lon + Math.toDegrees(dLon))
    }

    /** -180..180 범위의 부호 있는 헤딩 차이. 양수 = 오른쪽(시계방향)으로 돌았다. */
    fun headingDelta(fromDeg: Double, toDeg: Double): Double {
        var d = (toDeg - fromDeg) % 360.0
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    fun absHeadingDelta(fromDeg: Double, toDeg: Double): Double = abs(headingDelta(fromDeg, toDeg))
}

/**
 * FusedLocation 의 LocationData 에는 속도가 없어서, 연속된 위치로 속도를 추정한다.
 * 지수평활로 GPS 튐을 줄인다.
 */
class SpeedEstimator(private val alpha: Double = 0.5) {
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastT: Long? = null
    var speedKmh: Double = 0.0
        private set
    /** 평활하지 않은 순간 속도. 급정거 감지에 쓴다. */
    var rawKmh: Double = 0.0
        private set
    /** 직전 위치 이후 이동 거리(m). */
    var lastStepM: Double = 0.0
        private set

    fun update(lat: Double, lon: Double, t: Long) {
        val pLat = lastLat; val pLon = lastLon; val pT = lastT
        lastStepM = 0.0
        if (pLat != null && pLon != null && pT != null && t > pT) {
            val d = Geo.distanceM(pLat, pLon, lat, lon)
            val dt = (t - pT) / 1000.0
            val inst = d / dt * 3.6
            if (inst < 250) { // 비정상 점프는 버린다
                rawKmh = inst
                speedKmh = if (inst < 1.0) 0.0 else alpha * inst + (1 - alpha) * speedKmh // 멈추면 바로 0
                lastStepM = d
            }
        }
        lastLat = lat; lastLon = lon; lastT = t
    }

    fun reset() { lastLat = null; lastLon = null; lastT = null; speedKmh = 0.0; rawKmh = 0.0; lastStepM = 0.0 }
}
