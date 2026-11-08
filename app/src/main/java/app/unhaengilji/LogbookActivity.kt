package app.unhaengilji

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.unhaengilji.Ui.action
import app.unhaengilji.Ui.col
import app.unhaengilji.Ui.dp
import app.unhaengilji.Ui.gapBelow
import app.unhaengilji.Ui.label
import app.unhaengilji.Ui.row
import app.unhaengilji.Ui.weighted
import java.time.LocalDate
import java.util.Locale

/** 운행기록부 화면(landscape). 서식과 같은 표, 연도 합계, 월별 소계, 기준선 참고 계산. */
class LogbookActivity : Activity() {
  private lateinit var db: TripDb
  private lateinit var settings: Settings
  private var month: String = LocalDate.now().toString().substring(0, 7)

  override fun onCreate(s: Bundle?) {
    super.onCreate(s)
    db = TripDb(this)
    settings = Settings(this)
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }
    // 다른 앱에 가려져 있어 정차 화면이 뜨지 못한 운행이 있으면 앱을 열 때 먼저 보여 준다.
    if (s == null && settings.consented && db.unclassified().isNotEmpty()) {
      startActivity(Intent(this, StopCardActivity::class.java))
    }
  }

  override fun onResume() {
    super.onResume()
    if (!settings.consented) {
      startActivity(Intent(this, SettingsActivity::class.java))
      return
    }
    RecorderService.start(this)
    render()
  }

  private fun render() {
    val root = col(24)
    root.setBackgroundColor(Ui.BG)

    val head = row()
    head.weighted(label("운행일지  업무용승용차 운행기록부", 30f, bold = true), 1f)
    head.addView(action("설정", size = 22f) { startActivity(Intent(this, SettingsActivity::class.java)) })
    root.gapBelow(head, 8)
    val car = listOf(settings.carModel, settings.plate).filter { it.isNotEmpty() }.joinToString("  ")
    root.gapBelow(label(if (car.isEmpty()) "차종·자동차등록번호를 설정에서 입력해 주세요" else car, 22f, Ui.MUTED), 16)

    val year = month.substring(0, 4)
    val yt = TripRules.totals(db.byPrefix(year))
    val sum = row()
    sum.weighted(stat("총주행 거리 ($year)", "${formatKm(yt.totalKm)} km"), 1f, 12)
    sum.weighted(stat("업무용 사용거리", "${formatKm(yt.businessUseKm)} km"), 1f, 12)
    sum.weighted(stat("업무사용비율", String.format(Locale.US, "%.1f%%", yt.ratio * 100)), 1f)
    root.gapBelow(sum, 12)
    baselineLine(yt.ratio)?.let { root.gapBelow(it, 16) }

    val nav = row()
    val months = (db.months() + month).distinct().sortedDescending()
    val at = months.indexOf(month)
    nav.addView(action("이전 달", size = 22f) { months.getOrNull(at + 1)?.let { month = it; render() } })
    val title = label(month, 28f, bold = true)
    title.gravity = Gravity.CENTER
    nav.weighted(title, 1f)
    nav.addView(action("다음 달", size = 22f) { months.getOrNull(at - 1)?.let { month = it; render() } })
    root.gapBelow(nav, 10)

    val trips = db.byPrefix(month)
    root.gapBelow(tableHeader(), 2)
    if (trips.isEmpty()) root.gapBelow(label("이 달의 운행이 없습니다", 22f, Ui.MUTED), 8)
    trips.forEach { root.gapBelow(tableRow(it), 2) }

    val mt = TripRules.totals(trips)
    root.gapBelow(
      label(
        "월별 소계  주행 ${formatKm(mt.totalKm)} km · 출·퇴근 ${formatKm(mt.commuteKm)} km · 일반 업무 ${formatKm(mt.businessKm)} km · 개인 ${formatKm(mt.personalKm)} km · 미분류 ${formatKm(mt.unclassifiedKm)} km",
        22f,
        Ui.MUTED,
      ),
      16,
    )
    root.gapBelow(
      action("이 달 기록을 QR 코드로 내보내기", primary = true) {
        startActivity(Intent(this, QrActivity::class.java).putExtra(QrActivity.EXTRA_MONTH, month))
      },
      0,
    )

    setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(root) })
  }

  private fun stat(k: String, v: String): LinearLayout = col(14).apply {
    setBackgroundColor(Ui.PANEL)
    addView(label(k, 20f, Ui.MUTED))
    addView(label(v, 32f, bold = true))
  }

  private fun baselineLine(ratio: Double): TextView? {
    val r = TripRules.baseline(settings.annualCostWon, ratio)
    val msg = when (r.kind) {
      TripRules.Baseline.NO_COST -> return null
      TripRules.Baseline.BELOW_LINE -> "기준선 참고: 관련비용이 연 1,500만 원 이하라 기록 없이도 비율 100%가 적용되는 차"
      TripRules.Baseline.RECORD_RAISES -> "기준선 참고: 기록으로 인정액이 늘어나는 차 (기록 ${won(r.allowedWithRecord)} / 기록 없이 ${won(r.allowedWithout)})"
      TripRules.Baseline.RECORD_NOT_RAISES -> "기준선 참고: 지금 비율로는 기록이 인정액을 늘리지 않는 차 (기록 ${won(r.allowedWithRecord)} / 기록 없이 ${won(r.allowedWithout)})"
    }
    return label("$msg. 참고값이므로 세무 대리인에게 확인해 주세요.", 22f, Ui.WARN)
  }

  private fun won(v: Long) = String.format(Locale.US, "%,d원", v)

  private val weights = floatArrayOf(1.2f, 1.6f, 1.1f, 1.1f, 0.8f, 0.9f, 0.9f, 2.2f)

  private fun tableHeader(): LinearLayout = cells(
    listOf("사용일자", "사용자", "주행 전 계기판", "주행 후 계기판", "주행거리", "출·퇴근용", "일반 업무용", "비고"),
    Ui.MUTED,
    Ui.PANEL,
  )

  private fun tableRow(t: Trip): LinearLayout {
    val who = listOf(t.dept, t.name).filter { it.isNotEmpty() }.joinToString(" ")
    val note = when {
      t.purpose == Purpose.UNCLASSIFIED -> "미분류 (눌러서 분류)"
      t.purpose == Purpose.PERSONAL -> "개인"
      else -> t.note
    }
    val line = cells(
      listOf(
        t.date,
        who,
        formatKm(t.startKm),
        formatKm(t.endKm),
        formatKm(t.distanceKm),
        if (t.purpose == Purpose.COMMUTE) formatKm(t.distanceKm) else "",
        if (t.purpose == Purpose.BUSINESS) formatKm(t.distanceKm) else "",
        if (t.missing) listOf("기록되지 않은 운행", note).filter { it.isNotEmpty() }.joinToString(" · ") else note,
      ),
      if (t.missing) Ui.WARN else Ui.TEXT,
      Ui.BG,
    )
    if (t.purpose == Purpose.UNCLASSIFIED) {
      line.setOnClickListener {
        startActivity(Intent(this, StopCardActivity::class.java).putExtra(StopCardActivity.EXTRA_ID, t.id))
      }
    }
    return line
  }

  private fun cells(values: List<String>, color: Int, bg: Int): LinearLayout = row().apply {
    setBackgroundColor(bg)
    minimumHeight = dp(60)
    values.forEachIndexed { i, v ->
      val c = label(v, 20f, color)
      c.setPadding(dp(6), dp(8), dp(6), dp(8))
      weighted(c, weights[i])
    }
  }
}
