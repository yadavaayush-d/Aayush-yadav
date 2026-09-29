package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AIRequestDao
import com.example.data.local.dao.AIUsageDao
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.CreatorDao
import com.example.data.local.dao.DocumentDao
import com.example.data.local.dao.FeedbackDao
import com.example.data.local.dao.LanguageDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.QuizDao
import com.example.data.local.dao.StudyActivityDao
import com.example.data.local.dao.StudyPlanDao
import com.example.data.local.dao.SubscriptionDao
import com.example.data.local.dao.UserDao
import com.example.data.local.entity.AIRequestEntity
import com.example.data.local.entity.AIUsageEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.CreatorProjectEntity
import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentNoteEntity
import com.example.data.local.entity.FeedbackEntity
import com.example.data.local.entity.LanguagePracticeSessionEntity
import com.example.data.local.entity.LanguageProjectEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.QuizAttemptEntity
import com.example.data.local.entity.QuizQuestionEntity
import com.example.data.local.entity.SavedCreatorContentEntity
import com.example.data.local.entity.SavedLanguageContentEntity
import com.example.data.local.entity.StudyActivityEntity
import com.example.data.local.entity.StudyPlanEntity
import com.example.data.local.entity.SubscriptionEntity
import com.example.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AIRequestEntity::class,
        AIUsageEntity::class,
        DocumentEntity::class,
        ProjectEntity::class,
        SubscriptionEntity::class,
        FeedbackEntity::class,
        QuizAttemptEntity::class,
        QuizQuestionEntity::class,
        StudyPlanEntity::class,
        StudyActivityEntity::class,
        CreatorProjectEntity::class,
        SavedCreatorContentEntity::class,
        DocumentChunkEntity::class,
        DocumentNoteEntity::class,
        LanguageProjectEntity::class,
        SavedLanguageContentEntity::class,
        LanguagePracticeSessionEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class NexaDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun aiRequestDao(): AIRequestDao
    abstract fun aiUsageDao(): AIUsageDao
    abstract fun documentDao(): DocumentDao
    abstract fun projectDao(): ProjectDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun quizDao(): QuizDao
    abstract fun studyPlanDao(): StudyPlanDao
    abstract fun studyActivityDao(): StudyActivityDao
    abstract fun creatorDao(): CreatorDao
    abstract fun languageDao(): LanguageDao

    companion object {
        @Volatile
        private var INSTANCE: NexaDatabase? = null

        fun getDatabase(context: Context): NexaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NexaDatabase::class.java,
                    "nexa_ai_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
