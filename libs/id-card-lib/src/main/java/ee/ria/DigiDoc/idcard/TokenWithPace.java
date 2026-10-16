// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-FileCopyrightText: 2026 Stagnation OÜ
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import org.bouncycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.Set;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil;

/**
 * Extended ID1 token interface to create PACE-enabled cards
 */
public interface TokenWithPace extends Token {
    String TAG = TokenWithPace.class.getName();

    /**
     * Method to execute the PACE key-exchange, to allow for encrypted
     * communication between card and the application
     * @param can
     * @throws SmartCardReaderException
     */
    void tunnel(String can) throws SmartCardReaderException;

    /**
     * Detect the card by ATS historical bytes and return the matching
     * implementation. The method is named {@code atr()} on the reader
     * for historical reasons — on NFC, the equivalent is the ATS
     * historical bytes.
     *
     * @param reader connected NFC reader.
     * @param config required restriction on accepted card types. Use
     *               {@link TokenWithPaceConfig#allowAll()} to opt in to
     *               every supported card type.
     * @return matching TokenWithPace implementation.
     * @throws NotSupportedException if the ATS is unknown, or the detected
     *         card type is not in {@code config.allowedCardTypes()}.
     * @throws SmartCardReaderException on reader I/O failure.
     */
    static TokenWithPace create(
            NfcSmartCardReader reader,
            TokenWithPaceConfig config
    ) throws SmartCardReaderException {
        byte[] atr = reader.atr();
        Set<CardType> allowed = config.allowedCardTypes();

        LoggingUtil.Companion.debugLog(TAG,
                "ATR: " + (atr == null ? "null" : Hex.toHexString(atr)), null);

        if (atr == null) {
            throw new NotSupportedException("ATR/ATS cannot be null");
        }

        CardType detected = detectCardType(atr);

        if (!allowed.contains(detected)) {
            throw new NotSupportedException("Card type " + detected + " not in allowed config " + allowed);
        }

        return switch (detected) {
            case ID1 -> new IdemiaWithPace(reader);
            case THALES -> new ThalesWithPace(reader);
            case LATVIA_IDEMIA -> new LatviaIdemiaWithPace(reader);
        };
    }

    private static CardType detectCardType(byte[] atr) throws NotSupportedException {
        if (Arrays.equals(Hex.decode("0012233f536549440f9000"), atr)
                || Arrays.equals(Hex.decode("0012233f54654944320f9000"), atr)) {
            return CardType.ID1;
        }
        if (Arrays.equals(Hex.decode("8031d85365494464b085051012233f"), atr)) {
            return CardType.THALES;
        }
        if (Arrays.equals(Hex.decode("0012428f536549440f9000"), atr)
                || Arrays.equals(Hex.decode("0012428f54654944320f9000"), atr)) {
            return CardType.LATVIA_IDEMIA;
        }
        throw new NotSupportedException("ATS not supported");
    }
}
