package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.bytes;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.ok;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

/**
 * LV IDEMIA auth-sign and document-sign replay. Builds on
 * {@link LatviaIdemiaPaceReplayTest}'s PACE transcript and then drives
 * either {@link IdemiaWithPace#authenticate} or
 * {@link IdemiaWithPace#calculateSignature} through its captured
 * SELECT-AID + VERIFY + MSE-SET + sign sequence.
 *
 * <p>Wire-format details exercised:
 * <ul>
 *   <li>VERIFY P2 (0x01 for PIN1, 0x85 for PIN2) and IDEMIA 0xFF PIN padding.</li>
 *   <li>selectOberthurAid (auth) vs selectQSCDAid (sign) branching.</li>
 *   <li>LV-specific 3-byte MSE templates: {@code 80 01 04 84 01 82}
 *       for auth, {@code 80 01 54 84 01 9E} for sign.</li>
 *   <li>INS codes: 0x88 INTERNAL AUTHENTICATE vs 0x2A/9E/9A PSO
 *       COMPUTE-DIGITAL-SIGNATURE.</li>
 *   <li>MSE SET P2 — 0xA4 for auth template, 0xB6 for sign template.</li>
 * </ul>
 *
 * <p>Captured 96-byte ECDSA-P384 signatures from a synthetic test identity.
 */
public final class LatviaIdemiaSignFlowReplayTest {

    private static final String CAPTURED_AUTH_SIGNATURE =
            "d7b7a572375b62fd34ba6b69050fb90df08cc156e8a33b9b1f5b80908e8f0c1b"
                    + "579724c9d9bb6b8b0c4c8671a36b9193322c301e88e3dfcd6fd565e80ea2b7e5"
                    + "9c1cd8759cab661c1ee04eae9b571023725f1e8fbd9fb0c108b66b507fd76b3c";

    private static final String CAPTURED_SIGN_SIGNATURE =
            "068a585361a2129b48ba35cf4ddc819af54b51209ccccfbe7255c148df0d1300"
                    + "9451ab82960c05c54ef2b72da7d98148211f623cc537524510b0ed053a017378"
                    + "5dadbfceef239a3dc0a888fdc1c0268940749afc8070b17b004a5a8352f80cbf";

    @Test
    public void authenticate_replaysLvAuthSignFlow_returnsCapturedSignature() throws Exception {
        var fixture = ReplayFixture.lv()
                .with(r -> {
                    r.expect(TestApdus.SEL_OBERTHUR_AID, ok());
                    r.expect("00200001" + "0c" + TestPins.PIN1_PADDED_FF, ok());
                    // MSE SET AT (P2=0xA4) — LV 3-byte template + auth key 0x82.
                    r.expect("002241a4" + "06" + "800104" + "840182", ok());
                    // INTERNAL AUTHENTICATE — sends 48-byte hash, gets 96-byte r||s.
                    r.expect("00880000" + "30" + TestApdus.CAPTURED_AUTH_HASH + "00",
                            bytes(CAPTURED_AUTH_SIGNATURE));
                })
                .tunnel();

        byte[] signature = fixture.token.authenticate(
                TestPins.PIN1, Hex.decode(TestApdus.CAPTURED_AUTH_HASH));

        assertThat(Hex.toHexString(signature)).isEqualTo(CAPTURED_AUTH_SIGNATURE);
        fixture.assertAllConsumed();
    }

    @Test
    public void calculateSignature_replaysLvDocumentSignFlow_returnsCapturedSignature() throws Exception {
        var fixture = ReplayFixture.lv()
                .with(r -> {
                    r.expect(TestApdus.SEL_QSCD_AID, ok());
                    r.expect("00200085" + "0c" + TestPins.PIN2_PADDED_FF, ok());
                    // MSE SET DST (P2=0xB6) — sign template + sign key 0x9E.
                    r.expect("002241b6" + "06" + "800154" + "84019e", ok());
                    // PSO COMPUTE — INS 0x2A, P1/P2 = 0x9E/0x9A.
                    r.expect("002a9e9a" + "30" + TestApdus.SIGN_INPUT_HASH_48 + "00",
                            bytes(CAPTURED_SIGN_SIGNATURE));
                })
                .tunnel();

        byte[] signature = fixture.token.calculateSignature(
                TestPins.PIN2, Hex.decode(TestApdus.SIGN_INPUT_HASH_48), true);

        assertThat(Hex.toHexString(signature)).isEqualTo(CAPTURED_SIGN_SIGNATURE);
        fixture.assertAllConsumed();
    }
}
