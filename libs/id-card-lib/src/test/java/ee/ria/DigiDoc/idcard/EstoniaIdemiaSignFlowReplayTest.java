package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.bytes;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.ok;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

/**
 * EE IDEMIA auth-sign and document-sign replay. Same shape as
 * {@link LatviaIdemiaSignFlowReplayTest} but with EE-specific MSE
 * templates and key references:
 *
 * <ul>
 *   <li>EE MSE templates are 6 bytes ({@code 80 04 FF 20 08 00} for auth,
 *       {@code 80 04 FF 15 08 00} for sign); LV templates are 3 bytes
 *       (subclass override).</li>
 *   <li>EE key references — auth 0x81, sign 0x9F — vs LV's 0x82 / 0x9E.</li>
 *   <li>Both produce 96-byte ECDSA-P384 signatures over the same demo
 *       app TBSHASH.</li>
 * </ul>
 */
public final class EstoniaIdemiaSignFlowReplayTest {

    private static final String CAPTURED_AUTH_SIGNATURE =
            "0445217652db2401f64ea2222a21f8cfc8d05757e580c118a57d2c66318dd5b3"
                    + "5a6f360b3917ec58b57883997fb92c03ca449bfe95d674ddcdc7514800c618cf"
                    + "0d7e9e31ae4a430468043c5d857efab721b3354b4b7b7c85ca1d700a369fdf03";

    private static final String CAPTURED_SIGN_SIGNATURE =
            "14243b8394e92db530f560bff89fd12e68204ce1ed01668a847d747bd2375d85"
                    + "e8da807e0989bf6cb5a5f9211970b6d22b244e3233fb2bc2625bee7c490b2322"
                    + "8f1117649f03e04168b91805e6de07e7e35ed8fc05fdf1437b17efd2de77a275";

    @Test
    public void authenticate_replaysEeAuthSignFlow_returnsCapturedSignature() throws Exception {
        var fixture = ReplayFixture.ee()
                .with(r -> {
                    r.expect(TestApdus.SEL_OBERTHUR_AID, ok());
                    r.expect("00200001" + "0c" + TestPins.PIN1_PADDED_FF, ok());
                    // EE 6-byte MSE template + auth key 0x81.
                    r.expect("002241a4" + "09" + "8004ff200800" + "840181", ok());
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
    public void calculateSignature_replaysEeDocumentSignFlow_returnsCapturedSignature() throws Exception {
        var fixture = ReplayFixture.ee()
                .with(r -> {
                    r.expect(TestApdus.SEL_QSCD_AID, ok());
                    r.expect("00200085" + "0c" + TestPins.PIN2_PADDED_FF, ok());
                    // EE 6-byte sign template + sign key 0x9F.
                    r.expect("002241b6" + "09" + "8004ff150800" + "84019f", ok());
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
