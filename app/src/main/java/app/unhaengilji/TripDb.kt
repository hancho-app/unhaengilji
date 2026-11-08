package app.unhaengilji

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 기록은 기기 안에만 둔다. 위치 좌표는 저장하지 않고 목적지 글자만 비고에 넣는다. */
class TripDb(context: Context) : SQLiteOpenHelper(context, "trips.db", null, 1) {
  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE trips(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        date TEXT NOT NULL,
        dept TEXT NOT NULL DEFAULT '',
        name TEXT NOT NULL DEFAULT '',
        start_km REAL NOT NULL,
        end_km REAL NOT NULL,
        purpose TEXT NOT NULL,
        suggested TEXT,
        note TEXT NOT NULL DEFAULT '',
        missing INTEGER NOT NULL DEFAULT 0
      )
      """.trimIndent(),
    )
    db.execSQL("CREATE INDEX trips_date ON trips(date)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

  fun insert(t: Trip): Long = writableDatabase.insert(
    "trips",
    null,
    ContentValues().apply {
      put("date", t.date)
      put("dept", t.dept)
      put("name", t.name)
      put("start_km", t.startKm)
      put("end_km", t.endKm)
      put("purpose", t.purpose.name)
      put("suggested", t.suggested?.name)
      put("note", t.note)
      put("missing", if (t.missing) 1 else 0)
    },
  )

  fun classify(id: Long, purpose: Purpose) {
    val t = get(id) ?: return
    writableDatabase.update(
      "trips",
      ContentValues().apply {
        put("purpose", purpose.name)
        // 「개인」으로 고른 운행은 비고를 지운다.
        if (purpose == Purpose.PERSONAL) put("note", "")
        else if (t.note.isEmpty()) put("note", t.note)
      },
      "id=?",
      arrayOf(id.toString()),
    )
  }

  fun setDriver(id: Long, dept: String, name: String) {
    writableDatabase.update(
      "trips",
      ContentValues().apply { put("dept", dept); put("name", name) },
      "id=?",
      arrayOf(id.toString()),
    )
  }

  fun get(id: Long): Trip? = query("id=?", arrayOf(id.toString()), "id").firstOrNull()

  /** 사업연도·과세기간 합계는 달력 연도 기준으로 묶는다. */
  fun byPrefix(prefix: String): List<Trip> = query("date LIKE ?", arrayOf("$prefix%"), "start_km, id")

  fun unclassified(): List<Trip> = query("purpose=?", arrayOf(Purpose.UNCLASSIFIED.name), "id DESC")

  fun months(): List<String> {
    val out = mutableListOf<String>()
    readableDatabase.rawQuery("SELECT DISTINCT substr(date,1,7) FROM trips ORDER BY 1 DESC", null).use { c ->
      while (c.moveToNext()) out += c.getString(0)
    }
    return out
  }

  fun deleteByDriver(dept: String, name: String): Int =
    writableDatabase.delete("trips", "dept=? AND name=?", arrayOf(dept, name))

  private fun query(where: String, args: Array<String>, order: String): List<Trip> {
    val out = mutableListOf<Trip>()
    readableDatabase.query("trips", null, where, args, null, null, order).use { c -> while (c.moveToNext()) out += read(c) }
    return out
  }

  private fun read(c: Cursor): Trip {
    fun i(n: String) = c.getColumnIndexOrThrow(n)
    return Trip(
      id = c.getLong(i("id")),
      date = c.getString(i("date")),
      dept = c.getString(i("dept")),
      name = c.getString(i("name")),
      startKm = c.getDouble(i("start_km")),
      endKm = c.getDouble(i("end_km")),
      purpose = Purpose.valueOf(c.getString(i("purpose"))),
      suggested = c.getString(i("suggested"))?.let { Purpose.valueOf(it) },
      note = c.getString(i("note")),
      missing = c.getInt(i("missing")) == 1,
    )
  }
}
