package com.mathmilo.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Decimal values are stored as canonical strings (BigDecimal.toPlainString without trailing zeros). */
@Entity(
    tableName = "calculations",
    indices = [Index(value = ["token"], unique = true)],
)
data class CalculationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val token: String,
    val firstOperand: String,
    @ColumnInfo(name = "operator") val operatorCode: String,
    val secondOperand: String,
    val result: String,
    val rounded: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "daily_sets",
    indices = [Index(value = ["localDate"], unique = true)],
)
data class DailySetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO local date, e.g. 2026-10-01. */
    val localDate: String,
    val level: String,
    val createdAt: Long,
    val completedAt: Long? = null,
)

@Entity(
    tableName = "daily_questions",
    foreignKeys = [
        ForeignKey(
            entity = DailySetEntity::class,
            parentColumns = ["id"],
            childColumns = ["dailySetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["dailySetId", "position"], unique = true)],
)
data class DailyQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dailySetId: Long,
    val position: Int,
    val firstOperand: Int,
    @ColumnInfo(name = "operator") val operatorCode: String,
    val secondOperand: Int,
    val correctAnswer: Int,
    /** Comma-separated options in display order. */
    val options: String,
    val selectedAnswer: Int? = null,
    val answeredAt: Long? = null,
    val isCorrect: Boolean? = null,
)

@Entity(tableName = "extra_practice")
data class ExtraPracticeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Not a foreign key: practice records outlive pruned calculation history. */
    val sourceCalculationId: Long?,
    val firstOperand: Int,
    @ColumnInfo(name = "operator") val operatorCode: String,
    val secondOperand: Int,
    val correctAnswer: Int,
    val options: String,
    val createdAt: Long,
    val selectedAnswer: Int? = null,
    val answeredAt: Long? = null,
    val isCorrect: Boolean? = null,
)

/** Independent of source records, so pruning daily sets never deletes unresolved mistakes. */
@Entity(
    tableName = "mistakes",
    indices = [Index(value = ["normalizedExpressionKey"], unique = true)],
)
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normalizedExpressionKey: String,
    val firstOperand: Int,
    @ColumnInfo(name = "operator") val operatorCode: String,
    val secondOperand: Int,
    val correctAnswer: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val incorrectAttempts: Int,
    val resolvedAt: Long? = null,
)

/** One retry attempt, keyed by a client-generated id so recreation never double-applies it. */
@Entity(
    tableName = "mistake_retries",
    indices = [Index("mistakeId")],
)
data class MistakeRetryEntity(
    @PrimaryKey val attemptId: String,
    val mistakeId: Long,
    val options: String,
    val createdAt: Long,
    val selectedAnswer: Int? = null,
    val isCorrect: Boolean? = null,
    val answeredAt: Long? = null,
)

/** Single-row lifetime totals (id = 1), preserved when detailed records are pruned. */
@Entity(tableName = "progress_totals")
data class ProgressTotalsEntity(
    @PrimaryKey val id: Int = 1,
    val dailyAnswered: Int = 0,
    val dailyCorrect: Int = 0,
    val extraAnswered: Int = 0,
    val extraCorrect: Int = 0,
    val completedDays: Int = 0,
    val resolvedMistakeCount: Int = 0,
)

data class DailySetWithStats(
    @Embedded val set: DailySetEntity,
    val answered: Int,
    val correct: Int,
    val total: Int,
)
