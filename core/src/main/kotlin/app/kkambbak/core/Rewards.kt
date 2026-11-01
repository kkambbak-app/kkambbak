package app.kkambbak.core

/**
 * 깜빡 리워드: 신호 습관을 지키면 '차에서만 줄 수 있는 보상'이 열린다.
 * 진행 상황은 주차 상태에서만 보여 주고, 주행 중에는 아무것도 띄우지 않는다.
 */
enum class RewardKind(val korean: String) {
    PARK_CELEBRATION("주차 연출"),
    AMBIENT_COLOR("무드등 색"),
    VOICE("브리핑 목소리"),
    THEME("앱 테마"),
}

data class Reward(
    val id: String,
    val title: String,
    val description: String,
    val kind: RewardKind,
    val requirement: String,
    /** 무드등 색 리워드의 색(ARGB). */
    val colorArgb: Long? = null,
)

data class RewardInputs(
    val perfectTrips: Int,
    val bestStreak: Int,
    val currentStreak: Int,
    /** 회전 20번 이상이면서 제때 비율 90% 이상인 주(ISO 주)의 최대 연속 수. */
    val bestConsecutiveGoodWeeks: Int,
    val goodWeeks: Int,
)

data class RewardProgress(val reward: Reward, val unlocked: Boolean, val progress: Float, val progressText: String)

object RewardBook {
    const val GOOD_WEEK_RATE = 90.0
    const val GOOD_WEEK_MIN = 20

    val all = listOf(
        Reward("celebrate", "주차 축하등",
            "모든 회전을 제때 켠 주행을 마치고 P단에 놓으면, 실내 무드등이 3초간 앰버색으로 인사해요.",
            RewardKind.PARK_CELEBRATION, "회전 3번 이상을 전부 제때 켠 주행 1번", 0xFFFFB020),
        Reward("amber", "무드등 · 시그널 앰버",
            "방향지시등과 같은 앰버색을 내 차 무드등 색으로 쓸 수 있어요.",
            RewardKind.AMBIENT_COLOR, "연속 제때 점등 20번", 0xFFFFB020),
        Reward("teal", "무드등 · 새벽 청록",
            "한 주를 깔끔하게 보낸 운전자에게 열리는 차분한 청록색이에요.",
            RewardKind.AMBIENT_COLOR, "한 주 동안 회전 20번 이상, 제때 90% 이상", 0xFF1EC8B4),
        Reward("voice", "브리핑 목소리 · 두 번째",
            "주차 브리핑을 다른 목소리로 들을 수 있어요(차량이 지원하는 목소리 중에서).",
            RewardKind.VOICE, "연속 제때 점등 50번"),
        Reward("theme", "앱 테마 · 야간 시그널",
            "깜빡 화면을 야간 운전용 앰버 테마로 바꿀 수 있어요.",
            RewardKind.THEME, "제때 90% 이상인 주 2주 연속"),
    )

    fun inputs(results: List<ManeuverResult>, trips: List<TripSummary>): RewardInputs {
        var best = 0; var cur = 0
        for (r in results.sortedBy { it.timeMs }) {
            if (r.verdict == Verdict.ON_TIME) { cur++; if (cur > best) best = cur } else cur = 0
        }
        val weeks = Weekly.byIsoWeek(results).toSortedMap()
        var goodRun = 0; var bestRun = 0; var good = 0; var prevKey: Int? = null
        for ((key, v) in weeks) {
            val (n, ok) = v
            val isGood = n >= GOOD_WEEK_MIN && ok * 100.0 / n >= GOOD_WEEK_RATE
            if (isGood) {
                good++
                goodRun = if (prevKey != null && isNextWeek(prevKey, key) && goodRun > 0) goodRun + 1 else 1
                if (goodRun > bestRun) bestRun = goodRun
            } else goodRun = 0
            prevKey = key
        }
        return RewardInputs(trips.count { it.perfect }, best, cur, bestRun, good)
    }

    private fun isNextWeek(a: Int, b: Int): Boolean {
        val (ay, aw) = a / 100 to a % 100
        val (by, bw) = b / 100 to b % 100
        return (ay == by && bw == aw + 1) || (by == ay + 1 && bw == 1 && aw >= 52)
    }

    fun evaluate(i: RewardInputs): List<RewardProgress> = all.map { r ->
        when (r.id) {
            "celebrate" -> prog(r, i.perfectTrips, 1, "완벽한 주행 ${i.perfectTrips.coerceAtMost(1)}/1")
            "amber" -> prog(r, i.bestStreak, 20, "최고 연속 ${i.bestStreak.coerceAtMost(20)}/20 · 지금 ${i.currentStreak}")
            "teal" -> prog(r, i.goodWeeks, 1, "조건을 채운 주 ${i.goodWeeks.coerceAtMost(1)}/1")
            "voice" -> prog(r, i.bestStreak, 50, "최고 연속 ${i.bestStreak.coerceAtMost(50)}/50")
            "theme" -> prog(r, i.bestConsecutiveGoodWeeks, 2, "연속 ${i.bestConsecutiveGoodWeeks.coerceAtMost(2)}/2주")
            else -> RewardProgress(r, false, 0f, "")
        }
    }

    private fun prog(r: Reward, have: Int, need: Int, text: String) =
        RewardProgress(r, have >= need, (have.toFloat() / need).coerceIn(0f, 1f), text)

    /** 이번 주행으로 새로 열린 리워드 제목들. */
    fun newlyUnlocked(before: List<RewardProgress>, after: List<RewardProgress>): List<String> {
        val was = before.filter { it.unlocked }.map { it.reward.id }.toSet()
        return after.filter { it.unlocked && it.reward.id !in was }.map { it.reward.title }
    }
}
