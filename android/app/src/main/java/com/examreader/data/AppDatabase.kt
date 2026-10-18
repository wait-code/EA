package com.examreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PaperEntity::class,
        SectionEntity::class,
        ParagraphEntity::class,
        QuestionEntity::class,
        VocabEntity::class,
        DictEntryEntity::class,
        QuizPartEntity::class,
        QuizItemEntity::class,
        StudyStateEntity::class,
        QuizAnswerEntity::class,
        WordbookEntity::class,
        SentenceEntity::class,
        SettingEntity::class,
        ImportLogEntity::class,
        DraftEntity::class,
        GlossaryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun content(): ContentDao
    abstract fun study(): StudyDao
    abstract fun settings(): SettingsDao
    abstract fun draft(): DraftDao
    abstract fun glossary(): GlossaryDao

    companion object {
        private const val NAME = "exam-reader.db"

        /** v1 -> v2：新增草稿表（升级不清库） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `drafts` (" +
                        "`id` TEXT NOT NULL, " +
                        "`json` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        /** v2 -> v3：试卷增加 stage（scanned 扫描卷 / analyzed 解析卷） */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `papers` ADD COLUMN `stage` TEXT NOT NULL DEFAULT 'analyzed'")
            }
        }

        /** v3 -> v4：新增全局词库表 glossary（首次查阅机器翻译自动收录 / 外部 SQLite 词库导入） */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `glossary` (" +
                        "`word` TEXT NOT NULL, " +
                        "`phonetic` TEXT NOT NULL DEFAULT '', " +
                        "`partOfSpeech` TEXT NOT NULL DEFAULT '', " +
                        "`zh` TEXT NOT NULL DEFAULT '', " +
                        "`source` TEXT NOT NULL DEFAULT 'machine', " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`word`))"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
