package com.forcetower.uefs.core.storage.database.dao

import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.forcetower.uefs.core.model.api.EverythingSnippet
import com.forcetower.uefs.core.model.unes.EvaluationEntity
import com.forcetower.uefs.core.util.unaccent
import java.util.Locale

@Dao
abstract class EvaluationEntitiesDao {
    @Transaction
    open fun recreate(data: EverythingSnippet) {
        clear()
        val teachers = data.teachers.map { EvaluationEntity(0, it.teacherId, it.name, null, it.imageUrl, 0, it.name.toLowerCase(Locale.getDefault()).unaccent()) }
        val disciplines = data.disciplines.map {
            val coded = "${it.department}${it.code}"
            val search = "$coded ${it.name}".toLowerCase(Locale.getDefault()).unaccent()
            EvaluationEntity(0, it.disciplineId, it.name, coded, null, 1, search)
        }
        val students = data.students.map {
            val search = "${it.name} ${it.courseName}".toLowerCase(Locale.getDefault()).unaccent()
            EvaluationEntity(0, it.id, it.name, it.courseName, it.imageUrl, 2, search)
        }

        val batch = mutableListOf<EvaluationEntity>()
        batch.apply {
            addAll(teachers)
            addAll(disciplines)
            addAll(students)
        }
        insert(batch)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insert(values: List<EvaluationEntity>)

    @Query("DELETE FROM EvaluationEntity")
    protected abstract fun clear()

    open fun query(query: String): DataSource.Factory<Int, EvaluationEntity> {
        val realQuery = "%${query.toLowerCase(Locale.getDefault()).unaccent()}%"
        return if (query.isBlank()) {
            doQueryEmpty()
        } else {
            doQuery(realQuery)
        }
    }

    @Query("SELECT * FROM EvaluationEntity WHERE LOWER(searchable) LIKE :query ORDER BY name")
    abstract fun doQuery(query: String): DataSource.Factory<Int, EvaluationEntity>

    @Query("SELECT * FROM EvaluationEntity WHERE 0")
    abstract fun doQueryEmpty(): DataSource.Factory<Int, EvaluationEntity>
}