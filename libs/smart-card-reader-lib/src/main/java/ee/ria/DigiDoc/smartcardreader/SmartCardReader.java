package ee.ria.DigiDoc.smartcardreader;
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

import static com.google.common.primitives.Bytes.concat;
import static java.util.Arrays.copyOf;
import static java.util.Arrays.copyOfRange;

import java.util.Collections;

import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil;

public abstract class SmartCardReader implements AutoCloseable {
    private static final String TAG = SmartCardReader.class.getName();
    public abstract boolean connected();

    public abstract byte[] atr();

    /**
     * Makes the actual transaction, has to be implemented by specific readers.
     *
     * @param apdu APDU to send
     * @return Return bytes.
     * @throws SmartCardReaderException When something fails.
     */
    protected abstract byte[] transmit(byte[] apdu) throws SmartCardReaderException;

    /**
     * Transmit APDU to the smart card reader.
     * <p>
     * Automatically handles message chaining for large data transmissions and
     * reading additional data for large responses.
     *
     * @return Return bytes.
     * @throws SmartCardReaderException When something fails.
     */
    public byte[] transmit(int cla, int ins, int p1, int p2, byte[] data, Integer le)
            throws SmartCardReaderException {
        LoggingUtil.Companion.debugLog(TAG, String.format("transmit: %x %x %x %x %s %s", cla, ins, p1, p2, Collections.singletonList(data), le), null);
        byte[] response;
        if (data == null || data.length == 0) {
            response = transmit(SmartCardReader.appendLe(
                    new byte[] {(byte) cla, (byte) ins, (byte) p1, (byte) p2},
                    le));
        } else if (data.length < 256) {
            response = transmit(SmartCardReader.appendLe(
                    concat(
                            new byte[] {(byte) cla, (byte) ins, (byte) p1, (byte) p2,
                                    (byte) data.length},
                            data),
                    le));
        } else {
            int remaining = data.length;
            while (remaining >= 256) {
                transmit(SmartCardReader.appendLe(
                        concat(
                                new byte[] {0x10, (byte) ins, (byte) p1, (byte) p2, (byte) 0xFF},
                                copyOfRange(data, data.length - remaining,
                                        data.length - remaining + 255)),
                        le));
                remaining -= 255;
            }
            response = transmit(SmartCardReader.appendLe(
                    concat(
                            new byte[] {(byte) cla, (byte) ins, (byte) p1, (byte) p2,
                                    (byte) remaining},
                            copyOfRange(data, data.length - remaining, data.length)),
                    le));
        }

        byte sw1 = response[response.length - 2];
        byte sw2 = response[response.length - 1];
        if (sw1 == (byte) 0x90 && sw2 == 0x00) {
            return copyOf(response, response.length - 2);
        } else if (sw1 == 0x61) {
            return concat(
                    copyOf(response, response.length - 2),
                    transmit(0x00, 0xC0, 0x00, 0x00, null, (int) sw2));
        }
        throw new ApduResponseException(sw1, sw2);
    }

    protected static byte[] appendLe(byte[] apdu, Integer le) {
        if (le == null) {
            return apdu;
        } else {
            return concat(apdu, new byte[] {le.byteValue()});
        }
    }
}
