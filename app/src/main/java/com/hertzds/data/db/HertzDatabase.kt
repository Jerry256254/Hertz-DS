package com.hertzds.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        MemoryEntity::class,
        MemoryFts::class,
        ApiKeyEntity::class,
        UsageEntity::class,
        ScheduledTaskEntity::class,
        NotebookEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class HertzDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun memoryDao(): MemoryDao
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun usageDao(): UsageDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun notebookDao(): NotebookDao

    companion object {
        /**
         * Adds the `notebooks` table only. Every earlier release shipped at
         * schema version 1 — without this, real installs with real chats, keys
         * and memories would hit fallbackToDestructiveMigration on update and
         * lose everything, just because one new unrelated table was added.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notebooks` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `sharedWithAi` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun build(context: Context): HertzDatabase =
            Room.databaseBuilder(context, HertzDatabase::class.java, "hertz.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
