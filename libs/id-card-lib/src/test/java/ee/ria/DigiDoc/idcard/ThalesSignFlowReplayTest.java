package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.bytes;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.ok;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.okPadded;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

/**
 * Thales ESTEID2025 authenticate + calculateSignature replay.
 *
 * <p>Thales sign flow differs substantially from IDEMIA:
 * <ul>
 *   <li>PIN padding is 0x00 (not 0xFF).</li>
 *   <li>VERIFY P2 codes are 0x81/0x82/0x83 (PIN1/PIN2/PUK), not Idemia's
 *       0x01/0x85/0x02.</li>
 *   <li>Sign is a <em>2-step</em> PSO sequence — INS 0x2A P1/P2 0x90/0xA0
 *       loads the hash; INS 0x2A P1/P2 0x9E/0x9A retrieves the signature.</li>
 *   <li>{@code calculateSignature} guards with a pinChangedFlag check
 *       (GET DATA on tag DF2F under A0 wrapper) — a fresh card refuses
 *       to sign until PIN2 is changed once.</li>
 *   <li>MSE SET DST body: {@code 80 01 54 84 01 <keyRef>} — same template
 *       for both auth (keyRef 0x01) and sign (keyRef 0x05). {@code 0x54}
 *       in the algo identifier is {@code 0x24 + hash.length} for SHA-384.</li>
 * </ul>
 */
public final class ThalesSignFlowReplayTest {

    private static final String CAPTURED_AUTH_SIGNATURE =
            "a348a7f00bde39d16c9c3c615b589dfaca4f6f331f1a55937612edd28b9dd919"
                    + "aceca44e701e344d2680c5ffd1479f6a66b9c227a58b68b62da745b87dd9e818"
                    + "fe90d96f78fcb45fb6ab2a5af9369e942cb81108fa4011b3471ebfbf64dd6a36";

    private static final String CAPTURED_SIGN_SIGNATURE =
            "81d363fe890517cfe4103d1467eeb3c3969d153a3a4b3401c1f96a3b976aae1d"
                    + "edb058dc14c2fd988c6678928c7c3944081d58a4586b64c14a876819b87c0dd4"
                    + "31ba5bcb7bb4b381dca2747cad87bf55347d0523edc896362a9e057ff0d90f53";

    /**
     * pinChangedFlag() response: A0 wrapper carrying DF2F=0x01. The lib
     * only checks that DF2F is non-zero; the rest of the captured TLV
     * payload exercises the parser without affecting the gate.
     */
    private static final String CAPTURED_PIN_CHANGED_DATA =
            "a0348301828c04f0000000df210403ffa503df2702ffffdf28010cdf2f0101df"
                    + "3f1403050c01aa01ffff550055ffffaaff55aa000000";

    @Test
    public void authenticate_replaysThalesAuthSignFlow_returnsCapturedSignature() throws Exception {
        var fixture = ReplayFixture.thales()
                .with(r -> {
                    // VERIFY PIN1 — P2=0x81, 0x00-padded.
                    r.expect("00200081" + "0c" + TestPins.PIN1_PADDED_00, ok());
                    // MSE SET DST — body = 80 01 54 || 84 01 01 (auth key).
                    r.expect("002241b6" + "06" + "800154" + "840101", ok());
                    // PSO COMPUTE: INS=0x2A, P1/P2=0x90/0xA0. Sends
                    // TLV(0x90, hash) = 50 bytes. Lib ignores the response.
                    r.expect("002a90a0" + "32" + "9030" + TestApdus.CAPTURED_AUTH_HASH, ok());
                    // PSO GET SIG: INS=0x2A, P1/P2=0x9E/0x9A. Returns r||s.
                    r.expect("002a9e9a00", bytes(CAPTURED_AUTH_SIGNATURE));
                })
                .tunnel();

        byte[] signature = fixture.token.authenticate(
                TestPins.PIN1, Hex.decode(TestApdus.CAPTURED_AUTH_HASH));

        assertThat(Hex.toHexString(signature)).isEqualTo(CAPTURED_AUTH_SIGNATURE);
        fixture.assertAllConsumed();
    }

    @Test
    public void calculateSignature_replaysThalesDocSignFlow_returnsCapturedSignature() throws Exception {
        var fixture = ReplayFixture.thales()
                .with(r -> {
                    // pinChangedFlag — GET DATA wrapped at A0 with PIN2 ref.
                    // Refuses to sign unless DF2F (in the response) is non-zero.
                    r.expect("00cb00ff" + "05" + "a0038301" + "82" + "00",
                            okPadded(CAPTURED_PIN_CHANGED_DATA
                                    + "80000000000000000000"));
                    r.expect("00200082" + "0c" + TestPins.PIN2_PADDED_00, ok());
                    r.expect("002241b6" + "06" + "800154" + "840105", ok());
                    r.expect("002a90a0" + "32" + "9030" + TestApdus.SIGN_INPUT_HASH_48, ok());
                    r.expect("002a9e9a00", bytes(CAPTURED_SIGN_SIGNATURE));
                })
                .tunnel();

        byte[] signature = fixture.token.calculateSignature(
                TestPins.PIN2, Hex.decode(TestApdus.SIGN_INPUT_HASH_48), true);

        assertThat(Hex.toHexString(signature)).isEqualTo(CAPTURED_SIGN_SIGNATURE);
        fixture.assertAllConsumed();
    }
}
