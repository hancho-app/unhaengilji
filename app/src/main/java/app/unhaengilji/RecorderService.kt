package app.unhaengilji

import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.time.LocalDate

/**
 * 화면 뒤에서 기어와 적산거리를 받아 운행 한 건을 저장하고, 정차하면 정차 카드를 띄운다.
 * 차량 이벤트가 올 때만 동작하고 타이머로 값을 읽지 않는다.
 */
class RecorderService : Service() {
  private val tag = "UNHAENG"
  private val main = Handler(Looper.getMainLooper())
  private lateinit var vehicle: Vehicle
  private lateinit var settings: Settings
  private lateinit var db: TripDb
  private var navi: NaviHelper? = null
  private var car: Car? = null
  private var ux: CarUxRestrictionsManager? = null
  private var uxRestricted = false
  private var pendingCardId: Long? = null

  private var destination: DestinationInfo? = null
  private var bookmarks: List<BookmarkInfo> = emptyList()

  private val odoListener = object : DistanceDrivenListener {
    override fun onDistanceDrivenUpdated(d: Float?) {
      val km = d?.toDouble() ?: return
      if (TripRules.odoChanged(settings.lastSeenKm, km)) {
        settings.lastSeenKm = roundKm(km)
        Log.i(tag, "odo $km")
      }
    }
    override fun onFailed(e: Exception) { Log.w(tag, "odo_fail $e") }
  }

  private val gearListener = object : GearSelectionListener {
    override fun onSelected(g: VehicleGear?) {
      if (g == null || g == VehicleGear.GEAR_UNKNOWN) return
      Log.i(tag, "gear $g")
      readOdo { km -> onGear(g == VehicleGear.GEAR_PARK, km) }
    }
    override fun onFailed(e: Exception) { Log.w(tag, "gear_fail $e") }
  }

