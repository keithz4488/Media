package com.kzaller.shelf.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ItemEntity::class], version = 8, exportSchema = false)
abstract class ShelfDatabase : RoomDatabase() {
    abstract fun items(): ItemDao

    companion object {
        @Volatile private var INSTANCE: ShelfDatabase? = null

        fun get(context: Context): ShelfDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShelfDatabase::class.java,
                    "media-shelf.db",
                )
                    // Local DB is purely a cache; on schema bump just rebuild it
                    // and let the next refresh repopulate from the backend.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
