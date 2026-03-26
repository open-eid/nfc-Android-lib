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

import android.util.SparseArray;

import java.nio.charset.StandardCharsets;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;

/**
 * Latvian eID card NFC token.
 *
 * Extends IdemiaWithPace since the Latvian eID uses the same IDEMIA ID-One Cosmo v8
 * platform (IAS-ECC Oberthur) as the Estonian card. All PACE, SM, certificate, PIN,
 * signature, authentication, and decryption operations are identical.
 *
 * Only personalData() parsing differs due to the Latvian personal code format.
 */
class LatviaIdemiaWithPace extends IdemiaWithPace {

    LatviaIdemiaWithPace(NfcSmartCardReader reader) {
        super(reader);
    }

    @Override
    public PersonalData personalData() throws SmartCardReaderException {
        selectMainAid();
        // VERIFY WITH CARD: EF structure assumed same as Estonian IDEMIA (8 records at 50:01-50:08)
        reader.transmit(0x00, 0xA4, 0x01, 0x0C, new byte[] {0x50, 0x00}, null);
        SparseArray<String> data = new SparseArray<>();
        for (int i = 1; i <= 8; i++) {
            reader.transmit(0x00, 0xA4, 0x02, 0x0C, new byte[] {0x50, (byte) i}, null);
            byte[] record = reader.transmit(0x00, 0xB0, 0x00, 0x00, null, 0x00);
            data.put(i, new String(record, StandardCharsets.UTF_8).trim());
        }
        return LatviaPersonalDataParser.parse(data);
    }
}
