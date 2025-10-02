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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

public class TLV {
    private final int tag;
    private final byte[] value;
    public final List<TLV> children;

    public TLV(int tag, byte[] value, List<TLV> children) {
        this.tag = tag;
        this.value = value;
        this.children = children;
    }

    public int getTag() {
        return tag;
    }

    public byte[] getValue() {
        return value;
    }

    public static TLV from(byte[] data) {
        if (data == null || data.length < 2) return null;

        int tag = data[0] & 0xFF;
        int length = data[1] & 0xFF;

        if (data.length < 2 + length) return null;

        byte[] value = new byte[length];
        System.arraycopy(data, 2, value, 0, length);

        return new TLV(tag, value, new ArrayList<>());
    }

    public static List<TLV> parseTLVRecursive(byte[] data) {
        List<TLV> tlvs = parseTLVRecursive(data, 0, data.length);
        List<TLV> records = new ArrayList<>();
        for (TLV tlv : tlvs) {
            if (tlv.children != null) {
                records.addAll(tlv.children);
            }
        }

        return records;
    }

    /** @noinspection SameParameterValue*/
    private static List<TLV> parseTLVRecursive(byte[] data, int start, int end) {
        List<TLV> result = new ArrayList<>();
        int index = start;

        while (index + 2 <= end) {
            // Parse tag (1 or 2 bytes)
            int firstTagByte = data[index++] & 0xFF;
            int tag = firstTagByte;

            if ((firstTagByte & 0x1F) == 0x1F && index < end) {
                // Second tag byte expected
                int secondTagByte = data[index++] & 0xFF;
                tag = (firstTagByte << 8) | secondTagByte;
            }

            if (index >= end) break;

            // Parse 1-byte length
            int length = data[index++] & 0xFF;
            if (index + length > end) break;

            byte[] value = Arrays.copyOfRange(data, index, index + length);

            List<TLV> children = null;
            if ((firstTagByte & 0x20) != 0) { // constructed based on first byte
                children = parseTLVRecursive(value, 0, value.length);
            }

            result.add(new TLV(tag, value, children));
            index += length;
        }

        return result;
    }

    /** @noinspection unused*/
    public static List<TLV> sequenceOfRecords(byte[] data) {
        List<TLV> result = new ArrayList<>();
        if (data == null) return result;

        int index = 0;
        while (index + 2 <= data.length) {
            int tag = data[index] & 0xFF;
            int length = data[index + 1] & 0xFF;

            if (index + 2 + length > data.length) break;

            byte[] value = new byte[length];
            System.arraycopy(data, index + 2, value, 0, length);
            result.add(new TLV(tag, value, new ArrayList<>()));

            index += 2 + length;
        }
        return result;
    }

    public byte[] encode() throws SmartCardReaderException {
        if (value.length > 255) {
            throw new SmartCardReaderException("Only single-byte length supported");
        }
        byte[] result = new byte[2 + value.length];
        result[0] = (byte) tag;
        result[1] = (byte) value.length;
        System.arraycopy(value, 0, result, 2, value.length);
        return result;
    }

    public static byte[] encodeTLV(int tag, byte[] value) throws SmartCardReaderException {
        return new TLV(tag, value, new ArrayList<>()).encode();
    }
}
