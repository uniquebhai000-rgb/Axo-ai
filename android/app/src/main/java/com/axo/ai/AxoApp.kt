package com.axo.ai

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.axo.ai.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(ctx: Context) {
    val db = Room.databaseBuilder(ctx, AxoDb::class.java, "axo.db").build()
    val prefs = Prefs(ctx)
    val tokens = TokenStore(ctx)
    val api = Api(tokens)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (db.dao().assistantCount() == 0) db.dao().upsertAssistants(Seed.assistants)
        }
    }
}

class AxoApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}
