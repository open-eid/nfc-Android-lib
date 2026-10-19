// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-FileCopyrightText: 2026 Stagnation OÜ
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

/**
 * Exception when id card is not supported.
 */
public class NotSupportedException extends SmartCardReaderException {

    NotSupportedException(String message) {
        super(message);
    }

    NotSupportedException(String message, Throwable cause) {
        super(message, cause);
    }
}
