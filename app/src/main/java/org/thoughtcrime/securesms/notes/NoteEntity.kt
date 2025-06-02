package org.thoughtcrime.securesms.notes

data class NoteEntity(
    val id: Long,
    val title: String,
    val content: String,
    val colorId: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
