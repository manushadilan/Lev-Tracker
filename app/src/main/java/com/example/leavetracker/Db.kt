package com.example.leavetracker

import android.content.Context
import androidx.room.*

val CATEGORIES = listOf("Casual", "Sick", "Duty", "Half Day", "Short Leave")

const val SHORT_LIMIT = 2   // max Short Leave entries per month
val BALANCE_CATS = listOf("Casual", "Sick")

// Days counted per category in the report. Short Leave counts as zero.
fun daysFor(c: String) = when (c) { "Half Day" -> 0.5; "Short Leave" -> 0.0; else -> 1.0 }

@Entity(tableName = "leaves")
data class Leave(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,          // yyyy-MM-dd
    val category: String,
    val reason: String = ""
) {
    fun days() = daysFor(category)
}

@Dao
interface LeaveDao {
    @Insert suspend fun insert(l: Leave)
    @Update suspend fun update(l: Leave)
    @Query("DELETE FROM leaves WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("SELECT date FROM leaves WHERE date BETWEEN :a AND :b") suspend fun datesBetween(a: String, b: String): List<String>
    @Query("SELECT COUNT(*) FROM leaves WHERE category = 'Short Leave' AND date LIKE :pattern AND id != :id")
    suspend fun countShort(pattern: String, id: Long): Int
    @Query("SELECT COUNT(*) FROM leaves WHERE date = :d AND id != :id") suspend fun countOther(d: String, id: Long): Int
    @Query("SELECT * FROM leaves WHERE date LIKE :pattern ORDER BY date ASC")
    suspend fun forPattern(pattern: String): List<Leave>
}

@Database(entities = [Leave::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): LeaveDao
}

class Db(c: Context) {
    private val dao = Room.databaseBuilder(c.applicationContext, AppDb::class.java, "leaves_room.db").build().dao()

    suspend fun add(date: String, category: String, reason: String) = dao.insert(Leave(date = date, category = category, reason = reason))
    suspend fun update(id: Long, date: String, category: String, reason: String) = dao.update(Leave(id, date, category, reason))
    suspend fun existing(a: String, b: String) = dao.datesBetween(a, b).toSet()
    suspend fun shortCount(ym: String, excludeId: Long = 0) = dao.countShort("$ym-%", excludeId)
    suspend fun all() = dao.forPattern("%")
    suspend fun clash(d: String, id: Long) = dao.countOther(d, id) > 0
    suspend fun delete(id: Long) = dao.deleteById(id)

    // month = 0 means the whole year
    suspend fun forPeriod(year: Int, month: Int): List<Leave> =
        dao.forPattern(if (month == 0) "$year-%" else String.format("%d-%02d-%%", year, month))
}
