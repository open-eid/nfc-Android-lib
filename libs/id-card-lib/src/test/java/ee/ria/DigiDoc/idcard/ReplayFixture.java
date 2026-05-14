package ee.ria.DigiDoc.idcard;

import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;

import org.mockito.MockedConstruction;

import java.security.SecureRandom;
import java.util.function.Consumer;

/**
 * Builder-style harness for replay tests. Each card variant has its own
 * builder method ({@link #lv}, {@link #ee}, {@link #thales}) returning a
 * typed {@code Tunneled} that exposes the configured
 * {@link ApduReplayReader} and the post-tunnel token.
 *
 * <p>Typical usage:
 * <pre>{@code
 *   var f = ReplayFixture.lv()
 *           .with(LatviaIdemiaPersonalDataReplayTest::loadPersonalDataTranscript)
 *           .tunnel();
 *   PersonalData pd = f.token.personalData();
 *   f.assertAllConsumed();
 * }</pre>
 */
final class ReplayFixture {

    private ReplayFixture() {}

    static Builder<LatviaIdemiaWithPace> lv() {
        return new Builder<>(
                LatviaIdemiaPaceReplayTest::loadPaceTranscript,
                LatviaIdemiaPaceReplayTest::capturedSession1HostPrivateBytes,
                LatviaIdemiaWithPace::new,
                LatviaIdemiaPaceReplayTest.CAN);
    }

    static Builder<IdemiaWithPace> ee() {
        return new Builder<>(
                EstoniaIdemiaPaceReplayTest::loadPaceTranscript,
                EstoniaIdemiaPaceReplayTest::capturedSession1HostPrivateBytes,
                IdemiaWithPace::new,
                EstoniaIdemiaPaceReplayTest.CAN);
    }

    static Builder<ThalesWithPace> thales() {
        return new Builder<>(
                ThalesPaceReplayTest::loadPaceTranscript,
                ThalesPaceReplayTest::capturedSession1HostPrivateBytes,
                ThalesWithPace::new,
                ThalesPaceReplayTest.CAN);
    }

    static final class Builder<T extends TokenWithPace> {
        private final ApduReplayReader replay = new ApduReplayReader();
        private final java.util.function.Supplier<byte[][]> ephemeralBytes;
        private final java.util.function.Function<NfcSmartCardReader, T> tokenCtor;
        private final String can;

        private Builder(Consumer<ApduReplayReader> paceLoader,
                        java.util.function.Supplier<byte[][]> ephemeralBytes,
                        java.util.function.Function<NfcSmartCardReader, T> tokenCtor,
                        String can) {
            paceLoader.accept(replay);
            this.ephemeralBytes = ephemeralBytes;
            this.tokenCtor = tokenCtor;
            this.can = can;
        }

        /** Adds a post-PACE transcript (personal data, cert, sign, etc.). */
        Builder<T> with(Consumer<ApduReplayReader> loader) {
            loader.accept(replay);
            return this;
        }

        /** Adds a one-off expectation in-line. */
        Builder<T> expect(String capduHex, ApduReplayReader.Outcome outcome) {
            replay.expect(capduHex, outcome);
            return this;
        }

        /** Builds the reader, constructs the token, runs PACE with stubbed ephemerals. */
        Tunneled<T> tunnel() throws Exception {
            NfcSmartCardReader reader = replay.build();
            T token = tokenCtor.apply(reader);
            try (MockedConstruction<SecureRandom> ignored =
                         EphemeralKeyStub.of(ephemeralBytes.get())) {
                token.tunnel(can);
            }
            return new Tunneled<>(replay, token);
        }
    }

    static final class Tunneled<T extends TokenWithPace> {
        final ApduReplayReader replay;
        final T token;

        Tunneled(ApduReplayReader replay, T token) {
            this.replay = replay;
            this.token = token;
        }

        /** Truth-style assertion that every fixture expectation was consumed. */
        void assertAllConsumed() {
            if (!replay.unconsumed().isEmpty()) {
                throw new AssertionError("Unconsumed APDU expectations: " + replay.unconsumed());
            }
        }
    }
}
