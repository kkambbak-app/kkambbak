package app.kkambbak

import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kkambbak.core.Verdict
import app.kkambbak.ui.Tab
import app.kkambbak.ui.UiAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * 앱 전체 흐름 스모크 테스트(Pleos 차량 서비스가 없는 환경).
 * SDK 연결이 실패해도 앱이 죽지 않고, 시연 주행 → 주차 브리핑 → 사고 기록까지 이어지는지 확인한다.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestApp::class)
class AppSmokeTest {

    private fun idle(minutes: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(minutes))
    private fun idleSec(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))

    @Test
    fun demoDriveThenCrashFlow() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val c = ApplicationProvider.getApplicationContext<KkambbakApplication>().controller
            idle(1)

            c.onAction(UiAction.SelectProfile("시연 운전자"))
            var s = c.state.value
            assertEquals("시연 운전자", s.profileName)
            assertTrue("시연 기록이 들어가 있어야 한다", s.weekly.judged > 0)

            c.onAction(UiAction.StartDemoDrive)
            idleSec(20)
            assertTrue("시연 중에는 주행 화면", c.state.value.driving)
            assertNotNull("시연 관찰 패널", c.state.value.demo)
            idle(5)
            s = c.state.value
            assertFalse("주차하면 주행 화면이 아니다", s.driving)
            assertEquals(6, s.todayResults.size)
            assertEquals(4, s.todayResults.count { it.verdict == Verdict.ON_TIME })
            assertNotNull(s.briefing)
            assertTrue(s.briefing!!.startsWith("회전 6번 중 4번은 제때 켰어요"))

            c.onAction(UiAction.StartPerfectDemo)
            idle(6)
            s = c.state.value
            assertTrue(s.briefing!!.startsWith("이번 주행 회전 3번, 모두 제때 켰어요"))

            c.onAction(UiAction.StartCrashDemo)
            idle(3)
            s = c.state.value
            assertTrue("급정거 후 기록 제안", s.suddenStopPrompt)
            assertFalse("정차 상태에서는 안내 패널을 보여 준다", s.driving)
            c.onAction(UiAction.AcceptSuddenStop)
            assertNotNull(c.state.value.crashFlow)
            c.onAction(UiAction.StartStatement)
            idle(1)
            assertTrue(c.state.value.crashFlow!!.partial.isNotBlank())
            c.onAction(UiAction.FinishCrashFlow)
            s = c.state.value
            assertEquals(Tab.CRASH, s.tab)
            assertEquals(1, s.crashes.size)
            assertTrue(s.crashes[0].statement.isNotBlank())
            assertTrue(s.crashes[0].facts.first().startsWith("오른쪽 방향지시등"))

            // 리워드: 완벽한 주행 기록이 있으므로 주차 축하등은 열려 있다
            assertTrue(s.rewards.first { it.reward.id == "celebrate" }.unlocked)
            // 설정 저장·SDK 상태 표시
            c.onAction(UiAction.SetPreAlert(true))
            assertTrue(c.state.value.settings.preAlert)
            assertTrue(c.state.value.sources.isNotEmpty())
        }
    }
}
