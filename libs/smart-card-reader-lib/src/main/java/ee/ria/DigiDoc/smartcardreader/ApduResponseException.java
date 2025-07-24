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
