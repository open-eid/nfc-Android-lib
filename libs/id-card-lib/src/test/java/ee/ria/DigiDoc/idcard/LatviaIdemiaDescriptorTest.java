// SPDX-FileCopyrightText: 2026 Stagnation OÜ
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.bytes;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.ok;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

public final class LatviaIdemiaDescriptorTest {

    private static final String SEL_MAIN_AID =
            "00a4040c10a000000077010800070000fe00000100";

    private static final String CERT = "3003020101";

    private static final String SIGNATURE = "a1b2c3d4";

    @Test
    public void certificate_usesTheFileIdFromEfCd() throws Exception {
        var fixture = ReplayFixture.lv()
                .with(r -> {
                    TestPkcs15.loadDescriptorTranscript(
                            r, CertificateType.AUTHENTICATION, "3402", "82");
                    r.expect(SEL_MAIN_AID, ok());
                    r.expect("00a4090c04adf13402", ok());
                    TestPkcs15.loadFileRead(r, CERT);
                })
                .tunnel();

        byte[] certificate = fixture.token.certificate(CertificateType.AUTHENTICATION);

        assertThat(Hex.toHexString(certificate)).isEqualTo(CERT);
        fixture.assertAllConsumed();
    }

    @Test
    public void authenticate_usesTheKeyReferenceFromEfPrKd() throws Exception {
        var fixture = ReplayFixture.lv()
                .with(r -> {
                    TestPkcs15.loadDescriptorTranscript(
                            r, CertificateType.AUTHENTICATION, "3401", "81");
                    r.expect(TestApdus.SEL_OBERTHUR_AID, ok());
                    r.expect("00200001" + "0c" + TestPins.PIN1_PADDED_FF, ok());
                    r.expect("002241a4" + "06" + "800104" + "840181", ok());
                    r.expect("00880000" + "30" + TestApdus.CAPTURED_AUTH_HASH + "00",
                            bytes(SIGNATURE));
                })
                .tunnel();

        byte[] signature = fixture.token.authenticate(
                TestPins.PIN1, Hex.decode(TestApdus.CAPTURED_AUTH_HASH));

        assertThat(Hex.toHexString(signature)).isEqualTo(SIGNATURE);
        fixture.assertAllConsumed();
    }

    @Test
    public void authenticate_efPrKdCarriesNoKeyReference_fallsBackToSlotTwo() throws Exception {
        String efPrKdWithoutKeyRef = "a009" + "30020500" + "3003" + "040101";

        var fixture = ReplayFixture.lv()
                .with(r -> {
                    TestPkcs15.loadRawDescriptorTranscript(r, CertificateType.AUTHENTICATION,
                            TestPkcs15.efCd("3401"), efPrKdWithoutKeyRef);
                    r.expect(TestApdus.SEL_OBERTHUR_AID, ok());
                    r.expect("00200001" + "0c" + TestPins.PIN1_PADDED_FF, ok());
                    r.expect("002241a4" + "06" + "800104" + "840182", ok());
                    r.expect("00880000" + "30" + TestApdus.CAPTURED_AUTH_HASH + "00",
                            bytes(SIGNATURE));
                })
                .tunnel();

        byte[] signature = fixture.token.authenticate(
                TestPins.PIN1, Hex.decode(TestApdus.CAPTURED_AUTH_HASH));

        assertThat(Hex.toHexString(signature)).isEqualTo(SIGNATURE);
        fixture.assertAllConsumed();
    }

    @Test
    public void authenticate_nonEcPrivateKey_isRejectedBeforeThePinIsSent() throws Exception {
        String efPrKdNonEc = "300d" + "30020500" + "3007" + "040101" + "0202" + "0082";

        var fixture = ReplayFixture.lv()
                .with(r -> TestPkcs15.loadRawDescriptorTranscript(
                        r, CertificateType.AUTHENTICATION,
                        TestPkcs15.efCd("3401"), efPrKdNonEc))
                .tunnel();

        assertThrows(NotSupportedException.class, () -> fixture.token.authenticate(
                TestPins.PIN1, Hex.decode(TestApdus.CAPTURED_AUTH_HASH)));
        fixture.assertAllConsumed();
    }

    @Test
    public void descriptor_missingCertificateDirectoryEntry_fails() throws Exception {
        String efOdWithoutCertificates = "a006300404024001";

        var fixture = ReplayFixture.lv()
                .with(r -> {
                    r.expect(TestApdus.SEL_OBERTHUR_AID, ok());
                    r.expect(TestPkcs15.SEL_EF_OD, ok());
                    TestPkcs15.loadFileRead(r, efOdWithoutCertificates);
                })
                .tunnel();

        assertThrows(SmartCardReaderException.class,
                () -> fixture.token.certificate(CertificateType.AUTHENTICATION));
        fixture.assertAllConsumed();
    }

    @Test
    public void descriptor_isReadOncePerCertificateType() throws Exception {
        var fixture = ReplayFixture.lv()
                .with(r -> {
                    TestPkcs15.loadDescriptorTranscript(
                            r, CertificateType.AUTHENTICATION, "3401", "82");
                    for (int i = 0; i < 2; i++) {
                        r.expect(SEL_MAIN_AID, ok());
                        r.expect("00a4090c04adf13401", ok());
                        TestPkcs15.loadFileRead(r, CERT);
                    }
                })
                .tunnel();

        fixture.token.certificate(CertificateType.AUTHENTICATION);
        fixture.token.certificate(CertificateType.AUTHENTICATION);

        fixture.assertAllConsumed();
    }
}
