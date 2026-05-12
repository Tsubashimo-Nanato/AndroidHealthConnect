package com.example.healthconnectandroid.data

import androidx.room.TypeConverter
import java.time.Instant

class Converters {
    @TypeConverter fun longToInstant(v: Long?): Instant? = v?.let { Instant.ofEpochMilli(it) }
    @TypeConverter fun instantToLong(i: Instant?): Long? = i?.toEpochMilli()
}
