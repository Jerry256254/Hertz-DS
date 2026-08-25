package com.hertzds.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    ],
    version = 1,
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

    companion object {
        fun build(context: Context): HertzDatabase =
            Room.databaseBuilder(context, HertzDatabase::class.java, "hertz.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
