// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;

/**
 * ThalesWithPace extends Thales APDU protocol with Secure Messaging and PACE capabilities
 * enabling the use over NFC
 * @noinspection FieldCanBeLocal, SameParameterValue
 */
class ThalesWithPace extends Thales implements TokenWithPace {

    private static final PaceTunnel.Profile PACE_PROFILE = new PaceTunnel.Profile(
            "brainpoolP384r1",
            16,
            0x100,
            new byte[] {
                    (byte) 0x80, 0x0A, 0x04, 0x00, 0x7F, 0x00, 0x07, 0x02,
                    0x02, 0x04, 0x02, 0x04, (byte) 0x83, 0x01, 0x02});

    /**
     * NfcSmartCardReader to provide the encryption/decryption for
     */
    private final NfcSmartCardReader nfcReader;

    /**
     * Initialize Thales token with NfcSmartCardReader
     * @param reader
     */
    ThalesWithPace(NfcSmartCardReader reader) {
        super(reader);
        nfcReader = reader;
    }

    /**
     * Start PACE key-exchange with CAN
     * @param can
     * @throws SmartCardReaderException
     */
    public void tunnel(String can) throws SmartCardReaderException {
        PaceTunnel.open(nfcReader, () -> {
            reader.transmit(0x00, 0xA4, 0x02, 0x0C, new byte[]{0x01, 0x1C}, null);
            return PACE_PROFILE;
        }, can);
        selectMainAid();
    }

    /**
     * We have to override the main AID selection from the base class, since over NFC the APDU
     * with P2 = 0x00 does not yield a positive output. Therefore we have to use APDU, where
     * P2=0x0C. Although unfortunate - this kind of difference in behaviour over NFC vs. over
     * wire can be expected
     * @throws SmartCardReaderException
     */
    protected void selectMainAid() throws SmartCardReaderException {
        reader.transmit(0x00, 0xA4, 0x04, 0x0C, new byte[] {(byte)0xA0, 0x00, 0x00, 0x00, 0x63, 0x50, 0x4B, 0x43, 0x53, 0x2D, 0x31, 0x35}, null);
        // Master AID: A0000000181002030000000000000001
    }
}
