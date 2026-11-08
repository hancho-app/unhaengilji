package app.unhaengilji

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import app.unhaengilji.Ui.action
import app.unhaengilji.Ui.col
import app.unhaengilji.Ui.dp
import app.unhaengilji.Ui.label
import app.unhaengilji.Ui.row
import app.unhaengilji.Ui.weighted
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** 한 달 기록을 서식 순서의 CSV 로 QR 코드에 담는다. 양이 많으면 여러 장. 외부 서버를 쓰지 않는다. */
class QrActivity : Activity() {
  private var pages: List<String> = emptyList()
  private var at = 0
  private var month = ""

  override fun onCreate(s: Bundle?) {
    super.onCreate(s)
    month = intent.getStringExtra(EXTRA_MONTH) ?: ""
    pages = TripRules.toQrPages(month, TripRules.toCsvLines(TripDb(this).byPrefix(month)))
    render()
  }

  private fun render() {
    val root = row()
    root.setBackgroundColor(Ui.BG)
    root.setPadding(dp(24), dp(24), dp(24), dp(24))
    val image = ImageView(this)
    image.setImageBitmap(encode(pages[at], 960))
    root.weighted(image, 1.2f, 24)
    val side = col()
    side.addView(label("$month 운행기록", 30f, bold = true))
    side.addView(label("${at + 1} / ${pages.size} 장", 26f, Ui.MUTED))
    side.addView(label("휴대폰 카메라로 차례대로 찍어 주세요.", 22f, Ui.MUTED))
    side.gravity = Gravity.CENTER_VERTICAL
    if (at > 0) side.addView(action("이전 장") { at--; render() })
    if (at < pages.size - 1) side.addView(action("다음 장", primary = true) { at++; render() })
    side.addView(action("닫기") { finish() })
    root.weighted(side, 1f)
    setContentView(root)
  }

  private fun encode(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(
      text,
      BarcodeFormat.QR_CODE,
      size,
      size,
      mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L),
    )
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.RGB_565)
  }

  companion object {
    const val EXTRA_MONTH = "month"
  }
}
