package com.learneverywhere.app.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
        val controllerPlayer = ControllerBackedPlayer(Looper.getMainLooper())
        player = controllerPlayer
        mediaSession = MediaSession.Builder(this, controllerPlayer).build()

        val controller = PlaybackController.active
        if (controller != null) {
            controller.currentWord.onEach { controllerPlayer.refreshState() }.launchIn(serviceScope)
            controller.isPlaying.onEach { controllerPlayer.refreshState() }.launchIn(serviceScope)
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
}
