package com.learneverywhere.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.learneverywhere.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Android-обгортка (interfaces.md §playback: "ховає... медіа-сесію,
 * foreground-сервіс"). Не тримає жодної логіки черги — тільки:
 * - тримає системну `MediaSession` над [ControllerBackedPlayer], що
 *   відбиває [PlaybackController.active] (кнопки паузи/пропуску на екрані
 *   блокування/навушниках, R44) і промотує процес у foreground (R45,
 *   працює з вимкненим екраном);
 * - тримає audio focus і при втраті (вхідний дзвінок) ставить контролер на
 *   паузу, а при поверненні — відновлює (R45.1).
 *
 * Запускається й зупиняється самим [PlaybackController] (`startForegroundService`
 * у [PlaybackController.start], `stopService` у [PlaybackController.stop]) —
 * ніхто інший не звертається до цього сервісу напряму.
 *
 * **Фікс тікета 13 (`ForegroundServiceDidNotStartInTimeException`):** `onCreate()`
 * викликає [android.app.Service.startForeground] синхронно, першою дією, з базовою
 * нотифікацією — до побудови `MediaSession` і до запиту audio focus. Раніше сервіс
 * покладався на автоматичне просування в foreground всередині `MediaSessionService`
 * (через `MediaNotificationManager`, що реагує на зміни стану `Player`) — цей
 * механізм не гарантує виклику `startForeground` у межах системного таймауту
 * (кілька секунд від `startForegroundService`), тому Android вбивав процес. Зараз
 * `startForeground` не залежить від готовності `MediaSession`/`Player`/черги слів;
 * актуальний вміст (поточне слово) підвантажується окремо через
 * `NotificationManager.notify(...)` у [observeNotificationContent], коли
 * [PlaybackController.currentWord] стає відомим.
 */
@UnstableApi
class PlaybackMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ControllerBackedPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeFocusLoss = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val controller = PlaybackController.active
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                wasPlayingBeforeFocusLoss = controller?.isPlaying?.value == true
                controller?.pause()
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlayingBeforeFocusLoss) controller?.resume()
                wasPlayingBeforeFocusLoss = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Синхронно й негайно — до MediaSession, до Player, до audio focus. Це те,
        // що зупиняє системний таймер ForegroundServiceDidNotStartInTimeException;
        // усе, що йде нижче, може тривати скільки завгодно без ризику краху.
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.playback_notification_starting)))

        val controllerPlayer = ControllerBackedPlayer(Looper.getMainLooper())
        player = controllerPlayer
        mediaSession = MediaSession.Builder(this, controllerPlayer).build()

        val controller = PlaybackController.active
        if (controller != null) {
            controller.currentWord.onEach { controllerPlayer.refreshState() }.launchIn(serviceScope)
            controller.isPlaying.onEach { controllerPlayer.refreshState() }.launchIn(serviceScope)
            observeNotificationContent(controller)
        }

        requestAudioFocus()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        abandonAudioFocus()
        serviceScope.cancel()
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    /** Оновлює вміст уже показаної нотифікації (не `startForeground` вдруге — той
     * виклик уже відбувся синхронно в [onCreate]) щойно відомо поточне слово. */
    private fun observeNotificationContent(controller: PlaybackController) {
        controller.currentWord.onEach { word ->
            val text = word?.let { getString(R.string.playback_notification_now_playing, it.ukrainian) }
                ?: getString(R.string.playback_notification_starting)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, buildNotification(text))
        }.launchIn(serviceScope)
    }

    private fun buildNotification(contentText: String): Notification {
        ensureNotificationChannel()
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.playback_notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.playback_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.playback_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun requestAudioFocus() {
        val manager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = manager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        manager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        focusRequest?.let { manager.abandonAudioFocusRequest(it) }
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
    }
}
