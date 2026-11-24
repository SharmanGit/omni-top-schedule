package ru.topacademy.omni.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import ru.topacademy.omni.domain.model.Branch
import ru.topacademy.omni.domain.model.Lesson
import ru.topacademy.omni.domain.model.TaskQueue

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val id: String,
    val date: String,
    val dayOfWeek: Int,
    val lentaNum: Int,
    val startTime: String,
    val endTime: String,
    val subject: String,
    val room: String,
    val group: String,
    val branchId: String,
    val branchName: String,
    val branchCategory: String,
    val branchColor: String,
    val isAttendanceMarked: Boolean = false
) {
    fun toDomain(): Lesson = Lesson(
        id = id,
        date = date,
        dayOfWeek = dayOfWeek,
        lentaNum = lentaNum,
        startTime = startTime,
        endTime = endTime,
        subject = subject,
        room = room,
        group = group,
        branchId = branchId,
        branchName = branchName,
        branchCategory = branchCategory,
        branchColor = branchColor,
        isAttendanceMarked = isAttendanceMarked
    )

    companion object {
        fun fromDomain(l: Lesson): LessonEntity = LessonEntity(
            id = l.id,
            date = l.date,
            dayOfWeek = l.dayOfWeek,
            lentaNum = l.lentaNum,
            startTime = l.startTime,
            endTime = l.endTime,
            subject = l.subject,
            room = l.room,
            group = l.group,
            branchId = l.branchId,
            branchName = l.branchName,
            branchCategory = l.branchCategory,
            branchColor = l.branchColor,
            isAttendanceMarked = l.isAttendanceMarked
        )
    }
}

@Entity(tableName = "task_queue")
data class TaskQueueEntity(
    @PrimaryKey val id: Int = 1,
    val newHomeworkCount: Int,
    val pendingReviewsCount: Int,
    val oldestHwWaitDays: Int,
    val oldestReviewWaitDays: Int,
    val oldestHwWaitHours: Int = 0,
    val rawStudentsJson: String = "[]"
)

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey val cityId: String,
    val cityName: String,
    val category: String,
    val colorHex: String
) {
    fun toDomain(): Branch = Branch(cityId, cityName, category, colorHex)
    companion object {
        fun fromDomain(b: Branch): BranchEntity = BranchEntity(b.cityId, b.cityName, b.category, b.colorHex)
    }
}

@Entity(tableName = "teacher_profile")
data class TeacherProfileEntity(
    @PrimaryKey val id: Int = 1,
    val teacherId: String = "",
    val name: String,
    val email: String
)

@Dao
interface LessonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<LessonEntity>)

    @Query("SELECT * FROM lessons WHERE date BETWEEN :startDate AND :endDate ORDER BY lentaNum ASC, startTime ASC")
    fun getLessonsForWeek(startDate: String, endDate: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE date = :date ORDER BY lentaNum ASC, startTime ASC")
    fun getLessonsForDate(date: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE date BETWEEN :startDate AND :endDate ORDER BY lentaNum ASC, startTime ASC")
    suspend fun getLessonsForWeekSync(startDate: String, endDate: String): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE date = :date ORDER BY lentaNum ASC, startTime ASC")
    suspend fun getLessonsForDateSync(date: String): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE date >= :fromDate ORDER BY date ASC, startTime ASC")
    suspend fun getUpcomingLessonsSync(fromDate: String): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE id = :lessonId LIMIT 1")
    suspend fun getLessonByIdSync(lessonId: String): LessonEntity?

    @Query("DELETE FROM lessons WHERE date BETWEEN :startDate AND :endDate")
    suspend fun clearWeek(startDate: String, endDate: String)

    @Query("DELETE FROM lessons")
    suspend fun clearAllLessons()

    @Query("UPDATE lessons SET isAttendanceMarked = :marked WHERE id = :lessonId")
    suspend fun updateAttendanceStatus(lessonId: String, marked: Boolean)

    @Query("SELECT id FROM lessons WHERE isAttendanceMarked = 1")
    suspend fun getMarkedLessonIds(): List<String>
}

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskQueue(task: TaskQueueEntity)

    @Query("SELECT * FROM task_queue WHERE id = 1")
    fun getTaskQueue(): Flow<TaskQueueEntity?>

    @Query("SELECT * FROM task_queue WHERE id = 1")
    suspend fun getTaskQueueSync(): TaskQueueEntity?

    @Query("DELETE FROM task_queue")
    suspend fun clearTasks()
}

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: TeacherProfileEntity)

    @Query("SELECT * FROM teacher_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<TeacherProfileEntity?>

    @Query("DELETE FROM teacher_profile")
    suspend fun clearProfile()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBranches(branches: List<BranchEntity>)

    @Query("SELECT * FROM branches")
    fun getBranches(): Flow<List<BranchEntity>>

    @Query("DELETE FROM branches")
    suspend fun clearBranches()
}

@Database(
    entities = [LessonEntity::class, TaskQueueEntity::class, BranchEntity::class, TeacherProfileEntity::class],
    version = 4,
    exportSchema = false
)
abstract class OmniDatabase : RoomDatabase() {
    abstract fun lessonDao(): LessonDao
    abstract fun taskDao(): TaskDao
    abstract fun profileDao(): ProfileDao

    suspend fun clearAllUserData() {
        lessonDao().clearAllLessons()
        taskDao().clearTasks()
        profileDao().clearProfile()
        profileDao().clearBranches()
    }

    companion object {
        @Volatile
        private var INSTANCE: OmniDatabase? = null

        fun getInstance(context: Context): OmniDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OmniDatabase::class.java,
                    "omni_native.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
