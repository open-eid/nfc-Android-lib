// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

/**
 * Exception in creating PACE tunnel.
 */
public class PaceTunnelException extends SmartCardReaderException {

    public PaceTunnelException(Throwable cause) {
        super(cause);
    }

}
