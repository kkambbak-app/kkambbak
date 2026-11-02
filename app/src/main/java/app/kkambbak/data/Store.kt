package app.kkambbak.data

import android.content.Context
import app.kkambbak.core.CrashRecord
import app.kkambbak.core.CrashTrigger
import app.kkambbak.core.GearState
import app.kkambbak.core.ManeuverKind
import app.kkambbak.core.ManeuverResult
import app.kkambbak.core.Signal
import app.kkambbak.core.Snapshot
import app.kkambbak.core.TripSummary
import app.kkambbak.core.Verdict
import app.kkambbak.ui.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 운전자(프로필)별로 판정 결과·주행 요약·사고 기록을 앱 내부 저장소에만 둔다.
 * 서버 전송 없음. 주행 궤적은 애초에 저장 대상이 아니다.
 */
class Store(context: Context) {
    data class Profile(val id: String, val name: String)

    val profiles = listOf(Profile("driver1", "운전자 1"), Profile("driver2", "운전자 2"), Profile("demo", "시연 운전자"))

    private val root = File(context.filesDir, "profiles")
    private val prefs = context.getSharedPreferences("kkambbak", Context.MODE_PRIVATE)

    var currentProfileId: String
        get() = prefs.getString("profile", "driver1") ?: "driver1"
        set(v) { prefs.edit().putString("profile", v).apply() }

    fun profile(id: String) = profiles.first { it.id == id }

    private fun dir(p: String) = File(root, p).apply { mkdirs() }

    // ------------------------------------------------------------ 판정 결과

    fun appendResult(p: String, r: ManeuverResult) = File(dir(p), "results.jsonl").appendText(resultJson(r).toString() + "\n")

    fun results(p: String): List<ManeuverResult> = readLines(File(dir(p), "results.jsonl")).mapNotNull { runCatching { result(JSONObject(it)) }.getOrNull() }

    fun appendTrip(p: String, t: TripSummary) {
        val o = JSONObject().put("start", t.startMs).put("end", t.endMs).put("results", JSONArray(t.results.map { resultJson(it) }))
        File(dir(p), "trips.jsonl").appendText(o.toString() + "\n")
    }

    fun trips(p: String): List<TripSummary> = readLines(File(dir(p), "trips.jsonl")).mapNotNull { line ->
        runCatching {
            val o = JSONObject(line)
            val arr = o.getJSONArray("results")
            TripSummary(o.getLong("start"), o.getLong("end"), (0 until arr.length()).map { result(arr.getJSONObject(it)) })
        }.getOrNull()
    }

    // ------------------------------------------------------------ 사고 기록

    fun saveCrash(p: String, c: CrashRecord) {
        val o = JSONObject()
            .put("id", c.id).put("createdAt", c.createdAtMs).put("trigger", c.trigger.name)
            .put("facts", JSONArray(c.facts)).put("hash", c.hash)
            .put("statement", c.statement).put("statementHash", c.statementHash)
            .put("snapshots", JSONArray(c.snapshots.map { s ->
                JSONObject().put("t", s.timeMs).put("sig", s.signal.name).put("brk", s.brake).put("low", s.lowBeam)
                    .put("wip", s.wiper).put("gear", s.gear.name).put("v", s.speedKmh)
                    .apply { s.lat?.let { put("lat", it) }; s.lon?.let { put("lon", it) } }
            }))
        File(dir(p), "crashes").apply { mkdirs() }.resolve("${c.id}.json").writeText(o.toString())
    }

    fun crashes(p: String): List<CrashRecord> =
        (File(dir(p), "crashes").listFiles()?.toList() ?: emptyList()).mapNotNull { f ->
            runCatching {
                val o = JSONObject(f.readText())
                val sa = o.getJSONArray("snapshots")
                val fa = o.getJSONArray("facts")
                CrashRecord(
                    id = o.getString("id"), createdAtMs = o.getLong("createdAt"), trigger = CrashTrigger.valueOf(o.getString("trigger")),
                    snapshots = (0 until sa.length()).map { i ->
                        val s = sa.getJSONObject(i)
                        Snapshot(s.getLong("t"), Signal.valueOf(s.getString("sig")), s.getBoolean("brk"), s.getBoolean("low"), s.getBoolean("wip"),
                            GearState.valueOf(s.getString("gear")), s.getDouble("v"),
                            if (s.has("lat")) s.getDouble("lat") else null, if (s.has("lon")) s.getDouble("lon") else null)
                    },
                    facts = (0 until fa.length()).map { fa.getString(it) },
                    hash = o.getString("hash"), statement = o.optString("statement"), statementHash = o.optString("statementHash"),
                )
            }.getOrNull()
        }

    // ------------------------------------------------------------ 설정

    fun settings(p: String) = SettingsState(
        preAlert = prefs.getBoolean("$p.preAlert", false),
        storeSpots = prefs.getBoolean("$p.storeSpots", p == "demo"), // 실제 운전자는 기본 꺼짐, 시연 운전자는 켜짐
        autoBriefing = prefs.getBoolean("$p.autoBriefing", true),
        altVoice = prefs.getBoolean("$p.altVoice", false),
        nightTheme = prefs.getBoolean("$p.nightTheme", false),
        ambientRewardId = prefs.getString("$p.ambient", null),
    )

    fun saveSettings(p: String, s: SettingsState) {
        prefs.edit()
            .putBoolean("$p.preAlert", s.preAlert).putBoolean("$p.storeSpots", s.storeSpots)
            .putBoolean("$p.autoBriefing", s.autoBriefing).putBoolean("$p.altVoice", s.altVoice)
            .putBoolean("$p.nightTheme", s.nightTheme).putString("$p.ambient", s.ambientRewardId)
            .apply()
    }

    fun clear(p: String) {
        File(root, p).deleteRecursively()
    }

    fun isEmpty(p: String) = !File(dir(p), "results.jsonl").exists()

    // ------------------------------------------------------------ JSON

    private fun readLines(f: File): List<String> = if (f.exists()) f.readLines().filter { it.isNotBlank() } else emptyList()

    private fun resultJson(r: ManeuverResult) = JSONObject()
        .put("t", r.timeMs).put("kind", r.kind.name).put("place", r.place).put("verdict", r.verdict.name)
        .put("legal", r.legalDistanceM).put("nav", r.viaNavigation)
        .apply { r.signalDistanceM?.let { put("dist", it) }; r.lat?.let { put("lat", it) }; r.lon?.let { put("lon", it) } }

    private fun result(o: JSONObject) = ManeuverResult(
        timeMs = o.getLong("t"), kind = ManeuverKind.valueOf(o.getString("kind")), place = o.optString("place"),
        verdict = Verdict.valueOf(o.getString("verdict")),
        signalDistanceM = if (o.has("dist")) o.getDouble("dist") else null,
        legalDistanceM = o.getDouble("legal"), viaNavigation = o.optBoolean("nav", true),
        lat = if (o.has("lat")) o.getDouble("lat") else null, lon = if (o.has("lon")) o.getDouble("lon") else null,
    )
}
