// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

public enum CodeType {

    PIN1((byte) 0x01, (byte) 0x01, 4, 12),
    PIN2((byte) 0x02, (byte) 0x02, 5, 12),
    PUK((byte) 0x00, (byte) 0x03, 8, 12);

    public final byte value;
    public final byte retryValue;

    public final int minLength;

    public final int maxLength;

    CodeType(byte value, byte retryValue, int minLength, int maxLength) {
        this.value = value;
        this.retryValue = retryValue;
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    public boolean isLengthValid(int length) {
        return length >= minLength && length <= maxLength;
    }
}
