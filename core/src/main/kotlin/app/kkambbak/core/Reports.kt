package app.kkambbak.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlin.math.roundToInt

val KST: ZoneId = ZoneId.of("Asia/Seoul")

data class TripSummary(val startMs: Long, val endMs: Long, val results: List<ManeuverResult>) {
    val judged get() = results.size
    val onTime get() = results.count { it.verdict == Verdict.ON_TIME }
    val late get() = results.count { it.verdict == Verdict.LATE }
    val missed get() = results.count { it.verdict == Verdict.MISSED || it.verdict == Verdict.WRONG_SIDE }
    /** 회전 3번 이상을 모두 제때 켠 주행. 리워드 조건에 쓴다. */
    val perfect get() = judged >= 3 && onTime == judged
}

/** 비교 기준: 국토교통부·한국교통안전공단 교통문화지수(2024년 조사) 방향지시등 점등률. */
object Benchmarks {
    const val NATIONAL_SIGNAL_RATE = 75.59
    const val NATIONAL_SIGNAL_LABEL = "전국 평균 75.6% (2024 교통문화지수)"
}

/** 주차 후 10초 브리핑 문장. 감시가 아니라 기록 전달이 되도록, 잘한 것부터 말한다. */
object Briefing {
    fun trip(s: TripSummary, newlyUnlocked: List<String> = emptyList()): String {
        val n = s.judged
        val sb = StringBuilder()
        if (n == 0) {
            sb.append("이번 주행에서는 판정할 회전이 없었어요.")
        } else if (s.onTime == n) {
            sb.append("이번 주행 회전 ${n}번, 모두 제때 켰어요.")
        } else {
            sb.append("회전 ${n}번 중 ${s.onTime}번은 제때 켰어요. ")
            val parts = mutableListOf<String>()
            if (s.late > 0) parts += "늦게 켠 게 ${s.late}번"
            if (s.missed > 0) parts += "안 켠 게 ${s.missed}번"
            sb.append(parts.joinToString(", ")).append(" 있었어요.")
            worst(s)?.let { sb.append(" ").append(it) }
        }
        if (newlyUnlocked.isNotEmpty()) sb.append(" 새 리워드가 열렸어요: ").append(newlyUnlocked.joinToString(", ")).append(".")
        return sb.toString().trim()
    }

    private fun worst(s: TripSummary): String? {
        val missed = s.results.firstOrNull { it.verdict == Verdict.MISSED || it.verdict == Verdict.WRONG_SIDE }
        if (missed != null) {
            val where = placeLabel(missed)
            return if (missed.verdict == Verdict.WRONG_SIDE) "$where 반대쪽 지시등이 켜져 있었어요."
            else "$where 깜빡이가 없었어요."
        }
        val late = s.results.filter { it.verdict == Verdict.LATE }.minByOrNull { it.signalDistanceM ?: 0.0 } ?: return null
        val d = late.signalDistanceM?.roundToInt()
        val subject = if (late.place.isBlank()) late.kind.label else "${late.place} ${late.kind.label}"
        return if (d != null) "$subject${topic(subject)} ${d}미터 앞에서야 켰어요." else "$subject${topic(subject)} 조금 늦게 켰어요."
    }

    /** 받침 유무에 따른 보조사 은/는. */
    fun topic(word: String): String {
        val c = word.trim().lastOrNull() ?: return "는"
        if (c !in '가'..'힣') return "는"
        return if ((c - '가') % 28 != 0) "은" else "는"
    }

    private fun placeLabel(r: ManeuverResult): String =
        if (r.place.isBlank()) "${r.kind.label}할 때" else "${r.place} ${r.kind.label}에서"

