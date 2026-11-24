// SPDX-FileCopyrightText: 2026 Stagnation OÜ
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import static ee.ria.DigiDoc.idcard.ApduReplayReader.bytes;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.err6B00;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.ok;

final class TestPkcs15 {

    private TestPkcs15() {}

    static final String SEL_EF_OD = "00a4020c025031";
    static final String SEL_EF_PRKD = "00a4020c024001";
    static final String SEL_EF_CD = "00a4020c024003";

    static final String EF_OD = "a006300404024001a406300404024003";

    static String efCd(String certificateFileId) {
        return "300e" + "30020500" + "a108" + "3006" + "3004" + "0402" + certificateFileId;
    }

    static String efPrKd(String keyReference) {
        return "a00d" + "30020500" + "3007" + "040101" + "0202" + "00" + keyReference;
    }

    static void loadDescriptorTranscript(ApduReplayReader r, CertificateType type,
                                         String certificateFileId, String keyReference) {
        loadRawDescriptorTranscript(r, type, efCd(certificateFileId), efPrKd(keyReference));
    }

    static void loadRawDescriptorTranscript(ApduReplayReader r, CertificateType type,
                                            String efCd, String efPrKd) {
        r.expect(type == CertificateType.SIGNING
                ? TestApdus.SEL_QSCD_AID : TestApdus.SEL_OBERTHUR_AID, ok());

        r.expect(SEL_EF_OD, ok());
        loadFileRead(r, EF_OD);

        r.expect(SEL_EF_CD, ok());
        loadFileRead(r, efCd);

        r.expect(SEL_EF_PRKD, ok());
        loadFileRead(r, efPrKd);
    }

    static void loadFileRead(ApduReplayReader r, String payload) {
        r.expect("00b0000000", bytes(payload));
        r.expect(readAt(payload.length() / 2), err6B00());
    }

    static String readAt(int offset) {
        return String.format("00b0%04x00", offset);
    }
}
