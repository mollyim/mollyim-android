package org.thoughtcrime.securesms.export.formatter

import org.thoughtcrime.securesms.export.model.ExportedMessage

/**
 * Interface for chat exporters.
 */
interface ChatExporter {
    fun export(messages: List<ExportedMessage>): String
    fun getFileExtension(): String
}
