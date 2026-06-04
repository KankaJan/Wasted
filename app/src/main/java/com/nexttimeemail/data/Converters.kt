package com.nexttimeemail.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun rateTypeToString(value: RateType): String = value.name

    @TypeConverter
    fun stringToRateType(value: String): RateType = RateType.valueOf(value)
}
