package com.sziffer.challenger.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.model.challenge.PublicRouteItem
import java.util.*

class Converters {

    @TypeConverter
    fun listToJsonString(value: ArrayList<PublicRouteItem>?): String = Gson().toJson(value)

    @TypeConverter
    fun jsonStringToList(value: String): ArrayList<PublicRouteItem> {
        val typeJson = object : TypeToken<ArrayList<PublicRouteItem>>() {}.type
        return Gson().fromJson(value, typeJson)
    }

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time?.toLong()
    }
}