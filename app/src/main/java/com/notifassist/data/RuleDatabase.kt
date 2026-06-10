package com.notifassist.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AppRule::class], version = 1, exportSchema = false)
abstract class RuleDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao

    companion object {
        @Volatile private var INSTANCE: RuleDatabase? = null

        fun getInstance(context: Context): RuleDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RuleDatabase::class.java,
                    "notifassist.db"
                ).build().also { INSTANCE = it }
            }
    }
}
