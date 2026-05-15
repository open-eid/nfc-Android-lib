package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.okPadded;

import org.junit.jupiter.api.Test;



/**
 * End-to-end replay covering PACE + post-PACE personal data + auth
 * certificate read for a real Thales ESTEID2025 test card.
 *
 * <p>What this exercises that no other replay test does:
 * <ul>
 *   <li>{@link Thales#readFile} — a different file-read pattern from
 *       Idemia. The lib first SELECTs the file (P2=0x04, not 0x0C) to
 *       receive an FCI with the file size in tag 0x80 or 0x81, then
 *       loops READ BINARY with Le=min(0xE5, remaining). EF lookup is
 *       size-driven, not error-terminated.</li>
 *   <li>{@link Thales#personalData} — 8 SELECT EF + READ pairs anchored
 *       under a SELECT DF at DF.DD (different from Idemia's 0x5000).</li>
 *   <li>{@link Thales#certificate} — same readFile loop drives a
 *       1034-byte cert across 4 full {@code 0xDF}-byte chunks + a
 *       142-byte short last chunk. Cert size encoded in FCI tag 0x81.</li>
 * </ul>
 *
 * <p>Fixture is a real ESTEID2025 test card; identity "MÖLDER, HUGO
 * MARTIN, 38910239121" is synthetic test data.
 */
public final class ThalesPersonalDataReplayTest {

    @Test
    public void personalData_replaysThalesSession_decodesAllEightFields() throws Exception {
        var fixture = ReplayFixture.thales()
                .with(ThalesPersonalDataReplayTest::loadPersonalDataTranscript)
                .tunnel();

        PersonalData pd = fixture.token.personalData();

        // Identity from EF.5001..5008. Surname has 'Ö' encoded as 2-byte
        // UTF-8 (0xC3 0x96) — verifies the Thales lib doesn't truncate.
        assertThat(pd.surname()).isEqualTo("MÖLDER");
        assertThat(pd.givenNames()).isEqualTo("HUGO MARTIN");
        assertThat(pd.citizenship()).isEqualTo("FIN");
        assertThat(pd.personalCode()).isEqualTo("38910239121");
        assertThat(pd.documentNumber()).isEqualTo("AT0006680");
        assertThat(pd.cardType()).isEqualTo(CardType.THALES);
        // Thales field 5: DOB+place "23 10 1989" → 1989-10-23.
        assertThat(pd.dateOfBirth().toString()).isEqualTo("1989-10-23");
        // Thales field 8: doc expiry "05 10 2030" → 2030-10-05.
        assertThat(pd.documentExpiryDate().toString()).isEqualTo("2030-10-05");

        fixture.assertAllConsumed();
    }

    @Test
    public void certificate_replaysThalesAuthCertReadAndReturns1034ByteCert() throws Exception {
        var fixture = ReplayFixture.thales()
                .with(ThalesPersonalDataReplayTest::loadAuthCertTranscript)
                .tunnel();

        byte[] cert = fixture.token.certificate(CertificateType.AUTHENTICATION);

        // 4 chunks of 0xDF (223) + 1 short chunk of 0x8E (142) = 1034 bytes.
        // Matches the FCI tag 0x81 02 04 0A = 0x040A.
        assertThat(cert.length).isEqualTo(1034);
        // X.509 DER SEQUENCE header.
        assertThat(cert[0]).isEqualTo((byte) 0x30);
        assertThat(cert[1]).isEqualTo((byte) 0x82);
        // CN substring "MÖLDER,HUGO MARTIN,38910239121".
        String certHex = org.bouncycastle.util.encoders.Hex.toHexString(cert);
        assertThat(certHex).contains("4dc3964c4445522c4855474f204d415254494e2c3338393130323339313231");

        fixture.assertAllConsumed();
    }

    /**
     * Thales personal-data flow: 8 EFs under DF.DDDD: surname, given names,
     * sex, citizenship, DOB+place, personal code, doc number, doc expiry.
     *
     * <p>Each {@code readFile} call sees a 2-step exchange: SELECT EF
     * returns an FCI carrying the file size in tag {@code 81 02 ..};
     * the subsequent READ BINARY uses {@code Le = size} so the response
     * is exactly the unpadded field bytes (16-byte AES padding stripped
     * by the SM layer).
     */
    static void loadPersonalDataTranscript(ApduReplayReader r) {
        // SELECT DF (PKCS-15 child) — anchor for the 8 EF reads.
        r.expect("00a4080c02dfdd", okPadded(""));

        // EF 5001 — surname "MÖLDER" (7 UTF-8 bytes).
        r.expect("00a40204025001",
                okPadded("621381020007820101830250018a01058c0303ff00800000000000000000"));
        r.expect("00b0000007", okPadded("4dc3964c444552800000000000000000"));
        // EF 5002 — given names "HUGO MARTIN" (11 bytes).
        r.expect("00a40204025002",
                okPadded("62138102000b820101830250028a01058c0303ff00800000000000000000"));
        r.expect("00b000000b", okPadded("4855474f204d415254494e8000000000"));
        // EF 5003 — sex "M" (1 byte).
        r.expect("00a40204025003",
                okPadded("621381020001820101830250038a01058c0303ff00800000000000000000"));
        r.expect("00b0000001", okPadded("4d80000000000000000000000000000000"));
        // EF 5004 — citizenship "FIN" (3 bytes).
        r.expect("00a40204025004",
                okPadded("621381020003820101830250048a01058c0303ff00800000000000000000"));
        r.expect("00b0000003", okPadded("46494e80000000000000000000000000"));
        // EF 5005 — DOB+place "23 10 1989" (10 bytes).
        r.expect("00a40204025005",
                okPadded("62138102000a820101830250058a01058c0303ff00800000000000000000"));
        r.expect("00b000000a", okPadded("32332031302031393839800000000000"));
        // EF 5006 — personal code "38910239121" (11 bytes; same Le=0x0B as 5002).
        r.expect("00a40204025006",
                okPadded("62138102000b820101830250068a01058c0303ff00800000000000000000"));
        r.expect("00b000000b", okPadded("33383931303233393132318000000000"));
        // EF 5007 — document number "AT0006680" (9 bytes).
        r.expect("00a40204025007",
                okPadded("621381020009820101830250078a01058c0303ff00800000000000000000"));
        r.expect("00b0000009", okPadded("41543030303636383080000000000000"));
        // EF 5008 — document expiry "05 10 2030" (10 bytes; same Le=0x0A as 5005).
        r.expect("00a40204025008",
                okPadded("62138102000a820101830250088a01058c0303ff00800000000000000000"));
        r.expect("00b000000a", okPadded("30352031302032303330800000000000"));
    }

