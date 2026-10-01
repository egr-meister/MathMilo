package com.mathmilo.data.repository

import androidx.room.withTransaction
import com.mathmilo.data.local.CalculationEntity
import com.mathmilo.data.local.MathMiloDatabase
import com.mathmilo.domain.Operator
import com.mathmilo.domain.calculator.CalculationResult
import com.mathmilo.domain.calculator.Numbers
import com.mathmilo.domain.daily.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

data class HistoryEntry(
    val id: Long,
    val result: CalculationResult,
    val createdAt: Long,
)

class CalculationRepository(
    private val db: MathMiloDatabase,
    private val time: TimeProvider,
) {
    companion object {
        const val HISTORY_LIMIT = 50
    }

    private val dao = db.calculationDao()

    val history: Flow<List<HistoryEntry>> = dao.observeLatest(HISTORY_LIMIT).map { list ->
        list.map { e ->
            HistoryEntry(
                id = e.id,
                result = CalculationResult(
                    first = BigDecimal(e.firstOperand),
                    operator = Operator.fromCode(e.operatorCode),
                    second = BigDecimal(e.secondOperand),
                    value = BigDecimal(e.result),
                    rounded = e.rounded,
                ),
                createdAt = e.createdAt,
            )
        }
    }

    /**
     * Records a successful calculation. [token] identifies the Equals press; the unique index
     * makes a repeated insert of the same calculation a no-op. Returns the row id.
     */
    suspend fun record(result: CalculationResult, token: String): Long = db.withTransaction {
        dao.byToken(token)?.let { return@withTransaction it.id }
        val id = dao.insert(
            CalculationEntity(
                token = token,
                firstOperand = Numbers.canonical(result.first),
                operatorCode = result.operator.code,
                secondOperand = Numbers.canonical(result.second),
                result = Numbers.canonical(result.value),
                rounded = result.rounded,
                createdAt = time.nowMillis(),
            ),
        )
        dao.prune(HISTORY_LIMIT)
        id
    }

    suspend fun clear() = dao.clear()
}
