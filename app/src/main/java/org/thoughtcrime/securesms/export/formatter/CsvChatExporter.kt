package org.thoughtcrime.securesms.export.formatter

import org.thoughtcrime.securesms.export.model.ExportedAttachment
import org.thoughtcrime.securesms.export.model.ExportedMessage
import org.thoughtcrime.securesms.export.model.ExportedQuote

/**
 * Exports a list of chat messages to a CSV formatted string.
 */
class CsvChatExporter : ChatExporter {

    override fun export(messages: List<ExportedMessage>): String {
        val stringBuilder = StringBuilder()

        // CSV Header
        stringBuilder.appendLine(
            ""Message ID"," +
            ""Thread ID"," +
            ""Sender ID"," +
            ""Sender Name"," +
            ""Date Sent (UTC ms)"," +
            ""Date Received (UTC ms)"," +
            ""Body"," +
            ""Message Type"," +
            ""Is View Once"," +
            ""Is Remote Delete"," +
            ""Quoted Author ID"," +
            ""Quoted Author Name"," +
            ""Quoted Text"," +
            ""Quoted Original Timestamp (UTC ms)"," +
            ""Attachments (Content Type|File Name|Local URI|Size|Is VoiceNote|Is Sticker)""
        )

        // CSV Rows
        messages.forEach { message ->
            stringBuilder.append(""${escapeCsvField(message.id.toString())}",")
            stringBuilder.append(""${escapeCsvField(message.threadId.toString())}",")
            stringBuilder.append(""${escapeCsvField(message.senderId)}",")
            stringBuilder.append(""${escapeCsvField(message.senderName ?: "")}",")
            stringBuilder.append(""${message.dateSent}",")
            stringBuilder.append(""${message.dateReceived}",")
            stringBuilder.append(""${escapeCsvField(message.body ?: "")}",")
            stringBuilder.append(""${escapeCsvField(message.messageType)}",")
            stringBuilder.append(""${message.isViewOnce}",")
            stringBuilder.append(""${message.isRemoteDelete}",")

            message.quote?.let { quote ->
                stringBuilder.append(""${escapeCsvField(quote.authorId)}",")
                stringBuilder.append(""${escapeCsvField(quote.authorName ?: "")}",")
                stringBuilder.append(""${escapeCsvField(quote.text ?: "")}",")
                stringBuilder.append(""${quote.originalTimestamp}",")
            } ?: stringBuilder.append(""","","","",") // Empty fields for no quote

            // Format attachments: "ContentType1|FileName1|LocalURI1|Size1;ContentType2|FileName2|LocalURI2|Size2"
            val attachmentsString = message.attachments.joinToString(";") { attachment ->
                "${escapeCsvField(attachment.contentType)}|" +
                "${escapeCsvField(attachment.fileName ?: "")}|" +
                "${escapeCsvField(attachment.localUri ?: "")}|" +
                "${attachment.size}|" +
                "${attachment.isVoiceNote}|" +
                "${attachment.isSticker}"
            }
            stringBuilder.append(""${escapeCsvField(attachmentsString)}"")
            stringBuilder.appendLine()
        }

        return stringBuilder.toString()
    }

    private fun escapeCsvField(field: String?): String {
        return field?.replace(""", """") ?: ""
    }

    override fun getFileExtension(): String {
        return "csv"
    }
}
