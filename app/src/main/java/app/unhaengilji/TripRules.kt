package app.unhaengilji

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/** 서식의 용도 구분. 개인은 서식에 칸이 없어 주행거리에만 남는다. */
enum class Purpose(val label: String) {
  COMMUTE("출·퇴근용"),
  BUSINESS("일반 업무용"),
  PERSONAL("개인"),
  UNCLASSIFIED("미분류"),
}

data class Trip(
  val id: Long = 0,
  val date: String,
  val dept: String = "",
  val name: String = "",
  val startKm: Double,
  val endKm: Double,
  val purpose: Purpose = Purpose.UNCLASSIFIED,
  val suggested: Purpose? = null,
  val note: String = "",
  /** 기록 서비스가 돌지 않은 사이에 늘어난 적산거리로 만든 행. */
  val missing: Boolean = false,
) {
  val distanceKm: Double get() = roundKm(endKm - startKm)
}

fun roundKm(v: Double): Double = round(v * 10.0) / 10.0

fun formatKm(v: Double): String {
  val r = roundKm(v)
  return if (r == round(r)) String.format(Locale.US, "%,.0f", r) else String.format(Locale.US, "%,.1f", r)
}

/** 운행 분할. 기어가 P 를 벗어나면 시작, P 로 돌아오면 끝. 움직이지 않은 왕복은 버린다. */
object TripRules {
  const val BASELINE_WON = 15_000_000L

  sealed interface GearResult {
    data class Started(val startKm: Double) : GearResult
    data class Finished(val startKm: Double, val endKm: Double) : GearResult
    data object Discarded : GearResult
    data object None : GearResult
  }

  /**
   * @param wasParked 직전 기어가 P 였는가
   * @param isParked 이번 기어가 P 인가
   * @param openStartKm 진행 중인 운행의 시작 적산거리, 없으면 null
   */
  fun onGear(wasParked: Boolean, isParked: Boolean, openStartKm: Double?, odoKm: Double): GearResult = when {
    wasParked && !isParked && openStartKm == null -> GearResult.Started(roundKm(odoKm))
    isParked && openStartKm != null ->
      if (roundKm(odoKm - openStartKm) <= 0.0) GearResult.Discarded
      else GearResult.Finished(openStartKm, roundKm(odoKm))
    else -> GearResult.None
  }

  /** 기록 서비스가 다시 떴을 때 마지막 저장값과 현재값의 차이. 0 이하면 빠진 운행이 없다. */
  fun gapKm(lastSavedKm: Double?, currentKm: Double): Double {
    if (lastSavedKm == null) return 0.0
    val gap = roundKm(currentKm - lastSavedKm)
    return if (gap > 0.0) gap else 0.0
  }

  /** 알림이 값을 안 바꿔도 3~4초마다 오므로 같은 값은 저장하지 않는다. */
  fun odoChanged(lastKm: Double?, newKm: Double): Boolean = lastKm == null || roundKm(lastKm) != roundKm(newKm)

  // 한국 공휴일(대체공휴일 제외). 평일 판단에만 쓰고, 틀려도 미리 선택 하나가 달라질 뿐이다.
  private val FIXED = setOf("01-01", "03-01", "05-05", "06-06", "08-15", "10-03", "10-09", "12-25")
  private val LUNAR = setOf(
    "2026-02-16", "2026-02-17", "2026-02-18", "2026-05-24", "2026-09-24", "2026-09-25", "2026-09-26",
    "2027-02-06", "2027-02-07", "2027-02-08", "2027-05-13", "2027-09-14", "2027-09-15", "2027-09-16",
  )

  fun isWorkday(d: LocalDate): Boolean {
    if (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) return false
    val iso = d.toString()
    return iso !in LUNAR && iso.substring(5) !in FIXED
  }

  /** 평일에 즐겨찾기의 집·회사로 도착한 운행만 출·퇴근용을 미리 고른다. 그 밖에는 미리 선택 없음. */
  fun suggestPurpose(d: LocalDate, arrivedAtHomeOrOffice: Boolean): Purpose? =
    if (arrivedAtHomeOrOffice && isWorkday(d)) Purpose.COMMUTE else null

  /** 「개인」 운행은 비고를 비운다. 사용자가 끈 경우에도 비운다. */
  fun noteFor(purpose: Purpose, destination: String, noteEnabled: Boolean): String =
    if (purpose == Purpose.PERSONAL || !noteEnabled) "" else destination

  data class Totals(
    val totalKm: Double,
    val commuteKm: Double,
    val businessKm: Double,
    val personalKm: Double,
    val unclassifiedKm: Double,
  ) {
    /** 서식의 업무용 사용거리 = 출·퇴근용 + 일반 업무용. */
    val businessUseKm: Double get() = roundKm(commuteKm + businessKm)
    /** 업무사용비율. 총주행 거리가 0 이면 0. */
    val ratio: Double get() = if (totalKm <= 0.0) 0.0 else businessUseKm / totalKm
  }

