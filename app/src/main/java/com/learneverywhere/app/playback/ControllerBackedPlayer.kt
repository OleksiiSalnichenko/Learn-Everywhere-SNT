package com.learneverywhere.app.playback

import android.os.Looper
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Мінімальний `Player` для системної медіа-сесії (interfaces.md §playback,
 * "ховає... медіа-сесію"). Сам нічого не програє — лише віддзеркалює
 * [PlaybackController.active] (плей/пауза, заголовок треку = поточне
 * українське слово) і транслює системні команди (плей/пауза/skip з екрана
 * блокування чи навушників) назад у виклики контролера. Справжній рушій —
 * [PlaybackEngine] + `Speaker`, не цей клас.
 */
@UnstableApi
class ControllerBackedPlayer(applicationLooper: Looper) : SimpleBasePlayer(applicationLooper) {

    private val availableCommands = Player.Commands.Builder()
        .add(Player.COMMAND_PLAY_PAUSE)
        .add(Player.COMMAND_STOP)
        .add(Player.COMMAND_SEEK_TO_NEXT)
        .build()

    override fun getState(): State {
        val controller = PlaybackController.active
        val word = controller?.currentWord?.value
        val playing = controller?.isPlaying?.value == true

        val playlist = if (word == null) {
            emptyList()
        } else {
            listOf(
                MediaItemData.Builder(word.id)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(word.ukrainian).build())
                    .build(),
            )
        }

        return State.Builder()
            .setAvailableCommands(availableCommands)
            .setPlaylist(playlist)
            .setPlaybackState(if (word == null) Player.STATE_ENDED else Player.STATE_READY)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs(0L)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) PlaybackController.active?.resume() else PlaybackController.active?.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        PlaybackController.active?.stop()
        return Futures.immediateVoidFuture()
    }

    /** `COMMAND_SEEK_TO_NEXT` — єдина seek-команда, яку ми оголошуємо доступною (skip, A02). */
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        PlaybackController.active?.skipNext()
        return Futures.immediateVoidFuture()
    }

    /** [invalidateState] в базовому класі — `protected`; [PlaybackMediaService] сповіщає
     * про зміни [PlaybackController.currentWord]/[PlaybackController.isPlaying] через цей публічний міст. */
    fun refreshState() {
        invalidateState()
    }
}
