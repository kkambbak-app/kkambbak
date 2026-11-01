package app.kkambbak.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/** Pleos Connect의 portrait(앱 영역 한 칸) 창 크기에서도 화면이 깨지지 않는지 확인한다. */
class PortraitSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 960, screenHeight = 880, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.PORTRAIT, softButtons = false,
        ),
        theme = "android:Theme.Material.NoActionBar",
        useDeviceResolution = true,
        snapshotHandler = app.kkambbak.tools.FullResHandler(java.io.File("build/screens-portrait")),
    )
    private val base = SampleStates.base()

    @Test fun p01_today() = paparazzi.snapshot { KkambbakApp(base) {} }
    @Test fun p02_driving() = paparazzi.snapshot { KkambbakApp(SampleStates.drivingMidway()) {} }
    @Test fun p03_weekly() = paparazzi.snapshot { KkambbakApp(base.copy(tab = Tab.WEEKLY)) {} }
}
