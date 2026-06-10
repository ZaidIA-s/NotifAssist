package com.notifassist.service

import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

/**
 * Mengelola kendali playback musik lintas app (Spotify, YT Music, dll)
 * menggunakan MediaSessionManager Android — tanpa perlu akses khusus per-app.
 */
object MediaControlService {

    private const val TAG = "MediaControl"

    /** Pause semua sesi musik yang sedang aktif */
    fun pauseMusic(context: Context) {
        try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = msm.getActiveSessions(null)
            for (session in sessions) {
                val state = session.playbackState?.state
                if (state == PlaybackState.STATE_PLAYING ||
                    state == PlaybackState.STATE_BUFFERING) {
                    session.transportControls.pause()
                }
            }
        } catch (e: SecurityException) {
            // Butuh izin MEDIA_CONTENT_CONTROL — sudah ada di manifest
            Log.w(TAG, "No permission to control media: ${e.message}")
            // Fallback: kirim key event pause
            sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
        } catch (e: Exception) {
            Log.e(TAG, "pauseMusic error: ${e.message}")
            sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
        }
    }

    /** Resume sesi musik yang sebelumnya di-pause */
    fun resumeMusic(context: Context) {
        try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = msm.getActiveSessions(null)
            for (session in sessions) {
                val state = session.playbackState?.state
                if (state == PlaybackState.STATE_PAUSED) {
                    session.transportControls.play()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "resumeMusic error: ${e.message}")
            sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    /** Lagu berikutnya */
    fun nextTrack(context: Context) {
        try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.getActiveSessions(null).firstOrNull()?.transportControls?.skipToNext()
                ?: sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        } catch (e: Exception) {
            sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    /** Lagu sebelumnya */
    fun previousTrack(context: Context) {
        try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.getActiveSessions(null).firstOrNull()?.transportControls?.skipToPrevious()
                ?: sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        } catch (e: Exception) {
            sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    /** Cek apakah ada musik yang sedang bermain */
    fun isMusicPlaying(context: Context): Boolean {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.getActiveSessions(null).any {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            }
        } catch (e: Exception) { false }
    }

    /** Ambil info lagu yang sedang diputar */
    fun getNowPlaying(context: Context): String? {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val session = msm.getActiveSessions(null)
                .firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: return null
            val meta = session.metadata ?: return null
            val title  = meta.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val artist = meta.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            if (title.isNotBlank()) "$title${if (artist.isNotBlank()) " oleh $artist" else ""}"
            else null
        } catch (e: Exception) { null }
    }

    /** Fallback: kirim key event ke sistem */
    private fun sendMediaKey(context: Context, keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up   = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        am.dispatchMediaKeyEvent(down)
        am.dispatchMediaKeyEvent(up)
    }
}
