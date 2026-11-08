package app.unhaengilji

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 설정과 기록 서비스의 마지막 상태. 기록 자체는 TripDb 에 있다. */
class Settings(context: Context) {
  private val p = context.getSharedPreferences("unhaengilji", Context.MODE_PRIVATE)

  var consented: Boolean
    get() = p.getBoolean("consented", false)
    set(v) = p.edit().putBoolean("consented", v).apply()

  var carModel: String
    get() = p.getString("carModel", "") ?: ""
    set(v) = p.edit().putString("carModel", v).apply()

  var plate: String
    get() = p.getString("plate", "") ?: ""
    set(v) = p.edit().putString("plate", v).apply()

  var vin: String
    get() = p.getString("vin", "") ?: ""
    set(v) = p.edit().putString("vin", v).apply()

  var noteEnabled: Boolean
    get() = p.getBoolean("noteEnabled", true)
    set(v) = p.edit().putBoolean("noteEnabled", v).apply()

  var annualCostWon: Long?
    get() = if (p.contains("cost")) p.getLong("cost", 0) else null
    set(v) {
      if (v == null) p.edit().remove("cost").apply() else p.edit().putLong("cost", v).apply()
    }

  /** 운행기록이 이어지는 기준값: 마지막 운행의 주행 후 계기판 거리. */
  var anchorKm: Double?
    get() = if (p.contains("anchor")) p.getFloat("anchor", 0f).toDouble() else null
    set(v) {
      if (v == null) p.edit().remove("anchor").apply() else p.edit().putFloat("anchor", v.toFloat()).apply()
    }

  /** 진행 중인 운행의 주행 전 계기판 거리. 서비스가 죽어도 이어지도록 저장한다. */
  var openStartKm: Double?
    get() = if (p.contains("open")) p.getFloat("open", 0f).toDouble() else null
    set(v) {
      if (v == null) p.edit().remove("open").apply() else p.edit().putFloat("open", v.toFloat()).apply()
    }

  var wasParked: Boolean
    get() = p.getBoolean("wasParked", true)
    set(v) = p.edit().putBoolean("wasParked", v).apply()

  var lastSeenKm: Double?
    get() = if (p.contains("seen")) p.getFloat("seen", 0f).toDouble() else null
    set(v) {
      if (v != null) p.edit().putFloat("seen", v.toFloat()).apply()
    }

  /** 사용자 선택이 고정되어 있으면 그 번호, 없으면 -1. */
  var currentDriver: Int
    get() = p.getInt("currentDriver", -1)
    set(v) = p.edit().putInt("currentDriver", v).apply()

  var drivers: List<TripRules.Driver>
    get() {
      val raw = p.getString("drivers", "[]") ?: "[]"
      val a = JSONArray(raw)
      return List(a.length()) { i ->
        val o = a.getJSONObject(i)
        TripRules.Driver(o.getString("dept"), o.getString("name"), if (o.isNull("seat")) null else o.getInt("seat"))
      }
    }
    set(v) {
      val a = JSONArray()
      v.forEach {
        a.put(JSONObject().put("dept", it.dept).put("name", it.name).put("seat", it.seatPos ?: JSONObject.NULL))
      }
      p.edit().putString("drivers", a.toString()).apply()
    }
}
