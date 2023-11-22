package ee.ria.DigiDoc.smartcardreader.nfc.example.util

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.common.io.Files
import ee.ria.libdigidocpp.Container
import ee.ria.libdigidocpp.Signature
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object Utils {

    val origin = "ivxv.valimised.ee:443"

    const val SIGNATURE_CONTAINER_EXTENSION = "asice"
    private val containerFiles: MutableList<FileData> = mutableListOf()
    lateinit var container: Container
    lateinit var signature: Signature
    var signatureProfile = "time-stamp"
    var signatureIsAdded = false

    fun addFileContent(
        fileName: String,
        intent: Intent,
        contentResolver: ContentResolver
    ) {
        val fileData = FileData(fileName, intent, contentResolver)
        containerFiles.add(fileData)
    }

    fun getContainerFilesList(): MutableList<FileData> {
        return containerFiles
    }

    fun clearContainerFilesList() {
        containerFiles.clear()
    }

    fun deleteCachedDataFiles(activity: Activity) {
        val file = File(activity.cacheDir.absolutePath + "/datafiles")
        file.deleteRecursively()
    }

    fun getFileNameAndSize(uri: Uri, context: Context): Pair<String, Long> {
        var fileName: String = uri.lastPathSegment!!
        var fileSize: Long = 0
        if (uri.scheme.equals("content")) {
            try {
                val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)!!
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                    cursor.close()
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
        }
        return Pair(fileName, fileSize)
    }

    fun removeFileExtension(fileName: String): String {
        val index = fileName.lastIndexOf(".")
        return fileName.substring(0, index)
    }

    fun signatureContainerFile(fileName: String, filesDir: File): File {
        val containerDir = createContainersDir(filesDir)
        val containerFile = File(containerDir, fileName)
        if (containerFile.canonicalPath.startsWith(containerDir.canonicalPath)) {
            Files.createParentDirs(containerFile)
        } else {
            throw IOException("Invalid file path")
        }
        return containerFile
    }

    private fun createContainersDir(filesDir: File): File {
        val dir = File(filesDir, "containers")
        if (dir.mkdirs()) {
            Timber.log(Log.DEBUG, "Directories created for %s", dir.path)
        }
        return dir
    }

    fun getFilesFromCache(cacheDir: String): MutableList<File> {
        val fileList: MutableList<File> = mutableListOf()
        for (file in File("$cacheDir/datafiles").listFiles()!!) {
            fileList.add(file)
        }
        return fileList
    }

    fun filesToCache(cacheDir: String) {
        val datafilesDir = createDataFilesDir(File(cacheDir))
        Files.createParentDirs(datafilesDir)
        for (fileData in containerFiles) {
            val cacheFile = File(datafilesDir, fileData.getFileName())
            if (cacheFile.canonicalPath.startsWith(File(cacheDir).canonicalPath)) {
                try {
                    val inp: InputStream =
                        fileData.getContentResolver()
                            .openInputStream(fileData.getFileIntent().data!!)!!
                    val outputStream: OutputStream = FileOutputStream(cacheFile)
                    inp.copyTo(outputStream, DEFAULT_BUFFER_SIZE)
                    inp.close()
                    outputStream.close()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    private fun createDataFilesDir(cacheDir: File): File {
        val dir = File(cacheDir, "datafiles")
        if (dir.mkdirs()) {
            Timber.log(Log.DEBUG, "Directories created for %s", dir.path)
        }
        return dir
    }

    fun getMimeType(index: Int): String {
        val uri = containerFiles[index].getFileIntent().data
        val contentResolver = containerFiles[index].getContentResolver()
        return contentResolver.getType(uri!!)!!
    }

    fun checkFreeSpace(container: Container, activity: Activity): Boolean {
        val storageFreeSpace: Long = activity.filesDir.freeSpace / 1024
        var containerSize: Long = 0
        for (file in container.dataFiles()) {
            containerSize += file.fileSize() / 1024
        }
        if (storageFreeSpace < containerSize) {
            return false
        }
        return true
    }

}