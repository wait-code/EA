package com.examreader.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.examreader.ExamReaderApp
import com.examreader.MainActivity
import com.examreader.R
import com.examreader.data.DraftMapper
import com.examreader.data.DraftStore
import com.examreader.data.ScanJobBus
import com.examreader.data.ScanState
import com.examreader.data.VisionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 后台扫描前台服务：
 *   · 用户离开「扫描试卷出卷」页（甚至退到桌面）后继续识别
 *   · 常驻通知展示进度（多数国产 ROM 会把它渲染成胶囊/灵动岛样式）
 *   · 完成后发通知提醒；同时通过 ScanJobBus 让 App 内任意页面的顶部横幅同步
 *   · 识别结果立刻写成草稿（drafts 表），编辑器再每 10 秒自动保存
 */
class ScanService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uris = intent?.getStringArrayListExtra(EXTRA_URIS)
            ?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .orEmpty()
        if (uris.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundWithProgress(0, uris.size, "准备扫描…")
        if (job == null) {
            job = scope.launch { runScan(uris) }
        }
        return START_NOT_STICKY
    }

    private suspend fun runScan(uris: List<Uri>) {
        val app = application as ExamReaderApp
        runCatching {
            val settings = app.settingsRepository.current()
            val exam = VisionClient(settings).scan(this, uris) { done, total, message ->
                ScanJobBus.publish(ScanState.Running(done, total, message))
                updateProgress(done, total, message)
            }
            val draft = DraftMapper.fromCanonical(exam)
            draft.sourceLabel = "后台扫描"
            DraftStore.put(draft)

            val compiled = DraftMapper.toCompiled(draft)
            app.draftRepository.save(compiled, emptyMap(), "后台扫描")

            ScanJobBus.publish(ScanState.Success(compiled.id, compiled.meta.title))
            finishNotification(true, compiled.meta.title)
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) {
                // 用户点了「取消扫描」：不要报失败，直接复位
                ScanJobBus.publish(ScanState.Idle)
                notificationManager.cancel(NOTIFICATION_ID)
            } else {
                val message = e.message ?: "扫描失败（未知错误）"
                ScanJobBus.publish(ScanState.Failed(message))
                finishNotification(false, message)
            }
        }
        stopSelf()
    }

    // ---------------------------------------------------------------- 通知

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun baseNotification(title: String, text: String, ongoing: Boolean): NotificationCompat.Builder =
        NotificationCompat.Builder(this, ExamReaderApp.SCAN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(ongoing)
            .setAutoCancel(!ongoing)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)

    private fun startForegroundWithProgress(done: Int, total: Int, text: String) {
        val notification = baseNotification("正在扫描试卷", text, ongoing = true)
            .setProgress(total.coerceAtLeast(1), done, total <= 0)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateProgress(done: Int, total: Int, text: String) {
        val notification = baseNotification("正在扫描试卷（$done/$total）", text, ongoing = true)
            .setProgress(total.coerceAtLeast(1), done, total <= 0)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun finishNotification(success: Boolean, detail: String) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        val notification = if (success) {
            baseNotification("扫描完成", "$detail · 点此打开编辑器", ongoing = false)
        } else {
            baseNotification("扫描失败", detail.take(60), ongoing = false)
        }.build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URIS = "extra_uris"
        const val NOTIFICATION_ID = 4201

        fun start(context: Context, uris: List<Uri>) {
            val intent = Intent(context, ScanService::class.java).apply {
                putStringArrayListExtra(EXTRA_URIS, ArrayList(uris.map { it.toString() }))
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScanService::class.java))
        }
    }
}
