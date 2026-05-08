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
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil;

public class TLV {
    private static final String TAG = TLV.class.getName();

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

            // Parse DER length (1, 2, or 3 bytes). Lenient on malformation:
            // we stop parsing rather than throw, since callers (PrKDF /
            // EF.CardAccess walks) treat "didn't find what I wanted" the
            // same as "couldn't parse." Log so a maintainer triaging a
            // missing-key-ref or missing-PACEInfo report can tell the two
            // apart.
            int lengthOffset = index;
            int lengthByte = data[index++] & 0xFF;
            int length;
            if (lengthByte <= 0x7F) {
                length = lengthByte;
            } else if (lengthByte == 0x81) {
                if (index >= end) {
                    LoggingUtil.Companion.debugLog(TAG,
                        "TLV: truncated 0x81 length at offset " + lengthOffset
                            + ", stopping parse", null);
                    break;
                }
                length = data[index++] & 0xFF;
            } else if (lengthByte == 0x82) {
                if (index + 1 >= end) {
                    LoggingUtil.Companion.debugLog(TAG,
                        "TLV: truncated 0x82 length at offset " + lengthOffset
                            + ", stopping parse", null);
                    break;
                }
                length = ((data[index++] & 0xFF) << 8) | (data[index++] & 0xFF);
            } else {
                LoggingUtil.Companion.debugLog(TAG,
                    String.format("TLV: unsupported length form 0x%02X at offset %d, stopping parse",
                        lengthByte, lengthOffset),
                    null);
                break;
            }
            if (index + length > end) {
                LoggingUtil.Companion.debugLog(TAG,
                    "TLV: declared length " + length + " at offset " + lengthOffset
                        + " overruns buffer (remaining " + (end - index) + "), stopping parse",
                    null);
                break;
            }

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

    public TLV findByTag(int searchTag) {
        if (children == null) return null;
        for (TLV child : children) {
            if (child.tag == searchTag) return child;
        }
        return null;
    }

    public static TLV findByTag(List<TLV> tlvs, int searchTag) {
        for (TLV tlv : tlvs) {
            if (tlv.tag == searchTag) return tlv;
        }
        return null;
    }

    public static List<TLV> parseAll(byte[] data) {
        return parseTLVRecursive(data, 0, data.length);
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
