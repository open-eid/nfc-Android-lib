package ee.ria.libdigidocpp

import android.content.Context
import android.content.res.Resources.NotFoundException
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.inject.Inject

class DigiDocWrapperImpl @Inject constructor(private val context: Context) : DigiDocWrapper {

    override fun schemaDirectory() : String {
        return File(context.cacheDir.absolutePath + File.separator + SCHEMA_DIR).canonicalPath
    }

    override fun install() {
        val schemaDirPath = schemaDirectory()
        if (isSchemaUnpacked(schemaDirPath).not()) {
            unpackSchema(schemaDirPath)
        }
    }

    private fun isSchemaUnpacked(schemaDirPath: String): Boolean {
        try {
            val schemaDir = File(schemaDirPath)
            val fileList = schemaDir.list()
            return fileList != null && fileList.any { it.endsWith(XML_SCHEMA_DEFINITION_EXTENSION) }
        } catch (npEx: NullPointerException) {
            throw Exception(npEx.message)
        } catch (secEx: SecurityException) {
            throw Exception(secEx.message)
        }
    }

    @SuppressWarnings("ThrowsCount", "NestedBlockDepth")
    private fun unpackSchema(schemaDirPath: String) {
        try {
            val schemaDir = File(schemaDirPath)
            if (schemaDir.exists().not()) {
                if (schemaDir.mkdir().not()) {
                    throw Exception("DIGIDOC_SCHEMA_UNPACK_CREATE_DIR")
                }
            }

            ZipInputStream(context.resources.openRawResource(R.raw.schema)).use { zipInput ->
                var zipEntry: ZipEntry? = zipInput.nextEntry
                while (zipEntry != null) {
                    val entryOutFile = File(schemaDirPath, zipEntry.name)
                    val entryCanonicalPath = entryOutFile.canonicalPath
                    if (entryCanonicalPath.startsWith(schemaDirPath)) {
                        zipInput.copyTo(FileOutputStream(entryCanonicalPath))
                        zipEntry = zipInput.nextEntry
                    } else {
                        throw Exception("DIGIDOC_SCHEMA_UNPACK_ZIP_PATH_VIOLATION")
                    }
                }
            }
        } catch (resNotFoundEx: NotFoundException) {
            throw Exception(resNotFoundEx.message)
        } catch (zipEx: ZipException) {
            throw Exception(zipEx.message)
        } catch (fileNotFoundEx: FileNotFoundException) {
            throw Exception(fileNotFoundEx.message)
        } catch (ioEx: IOException) {
            throw Exception(ioEx.message)
        } catch (secEx: SecurityException) {
            throw Exception(secEx.message)
        }
    }

    private companion object {
        init {
            System.loadLibrary("digidoc_java")
        }

        private const val SCHEMA_DIR = "schema"
        private const val XML_SCHEMA_DEFINITION_EXTENSION = ".xsd"
    }
}
