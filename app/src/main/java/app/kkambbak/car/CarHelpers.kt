package app.kkambbak.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import app.kkambbak.core.DriveEvent

/**
 * AAOS CarUxRestrictions 구독. Pleos Connect는 속도·기어·브레이크로 주행 제한을 켜고 끄며,
 * 앱은 전달받은 제한 상태대로만 동작한다(지역 코드를 직접 해석하지 않는다).
 */
class UxRestrictionsMonitor(private val context: Context, private val onChange: (Boolean) -> Unit) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    var available = false
        private set

    fun start() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return
        try {
            val c = Car.createCar(context)
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            manager = m
            available = true
            onChange(m.currentCarUxRestrictions.isRequiresDistractionOptimization)
            m.registerListener { r: CarUxRestrictions -> onChange(r.isRequiresDistractionOptimization) }
        } catch (t: Throwable) {
            Log.w("kkambbak.ux", "CarUxRestrictions 사용 불가", t)
            available = false
        }
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
    }
}

/**
 * 리워드: 실내 무드등 색. Vehicle SDK Light API의 앰비언트 라이트 색을 주차 상태에서만 바꾼다.
 * 색 값의 형식은 차량이 지원하는 값 목록을 따르며, 지원하지 않는 차량에서는 화면 연출로 대신한다.
 */
class AmbientLight(private val vehicle: () -> Vehicle?) {
    private val main = Handler(Looper.getMainLooper())
    private val seat = SeatArea(SeatValue.ROW_1_LEFT)

    fun apply(argb: Long, done: (Boolean) -> Unit) {
        val light = runCatching { vehicle()?.getLight() }.getOrNull() ?: return done(false)
        try {
            light.setAmbientLightBrightnessColor(seat, (argb and 0xFFFFFF).toInt(), { _, _ -> main.post { done(true) } }, { main.post { done(false) } })
        } catch (t: Throwable) { done(false) }
    }

    /** 완벽한 주행 뒤 주차하면 3초간 앰버색으로 켰다가 원래 색으로 돌린다. */
    fun celebrate(argb: Long, done: (Boolean) -> Unit) {
        val light = runCatching { vehicle()?.getLight() }.getOrNull() ?: return done(false)
        try {
            light.getAmbientLightBrightnessColor(seat, { prev ->
                apply(argb) { ok ->
                    done(ok)
                    if (ok && prev != null) main.postDelayed({ apply(prev.toLong() and 0xFFFFFF) {} }, 3_000L)
                }
            }, { main.post { done(false) } })
        } catch (t: Throwable) { done(false) }
    }
}

/** 시연 스크립트를 실제 시간 흐름에 맞춰(배속 가능) 엔진에 넣는다. */
class DemoPlayer {
    private val main = Handler(Looper.getMainLooper())
    private var token = Any()
    var running = false
        private set

    fun play(events: List<DriveEvent>, speed: Double, onEvent: (DriveEvent, Float) -> Unit, onDone: () -> Unit) {
        stop()
        if (events.isEmpty()) return
        running = true
        val t0 = events.first().timeMs
        val total = (events.last().timeMs - t0).coerceAtLeast(1)
        val myToken = Any().also { token = it }
        events.forEach { e ->
            val delay = ((e.timeMs - t0) / speed).toLong()
            main.postAtTime({ if (token === myToken) onEvent(e, (e.timeMs - t0) / total.toFloat()) }, myToken, android.os.SystemClock.uptimeMillis() + delay)
        }
        main.postAtTime({ if (token === myToken) { running = false; onDone() } }, myToken, android.os.SystemClock.uptimeMillis() + (total / speed).toLong() + 50)
    }

    fun stop() {
        main.removeCallbacksAndMessages(token)
        token = Any()
        running = false
    }
}
