// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.smartcardreader;

import androidx.annotation.NonNull;

import java.util.Objects;

public class ApduResponseException extends SmartCardReaderException {

    public final byte sw1;
    public final byte sw2;

    public ApduResponseException(byte sw1, byte sw2) {
        super(String.format("APDU error response sw1=%x, sw2=%x", sw1, sw2));
        this.sw1 = sw1;
        this.sw2 = sw2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApduResponseException that = (ApduResponseException) o;
        return sw1 == that.sw1 &&
                sw2 == that.sw2;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sw1, sw2);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("ApduResponseException{sw1=%x, sw2=%x}", sw1, sw2);
    }
}
