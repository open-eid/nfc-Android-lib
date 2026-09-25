// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

public enum CertificateType {

    AUTHENTICATION((byte) 0xAA),
    SIGNING((byte) 0xDD);

    public final byte value;

    CertificateType(byte value) {
        this.value = value;
    }
}
