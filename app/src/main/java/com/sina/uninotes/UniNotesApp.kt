package com.sina.uninotes

import android.app.Application
import com.sina.uninotes.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UniNotesApp : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            container.backupRepository.recoverRestore()
            container.photoRepository.recoverInterruptedCaptures()
        }
    }
}
