package com.safeqr.scanner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reported_websites")
data class ReportEntity(
    @PrimaryKey val url: String,
    val issue: String,
    val timestamp: Long = System.currentTimeMillis()
)
