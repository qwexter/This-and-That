package xyz.qwexter.tat.models

import kotlinx.datetime.Instant

value class RecordId(val id: String)

value class RecordTitle(val title: String)

data class Record(
    val id: RecordId,
    val ownerId: String,
    val title: RecordTitle,
    val content: String?,
    val createdAt: Instant,
    val updatedAt: Instant?,
    val deletedAt: Instant?,
)
