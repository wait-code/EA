package com.examreader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.examreader.data.AppDatabase
import com.examreader.data.DraftRepository
import com.examreader.data.ExamRepository
import com.examreader.data.GlossaryRepository
import com.examreader.data.SettingsRepository
import com.examreader.data.StudyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExamReaderApp : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var examRepository: ExamRepository
        private set
    lateinit var studyRepository: StudyRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var draftRepository: DraftRepository
        private set
    lateinit var glossaryRepository: GlossaryRepository
        private set

    /** 思维导图是 SVG，需要显式注册解码器 */
    lateinit var imageLoader: ImageLoader
        private set

    /** 应用级协程作用域：界面销毁后仍可保存草稿等 */
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.build(this)
        examRepository = ExamRepository(this, database)
        studyRepository = StudyRepository(database)
        settingsRepository = SettingsRepository(database)
        draftRepository = DraftRepository(database)
        glossaryRepository = GlossaryRepository(database)
        imageLoader = ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .crossfade(true)
            .build()
        createScanChannel()
        backgroundScope.launch { examRepository.seedIfEmpty() }
    }

    private fun createScanChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(SCAN_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            SCAN_CHANNEL_ID,
            "试卷扫描",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "后台扫描试卷的进度与完成提醒"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val SCAN_CHANNEL_ID = "scan_status"

        fun from(context: Context): ExamReaderApp = context.applicationContext as ExamReaderApp
    }
}
