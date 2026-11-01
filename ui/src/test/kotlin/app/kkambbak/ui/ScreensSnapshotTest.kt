package app.kkambbak.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/**
 * 실제 Compose 화면 코드를 JVM에서 렌더링해 화면 시안·시연 영상 프레임을 만든다.
 * ./gradlew :ui:recordPaparazziDebug → ui/src/test/snapshots/images/
 */
class ScreensSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920, screenHeight = 880, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.1,
    )

    private val base = SampleStates.base()

    @Test fun t01_today() = paparazzi.snapshot { KkambbakApp(base) {} }
    @Test fun t02_driving() = paparazzi.snapshot { KkambbakApp(SampleStates.drivingMidway()) {} }
    @Test fun t03_weekly() = paparazzi.snapshot { KkambbakApp(base.copy(tab = Tab.WEEKLY)) {} }
    @Test fun t04_rewards() = paparazzi.snapshot { KkambbakApp(base.copy(tab = Tab.REWARDS)) {} }
    @Test fun t05_crash() {
        val c = SampleStates.crashRecord()
        paparazzi.snapshot { KkambbakApp(base.copy(tab = Tab.CRASH, crashes = listOf(c))) {} }
    }
    @Test fun t06_settings() = paparazzi.snapshot { KkambbakApp(base.copy(tab = Tab.SETTINGS)) {} }
    @Test fun t07_crashFlow() {
        val c = SampleStates.crashRecord()
        paparazzi.snapshot { KkambbakApp(base.copy(crashFlow = CrashFlow(c, partial = c.statement))) {} }
    }
    @Test fun t08_suddenStop() = paparazzi.snapshot { KkambbakApp(base.copy(suddenStopPrompt = true)) {} }
    @Test fun t09_celebration() = paparazzi.snapshot { KkambbakApp(base.copy(celebration = true)) {} }
}
