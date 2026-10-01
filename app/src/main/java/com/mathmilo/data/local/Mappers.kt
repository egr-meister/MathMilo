package com.mathmilo.data.local

import com.mathmilo.domain.Operator
import com.mathmilo.domain.generation.OptionCodec
import com.mathmilo.domain.generation.PracticeLevel
import com.mathmilo.domain.generation.Question
import com.mathmilo.domain.progress.DailyQuestionRecord
import com.mathmilo.domain.progress.DailySet
import com.mathmilo.domain.progress.DailySummary
import com.mathmilo.domain.progress.ExtraPracticeRecord
import com.mathmilo.domain.progress.MistakeRecord
import com.mathmilo.domain.progress.MistakeRetryRecord
import com.mathmilo.domain.progress.ProgressTotals
import java.time.LocalDate

fun DailySetEntity.toDomain() = DailySet(
    id = id,
    localDate = LocalDate.parse(localDate),
    level = PracticeLevel.fromCode(level),
    createdAt = createdAt,
    completedAt = completedAt,
)

fun DailyQuestionEntity.toDomain() = DailyQuestionRecord(
    id = id,
    dailySetId = dailySetId,
    position = position,
    question = Question(firstOperand, Operator.fromCode(operatorCode), secondOperand),
    options = OptionCodec.decode(options),
    selectedAnswer = selectedAnswer,
    answeredAt = answeredAt,
    isCorrect = isCorrect,
)

fun ExtraPracticeEntity.toDomain() = ExtraPracticeRecord(
    id = id,
    sourceCalculationId = sourceCalculationId,
    question = Question(firstOperand, Operator.fromCode(operatorCode), secondOperand),
    options = OptionCodec.decode(options),
    createdAt = createdAt,
    selectedAnswer = selectedAnswer,
    answeredAt = answeredAt,
    isCorrect = isCorrect,
)

fun MistakeEntity.toDomain() = MistakeRecord(
    id = id,
    normalizedKey = normalizedExpressionKey,
    question = Question(firstOperand, Operator.fromCode(operatorCode), secondOperand),
    firstSeenAt = firstSeenAt,
    lastSeenAt = lastSeenAt,
    incorrectAttempts = incorrectAttempts,
    resolvedAt = resolvedAt,
)

fun MistakeRetryEntity.toDomain() = MistakeRetryRecord(
    attemptId = attemptId,
    mistakeId = mistakeId,
    options = OptionCodec.decode(options),
    createdAt = createdAt,
    selectedAnswer = selectedAnswer,
    isCorrect = isCorrect,
    answeredAt = answeredAt,
)

fun ProgressTotalsEntity?.toDomain() = if (this == null) ProgressTotals() else ProgressTotals(
    dailyAnswered = dailyAnswered,
    dailyCorrect = dailyCorrect,
    extraAnswered = extraAnswered,
    extraCorrect = extraCorrect,
    completedDays = completedDays,
    resolvedMistakeCount = resolvedMistakeCount,
)

fun ProgressTotals.toEntity() = ProgressTotalsEntity(
    id = 1,
    dailyAnswered = dailyAnswered,
    dailyCorrect = dailyCorrect,
    extraAnswered = extraAnswered,
    extraCorrect = extraCorrect,
    completedDays = completedDays,
    resolvedMistakeCount = resolvedMistakeCount,
)

fun DailySetWithStats.toSummary() = DailySummary(set.toDomain(), answered, correct, total)
