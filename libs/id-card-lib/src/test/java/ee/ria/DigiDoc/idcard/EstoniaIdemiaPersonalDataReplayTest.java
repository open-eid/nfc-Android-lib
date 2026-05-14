package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.err6B00;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.okPadded;

import org.junit.jupiter.api.Test;



/**
 * End-to-end replay covering PACE + post-PACE personal data + auth
 * certificate read for a real EE IDEMIA (ESTEID2018) test card.
 * Two scenarios:
 *
 * <ul>
 *   <li>{@link #personalData_replaysEeSession_decodesAllEightFields}
 *       drives {@link Idemia#personalData} through its 1 + 1 + 8*2 = 18
 *       APDU sequence (SELECT MAIN AID + SELECT DF 0x5000 + then for each
 *       of EF 0x5001..0x5008: SELECT EF + READ BINARY). Each EF returns
 *       one UTF-8 field; {@link IdemiaPersonalDataParser} reassembles
 *       them into a {@link PersonalData} object.</li>
 *
 *   <li>{@link #certificate_replaysEeAuthCertReadAndReturns1032ByteCert}
 *       drives {@link Idemia#certificate} through the 5-chunk read of
 *       EF.AACE (offsets 0x0000 → 0x039C), terminated by 6B00 at 0x0408
 *       — verifies cert reassembly and reproduces the captured 1032-byte
 *       ECDSA-P384 X.509 cert.</li>
 * </ul>
 *
 * <p>EE-vs-LV differences:
 * <ul>
 *   <li>EE stores personal data in 8 separate EF records (LV puts most
 *       of it in the cert subject).</li>
 *   <li>EE cert is 1032 bytes (LV's is 1179) and ends on a partial
 *       chunk at offset 0x039C.</li>
 *   <li>{@code IdemiaPersonalDataParser} maps EF position → field.</li>
 * </ul>
 *
 * <p>Fixture is a test card; "JÕEORG, JAAK-KRISTJAN, 38001085718" is
 * synthetic test data.
 */
public final class EstoniaIdemiaPersonalDataReplayTest {

    @Test
    public void personalData_replaysEeSession_decodesAllEightFields() throws Exception {
        var fixture = ReplayFixture.ee()
                .with(EstoniaIdemiaPersonalDataReplayTest::loadPersonalDataTranscript)
                .tunnel();

        PersonalData pd = fixture.token.personalData();

        // All field decoding paths exercised (UTF-8, "DD MM YYYY" date,
        // citizenship code, gender derivation from personal code).
        assertThat(pd.surname()).isEqualTo("JÕEORG");
        assertThat(pd.givenNames()).isEqualTo("JAAK-KRISTJAN");
        assertThat(pd.citizenship()).isEqualTo("FIN");
        assertThat(pd.personalCode()).isEqualTo("38001085718");
        assertThat(pd.documentNumber()).isEqualTo("ES0250124");
        assertThat(pd.cardType()).isEqualTo(CardType.ID1);
        // EE field 5 carries DOB + place; the lib parses just the date.
        assertThat(pd.dateOfBirth().toString()).isEqualTo("1980-01-08");
        // EE field 8 is doc expiry "DD MM YYYY".
        assertThat(pd.documentExpiryDate().toString()).isEqualTo("2026-07-22");

        fixture.assertAllConsumed();
    }

    @Test
    public void certificate_replaysEeAuthCertReadAndReturns1032ByteCert() throws Exception {
        var fixture = ReplayFixture.ee()
                .with(EstoniaIdemiaPersonalDataReplayTest::loadAuthCertTranscript)
                .tunnel();

        byte[] cert = fixture.token.certificate(CertificateType.AUTHENTICATION);

        // 4 full chunks (231 B each) + 1 partial (108 B) = 1032 B.
        assertThat(cert.length).isEqualTo(1032);
        // X.509 DER SEQUENCE header.
        assertThat(cert[0]).isEqualTo((byte) 0x30);
        assertThat(cert[1]).isEqualTo((byte) 0x82);
        // CN substring "JÕEORG,JAAK-KRISTJAN,38001085718".
        String certHex = org.bouncycastle.util.encoders.Hex.toHexString(cert);
        assertThat(certHex).contains("4ac395454f52472c4a41414b2d4b524953544a414e2c3338303031303835373138");

        fixture.assertAllConsumed();
    }

