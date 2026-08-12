/*
 * Copyright 2017 - 2025 Riigi Infosüsteemi Amet
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package ee.ria.DigiDoc.smartcardreader.nfc.example.util

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil.Companion.errorLog
import ee.ria.libdigidocpp.Container
import ee.ria.libdigidocpp.Signature
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object Utils {

    val origin = "ivxv.valimised.ee:443"
    private val logTag = javaClass.simpleName
    const val SIGNATURE_CONTAINER_EXTENSION = "asice"
    private const val DEFAULT_MIME_TYPE = "application/octet-stream"
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
        var fileName: String = uri.lastPathSegment ?: ""
        var fileSize: Long = 0
        if (uri.scheme.equals("content")) {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex)
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0) {
                            fileSize = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (exception: Exception) {
                errorLog(logTag, "Unable to read file name and size", exception)
            }
        }
        return Pair(fileName, fileSize)
    }

    fun removeFileExtension(fileName: String): String = fileName.substringBeforeLast('.')

    fun signatureContainerFile(fileName: String, filesDir: File): File {
        val containerDir = createContainersDir(filesDir)
        val containerFile = File(containerDir, fileName)
        if (!containerFile.canonicalFile.toPath().startsWith(containerDir.canonicalFile.toPath())) {
            throw IOException("Invalid file path")
        }
        val parent = containerFile.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Unable to create container parent directories")
        }
        return containerFile
    }

    private fun createContainersDir(filesDir: File): File {
        val dir = File(filesDir, "containers")
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("Unable to create containers directory")
        }
        return dir
    }

    fun getFilesFromCache(cacheDir: String): MutableList<File> =
        File("$cacheDir/datafiles").listFiles()?.toMutableList() ?: mutableListOf()

    fun filesToCache(cacheDir: String) {
        val datafilesDir = createDataFilesDir(File(cacheDir))
        for (fileData in containerFiles) {
            val cacheFile = File(datafilesDir, fileData.getFileName())
            if (!cacheFile.canonicalFile.toPath().startsWith(File(cacheDir).canonicalFile.toPath())) {
                errorLog(logTag, "Data file resolves outside the cache directory, skipping")
                continue
            }
            val uri = fileData.getFileIntent().data
            if (uri == null) {
                errorLog(logTag, "Data file has no URI, skipping")
                continue
            }
            try {
                fileData.getContentResolver().openInputStream(uri)?.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                } ?: errorLog(logTag, "Could not open data file, skipping")
            } catch (ex: Exception) {
                errorLog(logTag, "Unable to cache data file", ex)
            }
        }
    }

    private fun createDataFilesDir(cacheDir: File): File {
        val dir = File(cacheDir, "datafiles")
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("Unable to create $dir")
        }
        return dir
    }

    fun getMimeType(index: Int): String {
        val uri = containerFiles[index].getFileIntent().data ?: return DEFAULT_MIME_TYPE
        return containerFiles[index].getContentResolver().getType(uri) ?: DEFAULT_MIME_TYPE
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