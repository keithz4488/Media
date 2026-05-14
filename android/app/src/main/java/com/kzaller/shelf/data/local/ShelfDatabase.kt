package com.kzaller.shelf.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ItemEntity::class], version = 1, exportSchema = false)
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
                ).build().also { INSTANCE = it }
            }
    }
}
