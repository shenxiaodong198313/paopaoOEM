package com.paopaoime.aikeyboard.ime.database.entry

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kb_directory")
data class KbDirectory(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "createdAtMillis")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updatedAtMillis")
    val updatedAtMillis: Long,
    @ColumnInfo(name = "deletable")
    val deletable: Int,
)


