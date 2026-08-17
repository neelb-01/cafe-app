package com.adl.cafe.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.adl.cafe.data.model.MenuItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuDao {

    @Query("SELECT COUNT(*) FROM menu_items")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(items: List<MenuItem>)

    @Query("SELECT * FROM menu_items WHERE id = :id")
    fun observeById(id: Long): Flow<MenuItem?>

    @Query("SELECT DISTINCT category FROM menu_items ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    /**
     * One query backs the whole menu screen: [category] of null means "All",
     * and a blank [query] disables the text filter.
     */
    @Query(
        """
        SELECT * FROM menu_items
        WHERE isAvailable = 1
          AND (:category IS NULL OR category = :category)
          AND (:query = '' OR name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
        ORDER BY isPopular DESC, name ASC
        """
    )
    fun observeMenu(category: String?, query: String): Flow<List<MenuItem>>
}
