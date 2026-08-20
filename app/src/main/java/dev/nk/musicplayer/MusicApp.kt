package dev.nk.musicplayer

import android.app.Application

class MusicApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
