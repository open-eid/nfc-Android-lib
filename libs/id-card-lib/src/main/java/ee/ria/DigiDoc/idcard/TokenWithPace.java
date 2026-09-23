// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import org.bouncycastle.util.encoders.Hex;

import java.util.Arrays;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil;

/**
 * Extended ID1 token interface to create PACE-enabled cards
 */
public interface TokenWithPace extends Token {
    String TAG = TokenWithPace.class.getName();

    int CAN_LENGTH = 6;

    /**
     * Method to execute the PACE key-exchange, to allow for encrypted
     * communication between card and the application
     * @param can
     * @throws SmartCardReaderException
     */
    void tunnel(String can) throws SmartCardReaderException;

    /**
     * Create an instance of TokenWithPace based on the current card in the NFC-reader.
     * <p>
     * We detect the card type by historical bytes - a subset of ATS. We call the method
     * atr() since we inherit from wired interface specific classes.
     *
     * @param reader NFC Smart card reader instance, must be connected.
     * @return TokenWithPace instance.
     * @throws SmartCardReaderException When card is not supported or reader is not connected.
     */
    static TokenWithPace create(NfcSmartCardReader reader) throws SmartCardReaderException {
        byte[] atr = reader.atr();
        LoggingUtil.Companion.debugLog(TAG, "ATR: " + (atr == null ? "null" : Hex.toHexString(atr)), null);

        if (atr == null) {
            throw new SmartCardReaderException("ATR/ATS cannot be null");
        }
        if (Arrays.equals(Hex.decode("0012233f536549440f9000"), atr)) {
            return new IdemiaWithPace(reader);
        }
        if (Arrays.equals(Hex.decode("0012233f54654944320f9000"), atr)) {
            return new IdemiaWithPace(reader);
        }
        if (Arrays.equals(Hex.decode("8031d85365494464b085051012233f"), atr)) {
            return new ThalesWithPace(reader);
        }

        throw new SmartCardReaderException("ATS not supported");
    }
}
