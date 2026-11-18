package com.example.yesmin.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ReminderEntity::class,
        AssistantCallLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class YesminDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun reminderDao(): ReminderDao
    abstract fun assistantCallLogDao(): AssistantCallLogDao

    companion object {
        @Volatile
        private var INSTANCE: YesminDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema v1 baseline migration scaffold for future version upgrades
            }
        }

        fun getInstance(context: Context): YesminDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    YesminDatabase::class.java,
                    "yesmin_ai.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
