package com.adl.cafe.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.adl.cafe.data.dao.CartDao
import com.adl.cafe.data.dao.MenuDao
import com.adl.cafe.data.dao.OrderDao
import com.adl.cafe.data.model.CartItem
import com.adl.cafe.data.model.MenuItem
import com.adl.cafe.data.model.OrderEntity
import com.adl.cafe.data.model.OrderLine

@Database(
    entities = [MenuItem::class, CartItem::class, OrderEntity::class, OrderLine::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun menuDao(): MenuDao
    abstract fun cartDao(): CartDao
    abstract fun orderDao(): OrderDao

    companion object {
        private const val DB_NAME = "adl_cafe.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                // Sample project: schema changes just rebuild the DB instead of migrating.
                .fallbackToDestructiveMigration()
                .build()
    }
}
