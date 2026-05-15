package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.okPadded;

import org.junit.jupiter.api.Test;

/**
 * End-to-end replay covering PACE + post-PACE personal data + auth
 * certificate read for a real LV IDEMIA test card session.
 *
 * <p>Extends {@link LatviaIdemiaPaceReplayTest} (which covers just the
 * PACE handshake) through the rest of the captured session up to the
 * point where the lib parses {@code PersonalData}. What this exercises
 * on top of PACE:
 * <ul>
 *   <li>Post-PACE SELECT-AID + SELECT-DF + SELECT-EF + READ BINARY
 *       sequence for EF 0x5001 (personal code).</li>
 *   <li>Chunked READ BINARY for EF.AACE (the auth certificate), 6 chunks
 *       at 0x00E7 offsets, terminated by 6B00 EOF.</li>
 *   <li>X.509 cert parse + RDN extraction (surname, given name, serialNumber,
 *       country) using BouncyCastle.</li>
 *   <li>LV personal-code → date-of-birth mapping in {@code LatviaPersonalDataParser}.</li>
 *   <li>The PersonalData schema split (citizenship vs issuingCountry,
 *       documentExpiryDate vs certExpiryDate).</li>
 * </ul>
 *
 * <p>The mock intercepts at {@code transmit(int,int,int,int,byte[],Integer)}
 * — the public form. NfcSmartCardReader's secure-messaging layer is
 * therefore short-circuited; the stub returns plaintext payloads
 * directly, matching what the real {@code apduEncryptor.decryptAndVerify}
 * would have produced. Captured "Decrypted data" hex from the log is
 * pasted verbatim including ISO 7816-4 padding; the shared replay reader
 * strips the trailing 0x80 + 0x00*n before handing the bytes to the lib.
 *
 * <p>Fixture is a test card; "PARAUDZIŅA MĀRA" is a synthetic test
 * identity, no real personal data.
 */
public final class LatviaIdemiaPersonalDataReplayTest {

    @Test
    public void personalData_replaysLvSession_decodesPersonalDataAndCert() throws Exception {
        var fixture = ReplayFixture.lv()
                .with(LatviaIdemiaPersonalDataReplayTest::loadPersonalDataTranscript)
                .tunnel();

        PersonalData pd = fixture.token.personalData();

        // Identity from cert subject (verified against the captured base64 cert
        // logged at 12:06:04.951 — synthetic identity).
        assertThat(pd.surname()).isEqualTo("PARAUDZIŅA");
        assertThat(pd.givenNames()).isEqualTo("MĀRA");
        assertThat(pd.personalCode()).isEqualTo("326305-17052");
        assertThat(pd.documentNumber()).isEqualTo("PNOLV-326305-17052");
        assertThat(pd.issuingCountry()).isEqualTo("LV");
        assertThat(pd.cardType()).isEqualTo(CardType.LATVIA_IDEMIA);
        // X.509 notAfter in UTC — matches log "certExpiry=2031-02-16".
        assertThat(pd.certExpiryDate().toString()).isEqualTo("2031-02-16");
        // LV cards don't expose document expiry over NFC.
        assertThat(pd.documentExpiryDate()).isNull();
        // CA's country, not citizenship — see LatviaIdemiaWithPace.personalData() javadoc.
        assertThat(pd.citizenship()).isEmpty();

        fixture.assertAllConsumed();
    }

