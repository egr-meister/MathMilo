package com.mathmilo

import android.app.Application
import com.mathmilo.data.repository.AppContainer

class MathMiloApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