    /**
     * EE personal-data flow captured 2026-05-14 (logcat session at 12:16:11).
     * 8 EF records — surname / given names / sex / citizenship / DOB+place /
     * personal code / doc number / doc expiry — each fits in one 16-byte
     * AES block, so the post-decrypt payload is short.
     */
    static void loadPersonalDataTranscript(ApduReplayReader r) {
        // selectMainAid() — re-select after PACE
        r.expect("00a4040c10a000000077010800070000fe00000100", okPadded(""));
        // SELECT DF 0x5000
        r.expect("00a4010c025000", okPadded(""));

        // EF 0x5001 — surname "JÕEORG" (7 UTF-8 bytes; 'Õ' is 2 bytes)
        r.expect("00a4020c025001", okPadded(""));
        r.expect("00b0000000", okPadded("4ac395454f5247800000000000000000"));
        // EF 0x5002 — given names "JAAK-KRISTJAN" (13 bytes)
        r.expect("00a4020c025002", okPadded(""));
        r.expect("00b0000000", okPadded("4a41414b2d4b524953544a414e800000"));
        // EF 0x5003 — sex "M"
        r.expect("00a4020c025003", okPadded(""));
        r.expect("00b0000000", okPadded("4d80000000000000000000000000000000"));
        // EF 0x5004 — citizenship "FIN"
        r.expect("00a4020c025004", okPadded(""));
        r.expect("00b0000000", okPadded("46494e80000000000000000000000000"));
        // EF 0x5005 — DOB+place "08 01 1980 FIN"
        r.expect("00a4020c025005", okPadded(""));
        r.expect("00b0000000", okPadded("303820303120313938302046494e80"));
        // EF 0x5006 — personal code "38001085718"
        r.expect("00a4020c025006", okPadded(""));
        r.expect("00b0000000", okPadded("3338303031303835373138800000000000"));
        // EF 0x5007 — document number "ES0250124"
        r.expect("00a4020c025007", okPadded(""));
        r.expect("00b0000000", okPadded("4553303235303132348000000000000000"));
        // EF 0x5008 — document expiry "22 07 2026"
        r.expect("00a4020c025008", okPadded(""));
        r.expect("00b0000000", okPadded("32322030372032303236800000"));
    }

    /**
     * Auth certificate read flow, captured 2026-05-14 (logcat session at
     * 12:15:29). 4 full 231-byte chunks + 1 partial 108-byte chunk +
     * 6B00 EOF — matches the captured 1032-byte ESTEID2018 test cert.
     */
    static void loadAuthCertTranscript(ApduReplayReader r) {
        // selectMainAid() — re-select after PACE
        r.expect("00a4040c10a000000077010800070000fe00000100", okPadded(""));
        // SELECT EF.AACE (auth cert)
        r.expect("00a4090c04adf13401", okPadded(""));

        // Chunk 1 @ offset 0x0000 — DER SEQUENCE header + TBSCertificate start
        r.expect("00b0000000", okPadded(
                "3082040430820365a00302010202105942527e400d2cda60fa9d5f3bcafdfc300a06082a8648ce3d0403043060310b3009060355040613024545311b3019060355040a0c12534b20494420536f6c7574696f6e732041533117301506035504610c0e4e545245452d3130373437303133311b301906035504030c1254455354206f662045535445494432303138301e170d3231303732333130343334335a170d3236303732323231353935395a307f310b3009060355040613024545312a302806035504030c214ac395454f52472c4a41414b2d4b524953544a414e2c33383030313038353731800000000000000000"));
        // Chunk 2 @ offset 0x00E7 — subject continuation + public-key info
        r.expect("00b000e700", okPadded(
                "383110300e06035504040c074ac395454f524731163014060355042a0c0d4a41414b2d4b524953544a414e311a301806035504051311504e4f45452d33383030313038353731383076301006072a8648ce3d020106052b810400220362000454bcd2ae669dbef648d50624569dd1e1f1e6f0a4706bf7a22241b78006c766d4100d0a9579a3c650eb22f11c52d60e0849b80090eebcbb2da6a9cdddc60242fc8ad4d430e4045ca1e47535267c76995713a569ae0a09a6c8194c575b81d51b57a38201c3308201bf30090603551d1304023000300e0603551d0f0101ff0404030203883047060355800000000000000000"));
        // Chunk 3 @ offset 0x01CE — extensions: cert policies, SAN, SKI
        r.expect("00b001ce00", okPadded(
                "1d200440303e3032060b2b060104018391210102023023302106082b06010505070201161568747470733a2f2f7777772e736b2e65652f4350533008060604008f7a0102301f0603551d1104183016811433383030313038353731384065657374692e6565301d0603551d0e041604142377cb49db633376c2d00ea39227871da51157a7306106082b06010505070103045530533051060604008e46010530473045163f68747470733a2f2f736b2e65652f656e2f7265706f7369746f72792f636f6e646974696f6e732d666f722d7573652d6f662d6365727469666963617465732f1302454e800000000000000000"));
        // Chunk 4 @ offset 0x02B5 — EKU, AKI, AIA, signature alg
        r.expect("00b002b500", okPadded(
                "30200603551d250101ff0416301406082b0601050507030206082b06010505070304301f0603551d23041830168014c0849929c44e9f3b0234f699e10a560008293e7b307306082b0601050507010104673065302c06082b060105050730018620687474703a2f2f6169612e64656d6f2e736b2e65652f65737465696432303138303506082b060105050730028629687474703a2f2f632e736b2e65652f546573745f6f665f455354454944323031382e6465722e637274300a06082a8648ce3d04030403818c00308188024200f0e965ff3dd335086d80a814515f1f69918a0330f2ed4678e6800000000000000000"));
        // Chunk 5 @ offset 0x039C — signature (108-byte short chunk)
        r.expect("00b0039c00", okPadded(
                "59d3e9d5699446607012377c3fa3b57b7f3643cc28e2b48c50bacb4753b43f10260ce8add8d5c468024201143e0d96af6f866feadcde43035c21cf1c587ca04dd7442d1c37adf38e0c1c03c4bae2f46857087057ee19f7ee24cc5dbe612d8c0ee9194f08c76bae7d19e3c26c80000000"));
        // Read past EOF @ offset 0x0408 — SW 6B00 stops the cert-chunk loop.
        r.expect("00b0040800", err6B00());
    }
}
