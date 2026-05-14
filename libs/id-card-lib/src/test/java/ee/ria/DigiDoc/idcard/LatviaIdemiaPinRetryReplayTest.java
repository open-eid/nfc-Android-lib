package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.err;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.ok;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * LV IDEMIA PIN retry-counter chain + PUK unblock+change replay,
 * mirroring the captured sessions at 12:06:21–12:08:53 (failing PIN1)
 * and 12:09:18 (PUK unblock).
 *
 * <p>The retry test pins the chip SW → {@link CodeVerificationException}
 * retry-count mapping that downstream UIs depend on for messages like
 * "Retries left: 1". The unblock test pins the SELECT MAIN AID +
 * VERIFY PUK + RESET RETRY COUNTER (INS 0x2C, P1=0x02, P2=0x01) byte
 * sequence.
 */
public final class LatviaIdemiaPinRetryReplayTest {

    private static final String SEL_OBERTHUR_AID =
            "00a4040c0de828bd080ff2504f5420415750";

    @ParameterizedTest(name = "SW {0} {1} → {2} retries left")
    @CsvSource({
            "0x63, 0xC2, 2",
            "0x63, 0xC1, 1",
            "0x69, 0x83, 0",
    })
    public void verifyPin1_failure_translatesToCodeVerificationException(
            int sw1, int sw2, int expectedRetries) throws Exception {
        var fixture = ReplayFixture.lv()
                .with(r -> {
                    r.expect(SEL_OBERTHUR_AID, ok());
                    r.expect("00200001" + "0c" + TestPins.WRONG_PIN1_PADDED_FF,
                            err(sw1, sw2));
                })
                .tunnel();

        CodeVerificationException ex = assertThrows(CodeVerificationException.class,
                () -> fixture.token.authenticate(TestPins.WRONG_PIN1, new byte[48]));
        assertThat(ex.getType()).isEqualTo(CodeType.PIN1);
        assertThat(ex.getRetries()).isEqualTo(expectedRetries);
        fixture.assertAllConsumed();
    }

    @Test
    public void unblockAndChangeCode_pin1_replaysFullPukUnblockSequence() throws Exception {
        // 3-step flow under MAIN AID: re-select, VERIFY PUK (P2=0x02),
        // RESET RETRY COUNTER (INS 0x2C, P1=0x02 "change without current",
        // P2=0x01 picks PIN1).
        var fixture = ReplayFixture.lv()
                .with(r -> {
                    r.expect("00a4040c10a000000077010800070000fe00000100", ok());
                    r.expect("00200002" + "0c" + TestPins.PUK_PADDED_FF, ok());
                    r.expect("002c0201" + "0c" + TestPins.NEW_PIN1_PADDED_FF, ok());
                })
                .tunnel();

        fixture.token.unblockAndChangeCode(TestPins.PUK, CodeType.PIN1, TestPins.NEW_PIN1);

        fixture.assertAllConsumed();
    }
}
