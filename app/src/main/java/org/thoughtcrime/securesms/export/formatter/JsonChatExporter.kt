package org.thoughtcrime.securesms.export.formatter

import com.google.gson.GsonBuilder // A common JSON library, replace if another is standard in the project
import org.thoughtcrime.securesms.export.model.ExportedMessage

/**
 * Exports a list of chat messages to a JSON string.
 */
class JsonChatExporter : ChatExporter {

    override fun export(messages: List<ExportedMessage>): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(messages)
    }

    override fun getFileExtension(): String {
        return "json"
    }
}
