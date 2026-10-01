package com.mathmilo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        CalculationEntity::class,
        DailySetEntity::class,
        DailyQuestionEntity::class,
        ExtraPracticeEntity::class,
        MistakeEntity::class,
        MistakeRetryEntity::class,
        ProgressTotalsEntity::class,
    ],
    version = MathMiloDatabase.VERSION,
    exportSchema = true,
)
abstract class MathMiloDatabase : RoomDatabase() {
    abstract fun calculationDao(): CalculationDao
    abstract fun practiceDao(): PracticeDao

    companion object {
        const val VERSION = 1
        private const val NAME = "mathmilo.db"

        fun create(context: Context): MathMiloDatabase =
            Room.databaseBuilder(context.applicationContext, MathMiloDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                // Intentionally NO fallbackToDestructiveMigration(): updates must migrate data.
                .build()
    }
}

/**
 * Schema migrations. Schemas are exported to app/schemas/ (commit them). For every future
 * version bump add an explicit Migration here, e.g.:
 *
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE mistakes ADD COLUMN note TEXT")
 *     }
 * }
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
