package app.unhaengilji

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import app.unhaengilji.Ui.action
import app.unhaengilji.Ui.col
import app.unhaengilji.Ui.gapBelow
import app.unhaengilji.Ui.input
import app.unhaengilji.Ui.label
import app.unhaengilji.Ui.row
import app.unhaengilji.Ui.weighted

/** 처음 실행 때의 동의와 설정. 키보드 입력은 정차 중에만 가능하다(DO 미선언이라 플랫폼이 차단). */
class SettingsActivity : Activity() {
  private lateinit var settings: Settings
  private lateinit var db: TripDb
  private var vehicle: Vehicle? = null
  private val main = Handler(Looper.getMainLooper())

  override fun onCreate(s: Bundle?) {
    super.onCreate(s)
    settings = Settings(this)
    db = TripDb(this)
    if (settings.consented) render() else renderConsent()
  }

  private fun scroll(root: android.view.View) {
    setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(root) })
  }

  private fun renderConsent() {
    val root = col(32)
    root.gapBelow(label("운행기록 수집 동의", 34f, bold = true), 20)
    listOf(
      "수집 항목: 사용일자, 주행 전·후 계기판 거리, 용도, 사용자(부서·성명), 내비 목적지 이름과 주소, 운전석 위치, 차대번호",
      "목적: 업무용승용차 운행기록부 작성",
      "보유기간: 사용자가 삭제할 때까지, 이 차 안에만 저장. 외부 서버로 보내지 않습니다.",
      "위치 좌표는 저장하지 않습니다. 목적지 글자 기재는 설정에서 끌 수 있고, 「개인」으로 고른 운행의 비고는 비웁니다.",
    ).forEach { root.gapBelow(label(it, 24f), 14) }
    root.gapBelow(
      action("동의하고 시작", primary = true) {
        settings.consented = true
        RecorderService.start(this)
        render()
      },
      12,
    )
    root.gapBelow(action("동의하지 않음") { finishAffinity() }, 0)
    scroll(root)
  }

  private fun render() {
    if (vehicle == null) vehicle = Vehicle(this).also { it.initialize() }
    val root = col(28)
    root.gapBelow(label("설정", 34f, bold = true), 16)

    val model = input("차종", settings.carModel)
    val plate = input("자동차등록번호", settings.plate)
    val cost = input("연 관련비용 추정값(원, 선택)", settings.annualCostWon?.toString() ?: "", number = true)
    root.gapBelow(model, 8)
    root.gapBelow(plate, 8)
    root.gapBelow(cost, 8)
    root.gapBelow(label(if (settings.vin.isEmpty()) "차대번호: 차에서 받지 못함" else "차대번호: ${settings.vin}", 22f, Ui.MUTED), 8)
    root.gapBelow(
      Switch(this).apply {
        text = "내비 목적지 이름과 주소를 비고에 기재"
        textSize = 22f
        setTextColor(Ui.TEXT)
        isChecked = settings.noteEnabled
        minHeight = 120
        setOnCheckedChangeListener { _, on -> settings.noteEnabled = on }
      },
      8,
    )
    root.gapBelow(
      action("저장", primary = true) {
        save(model, plate, cost)
        finish()
      },
      24,
    )

    root.gapBelow(label("사용자", 28f, bold = true), 8)
    val dept = input("부서")
    val name = input("성명")
    val status: TextView = label("", 22f, Ui.MUTED)
    root.gapBelow(dept, 8)
    root.gapBelow(name, 8)
    root.gapBelow(
      action("지금 운전석 위치로 사용자 등록") {
        val d = dept.text.toString().trim()
        val n = name.text.toString().trim()
        if (n.isEmpty()) {
          status.text = "성명을 입력해 주세요"
        } else {
          save(model, plate, cost)
          readSeat { pos ->
            settings.drivers = settings.drivers + TripRules.Driver(d, n, pos)
            render()
            if (pos == null) status.text = "운전석 위치를 읽지 못해 사용자를 손으로 고릅니다"
          }
        }
      },
      8,
    )
    root.gapBelow(status, 8)

    val drivers = settings.drivers
    drivers.forEachIndexed { i, d ->
      val r = row()
      val tag = if (settings.currentDriver == i) "  (현재 사용자)" else ""
      val seat = d.seatPos?.let { "운전석 $it" } ?: "운전석 위치 없음"
      r.weighted(label("${d.dept} ${d.name} · $seat$tag", 22f), 1f, 8)
      r.addView(action("전환", size = 20f) { settings.currentDriver = i; render() })
      r.addView(
        action("기록 삭제", size = 20f) {
          db.deleteByDriver(d.dept, d.name)
          settings.drivers = drivers.filterIndexed { j, _ -> j != i }
          if (settings.currentDriver == i) settings.currentDriver = -1
          render()
        },
      )
      root.gapBelow(r, 8)
    }
    scroll(root)
  }

  private fun save(model: EditText, plate: EditText, cost: EditText) {
    settings.carModel = model.text.toString().trim()
    settings.plate = plate.text.toString().trim()
    settings.annualCostWon = cost.text.toString().trim().toLongOrNull()
  }

  private fun readSeat(then: (Int?) -> Unit) {
    var done = false
    fun finish(v: Int?) {
      if (done) return
      done = true
      main.post { then(v) }
    }
    val v = vehicle
    if (v == null) {
      finish(null)
      return
    }
    runCatching {
      v.getSeat().getCurrentSeatForeAftPosition(SeatArea(SeatValue.ROW_1_LEFT), { finish(it) }, { finish(null) })
    }.onFailure { finish(null) }
    main.postDelayed({ finish(null) }, 1500)
  }

  override fun onDestroy() {
    main.removeCallbacksAndMessages(null)
    runCatching { vehicle?.release() }
    super.onDestroy()
  }
}
