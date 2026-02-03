import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.util.Properties
import kotlin.apply

abstract class SupportedLocalesExtension(private val project: Project) {
  fun fromResDir(resDir: Directory): Provider<List<String>> {
    return project.providers.of(LanguageListValueSource::class.java) {
      parameters.resDir.set(resDir)
    }
  }
}

project.extensions.create("supportedLocales", SupportedLocalesExtension::class.java, project)

abstract class LanguageListValueSource : ValueSource<List<String>, LanguageListValueSource.Params> {
  interface Params : ValueSourceParameters {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val resDir: DirectoryProperty
  }

  override fun obtain(): List<String> {
    // In API 35, language codes for Hebrew and Indonesian now use the ISO 639-1 code ("he" and "id").
    // However, the value resources still only support the outdated code ("iw" and "in") so we have
    // to manually indicate that we support these languages.
    val updatedLanguageCodes = listOf("he", "id")

    val resRoot = parameters.resDir.asFile.get()

    val languages = resRoot
      .walkTopDown()
      .filter { it.isFile && it.name == "strings.xml" }
      .mapNotNull { stringFile -> stringFile.parentFile?.name }
      .map { valuesFolderName -> valuesFolderName.removePrefix("values-") }
      .filter { valuesFolderName -> valuesFolderName != "values" }
      .map { languageCode -> languageCode.replace("-r", "_") }
      .toList()
      .distinct()
      .sorted()

    return languages + updatedLanguageCodes + "en"
  }
}

abstract class PropertiesFileValueSource : ValueSource<Properties?, PropertiesFileValueSource.Params> {
  interface Params : ValueSourceParameters {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val file: RegularFileProperty
  }

  override fun obtain(): Properties? {
    val f: File = parameters.file.asFile.get()
    if (!f.exists()) return null

    return Properties().apply {
      f.inputStream().use { load(it) }
    }
  }
}
