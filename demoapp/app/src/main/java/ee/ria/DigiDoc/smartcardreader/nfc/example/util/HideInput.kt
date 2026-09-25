// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.smartcardreader.nfc.example.util

import android.text.method.PasswordTransformationMethod
import android.view.View

class HideInput : PasswordTransformationMethod() {
    companion object {
        const val HIDE_CHAR = '*'
    }

    override fun getTransformation(source: CharSequence, view: View): CharSequence {
        return PasswordCharSequence(source)
    }

    inner class PasswordCharSequence(private val source: CharSequence) : CharSequence {

        override val length: Int
            get() = source.length

        @Suppress("SameReturnValue")
        override fun get(index: Int): Char = HIDE_CHAR

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            return source.subSequence(startIndex, endIndex)
        }
    }
}