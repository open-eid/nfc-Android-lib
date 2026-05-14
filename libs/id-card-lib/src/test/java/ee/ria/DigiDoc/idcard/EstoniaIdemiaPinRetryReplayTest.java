package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.err;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.ok;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * EE IDEMIA PIN retry + PUK unblock replay, captured 2026-05-14
 * (sessions at 12:16:25–12:16:44 for PIN1 failures, 12:17:45 for PUK
 * unblock). The retry SW → exception mapping lives in
 * {@code Idemia.handleApduResponseException} and is shared between EE
 * and LV; this test exercises it on the EE path so any card-family
 * override that drifts apart fails here.
 */
public final class EstoniaIdemiaPinRetryReplayTest {

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
        var fixture = ReplayFixture.ee()
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
        var fixture = ReplayFixture.ee()
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
