package com.adl.cafe.data

import androidx.room.TypeConverter
import com.adl.cafe.data.model.DrinkSize
import com.adl.cafe.data.model.OrderStatus
import com.adl.cafe.data.model.OrderType

/** Enums are persisted by name, so reordering the enum can't corrupt saved rows. */
class Converters {

    @TypeConverter
    fun drinkSizeToString(value: DrinkSize): String = value.name

    @TypeConverter
    fun stringToDrinkSize(value: String): DrinkSize =
        DrinkSize.entries.firstOrNull { it.name == value } ?: DrinkSize.SMALL

    @TypeConverter
    fun orderTypeToString(value: OrderType): String = value.name

    @TypeConverter
    fun stringToOrderType(value: String): OrderType =
        OrderType.entries.firstOrNull { it.name == value } ?: OrderType.PICKUP

    @TypeConverter
    fun orderStatusToString(value: OrderStatus): String = value.name

    @TypeConverter
    fun stringToOrderStatus(value: String): OrderStatus =
        OrderStatus.entries.firstOrNull { it.name == value } ?: OrderStatus.PLACED
}
