package app.unhaengilji

import android.app.Activity
import android.app.NotificationManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.widget.Button
import app.unhaengilji.Ui.action
import app.unhaengilji.Ui.col
import app.unhaengilji.Ui.dp
import app.unhaengilji.Ui.gapBelow
import app.unhaengilji.Ui.label

/** 정차 화면. 방금 끝난 운행과 용도 버튼 셋. 키보드 입력은 없다. */
class StopCardActivity : Activity() {
  private lateinit var db: TripDb

  override fun onCreate(s: Bundle?) {
    super.onCreate(s)
    db = TripDb(this)
    val id = intent.getLongExtra(EXTRA_ID, -1)
    show(db.get(id)?.takeIf { it.purpose == Purpose.UNCLASSIFIED } ?: db.unclassified().firstOrNull())
  }

  private fun show(t: Trip?) {
    val root = col(28)
    root.setBackgroundColor(Ui.BG)
    if (t == null) {
      root.gapBelow(label("분류할 운행이 없습니다", 30f, bold = true), 24)
      root.gapBelow(action("닫기") { finish() }, 0)
      setContentView(root)
      return
    }
    getSystemService(NotificationManager::class.java).cancel(RecorderService.ID_CARD)
    root.gapBelow(label(if (t.missing) "기록되지 않은 운행" else "방금 운행", 26f, if (t.missing) Ui.WARN else Ui.MUTED), 4)
    root.gapBelow(label("${formatKm(t.distanceKm)} km", 64f, bold = true), 8)
    root.gapBelow(label("주행 전 ${formatKm(t.startKm)} km  →  주행 후 ${formatKm(t.endKm)} km", 22f, Ui.MUTED), 8)
    if (t.note.isNotEmpty()) root.gapBelow(label("비고: ${t.note}", 22f, Ui.MUTED), 8)
    val who = listOf(t.dept, t.name).filter { it.isNotEmpty() }.joinToString(" ")
    root.gapBelow(label(if (who.isEmpty()) "사용자: 선택 안 됨" else "사용자: $who", 22f, Ui.MUTED), 24)

    listOf(Purpose.COMMUTE, Purpose.BUSINESS, Purpose.PERSONAL).forEach { p ->
      val b: Button = action(p.label, primary = t.suggested == p, size = 30f) {
        db.classify(t.id, p)
        Log.i("UNHAENG", "classified ${t.id} $p")
        val next = db.unclassified().firstOrNull { it.id != t.id }
        if (next == null) finish() else show(next)
      }
      root.gapBelow(b, 14)
    }

    val drivers = Settings(this).drivers
    if (drivers.isNotEmpty()) {
      root.gapBelow(
        action("사용자 바꾸기", size = 22f) {
          val i = drivers.indexOfFirst { it.dept == t.dept && it.name == t.name }
          val d = drivers[(i + 1) % drivers.size]
          db.setDriver(t.id, d.dept, d.name)
          show(db.get(t.id))
        },
        14,
      )
    }
    val rest = db.unclassified().size - 1
    if (rest > 0) root.gapBelow(label("미분류 ${rest}건이 더 있습니다", 22f, Ui.WARN), 14)
    root.gapBelow(action("나중에", size = 22f) { finish() }, 0)
    root.background = GradientDrawable().apply { setColor(Ui.BG) }
    root.setPadding(dp(28), dp(28), dp(28), dp(28))
    setContentView(root)
  }

  companion object {
    const val EXTRA_ID = "trip_id"
  }
}
