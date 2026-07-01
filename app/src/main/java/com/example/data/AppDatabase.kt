package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Member::class, Attendance::class, SyncLog::class, InboxMessage::class, AppNotification::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE members ADD COLUMN title TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE members ADD COLUMN requiresLocation INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE attendance ADD COLUMN locationData TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `senderId` INTEGER NOT NULL, `senderName` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isRead` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attendance ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE attendance ADD COLUMN rejectionReason TEXT DEFAULT NULL")
                db.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `memberId` INTEGER NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isRead` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE members ADD COLUMN profileImage TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "attendance_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