    fun weekly(w: WeeklyStats): String {
        if (w.judged == 0) return "이번 주에는 아직 판정한 회전이 없어요."
        val rate = w.signalUseRate.roundToInt()
        val legal = w.legalRate.roundToInt()
        val cmp = if (w.signalUseRate >= Benchmarks.NATIONAL_SIGNAL_RATE) "전국 평균보다 높아요" else "전국 평균보다 조금 낮아요"
        return "이번 주 회전 ${w.judged}번, 켠 비율은 ${rate}퍼센트로 $cmp. 법정 거리 앞에서 켠 비율은 ${legal}퍼센트예요."
    }
}

data class DayStat(val date: LocalDate, val judged: Int, val onTime: Int, val late: Int, val missed: Int) {
    val legalRate: Double get() = if (judged == 0) 0.0 else onTime * 100.0 / judged
    val signalUseRate: Double get() = if (judged == 0) 0.0 else (onTime + late) * 100.0 / judged
}

data class WeeklyStats(
    val days: List<DayStat>,
    val judged: Int,
    val onTime: Int,
    val late: Int,
    val missed: Int,
    val topMissedPlaces: List<Pair<String, Int>>,
) {
    /** 켠 비율(늦게 포함). 교통문화지수 '점등률'과 비교할 수 있는 값. */
    val signalUseRate: Double get() = if (judged == 0) 0.0 else (onTime + late) * 100.0 / judged
    /** 법정 거리 앞에서 켠 비율. */
    val legalRate: Double get() = if (judged == 0) 0.0 else onTime * 100.0 / judged
}

object Weekly {
    fun compute(results: List<ManeuverResult>, nowMs: Long): WeeklyStats {
        val today = Instant.ofEpochMilli(nowMs).atZone(KST).toLocalDate()
        val from = today.minusDays(6)
        val inRange = results.filter {
            val d = Instant.ofEpochMilli(it.timeMs).atZone(KST).toLocalDate()
            !d.isBefore(from) && !d.isAfter(today)
        }
        val days = (0..6).map { i ->
            val d = from.plusDays(i.toLong())
            val rs = inRange.filter { Instant.ofEpochMilli(it.timeMs).atZone(KST).toLocalDate() == d }
            DayStat(d, rs.size, rs.count { it.verdict == Verdict.ON_TIME }, rs.count { it.verdict == Verdict.LATE },
                rs.count { it.verdict == Verdict.MISSED || it.verdict == Verdict.WRONG_SIDE })
        }
        val missedPlaces = inRange
            .filter { it.verdict != Verdict.ON_TIME && it.place.isNotBlank() }
            .groupingBy { "${it.place} ${it.kind.label}" }.eachCount()
            .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }
        return WeeklyStats(
            days = days,
            judged = inRange.size,
            onTime = inRange.count { it.verdict == Verdict.ON_TIME },
            late = inRange.count { it.verdict == Verdict.LATE },
            missed = inRange.count { it.verdict == Verdict.MISSED || it.verdict == Verdict.WRONG_SIDE },
            topMissedPlaces = missedPlaces,
        )
    }

    /** ISO 주 단위 법정거리 준수율. 리워드 판정에 쓴다. key = 연도*100+주차 */
    fun byIsoWeek(results: List<ManeuverResult>): Map<Int, Pair<Int, Int>> =
        results.groupBy {
            val d = Instant.ofEpochMilli(it.timeMs).atZone(KST).toLocalDate()
            d.get(IsoFields.WEEK_BASED_YEAR) * 100 + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        }.mapValues { (_, rs) -> rs.size to rs.count { it.verdict == Verdict.ON_TIME } }
}

enum class VoiceIntent { CRASH_RECORD, TODAY, WEEKLY, REPEAT, UNKNOWN }

object VoiceIntents {
    fun parse(text: String): VoiceIntent {
        val t = text.replace(" ", "")
        return when {
            "사고" in t -> VoiceIntent.CRASH_RECORD
            "이번주" in t || "주간" in t || "일주일" in t -> VoiceIntent.WEEKLY
            "다시" in t -> VoiceIntent.REPEAT
            "오늘" in t || "어땠" in t || "깜빡이" in t -> VoiceIntent.TODAY
            else -> VoiceIntent.UNKNOWN
        }
    }
}