    /**
     * Thales auth-cert read. SELECT cert at {@code AD F1 34 11} returns FCI
     * with size 0x040A; cert reads in chunks of 0xDF (chip-imposed limit
     * despite Le=0xE5) with a 0x8E-byte short final chunk.
     */
    static void loadAuthCertTranscript(ApduReplayReader r) {
        // SELECT auth cert file. FCI carries size 0x040A (1034) in tag 81.
        r.expect("00a4080404adf13411",
                okPadded("62148102040a820101830234118a01058c0443f1f10080000000000000000000"));

        // Chunk 1 @ 0x0000 — Le=0xE5, chip returns 0xDF bytes (SM block clamp).
        r.expect("00b00000e5", okPadded(
                "308204063082038ba0030201020214735c1db6fe0ce0356f13c27b363cf388f1c33c88300a06082a8648ce3d040303305c3118301606035504030c0f5465737420455354454944323032353117301506035504610c0e4e545245452d3137303636303439311a3018060355040a0c115a65746573204573746f6e6961204fc39c310b3009060355040613024545301e170d3235313030363137323332305a170d3330313030353230353935395a307b3128302606035504030c1f4dc3964c4445522c4855474f204d415254494e2c3338393130323339313231311a3018060380"));
        // Chunk 2 @ 0x00DF
        r.expect("00b000dfe5", okPadded(
                "5504051311504e4f45452d333839313032333931323131143012060355042a0c0b4855474f204d415254494e3110300e06035504040c074dc3964c444552310b30090603550406130245453076301006072a8648ce3d020106052b8104002203620004f94ee50d024ad55e798e956005db6c1ee0e429543f22606b17ddfabfaed96ec888ae113d5cdc1fdbf194b3ecad3c3d7e2ab48cf78c2808287b0d1d624fc40ed57a7af6b629918751b4b5cd9007fc828b2705214420d8e91332c4a2188b3fb847a38201ed308201e930090603551d1304023000301f0603551d23041880"));
        // Chunk 3 @ 0x01BE
        r.expect("00b001bee5", okPadded(
                "30168014eef2953f8cb2fc519e84e6e65e84117e42ba2036307006082b0601050507010104643062303806082b06010505073002862c687474703a2f2f6372742d746573742e656964706b692e65652f74657374455354454944323032352e637274302606082b06010505073001861a687474703a2f2f6f6373702d746573742e656964706b692e6565301f0603551d1104183016811433383931303233393132314065657374692e656530560603551d20044f304d3008060604008f7a01023041060e8837010306010401839121020101302f302d06082b06010505070280"));
        // Chunk 4 @ 0x029D
        r.expect("00b0029de5", okPadded(
                "01162168747470733a2f2f7265706f7369746f72792d746573742e656964706b692e6565301d0603551d250416301406082b0601050507030206082b06010505070304304306082b06010505070103043730353033060604008e46010530293027162168747470733a2f2f7265706f7369746f72792d746573742e656964706b692e65651302656e303d0603551d1f043630343032a030a02e862c687474703a2f2f63726c2d746573742e656964706b692e65652f74657374455354454944323032352e63726c301d0603551d0e041604149935e58cdd19e12ad6809b4c0a80"));
        // Chunk 5 @ 0x037C — last chunk, Le=0x8E matches remaining bytes.
        r.expect("00b0037c8e", okPadded(
                "92219ef4103141300e0603551d0f0101ff040403020388300a06082a8648ce3d0403030369003066023100be5e97121503fe4fb939ca6b0bacdeb28ece4e29d8eaad1ba65578c200548acfd9fa6427c75cc4382360249e10f20e55023100c74342cd14cfc3a1da5bcbf6c70463077c044aa97a4719f4dbf1c526ac94d66470472b70a4c8fac85404b2b01092f3e88000"));
    }
}