    /**
     * Post-PACE personal data + cert read transcript. The certificate read
     * uses the FCI form: SELECT cert with P2 = 0x04, parse size from FCI
     * tag 0x80 (1182 bytes), then READ BINARY at {@code Le = 0xE5} chunks
     * for the declared size — no 6B 00 EOF probe.
     *
     * <p>Outcomes below are post-decrypt payloads — ISO 7816-4 padding
     * included; the shared replay reader strips the trailing 0x80 + 0x00*
     * at call time.
     */
    static void loadPersonalDataTranscript(ApduReplayReader r) {
        // selectMainAid() — re-select after PACE
        r.expect("00a4040c10a000000077010800070000fe00000100", okPadded(""));
        // SELECT DF 0x5000 (under main AID)
        r.expect("00a4010c025000", okPadded(""));
        // SELECT EF 0x5001 (personal code file)
        r.expect("00a4020c025001", okPadded(""));
        // READ BINARY EF 0x5001 — Latvian personal code "326305-17052" UTF-8
        r.expect("00b0000000", okPadded("3332363330352d313730353280000000"));

        // certificate(AUTHENTICATION) — selectMainAid() again
        r.expect("00a4040c10a000000077010800070000fe00000100", okPadded(""));
        // SELECT EF.AACE (auth cert) via P1=0x09 P2=0x04 (FCI form).
        // Response: ISO 7816-4 FCP template (tag 62) carrying tag 80 with
        // file size 0x049E = 1182 bytes, plus standard descriptors.
        r.expect("00a4090404adf13401", okPadded(
                "621e8002049e8201018302340188008a0105a10c8c0443ffc3009c0443ffc300"));

        // 6 chunked reads of declared 1182 bytes: 5 full chunks at Le=0xE5
        // (229 bytes) + 1 partial at Le=0x25 (37 bytes). No 6B 00 probe.
        r.expect("00b00000e5", okPadded(
                "3082049a308203fba00302010202102d9a9078d07b83e06993271b430d94b8300a06082a8648ce3d040303308183310b3009060355040613024c5631393037060355040a0c30564153204c617476696a61732056616c73747320726164696f20756e2074656c6576c4ab7a696a61732063656e747273311a301806035504610c114e54524c562d3430303033303131323033311d301b06035504030c1444454d4f204c5620654944204943412032303234301e170d3236303231363134313830335a170d3331303231363134313830335a306c310b3009060355040613024c56311a3018068000000000000000000000"));
        r.expect("00b000e5e5", okPadded(
                "035504030c114dc4805241205041524155445a49c585413114301206035504040c0b5041524155445a49c58541310e300c060355042a0c054dc4805241311b301906035504051312504e4f4c562d3332363330352d31373035323076301006072a8648ce3d020106052b81040022036200045ba231c6013505812886ca50a8ac0476d6eafa07179485b9251f008404aa1c0605fadb0ced3dcfb5f6135b16f0790f72945792595310ab4ea3b8ebb72a5aa903fa628fa7dfec6d86c9e09cf9f8e14b86fe0a7e336bb01704595481335a1cdb35a382024830820244300c0603551d130101ff048000000000000000000000"));
        r.expect("00b001cae5", okPadded(
                "023000300e0603551d0f0101ff040403020388301d0603551d250416301406082b0601050507030206082b06010505070304301d0603551d0e0416041425bb022e632b0215ec225c923e38700e3b86b4ce301f0603551d2304183016801442b3248d335eadd9cabb243e8eb5a98dab701fec3081fb0603551d200481f33081f0303b060604008f7a01023031302f06082b06010505070201162368747470733a2f2f7777772e65706172616b7374732e6c762f7265706f7369746f72793081b0060c2b0601040181fa3d0201020230819f302f06082b06010505070201162368747470733a8000000000000000000000"));
        r.expect("00b002afe5", okPadded(
                "2f2f7777772e65706172616b7374732e6c762f7265706f7369746f7279306c06082b0601050507020230600c5ec5a0697320736572746966696bc48174732069722069656bc4bc61757473204c617476696a61732052657075626c696b617320697a736e69656774c48120706572736f6e752061706c696563696e6fc5a1c48120646f6b756d656e74c481307d06082b060105050701010471306f304206082b060105050730028636687474703a2f2f64656d6f2e65706172616b7374732e6c762f636572742f64656d6f5f4c565f6549445f4943415f323032342e637274302906082b068000000000000000000000"));
        r.expect("00b00394e5", okPadded(
                "010505073001861d687474703a2f2f6f6373702e707265702e65706172616b7374732e6c7630480603551d1f0441303f303da03ba0398637687474703a2f2f64656d6f2e65706172616b7374732e6c762f63726c2f64656d6f5f4c565f6549445f4943415f323032345f382e63726c300a06082a8648ce3d04030303818c0030818802420138669e93fd16375f950fc4932b38134b515c4135f571e1ccf03fc2cdf7d0443ef0400b11f61bee17e4b889010ce4b47326a98122f2b697123c95b03ee38429ff97024201c924cb7037ecbb32f3574e29b2ddbd224af574a59b85c095f7448ea08000000000000000000000"));
        r.expect("00b0047925", okPadded(
                "bbfd0ea69757429ae0ba3d971adbb02f44547a51a6061b4a603c5d16e3dac682f246c516aa8000000000000000000000"));
    }
}
