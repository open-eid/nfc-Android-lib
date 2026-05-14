package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

import org.junit.jupiter.api.Test;

/**
 * Locks the public contracts of the id-card-lib exception hierarchy:
 * subclassing relationships (callers' broader catch blocks must keep
 * working), accessor outputs, and message formatting.
 */
public final class ExceptionsTest {

    @Test
    public void codeVerificationException_carriesTypeAndRetries() {
        CodeVerificationException ex = new CodeVerificationException(CodeType.PIN1, 2);

        assertThat(ex.getType()).isEqualTo(CodeType.PIN1);
        assertThat(ex.getRetries()).isEqualTo(2);
        assertThat(ex).hasMessageThat().contains("PIN1");
        assertThat(ex).hasMessageThat().contains("Retries left: 2");
        // Subclass relationships matter for callers' catch blocks:
        assertThat(ex).isInstanceOf(IdCardException.class);
        assertThat(ex).isInstanceOf(SmartCardReaderException.class);
    }

    @Test
    public void codeVerificationException_handlesPin2AndPukAndZeroRetries() {
        CodeVerificationException pin2 = new CodeVerificationException(CodeType.PIN2, 1);
        CodeVerificationException puk = new CodeVerificationException(CodeType.PUK, 0);

        assertThat(pin2.getType()).isEqualTo(CodeType.PIN2);
        assertThat(pin2.getRetries()).isEqualTo(1);
        assertThat(puk.getType()).isEqualTo(CodeType.PUK);
        assertThat(puk.getRetries()).isEqualTo(0);
    }

    @Test
    public void paceTunnelException_wrapsCause() {
        Throwable cause = new RuntimeException("PACE failed");
        PaceTunnelException ex = new PaceTunnelException(cause);

        assertThat(ex.getCause()).isSameInstanceAs(cause);
        assertThat(ex).isInstanceOf(SmartCardReaderException.class);
    }

    @Test
    public void idCardException_messageOnly_preservesMessage() {
        // IdCardException constructors are package-private — exercise them
        // via the public CodeVerificationException subclass, which is the
        // only direct caller. Validates the message-carrying constructor.
        CodeVerificationException ex = new CodeVerificationException(CodeType.PIN1, 3);
        assertThat(ex).hasMessageThat().isNotEmpty();
    }

    @Test
    public void notSupportedException_isSmartCardReaderException() throws Exception {
        // NotSupportedException ctors are package-private; surface it via
        // its real producer — TokenWithPace.create with a null ATS.
        NotSupportedException ex = (NotSupportedException) catchException(() -> {
            ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader r =
                org.mockito.Mockito.mock(ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader.class,
                    org.mockito.Mockito.withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
            org.mockito.Mockito.when(r.atr()).thenReturn(null);
            TokenWithPace.create(r);
        });
        assertThat(ex).isInstanceOf(SmartCardReaderException.class);
        assertThat(ex).hasMessageThat().contains("ATR/ATS cannot be null");
    }

    /** Helper: invoke a throwing block, return the thrown exception (or fail). */
    private static Exception catchException(ThrowingRunnable r) {
        try {
            r.run();
        } catch (Exception e) {
            return e;
        }
        throw new AssertionError("expected exception was not thrown");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
