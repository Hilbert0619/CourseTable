package io.github.hilbert0619.coursetable.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyHourDao {
    // Courses

    @Query("SELECT * FROM courses ORDER BY createdAt, id")
    fun courses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun course(id: String): CourseEntity?

    @Upsert
    suspend fun upsertCourse(course: CourseEntity)

    @Insert
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourse(id: String)

    @Query("DELETE FROM courses")
    suspend fun deleteAllCourses()

    @Transaction
    suspend fun replaceCourses(courses: List<CourseEntity>) {
        deleteAllCourses()
        insertCourses(courses)
    }

    // Settings

    @Query("SELECT * FROM settings WHERE id = 0")
    fun settings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 0")
    suspend fun currentSettings(): SettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: SettingsEntity)

    // Course list

    @Query("SELECT * FROM course_templates ORDER BY createdAt, id")
    fun templates(): Flow<List<CourseTemplateEntity>>

    @Insert
    suspend fun insertTemplates(templates: List<CourseTemplateEntity>)

    @Query("DELETE FROM course_templates WHERE id IN (:ids)")
    suspend fun deleteTemplates(ids: List<String>)

    // To-dos

    @Query("SELECT * FROM todos ORDER BY createdAt, id")
    fun todos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun todo(id: String): TodoEntity?

    @Upsert
    suspend fun upsertTodo(todo: TodoEntity)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteTodo(id: String)

    // Focus history

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun focusSessions(limit: Int): Flow<List<FocusSessionEntity>>

    /** Keeps the first record of a session (iOS `recordFocus`). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFocusSession(session: FocusSessionEntity)
}

/** The app's data: the timetable, the course list, to-dos and focus history (`studyhour.db`). */
@Database(
    entities = [CourseEntity::class, CourseTemplateEntity::class, SettingsEntity::class, TodoEntity::class, FocusSessionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): StudyHourDao

    companion object {
        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "studyhour.db").build()

        /** For tests and screenshots. */
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
}
