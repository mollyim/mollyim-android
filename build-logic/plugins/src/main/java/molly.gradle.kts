import com.android.build.gradle.AppExtension
import org.jetbrains.kotlin.com.google.gson.Gson
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Utility object for parsing and manipulating strings.xml files.
 */
object StringsXmlParser {
  fun parse(stringsFile: File): Pair<Document, List<Element>> {
    val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val doc = docBuilder.parse(stringsFile).apply {
      xmlStandalone = true
    }
    return doc to doc.getElements("string") + doc.getElements("plurals")
  }

  fun writeToFile(doc: Document, file: File) {
    val transformer = TransformerFactory.newInstance().newTransformer()
    transformer.transform(DOMSource(doc), StreamResult(file))
  }

  private fun Document.getElements(tagName: String) =
    getElementsByTagName(tagName).let { nodes ->
      (0 until nodes.length).map { nodes.item(it) as Element }
    }
}

val updateTranslationsForMolly by tasks.registering {
  group = "Molly"
  description = "Updates references to \"Signal\" with \"Molly\" in all translation files."

  doLast {
    val englishFile = file("src/main/res/values/strings.xml")
    val (_, englishStrings) = StringsXmlParser.parse(englishFile)

    // Gather all string names containing "mollyify" attribute
    val mollyifyList = englishStrings
      .filter { it.getAttribute("mollyify") == "true" }
      .map { it.getAttribute("name") }
      .toSet()

    // Iterate through each translation file and perform the replacements
    project.fileTree("src/main/res").matching {
      include("**/values-*/strings.xml")
    }.forEach { translationFile ->
      try {
        val (translationDoc, translatedStrings) = StringsXmlParser.parse(translationFile)
        var modified = false

        translatedStrings.forEach { elem ->
          val name = elem.getAttribute("name")
          if (name in mollyifyList) {
            when (elem.tagName) {
              "string" -> {
                modified = elem.replaceSignalRefs() or modified
              }

              "plurals" -> {
                val items = elem.getElementsByTagName("item")
                for (i in 0 until items.length) {
                  val item = items.item(i) as Element
                  modified = item.replaceSignalRefs() or modified
                }
              }
            }
          }
        }

        if (modified) {
          // Write back the modified translation file only if replacements were made
          StringsXmlParser.writeToFile(translationDoc, translationFile)
          logger.lifecycle("Updated translations in: ${translationFile.path}")
        }
      } catch (e: Exception) {
        logger.error("Error processing file: ${translationFile.path}, ${e.message}")
      }
    }
  }
}

private fun Element.replaceSignalRefs(): Boolean {
  val oldContent = textContent
  textContent = textContent
    .replace("Signal", "Molly")
    .replace("signal.org", "molly.im")
  return oldContent != textContent
}

val version by tasks.registering {
  doLast {
    val android = project.extensions.getByType(AppExtension::class)
    val versionCode = android.defaultConfig.versionCode
    val versionName = android.defaultConfig.versionName

    val versionInfo = mapOf(
      "versionCode" to versionCode,
      "versionName" to versionName
    )

    println(Gson().toJson(versionInfo))
  }
}
