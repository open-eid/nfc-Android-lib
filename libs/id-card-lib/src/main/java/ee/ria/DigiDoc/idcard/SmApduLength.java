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

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

/**
 * Bounds checks for SM-encrypted C-APDU length fields. Values beyond
 * the supported encodings would silently truncate when cast to {@code byte},
 * producing malformed APDUs that the card rejects with no diagnostic.
 *
 * <p>Callers using sizes that exceed these limits would need to switch
 * to BER multi-byte length encoding (DO87) or extended-length APDUs (Lc)
 * — currently neither is implemented because no production flow hits the
 * thresholds. The checks here are defensive guards that fail loudly if
 * a future flow does.
 *
 * <p>Throws a checked {@link SmartCardReaderException} so it integrates
 * with the existing exception surface that callers (demoapp etc.) already
 * catch. The underlying {@link IllegalArgumentException} carries the
 * detail message and is preserved as {@code cause} for diagnostics.
 */
final class SmApduLength {

    /**
     * Maximum value encodable in a single BER length byte (short form).
     * Values 0x80–0xFF in a length byte are reserved as multi-byte length
     * indicators in BER; using them as direct lengths produces malformed
     * TLVs that ISO 7816 parsers misinterpret.
     */
    static final int MAX_SHORT_BER_LENGTH = 0x7F;

    /**
     * Maximum value of the single-byte ISO 7816-4 short-APDU Lc field.
     * Values above this would require the extended-length APDU encoding,
     * which IDEMIA cards over NFC are documented to reject.
     */
    static final int MAX_SHORT_LC = 0xFF;

    private SmApduLength() {
    }

    static void requireShortBerLength(int length, String label) throws SmartCardReaderException {
        if (length < 0 || length > MAX_SHORT_BER_LENGTH) {
            throw new SmartCardReaderException(new IllegalArgumentException(
                    label + " length " + length
                            + " exceeds BER short-form max 0x7F; SM payload too large "
                            + "for current encoding (would need BER multi-byte length)"));
        }
    }

    static void requireSingleByteLc(int length) throws SmartCardReaderException {
        if (length < 0 || length > MAX_SHORT_LC) {
            throw new SmartCardReaderException(new IllegalArgumentException(
                    "SM C-APDU Lc " + length
                            + " exceeds single-byte max 0xFF; extended-length APDUs "
                            + "are not supported"));
        }
    }
}
