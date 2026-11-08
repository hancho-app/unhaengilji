package app.unhaengilji

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** 화면을 코드로 짓는 작은 도구. 글자와 버튼은 차 화면 기준으로 크게 잡는다. */
object Ui {
  val BG = Color.parseColor("#0F1318")
  val PANEL = Color.parseColor("#1B222B")
  val LINE = Color.parseColor("#33404E")
  val TEXT = Color.parseColor("#F2F5F8")
  val MUTED = Color.parseColor("#A7B3C0")
  val ACCENT = Color.parseColor("#3D8BFF")
  val WARN = Color.parseColor("#F2B84B")

  fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

  fun Context.col(pad: Int = 0): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    if (pad > 0) setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
  }

  fun Context.row(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
  }

  fun Context.label(s: String, size: Float = 22f, color: Int = TEXT, bold: Boolean = false): TextView = TextView(this).apply {
    text = s
    textSize = size
    setTextColor(color)
    if (bold) typeface = Typeface.DEFAULT_BOLD
  }

  fun Context.action(s: String, primary: Boolean = false, size: Float = 24f, onClick: (View) -> Unit): Button = Button(this).apply {
    text = s
    textSize = size
    isAllCaps = false
    setTextColor(TEXT)
    minHeight = dp(76)
    minimumHeight = dp(76)
    background = GradientDrawable().apply {
      cornerRadius = dp(14).toFloat()
      setColor(if (primary) ACCENT else PANEL)
      setStroke(dp(2), if (primary) ACCENT else LINE)
    }
    setPadding(dp(20), dp(8), dp(20), dp(8))
    setOnClickListener(onClick)
  }

  fun Context.input(hint: String, value: String = "", number: Boolean = false): EditText = EditText(this).apply {
    this.hint = hint
    setText(value)
    textSize = 22f
    setTextColor(TEXT)
    setHintTextColor(MUTED)
    inputType = if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
    // setSingleLine 이 줄 수 기준으로 최소 높이를 덮어쓰므로 그 뒤에 잡는다. 앞에 두면 입력 칸이 49px 로 줄었다(실측).
    isSingleLine = true
    minimumHeight = dp(76)
  }

  fun LinearLayout.gapBelow(v: View, gap: Int): LinearLayout {
    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    lp.bottomMargin = (gap * context.resources.displayMetrics.density).toInt()
    addView(v, lp)
    return this
  }

  fun LinearLayout.weighted(v: View, w: Float, gap: Int = 0): LinearLayout {
    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
    lp.marginEnd = (gap * context.resources.displayMetrics.density).toInt()
    addView(v, lp)
    return this
  }
}
