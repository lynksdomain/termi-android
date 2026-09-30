package com.termi.android.music

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The application context binding survives rotation, and is released when the UI closes. */
class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val _service = MutableStateFlow<PlaybackService?>(null)
    val service = _service.asStateFlow()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            _service.value = (binder as PlaybackService.LocalBinder).service
        }
        override fun onServiceDisconnected(name: ComponentName?) { _service.value = null }
    }
    private val bound = application.bindService(
        Intent(application, PlaybackService::class.java).setAction(PlaybackService.BIND_UI),
        connection, Context.BIND_AUTO_CREATE,
    )
    override fun onCleared() {
        if (bound) getApplication<Application>().unbindService(connection)
        _service.value = null
    }
}
