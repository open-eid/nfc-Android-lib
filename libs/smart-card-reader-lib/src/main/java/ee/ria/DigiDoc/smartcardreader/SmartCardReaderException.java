// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.smartcardreader;

public class SmartCardReaderException extends Exception {

    public SmartCardReaderException(String message) {
        super(message);
    }

    public SmartCardReaderException(Throwable cause) {
        super(cause);
    }

    public SmartCardReaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
