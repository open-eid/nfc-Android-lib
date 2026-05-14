package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.err;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.ok;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Thales PIN retry + PUK unblock replay (captured 2026-05-14, sessions
 * at 13:34:10–:41 for PIN1 failures, 13:35:06 for PUK unblock).
 *
 * <p>Thales differs from IDEMIA on three retry-related details that
 * this test pins:
 * <ul>
 *   <li>PIN padding is 0x00, not 0xFF (see {@code Thales.code}).</li>
 *   <li>{@code handleApduResponseException} also maps SW {@code 69 84}
 *       (selected PIN reference already locked) to "0 retries" — the
 *       4th row of the parametrized test below.</li>
 *   <li>{@code unblockAndChangeCode} is a <em>single</em> APDU carrying
 *       (PUK || newPIN) instead of IDEMIA's 3-step flow.</li>
 * </ul>
 */
public final class ThalesPinRetryReplayTest {

    @ParameterizedTest(name = "SW {0} {1} → {2} retries left")
    @CsvSource({
            "0x63, 0xC2, 2",
            "0x63, 0xC1, 1",
            "0x69, 0x83, 0",
            "0x69, 0x84, 0",
    })
    public void verifyPin1_failure_translatesToCodeVerificationException(
            int sw1, int sw2, int expectedRetries) throws Exception {
        var fixture = ReplayFixture.thales()
                .with(r -> r.expect(
                        "00200081" + "0c" + TestPins.WRONG_PIN1_PADDED_00,
                        err(sw1, sw2)))
                .tunnel();

        CodeVerificationException ex = assertThrows(CodeVerificationException.class,
                () -> fixture.token.authenticate(TestPins.WRONG_PIN1, new byte[48]));
        assertThat(ex.getType()).isEqualTo(CodeType.PIN1);
        assertThat(ex.getRetries()).isEqualTo(expectedRetries);
        fixture.assertAllConsumed();
    }

    @Test
    public void unblockAndChangeCode_pin1_replaysSingleApduUnblock() throws Exception {
        // Single transmit: INS 0x2C, P1=0x00 (PUK provided), P2=0x81 (PIN1).
        // Data = code(puk) || code(newPin) = 24 bytes 0x00-padded.
        var fixture = ReplayFixture.thales()
                .with(r -> r.expect(
                        "002c0081" + "18" + TestPins.PUK_PADDED_00 + TestPins.NEW_PIN1_PADDED_00,
                        ok()))
                .tunnel();

        fixture.token.unblockAndChangeCode(TestPins.PUK, CodeType.PIN1, TestPins.NEW_PIN1);

        fixture.assertAllConsumed();
    }
}
