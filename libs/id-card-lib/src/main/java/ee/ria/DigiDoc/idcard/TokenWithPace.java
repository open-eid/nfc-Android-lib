package ee.ria.DigiDoc.idcard;

import org.bouncycastle.util.encoders.Hex;

import java.util.Arrays;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;

/**
 * Extended ID1 token interface to create PACE-enabled cards
 */
public interface TokenWithPace extends Token {

    /**
     * Method to execute the PACE key-exchange, to allow for encrypted
     * communication between card and the application
     * @param can
     * @throws SmartCardReaderException
     */
    void tunnel(String can) throws SmartCardReaderException;

    /**
     * Create an instance of TokenWithPace based on the current card in the NFC-reader.
     *
     * We detect the card type by historical bytes - a subset of ATS. We call the method
     * atr() since we inherit from wired interface specific classes.
     *
     * @param reader NFC Smart card reader instance, must be connected.
     * @return TokenWithPace instance.
     * @throws SmartCardReaderException When card is not supported or reader is not connected.
     */
    static TokenWithPace create(NfcSmartCardReader reader) throws SmartCardReaderException {
        byte[] atr = reader.atr();
        if (atr == null) {
            throw new SmartCardReaderException("ATR/ATS cannot be null");
        }
        if (Arrays.equals(Hex.decode("0012233f536549440f9000"), atr)) {
            return new ID1WithPace(reader);
        }
        throw new SmartCardReaderException("ATS not supported");
    }
}
