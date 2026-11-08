package app.unhaengilji

import app.unhaengilji.TripRules.Baseline
import app.unhaengilji.TripRules.GearResult
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRulesTest {
  @Test fun leavingParkStartsATrip() {
    assertEquals(GearResult.Started(100.0), TripRules.onGear(true, false, null, 100.0))
  }

  @Test fun reverseThenDriveKeepsTheSameTrip() {
    // P→R 에서 시작했으면 R→D 는 새 운행이 아니다.
    assertEquals(GearResult.None, TripRules.onGear(false, false, 100.0, 100.0))
  }

  @Test fun returningToParkFinishes() {
    assertEquals(GearResult.Finished(100.0, 127.0), TripRules.onGear(false, true, 100.0, 127.0))
  }

  @Test fun noMovementIsDiscarded() {
    assertEquals(GearResult.Discarded, TripRules.onGear(false, true, 100.0, 100.0))
  }

  @Test fun gapOnlyWhenOdometerAdvanced() {
    assertEquals(15.0, TripRules.gapKm(100.0, 115.0), 0.0)
    assertEquals(0.0, TripRules.gapKm(100.0, 100.0), 0.0)
    assertEquals(0.0, TripRules.gapKm(100.0, 90.0), 0.0)
    assertEquals(0.0, TripRules.gapKm(null, 90.0), 0.0)
  }

  @Test fun repeatedOdoIsFiltered() {
    assertTrue(TripRules.odoChanged(null, 1.0))
    assertEquals(false, TripRules.odoChanged(12345.0, 12345.0))
    assertTrue(TripRules.odoChanged(12345.0, 12345.2))
  }

  @Test fun commuteSuggestedOnlyOnWorkdays() {
    val monday = LocalDate.of(2026, 11, 9)
    val sunday = LocalDate.of(2026, 11, 8)
    val holiday = LocalDate.of(2026, 12, 25)
    assertEquals(Purpose.COMMUTE, TripRules.suggestPurpose(monday, true))
    assertNull(TripRules.suggestPurpose(monday, false))
    assertNull(TripRules.suggestPurpose(sunday, true))
    assertNull(TripRules.suggestPurpose(holiday, true))
  }

  @Test fun personalTripHasNoNote() {
    assertEquals("", TripRules.noteFor(Purpose.PERSONAL, "거래처", true))
    assertEquals("거래처", TripRules.noteFor(Purpose.BUSINESS, "거래처", true))
    assertEquals("", TripRules.noteFor(Purpose.BUSINESS, "거래처", false))
  }

  private fun trip(start: Double, end: Double, p: Purpose) = Trip(date = "2026-11-09", startKm = start, endKm = end, purpose = p)

  @Test fun totalsFollowTheForm() {
    val t = TripRules.totals(
      listOf(
        trip(0.0, 10.0, Purpose.COMMUTE),
        trip(10.0, 40.0, Purpose.BUSINESS),
        trip(40.0, 50.0, Purpose.PERSONAL),
        trip(50.0, 60.0, Purpose.UNCLASSIFIED),
      ),
    )
    assertEquals(60.0, t.totalKm, 0.0)
    assertEquals(40.0, t.businessUseKm, 0.0)
    assertEquals(10.0, t.personalKm, 0.0)
    assertEquals(40.0 / 60.0, t.ratio, 1e-9)
  }

  @Test fun emptyTotalsHaveZeroRatio() {
    assertEquals(0.0, TripRules.totals(emptyList()).ratio, 0.0)
  }

  @Test fun continuityBreakIsCounted() {
    val ok = listOf(trip(0.0, 10.0, Purpose.BUSINESS), trip(10.0, 20.0, Purpose.BUSINESS))
    val broken = listOf(trip(0.0, 10.0, Purpose.BUSINESS), trip(15.0, 20.0, Purpose.BUSINESS))
    assertEquals(0, TripRules.continuityBreaks(ok))
    assertEquals(1, TripRules.continuityBreaks(broken))
  }

  @Test fun baselineBelowLineIsFullyAllowed() {
    val r = TripRules.baseline(12_900_000, 0.2)
    assertEquals(Baseline.BELOW_LINE, r.kind)
    assertEquals(12_900_000, r.allowedWithout)
  }

  @Test fun baselineRecordRaisesOnlyAboveTheBreakeven() {
    // 3,000만 원이면 손익분기 비율은 50%.
    assertEquals(Baseline.RECORD_RAISES, TripRules.baseline(30_000_000, 0.8).kind)
    assertEquals(24_000_000, TripRules.baseline(30_000_000, 0.8).allowedWithRecord)
    assertEquals(Baseline.RECORD_NOT_RAISES, TripRules.baseline(30_000_000, 0.4).kind)
    assertEquals(15_000_000, TripRules.baseline(30_000_000, 0.4).allowedWithout)
  }

  @Test fun baselineWithoutInput() {
    assertEquals(Baseline.NO_COST, TripRules.baseline(null, 0.5).kind)
  }

  @Test fun driverSuggestedOnlyWhenUnique() {
    val a = TripRules.Driver("영업", "A", 40)
    val b = TripRules.Driver("영업", "B", 60)
    val c = TripRules.Driver("영업", "C", 60)
    assertEquals(a, TripRules.suggestDriver(listOf(a, b), 40))
    assertNull(TripRules.suggestDriver(listOf(a, b, c), 60))
    assertNull(TripRules.suggestDriver(listOf(a, b), null))
    assertNull(TripRules.suggestDriver(listOf(a, b), 50))
  }

  @Test fun csvQuotesCommaInNote() {
    val line = TripRules.toCsvLines(listOf(Trip(date = "2026-11-09", startKm = 0.0, endKm = 10.0, purpose = Purpose.BUSINESS, note = "A, B"))).single()
    assertEquals("2026-11-09,,,0,10,10,,10,\"A, B\"", line)
  }

  @Test fun qrPagesKeepEveryLineWithinTheLimit() {
    val lines = List(200) { "2026-11-09,영업부,홍길동,1000,1010,10,,10,거래처 주소 $it" }
    val pages = TripRules.toQrPages("2026-11", lines, 1000)
    assertTrue(pages.size > 1)
    pages.forEach { assertTrue(it.toByteArray().size <= 1000) }
    assertEquals(lines.size, pages.sumOf { it.lines().size - 1 })
    assertTrue(pages[0].startsWith("UHJ1|1/${pages.size}|2026-11|"))
  }

  @Test fun emptyMonthStillMakesOnePage() {
    assertEquals(1, TripRules.toQrPages("2026-11", emptyList()).size)
  }
}