  fun totals(trips: List<Trip>): Totals {
    fun sum(p: Purpose) = roundKm(trips.filter { it.purpose == p }.sumOf { it.distanceKm })
    return Totals(
      totalKm = roundKm(trips.sumOf { it.distanceKm }),
      commuteKm = sum(Purpose.COMMUTE),
      businessKm = sum(Purpose.BUSINESS),
      personalKm = sum(Purpose.PERSONAL),
      unclassifiedKm = sum(Purpose.UNCLASSIFIED),
    )
  }

  /** 앞 운행의 주행 후 값과 다음 운행의 주행 전 값이 어긋난 자리의 수. 0 이면 이어진다. */
  fun continuityBreaks(tripsInOrder: List<Trip>): Int =
    tripsInOrder.zipWithNext().count { (a, b) -> roundKm(b.startKm) != roundKm(a.endKm) }

  enum class Baseline { NO_COST, BELOW_LINE, RECORD_RAISES, RECORD_NOT_RAISES }

  data class BaselineResult(val kind: Baseline, val allowedWithRecord: Long, val allowedWithout: Long)

  /**
   * 법인세법 시행령 제50조의2 제7항. 기록이 없으면 관련비용 가운데 기준선을 넘는 부분이 비용으로 인정되지 않는다.
   * 기준선 이하이면 기록 없이도 업무사용비율 100%. 부동산임대업 특례·월할은 반영하지 않으므로 참고값이다.
   */
  fun baseline(annualCostWon: Long?, ratio: Double): BaselineResult {
    if (annualCostWon == null || annualCostWon <= 0) return BaselineResult(Baseline.NO_COST, 0, 0)
    if (annualCostWon <= BASELINE_WON) return BaselineResult(Baseline.BELOW_LINE, annualCostWon, annualCostWon)
    val with = (annualCostWon * ratio).toLong()
    val kind = if (with > BASELINE_WON) Baseline.RECORD_RAISES else Baseline.RECORD_NOT_RAISES
    return BaselineResult(kind, with, BASELINE_WON)
  }

  data class Driver(val dept: String, val name: String, val seatPos: Int?)

  /** 등록 위치와 같으면(허용 오차 안) 그 사용자를 제안. 둘 이상이 맞으면 제안하지 않는다. */
  fun suggestDriver(drivers: List<Driver>, seatPos: Int?, tolerance: Int = 0): Driver? {
    if (seatPos == null) return null
    val hits = drivers.filter { it.seatPos != null && abs(it.seatPos - seatPos) <= tolerance }
    return hits.singleOrNull()
  }

  /** 서식 순서의 CSV 한 달치. */
  fun toCsvLines(trips: List<Trip>): List<String> {
    fun q(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    return trips.map { t ->
      val commute = if (t.purpose == Purpose.COMMUTE) formatPlain(t.distanceKm) else ""
      val business = if (t.purpose == Purpose.BUSINESS) formatPlain(t.distanceKm) else ""
      listOf(t.date, q(t.dept), q(t.name), formatPlain(t.startKm), formatPlain(t.endKm), formatPlain(t.distanceKm), commute, business, q(t.note))
        .joinToString(",")
    }
  }

  private fun formatPlain(v: Double): String = formatKm(v).replace(",", "")

  /**
   * QR 한 장에 담을 만큼 줄 단위로 나눈다. 각 장은 "UHJ1|장번호/전체|머리|" 뒤에 CSV 가 온다.
   * 한 줄이 한 장을 넘으면 그 줄만 단독으로 담고, QR 생성 쪽 한계에 맡기지 않도록 잘라 낸다.
   */
  fun toQrPages(header: String, lines: List<String>, maxBytes: Int = 1400): List<String> {
    val pages = mutableListOf<MutableList<String>>()
    var cur = mutableListOf<String>()
    var size = 0
    val room = maxBytes - header.toByteArray().size - 20
    for (raw in lines) {
      val line = clipBytes(raw, room)
      val n = line.toByteArray().size + 1
      if (cur.isNotEmpty() && size + n > room) {
        pages += cur; cur = mutableListOf(); size = 0
      }
      cur += line; size += n
    }
    if (cur.isNotEmpty() || pages.isEmpty()) pages += cur
    return pages.mapIndexed { i, p -> "UHJ1|${i + 1}/${pages.size}|$header|\n" + p.joinToString("\n") }
  }

  private fun clipBytes(s: String, max: Int): String {
    if (s.toByteArray().size <= max) return s
    var out = s
    while (out.toByteArray().size > max) out = out.dropLast(1)
    return out
  }
}
