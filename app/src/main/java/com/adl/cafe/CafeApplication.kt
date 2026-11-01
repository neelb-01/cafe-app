package com.adl.cafe

import android.app.Application
import android.util.Log
import com.adl.cafe.data.AppDatabase
import com.adl.cafe.data.CafeRepository
import com.adl.cafe.data.remote.SupabaseCafeRemote
import com.adl.cafe.data.remote.SupabaseChatRemote
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency container. The project is small enough that a DI framework
 * would cost more in build setup than it saves.
 */
class CafeApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: CafeRepository by lazy {
        check(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()) {
            "Set supabase.url and supabase.key in local.properties"
        }
        val db = AppDatabase.getInstance(this)
        val supabase = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
            install(Postgrest)
            install(Functions)
            // The assistant can take several seconds to reply; the default is 10.
            requestTimeout = 60.seconds
        }
        CafeRepository(
            db,
            db.menuDao(),
            db.cartDao(),
            db.orderDao(),
            SupabaseCafeRemote(supabase),
            SupabaseChatRemote(supabase)
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            repository.seedIfEmpty()
            // Offline, or the backend is down: keep showing the menu we have.
            try {
                repository.refreshMenu()
            } catch (e: Exception) {
                Log.w(TAG, "Menu refresh failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "CafeApplication"
    }
}
