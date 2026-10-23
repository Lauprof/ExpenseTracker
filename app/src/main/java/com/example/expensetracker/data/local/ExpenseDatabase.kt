package com.example.expensetracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ExpenseEntity::class], version = 3, exportSchema = false)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: ExpenseDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN fee REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE expenses ADD COLUMN balanceAfter REAL")
                db.execSQL("ALTER TABLE expenses ADD COLUMN direction TEXT NOT NULL DEFAULT 'OUT'")
                db.execSQL("ALTER TABLE expenses ADD COLUMN linkedCode TEXT")
                db.execSQL("ALTER TABLE expenses ADD COLUMN needsReview INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE expenses SET direction = 'IN' WHERE type = 'INCOME' " +
                        "OR lower(rawSmsBody) LIKE '%received%' OR lower(rawSmsBody) LIKE '%credited%'"
                )
            }
        }

        fun getInstance(context: Context): ExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    "expense_tracker_db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
