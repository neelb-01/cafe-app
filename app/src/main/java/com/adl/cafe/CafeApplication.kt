package com.adl.cafe

import android.app.Application
import com.adl.cafe.data.AppDatabase
import com.adl.cafe.data.CafeRepository
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
        val db = AppDatabase.getInstance(this)
        CafeRepository(db.menuDao(), db.cartDao(), db.orderDao())
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { repository.seedIfEmpty() }
    }
}
