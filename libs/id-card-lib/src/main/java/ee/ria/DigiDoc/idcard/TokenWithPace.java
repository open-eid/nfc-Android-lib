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

    // ATS historical-byte patterns used to identify card generation. The
    // trailing ASCII bytes encode the chip family: "SeID" (newer generation,
    // 53 65 49 44) and "TeID2" (older Te+ID 2.x generation, 54 65 49 44 32).
    // The 23 3f / 42 8f bytes encode the issuer country (EE vs LV).
    /** Estonian IDEMIA, newer "SeID" historical bytes. */
    byte[] ATS_EE_IDEMIA_SEID = Hex.decode("0012233f536549440f9000");
    /** Estonian IDEMIA, older "TeID2" historical bytes. */
    byte[] ATS_EE_IDEMIA_TEID2 = Hex.decode("0012233f54654944320f9000");
    /** Estonian Thales (single known ATS as of writing). */
    byte[] ATS_EE_THALES = Hex.decode("8031d85365494464b085051012233f");
    /** Latvian IDEMIA, newer "SeID" historical bytes. */
    byte[] ATS_LV_IDEMIA_SEID = Hex.decode("0012428f536549440f9000");
    /** Latvian IDEMIA, older "TeID2" historical bytes. */
    byte[] ATS_LV_IDEMIA_TEID2 = Hex.decode("0012428f54654944320f9000");

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
     * We detect the card type by historical bytes - a subset of ATS. The method is named
     * atr() for historical reasons — on NFC, the equivalent is the ATS historical bytes.
     *
     * @param reader NFC Smart card reader instance, must be connected.
     * @return TokenWithPace instance.
     * @throws SmartCardReaderException When card is not supported or reader is not connected.
     */
    static TokenWithPace create(NfcSmartCardReader reader) throws SmartCardReaderException {
        byte[] atr = reader.atr();
        LoggingUtil.Companion.debugLog(TAG, "ATR: " + (atr == null ? "null" : Hex.toHexString(atr)), null);

        if (atr == null) {
            throw new NotSupportedException("ATR/ATS cannot be null");
        }
        if (Arrays.equals(ATS_EE_IDEMIA_SEID, atr) || Arrays.equals(ATS_EE_IDEMIA_TEID2, atr)) {
            return new IdemiaWithPace(reader);
        }
        if (Arrays.equals(ATS_EE_THALES, atr)) {
            return new ThalesWithPace(reader);
        }
        if (Arrays.equals(ATS_LV_IDEMIA_SEID, atr) || Arrays.equals(ATS_LV_IDEMIA_TEID2, atr)) {
            return new LatviaIdemiaWithPace(reader);
        }

        throw new NotSupportedException("ATS not supported");
    }
}
