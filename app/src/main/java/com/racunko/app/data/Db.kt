package com.racunko.app.data

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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.racunko.app.parser.MonthYear
import com.racunko.app.parser.PayeeProfile
import com.racunko.app.parser.StoredBill

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey val roKey: String,
    val altKey: String,
    val provider: String,
    val address: String,
    val month: Int?,
    val year2: Int?,
    val amount: Long?,
    val recipientAccount: String,
    /** Final file name WITHOUT extension. */
    val finalName: String,
    val qrImageUri: String?,
    val paired: Boolean,
    val timestamp: Long
) {
    fun toStored(): StoredBill = StoredBill(
        roKey = roKey,
        altKey = altKey,
        provider = provider,
        address = address,
        month = if (month != null && year2 != null) MonthYear(month, year2) else null,
        amount = amount,
        recipientAccount = recipientAccount,
        name = finalName,
        paired = paired
    )
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY timestamp DESC")
    suspend fun all(): List<BillEntity>

    @Query("SELECT * FROM bills WHERE roKey = :key LIMIT 1")
    suspend fun byKey(key: String): BillEntity?

    @Query("SELECT * FROM bills WHERE finalName = :name LIMIT 1")
    suspend fun byName(name: String): BillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bill: BillEntity)

    @Query("UPDATE bills SET paired = :paired WHERE roKey = :key")
    suspend fun setPaired(key: String, paired: Boolean)

    @Query("UPDATE bills SET qrImageUri = NULL WHERE roKey = :key")
    suspend fun clearQrUri(key: String)

    @Query("DELETE FROM bills")
    suspend fun clear()
}

/** Change 6: remembered payee, keyed by checksum-valid 18-digit recipient account. */
@Entity(tableName = "payee_profiles")
data class PayeeProfileEntity(
    @PrimaryKey val account: String,
    val provider: String,
    val addressLabel: String,
    val displayName: String,
    val lastReferenceShape: String,
    val updatedAt: Long
) {
    fun toProfile(): PayeeProfile = PayeeProfile(account, provider, addressLabel, displayName)
}

@Dao
interface PayeeDao {
    @Query("SELECT * FROM payee_profiles WHERE account = :account LIMIT 1")
    suspend fun byAccount(account: String): PayeeProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(payee: PayeeProfileEntity)

    @Query("DELETE FROM payee_profiles")
    suspend fun clear()
}

/**
 * v1.4.3 Change 1: a processed card persisted by its (stable) file name, so the
 * Računi/Potvrde lists survive backgrounding, the share round-trip and process
 * death. Rebuilt on start/resume and reconciled against the folder scan. The QR
 * bitmap is NOT stored (re-derived on demand); only the gallery URI is.
 */
@Entity(tableName = "card_records")
data class CardRecordEntity(
    @PrimaryKey val name: String,
    val mode: String,
    val uri: String,
    val provider: String,
    val address: String,
    val month: Int?,
    val year2: Int?,
    val amount: Long?,
    val roDigits: String,
    val recipientAccount: String,
    val accountVerified: Boolean,
    val hasQr: Boolean,
    val qrGenerated: Boolean,
    val qrImageUri: String?,
    val matched: Boolean,
    val paired: Boolean,
    val isImage: Boolean,
    /** List-only delete (Change 4): file kept, but card hidden so backfill won't revive it. */
    val dismissed: Boolean,
    val timestamp: Long,
    /** v1.6: payment deadline as an epoch day; null when the bill prints none. */
    val dueDateEpochDay: Long? = null,
    /** v1.6: per-bill reminder, mirroring mani's „Podseti me da se približava plaćanje". */
    val remindEnabled: Boolean = true,
    val remindDaysBefore: Int = 3,
    /** Kept for the future system notification; the in-app banner shows on open. */
    val remindHour: Int = 10,
    val remindMinute: Int = 0
)

@Dao
interface CardDao {
    @Query("SELECT * FROM card_records ORDER BY timestamp DESC")
    suspend fun all(): List<CardRecordEntity>

    @Query("SELECT * FROM card_records WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): CardRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CardRecordEntity)

    @Query("DELETE FROM card_records WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("UPDATE card_records SET dismissed = 1 WHERE name = :name")
    suspend fun setDismissed(name: String)

    @Query("DELETE FROM card_records")
    suspend fun clear()
}

@Database(
    entities = [BillEntity::class, PayeeProfileEntity::class, CardRecordEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun bills(): BillDao
    abstract fun payees(): PayeeDao
    abstract fun cards(): CardDao

    companion object {
        @Volatile private var instance: AppDb? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `payee_profiles` (" +
                        "`account` TEXT NOT NULL, `provider` TEXT NOT NULL, " +
                        "`addressLabel` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                        "`lastReferenceShape` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`account`))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `card_records` (" +
                        "`name` TEXT NOT NULL, `mode` TEXT NOT NULL, `uri` TEXT NOT NULL, " +
                        "`provider` TEXT NOT NULL, `address` TEXT NOT NULL, " +
                        "`month` INTEGER, `year2` INTEGER, `amount` INTEGER, " +
                        "`roDigits` TEXT NOT NULL, `recipientAccount` TEXT NOT NULL, " +
                        "`accountVerified` INTEGER NOT NULL, `hasQr` INTEGER NOT NULL, " +
                        "`qrGenerated` INTEGER NOT NULL, `qrImageUri` TEXT, " +
                        "`matched` INTEGER NOT NULL, `paired` INTEGER NOT NULL, " +
                        "`isImage` INTEGER NOT NULL, `dismissed` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, PRIMARY KEY(`name`))"
                )
            }
        }

        /** v1.6: payment deadline + per-bill reminder. Defaults match the entity. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `card_records` ADD COLUMN `dueDateEpochDay` INTEGER")
                db.execSQL("ALTER TABLE `card_records` ADD COLUMN `remindEnabled` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `card_records` ADD COLUMN `remindDaysBefore` INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE `card_records` ADD COLUMN `remindHour` INTEGER NOT NULL DEFAULT 10")
                db.execSQL("ALTER TABLE `card_records` ADD COLUMN `remindMinute` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "racunko.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}