  private val naviListener = object : NaviHelperEventListener {
    override fun onDestinationChanged() { runCatching { navi?.getDestinationInfo() } }
    override fun onDestinationInfo(destinationInfo: DestinationInfo) { destination = destinationInfo }
    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
      Log.i(tag, "arrived")
      runCatching { navi?.getDestinationInfo() }
    }
    override fun onBookmarkInfo(bookmarkInfo: List<BookmarkInfo>) { bookmarks = bookmarkInfo }
    override fun onBookMarkChanged() { runCatching { navi?.getBookmarkInfo() } }
  }

  override fun onBind(i: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    settings = Settings(this)
    db = TripDb(this)
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "운행 기록 중", NotificationManager.IMPORTANCE_LOW))
    nm.createNotificationChannel(NotificationChannel(CH_CARD, "정차 카드", NotificationManager.IMPORTANCE_HIGH))
    startForeground(
      ID_SERVICE,
      Notification.Builder(this, CH_SERVICE)
        .setContentTitle("운행일지")
        .setContentText("운행을 기록하고 있습니다")
        .setSmallIcon(android.R.drawable.ic_menu_edit)
        .build(),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
    )
    vehicle = Vehicle(this)
    vehicle.initialize()
    connectUxRestrictions()
    connectNavi()
    // 연결 직후의 호출은 실패할 수 있어 잠시 뒤에 등록한다(시험앱 실측 3초).
    main.postDelayed({ afterConnected() }, 3000)
  }

  private fun afterConnected() {
    vehicle.getCarInfo().getVINInfo(
      {
        // 에뮬레이터는 "invalid" 를 준다. 차대번호는 17자이므로 그 밖의 값은 받지 못한 것으로 둔다.
        val v = it?.trim()?.trim('"') ?: ""
        settings.vin = if (v.length == 17) v else ""
        Log.i(tag, "vin_ok ${settings.vin.isNotEmpty()}")
      },
      { Log.w(tag, "vin_fail $it") },
    )
    vehicle.getOdometer().registerDistanceDriven(odoListener)
    vehicle.getGear().registerGearSelection(gearListener)
    Log.i(tag, "registered")
    readOdo { km ->
      reconcile(km)
      vehicle.getGear().getCurrentGearSelectionState(
        { g ->
          if (g != null && g != VehicleGear.GEAR_UNKNOWN) {
            val parked = g == VehicleGear.GEAR_PARK
            // 꺼진 사이 P 로 돌아온 운행은 지금 닫는다.
            if (parked && settings.openStartKm != null) onGear(true, km)
            settings.wasParked = parked
          }
        },
        { Log.w(tag, "gear_get_fail $it") },
      )
    }
  }

  private fun readOdo(then: (Double) -> Unit) {
    vehicle.getOdometer().getCurrentDistanceDriven(
      { v -> if (v != null) main.post { then(v.toDouble()) } },
      { Log.w(tag, "odo_get_fail $it") },
    )
  }

  /** 기록 서비스가 다시 떴을 때 진행 중 운행이 없으면, 벌어진 적산거리를 빠진 운행 한 건으로 만든다. */
  private fun reconcile(km: Double) {
    if (settings.openStartKm != null) return
    val anchor = settings.anchorKm
    if (anchor == null) {
      settings.anchorKm = roundKm(km)
      Log.i(tag, "anchor_first $km")
      return
    }
    insertMissingIfGap(anchor, km)
  }

  private fun insertMissingIfGap(anchor: Double, km: Double): Boolean {
    val gap = TripRules.gapKm(anchor, km)
    if (gap <= 0.0) return false
    val id = db.insert(
      Trip(date = LocalDate.now().toString(), startKm = anchor, endKm = roundKm(km), missing = true),
    )
    settings.anchorKm = roundKm(km)
    Log.i(tag, "missing_trip $gap id=$id")
    requestCard(id)
    return true
  }

  private fun onGear(parked: Boolean, km: Double) {
    val wasParked = settings.wasParked
    settings.wasParked = parked
    when (val r = TripRules.onGear(wasParked, parked, settings.openStartKm, km)) {
      is TripRules.GearResult.Started -> {
        settings.anchorKm?.let { insertMissingIfGap(it, r.startKm) }
        settings.openStartKm = r.startKm
        runCatching { navi?.getDestinationInfo() }
        Log.i(tag, "trip_start ${r.startKm}")
      }
      is TripRules.GearResult.Finished -> {
        settings.openStartKm = null
        settings.anchorKm = r.endKm
        Log.i(tag, "trip_end ${r.startKm} ${r.endKm}")
        withDriver { d -> saveFinished(r.startKm, r.endKm, d) }
      }
      TripRules.GearResult.Discarded -> {
        settings.openStartKm = null
        Log.i(tag, "trip_discarded")
      }
      TripRules.GearResult.None -> {
        // 서비스가 뜨기 전에 출발해 진행 중 운행이 없는 채로 정차하면 빠진 운행으로 메운다.
        if (parked) settings.anchorKm?.let { insertMissingIfGap(it, km) }
      }
    }
  }

  /** 운전석 위치로 사용자를 고른다. 읽지 못하면 고정 사용자, 그것도 없으면 빈칸. */
  private fun withDriver(then: (TripRules.Driver?) -> Unit) {
    var done = false
    fun finish(seat: Int?) {
      if (done) return
      done = true
      val all = settings.drivers
      val picked = TripRules.suggestDriver(all, seat) ?: all.getOrNull(settings.currentDriver)
      main.post { then(picked) }
    }
    runCatching {
      vehicle.getSeat().getCurrentSeatForeAftPosition(SeatArea(SeatValue.ROW_1_LEFT), { finish(it) }, { finish(null) })
    }.onFailure { finish(null) }
    main.postDelayed({ finish(null) }, 1500)
  }

  private fun saveFinished(startKm: Double, endKm: Double, d: TripRules.Driver?) {
    val dest = destination
    val destText = listOfNotNull(dest?.name, dest?.address).filter { it.isNotBlank() }.joinToString(" ")
    val arrivedHomeOrOffice = dest != null && bookmarks.any { b ->
      (b.category == BookmarkType.HOME || b.category == BookmarkType.OFFICE) &&
        ((b.name.isNotBlank() && b.name == dest.name) || (b.address.isNotBlank() && b.address == dest.address))
    }
    val today = LocalDate.now()
    val id = db.insert(
      Trip(
        date = today.toString(),
        dept = d?.dept ?: "",
        name = d?.name ?: "",
        startKm = startKm,
        endKm = endKm,
        suggested = TripRules.suggestPurpose(today, arrivedHomeOrOffice),
        note = TripRules.noteFor(Purpose.BUSINESS, destText, settings.noteEnabled),
      ),
    )
    destination = null
    requestCard(id)
  }

  private fun requestCard(id: Long) {
    if (uxRestricted) {
      pendingCardId = id
      Log.i(tag, "card_deferred $id")
    } else {
      postCard(id)
    }
  }

  private fun postCard(id: Long) {
    val t = db.get(id) ?: return
    val nm = getSystemService(NotificationManager::class.java)
    val open = PendingIntent.getActivity(
      this,
      id.toInt(),
      Intent(this, StopCardActivity::class.java).putExtra(StopCardActivity.EXTRA_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val b = Notification.Builder(this, CH_CARD)
      .setContentTitle(if (t.missing) "기록되지 않은 운행 ${formatKm(t.distanceKm)} km" else "방금 운행 ${formatKm(t.distanceKm)} km")
      .setContentText(t.suggested?.let { "${it.label} 맞으면 한 번 눌러 주세요" } ?: "용도를 골라 주세요")
      .setSmallIcon(android.R.drawable.ic_menu_edit)
      .setCategory(Notification.CATEGORY_REMINDER)
      .setContentIntent(open)
      .setFullScreenIntent(open, true)
      .setAutoCancel(true)
    listOf(Purpose.COMMUTE, Purpose.BUSINESS, Purpose.PERSONAL).forEach { p ->
      val pi = PendingIntent.getBroadcast(
        this,
        (id * 10 + p.ordinal).toInt(),
        Intent(this, ClassifyReceiver::class.java)
          .putExtra(StopCardActivity.EXTRA_ID, id)
          .putExtra(ClassifyReceiver.EXTRA_PURPOSE, p.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
      b.addAction(Notification.Action.Builder(null, p.label, pi).build())
    }
    nm.notify(ID_CARD, b.build())
    Log.i(tag, "card_posted $id")
    // 에뮬레이터 실측: 일반 앱 알림은 헤즈업으로 보이지 않았다. 그래서 정차 화면을 직접 연다.
    // 앱이 화면에 있으면 열리고, 다른 앱에 가려져 있으면 플랫폼이 막는다. 그때는 다음 실행 때 미분류부터 보여 준다.
    runCatching {
      startActivity(Intent(this, StopCardActivity::class.java).putExtra(StopCardActivity.EXTRA_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      Log.i(tag, "card_activity_requested $id")
    }.onFailure { Log.w(tag, "card_activity_fail $it") }
  }

  /** 플랫폼이 내려 주는 주행 규제 상태를 따른다. 규제 중이면 카드를 미뤘다가 풀릴 때 띄운다. */
  private fun connectUxRestrictions() {
    runCatching {
      val c = Car.createCar(this) ?: return
      car = c
      val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
      ux = m
      uxRestricted = m.currentCarUxRestrictions?.isRequiresDistractionOptimization == true
      m.registerListener { r: CarUxRestrictions ->
        val was = uxRestricted
        uxRestricted = r.isRequiresDistractionOptimization
        Log.i(tag, "ux_restricted $uxRestricted")
        if (was && !uxRestricted) pendingCardId?.let { id -> pendingCardId = null; main.post { postCard(id) } }
      }
      Log.i(tag, "ux_connected restricted=$uxRestricted")
    }.onFailure { Log.w(tag, "ux_unavailable $it") }
  }

  private fun connectNavi() {
    runCatching {
      val n = NaviHelper(this)
      n.initialize(
        {
          n.addListener(naviListener)
          runCatching { n.getBookmarkInfo() }
          runCatching { n.getDestinationInfo() }
          Log.i(tag, "navi_connected")
        },
        { Log.w(tag, "navi_fail $it") },
      )
      navi = n
    }.onFailure { Log.w(tag, "navi_unavailable $it") }
  }

  override fun onDestroy() {
    main.removeCallbacksAndMessages(null)
    runCatching { vehicle.getOdometer().unregisterDistanceDriven(odoListener) }
    runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
    runCatching { vehicle.release() }
    runCatching { navi?.removeListener(naviListener); navi?.release() }
    runCatching { ux?.unregisterListener(); car?.disconnect() }
    super.onDestroy()
  }

  companion object {
    private const val CH_SERVICE = "service"
    private const val CH_CARD = "stop_card"
    private const val ID_SERVICE = 1
    const val ID_CARD = 2

    fun start(c: Context) {
      if (Settings(c).consented) c.startForegroundService(Intent(c, RecorderService::class.java))
    }
  }
}
