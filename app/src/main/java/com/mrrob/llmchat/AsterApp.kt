package com.mrrob.llmchat

import android.app.Application
import androidx.room.Room
import com.mrrob.llmchat.data.AppRepository
import com.mrrob.llmchat.data.AsterDatabase
import com.mrrob.llmchat.data.ConnectivityMonitor
import com.mrrob.llmchat.data.DebugLog
import com.mrrob.llmchat.data.LlmClient
import com.mrrob.llmchat.data.SettingsStore

/** App-wide singletons; created once, injected into every view model. */
class AppContainer(context: Application) {
    val settingsStore = SettingsStore(context)
    val debugLog = DebugLog()
    val database: AsterDatabase = Room.databaseBuilder(context, AsterDatabase::class.java, "aster.db")
        .fallbackToDestructiveMigration()
        .build()
    val client = LlmClient(settingsStore)
    val repository = AppRepository(database, settingsStore, client)
    val connectivity = ConnectivityMonitor(context)
}

class AsterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
