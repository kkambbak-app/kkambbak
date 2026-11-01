package app.kkambbak

import android.content.ComponentName
import org.robolectric.Shadows.shadowOf

/**
 * Robolectric은 설치되지 않은 서비스에도 bindService를 '연결됨'으로 흉내 내며 ComponentName을 null로 넘긴다.
 * 실제 기기와 같도록(Pleos 서비스가 없으면 연결 실패) Pleos 서비스 바인딩을 막고 앱을 띄운다.
 */
class TestApp : KkambbakApplication() {
    override fun onCreate() {
        val app = shadowOf(this)
        app.setUnbindServiceCallsOnServiceDisconnected(false) // 실제 기기는 unbind 때 onServiceDisconnected를 부르지 않는다
        listOf(
            "ai.pleos.playground.vehicle.VEHICLE_START",
            "ai.pleos.playground.intent.action.NAVI_START",
            "ai.pleos.playground.caas.intent.action.SPEECH_START",
            "ai.pleos.playground.service.location.fused.FUSED_LOCATION_START",
        ).forEach { app.declareActionUnbindable(it) }
        app.declareComponentUnbindable(ComponentName("ai.pleos.playground.service.location.fused", "ai.pleos.playground.service.location.fused.FusedLocationService"))
        super.onCreate()
    }
}
