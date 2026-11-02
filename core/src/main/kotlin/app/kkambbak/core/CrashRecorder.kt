package app.kkambbak.core

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

enum class CrashTrigger(val korean: String) {
    VOICE("음성 명령"),
    BUTTON("화면 버튼"),
    SUDDEN_STOP("급정거 감지 후 확인"),
}

/**
 * 사고 기록지. 직전 60초의 차량 신호를 고정한 사실 기록이며, 과실을 판단하지 않는다.
 * [hash] 는 스냅샷들을 순서대로 이어 만든 SHA-256 체인이라, 나중에 한 줄이라도 바뀌면 값이 달라진다.
 */
data class CrashRecord(
    val id: String,
    val createdAtMs: Long,
    val trigger: CrashTrigger,
    val snapshots: List<Snapshot>,
    val facts: List<String>,
    val hash: String,
    val statement: String = "",
    val statementHash: String = "",
) {
    fun withStatement(text: String): CrashRecord =
        copy(statement = text, statementHash = Hashing.sha256Hex(hash + "|" + text))
}

object Hashing {
    fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun chain(snapshots: List<Snapshot>): String {
        var h = "kkambbak-v1"
        for (s in snapshots) h = sha256Hex(h + "|" + canonical(s))
        return h
    }

    fun canonical(s: Snapshot): String =
        listOf(s.timeMs, s.signal, s.brake, s.lowBeam, s.wiper, s.gear, "%.1f".format(Locale.US, s.speedKmh),
            s.lat?.let { "%.5f".format(Locale.US, it) } ?: "-", s.lon?.let { "%.5f".format(Locale.US, it) } ?: "-")
            .joinToString(",")
}

/** 직전 60초 링버퍼. 메모리에만 있고, 사고 기록을 남길 때만 고정·저장된다. */
class CrashBuffer(private val windowMs: Long = 60_000L) {
    private val buf = ArrayDeque<Snapshot>()

    fun add(s: Snapshot) {
        val last = buf.lastOrNull()
        // 같은 상태가 250ms 안에 반복되면 건너뛴다(신호 변화는 항상 남긴다).
        if (last != null && s.timeMs - last.timeMs < 250 && sameState(last, s)) return
        buf.addLast(s)
        while (buf.isNotEmpty() && s.timeMs - buf.first().timeMs > windowMs) buf.removeFirst()
    }

    fun snapshot(): List<Snapshot> = buf.toList()
    fun clear() = buf.clear()

    private fun sameState(a: Snapshot, b: Snapshot) =
        a.signal == b.signal && a.brake == b.brake && a.lowBeam == b.lowBeam && a.wiper == b.wiper && a.gear == b.gear

    fun freeze(trigger: CrashTrigger, atMs: Long): CrashRecord {
        val snaps = buf.filter { atMs - it.timeMs <= windowMs }
        return CrashRecord(
            id = "crash-$atMs",
            createdAtMs = atMs,
            trigger = trigger,
            snapshots = snaps,
            facts = CrashFacts.describe(snaps, atMs),
            hash = Hashing.chain(snaps),
        )
    }
}

/** 기록에서 '사실'만 문장으로 뽑는다. 해석·과실 판단은 하지 않는다. */
object CrashFacts {
    fun describe(snaps: List<Snapshot>, atMs: Long): List<String> {
        if (snaps.isEmpty()) return listOf("기록된 차량 신호가 없습니다.")
        val out = mutableListOf<String>()
        val last = snaps.last()

        // 방향지시등: 마지막으로 켜진 시점부터 유지됐는지
        val lastSignalOn = lastContinuousOn(snaps) { it.signal != Signal.OFF }
        if (lastSignalOn != null) {
            val side = if (lastSignalOn.second.signal == Signal.LEFT) "왼쪽" else "오른쪽"
            out += "${side} 방향지시등: 기록 시점 ${secs(atMs - lastSignalOn.first)}초 전부터 켜져 있었음"
        } else {
            val everOn = snaps.lastOrNull { it.signal != Signal.OFF }
            out += if (everOn != null) "방향지시등: 기록 시점 ${secs(atMs - everOn.timeMs)}초 전에 꺼짐"
            else "방향지시등: 직전 60초 동안 켜지지 않음"
        }

        val brakeOn = lastContinuousOn(snaps) { it.brake }
        out += if (brakeOn != null) "브레이크: 기록 시점 ${secs(atMs - brakeOn.first)}초 전부터 밟음" else "브레이크: 기록 시점에 밟지 않음"

        val peak = snaps.filter { atMs - it.timeMs <= 10_000 }.maxOfOrNull { it.speedKmh } ?: 0.0
        out += "속도(위치로 추정): 직전 10초 최고 약 ${peak.roundToInt()} km/h, 기록 시점 약 ${last.speedKmh.roundToInt()} km/h"
        out += "전조등: " + (if (last.lowBeam) "켜짐" else "꺼짐") + " · 와이퍼: " + (if (last.wiper) "작동" else "정지")
        if (last.lat != null && last.lon != null) out += "위치: %.5f, %.5f".format(Locale.US, last.lat, last.lon)
        out += "기록 시각: " + kst(atMs)
        return out
    }

    /** 마지막 스냅샷에서 거꾸로 올라가며 조건이 계속 참이었던 구간의 시작 시각. */
    private fun lastContinuousOn(snaps: List<Snapshot>, cond: (Snapshot) -> Boolean): Pair<Long, Snapshot>? {
        if (!cond(snaps.last())) return null
        var start = snaps.last()
        for (i in snaps.indices.reversed()) {
            if (cond(snaps[i])) start = snaps[i] else break
        }
        return start.timeMs to start
    }

    private fun secs(ms: Long): String = "%.1f".format(Locale.US, ms / 1000.0)

    fun kst(ms: Long): String = SimpleDateFormat("yyyy. M. d. HH:mm:ss", Locale.KOREA)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }.format(Date(ms))
}

/** 시속 40km 이상에서 3초 안에 거의 멈추면 '급정거'로 보고 사고 기록을 제안한다(자동 저장하지 않음). */
class SuddenStopDetector(
    private val fromKmh: Double = 40.0,
    private val toKmh: Double = 5.0,
    private val withinMs: Long = 3_000L,
    private val cooldownMs: Long = 60_000L,
) {
    private val hist = ArrayDeque<Pair<Long, Double>>()
    private var lastFired = Long.MIN_VALUE / 2

    fun update(speedKmh: Double, t: Long): Boolean {
        hist.addLast(t to speedKmh)
        while (hist.isNotEmpty() && t - hist.first().first > withinMs) hist.removeFirst()
        if (speedKmh > toKmh) return false
        if (t - lastFired < cooldownMs) return false
        val peak = hist.maxOfOrNull { it.second } ?: 0.0
        if (peak >= fromKmh) { lastFired = t; return true }
        return false
    }
}
