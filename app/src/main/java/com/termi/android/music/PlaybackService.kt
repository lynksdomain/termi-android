package com.termi.android.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.termi.android.MainActivity
import com.termi.android.R
import com.termi.android.data.TermiDatabase
import com.termi.android.pkgs.MixtapePlaybackManager
import com.termi.android.stats.MusicStatsManager

/** Owns both players across activity recreation and while playback is in the background. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    lateinit var music: AudioPlayerEngine
        private set
    lateinit var mixtape: MixtapePlaybackManager
        private set
    lateinit var musicStats: MusicStatsManager
        private set
    private var session: MediaSession? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: PlaybackService get() = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        music = AudioPlayerEngine(applicationContext) { requestPlay(this, MUSIC) }
        mixtape = MixtapePlaybackManager(applicationContext) { requestPlay(this, MIXTAPE) }
        musicStats = MusicStatsManager(TermiDatabase.getInstance(this).listenEventDao())
        music.onSongTransition = { previous, elapsed, next ->
            if (previous != null) musicStats.endListening(elapsed / 1000.0)
            if (next != null) musicStats.beginListening(next)
        }
        session = MediaSession.Builder(this, music.player)
            .setSessionActivity(openAppIntent()).build()
        addSession(requireNotNull(session))
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == BIND_UI) binder else super.onBind(intent)

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EXTRA_PLAYER)
        if (mode == MUSIC || mode == MIXTAPE) {
            // Android 15 requires a foreground service before an audio-focus request.
            // Media3 replaces this short startup notification with its media notification.
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW))
            startForeground(NOTIFICATION_ID, Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("Termi")
                .setContentText("Starting playback").setContentIntent(openAppIntent()).build())
            val active: Player = if (mode == MIXTAPE) {
                music.pause()
                mixtape.player
            } else {
                mixtape.player.pause()
                music.player
            }
            session?.setPlayer(active)
            active.play()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!music.player.isPlaying && !mixtape.player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        music.release()
        mixtape.release()
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    companion object {
        const val BIND_UI = "com.termi.android.BIND_PLAYBACK"
        private const val MUSIC = "music"
        private const val MIXTAPE = "mixtape"
        private const val EXTRA_PLAYER = "player"
        private const val CHANNEL = "termi_playback"
        private const val NOTIFICATION_ID = 1001
        private fun requestPlay(context: Context, mode: String) {
            ContextCompat.startForegroundService(context,
                Intent(context, PlaybackService::class.java).putExtra(EXTRA_PLAYER, mode))
        }
    }
}
