/*
 * Copyright 2017 - 2024 Riigi Infosüsteemi Amet
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

package ee.ria.DigiDoc.smartcardreader.nfc;

import static java.util.Arrays.copyOfRange;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.util.Log;

import androidx.annotation.NonNull;

import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.security.GeneralSecurityException;

import ee.ria.DigiDoc.smartcardreader.ApduResponseException;
import ee.ria.DigiDoc.smartcardreader.SmartCardReader;
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import timber.log.Timber;

/**
 * SmartCardReader implementation for NFC
 */
public class NfcSmartCardReader extends SmartCardReader {
    /**
     * Actual smart-card handle
     */
    private final IsoDep card;

    /**
     * Handle to the APDU encryption/decryption oracle, only present if
     * tunnel has been established
     */
    private ApduEncryptor apduEncryptor = null;

    /**
     * Create NfcSmartCardReader from the detected tag
     *
     * @param tag
     */
    public NfcSmartCardReader(Tag tag) throws SmartCardReaderException {
        card = IsoDep.get(tag); // recognized tag to create the tunnel with

        // maximum amount of time to process the data, 50000 == 50 seconds
        // this applies to trancieve calls and since we do cryptography, we
        // allow it to be long. Under normal circumstances we could survive
        // with the 5 second timeout, but when there has been wrong CAN entry
        // 10 times in a row, the card protection mechanisms introduce a delay.
        // Since length of the delay is only validated empirically to be around
        // 30 seconds, we have established 50 seconds as resonable upper
        // boundary. It is long enough to cover the delay and it does not
        // disturb any positive cases.
        card.setTimeout(50000);

        try {
            card.connect();
        } catch (IOException ex) {
            throw new SmartCardReaderException(ex);
        }
    }

    /**
     * Close smart-card connection
     */
    @Override
    public void close() {
        try {
            card.close();
        } catch (IOException ex) {
            Timber.log(Log.ERROR, ex);
        }
    }

    /**
     * Check if the connection with the card exists
     *
     * @return
     */
    public boolean connected() {
        return card.isConnected();
    }

    /**
     * Retrieve historical bytes from the card
     * @return
     */
    public byte[] atr() {
        return card.getHistoricalBytes();
    }

    /**
     * set APDU encryption/decryption oracle
     *
     * @param apduEncryptor
     */
    public void setApduEncryptor(ApduEncryptor apduEncryptor) {
        this.apduEncryptor = apduEncryptor;
    }

    /**
     * APDU transaction with NFC reader
     *
     * @param apdu APDU to send
     * @return Return bytes.
     * @throws SmartCardReaderException When something fails.
     */
    protected byte[] transmit(byte[] apdu) throws SmartCardReaderException {
        try {
            Timber.log(Log.DEBUG, Hex.toHexString(apdu));
            return card.transceive(apdu);
        } catch (IOException ex) {
            throw new SmartCardReaderException(ex);
        }
    }

    /**
     * NfcSmartCardReader overrides transmit logic from the SmartCardReader, since
     * the encryption/decryption slightly changes the way that e.g. retrieval of extra
     * data is handled.
     *
     * @return
     * @throws SmartCardReaderException
     */
    public byte[] transmit(int cla, int ins, int p1, int p2, byte[] data, Integer le)
            throws SmartCardReaderException {

        if (apduEncryptor == null) {
            // Before we have oracle all communication is unencrypted
            return super.transmit(cla, ins, p1, p2, data, le);
        }

        try {
            // Encryption of the C-APDU
            byte[] response;
            if (data == null || data.length == 0) {
                response = transmit(apduEncryptor.encryptAndMac(cla, ins, p1, p2, data, le));
            } else if (data.length < 256) {
                response = transmit(apduEncryptor.encryptAndMac(cla, ins, p1, p2, data, le));
            } else {
                int remaining = data.length;
                while (remaining >= 256) {
                    transmit(apduEncryptor.encryptAndMac(0x10, ins, p1, p2,
                            copyOfRange(data, data.length - remaining,
                                    data.length - remaining + 255),
                            le));

                    remaining -= 255;
                }
                response = transmit(apduEncryptor.encryptAndMac(cla, ins, p1, p2,
                        copyOfRange(data, data.length - remaining, data.length), le));
            }

            byte sw1 = response[response.length - 2];
            byte sw2 = response[response.length - 1];
            Timber.log(Log.DEBUG, "R-APDU: SW1: 0x%02X, SW2: 0x%02X", sw1, sw2);

            // Decryption of the R-APDU
            if (sw1 == (byte) 0x90 && sw2 == 0x00) {
                return apduEncryptor.decryptAndVerify(response);
            } else if (sw1 == 0x61) {
                // NB! GET RESPONSE for extra data must be unencrypted
                byte[] missing = super.transmit(0x00, 0xC0, 0x00, 0x00, null, (int) sw2);

                // We combine the whole APDU for decryptAndVerify
                return apduEncryptor.decryptAndVerify(combineCompleteRApdu(response, missing));
            }
            throw new ApduResponseException(sw1, sw2);
        } catch (GeneralSecurityException ex) {
            throw new SmartCardReaderException(ex);
        }
    }

    /**
     * Concatenate 2 R-APDU's to form a single encrypted R-APDU
     *
     * @param response - encrypted R-APDU that ended with 0x61
     * @param missing - unencrypted data of R-APDU from GET RESPONSE
     * @return
     */
    @NonNull
    private static byte[] combineCompleteRApdu(byte[] response, byte[] missing) {
        byte[] ret = new byte[response.length + missing.length];
        int cursor = 0;
        // Omit 2 status bytes of the R-APDU
        System.arraycopy(response, 0, ret, cursor, response.length - 2);
        cursor += response.length - 2;
        // Insert extra data
        System.arraycopy(missing, 0, ret, cursor, missing.length);
        cursor += missing.length;
        // Insert status bytes from original R-APDU
        System.arraycopy(response, response.length - 2, ret, cursor, 2);
        return ret;
    }
}
