// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.smartcardreader.nfc.example.util

import android.content.ContentResolver
import android.content.Intent

class FileData(
    private var fileName: String,
    private var intent: Intent,
    private var contentResolver: ContentResolver
) {

    fun getFileName(): String {
        return fileName
    }

    fun getFileIntent(): Intent {
        return intent
    }

    fun getContentResolver(): ContentResolver {
        return contentResolver
    }

}