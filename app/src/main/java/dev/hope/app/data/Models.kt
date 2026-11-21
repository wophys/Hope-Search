package dev.hope.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

enum class Algo(val label: String) {
    PRONOUNCEABLE("Pronounceable"), SOFT("Soft"), SHARP("Sharp"), MIXED("Mixed")
}

enum class Style(val label: String) {
    PLAIN("Plain"), DIGITS("Digits"), UNDERSCORE("Underscore"), DOUBLE("Double")
}

enum class Tme { NONE, FREE, TAKEN, INVALID, ERROR }

enum class Frag { NONE, LISTED, SOLD, TAKEN, NOT_LISTED, ERROR }

@Entity(tableName = "usernames")
data class UsernameEntity(
    @PrimaryKey val name: String,
    val tme: String = Tme.NONE.name,
    val fragment: String = Frag.NONE.name,
    val priceTon: Double? = null,
    val favorite: Boolean = false,
    val algo: String = "",
    val checkedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

/** Находка: свободен в t.me или выставлен на Fragment. */
val UsernameEntity.isHit: Boolean
    get() = tme == Tme.FREE.name || fragment == Frag.LISTED.name

@Dao
interface UsernameDao {
    @Query("SELECT * FROM usernames ORDER BY MAX(checkedAt, createdAt) DESC")
    fun observeAll(): Flow<List<UsernameEntity>>

    @Query("SELECT name FROM usernames")
    suspend fun allNames(): List<String>

    @Query("SELECT * FROM usernames WHERE name = :name")
    suspend fun get(name: String): UsernameEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(e: UsernameEntity): Long

    @Query("UPDATE usernames SET tme = :tme, fragment = :fragment, priceTon = :price, checkedAt = :at WHERE name = :name")
    suspend fun updateCheck(name: String, tme: String, fragment: String, price: Double?, at: Long)

    @Query("UPDATE usernames SET favorite = :fav WHERE name = :name")
    suspend fun setFavorite(name: String, fav: Boolean)

    @Query("DELETE FROM usernames WHERE name = :name")
    suspend fun delete(name: String)

    @Query("DELETE FROM usernames WHERE favorite = 0")
    suspend fun clearNonFavorites()
}

@Database(entities = [UsernameEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): UsernameDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "hope.db")
                .build().also { instance = it }
        }
    }
}
