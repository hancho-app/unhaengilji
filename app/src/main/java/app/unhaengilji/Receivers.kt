package app.unhaengilji

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** 시동(부팅 완료) 뒤 기록 서비스를 사람 손 없이 다시 띄운다. 동의 전에는 띄우지 않는다. */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(c: Context, i: Intent) {
    Log.i("UNHAENG", "boot_received ${i.action}")
    RecorderService.start(c)
  }
}

/** 정차 카드 알림의 용도 버튼. */
class ClassifyReceiver : BroadcastReceiver() {
  override fun onReceive(c: Context, i: Intent) {
    val id = i.getLongExtra(StopCardActivity.EXTRA_ID, -1)
    val p = i.getStringExtra(EXTRA_PURPOSE)?.let { Purpose.valueOf(it) } ?: return
    if (id < 0) return
    TripDb(c).classify(id, p)
    c.getSystemService(NotificationManager::class.java).cancel(RecorderService.ID_CARD)
    Log.i("UNHAENG", "classified $id $p")
  }

  companion object {
    const val EXTRA_PURPOSE = "purpose"
  }
}
