import java.util.Properties

val envFile: File? = rootProject.file("environment.properties")
val envProps = Properties()

if (envFile?.exists() ?: false) {
    envFile.inputStream().use { envProps.load(it) }
}
val libVersion: String? = envProps.getProperty("version")
val libSuffix: String? = envProps.getProperty("suffix")

subprojects {
    pluginManager.withPlugin("com.android.library") {
        if (!libVersion.isNullOrBlank() || !libSuffix.isNullOrBlank()) {
            val parts = mutableListOf(project.name)

            if (!libVersion.isNullOrBlank()) {
                parts.add(libVersion)
            }

            if (!libSuffix.isNullOrBlank()) {
                parts.add(libSuffix)
            }

            project.extensions.configure<BasePluginExtension>("base") {
                archivesName.set(parts.joinToString("-"))
            }
        }
    }
}
