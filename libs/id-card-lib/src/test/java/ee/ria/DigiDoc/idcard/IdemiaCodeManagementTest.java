package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ee.ria.DigiDoc.smartcardreader.ApduResponseException;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

/**
 * Drives the post-PACE Token surface on {@link IdemiaWithPace} —
 * {@code codeRetryCounter}, {@code changeCode}, {@code unblockAndChangeCode},
 * {@code certificate(AUTH/SIGN)}, {@code pinChangedFlag} — against a mocked
 * reader. These methods are pure APDU plumbing inherited from {@link Idemia};
 * the tests verify the exact CLA/INS/P1/P2 (and, for the data-carrying
 * commands, body bytes) emitted on the wire.
 */
public final class IdemiaCodeManagementTest {

    @Test
    public void pinChangedFlag_returnsConstant() {
        IdemiaWithPace token = new IdemiaWithPace(new CommandStubReader().build());
        assertThat(token.pinChangedFlag()).isEqualTo(1);
    }

    // -------- codeRetryCounter --------

    @Test
    public void codeRetryCounter_pin1_selectsMainAidAndReturnsRetryByte() throws Exception {
        // The retry counter is at index 13 of the GET DATA response.
        CommandStubReader stub = new CommandStubReader().respondTo(0x00, 0xCB, 0x3F, 0xFF, padded(14, 5));
        IdemiaWithPace token = new IdemiaWithPace(stub.build());

        int retries = token.codeRetryCounter(CodeType.PIN1);

        assertThat(retries).isEqualTo(5);
        // First APDU: SELECT MAIN AID (PIN1 lives there)
        CommandStubReader.assertHeader(stub.captured.get(0), 0x00, 0xA4, 0x04, 0x0C);
        // Second APDU: GET DATA with the PIN1 status tag
        CommandStubReader.assertHeader(stub.captured.get(1), 0x00, 0xCB, 0x3F, 0xFF);
    }

    @Test
    public void codeRetryCounter_pin2_selectsQscdAid() throws Exception {
        CommandStubReader stub = new CommandStubReader().respondTo(0x00, 0xCB, 0x3F, 0xFF, padded(14, 3));
        IdemiaWithPace token = new IdemiaWithPace(stub.build());

        int retries = token.codeRetryCounter(CodeType.PIN2);

        assertThat(retries).isEqualTo(3);
        // First APDU: SELECT QSCD AID (PIN2 lives there, not under MAIN)
        CommandStubReader.assertHeader(stub.captured.get(0), 0x00, 0xA4, 0x04, 0x0C);
        // The data payload encodes the QSCD AID, distinct from MAIN AID
        byte[] qscdAid = Hex.decode("515343442041" + "70706c69636174696f6e"); // "QSCD Application"
        assertThat(stub.captured.get(0).data).isEqualTo(qscdAid);
        CommandStubReader.assertHeader(stub.captured.get(1), 0x00, 0xCB, 0x3F, 0xFF);
    }

    @Test
    public void codeRetryCounter_puk_selectsMainAid() throws Exception {
        CommandStubReader stub = new CommandStubReader().respondTo(0x00, 0xCB, 0x3F, 0xFF, padded(14, 0));
        IdemiaWithPace token = new IdemiaWithPace(stub.build());

        assertThat(token.codeRetryCounter(CodeType.PUK)).isEqualTo(0);
        CommandStubReader.assertHeader(stub.captured.get(0), 0x00, 0xA4, 0x04, 0x0C); // MAIN AID
    }

    // -------- changeCode --------

    @Test
    public void changeCode_pin1_emitsChangeReferenceData() throws Exception {
        CommandStubReader stub = new CommandStubReader();
        IdemiaWithPace token = new IdemiaWithPace(stub.build());

        token.changeCode(CodeType.PIN1, "1234".getBytes(), "5678".getBytes());

        // Sequence: SELECT MAIN AID, then CHANGE REFERENCE DATA (INS=0x24)
        CommandStubReader.assertHeader(stub.captured.get(0), 0x00, 0xA4, 0x04, 0x0C);
        CommandStubReader.assertHeader(stub.captured.get(1), 0x00, 0x24, 0x00, 0x01); // P2 = 0x01 for PIN1
        // Data: code("1234") || code("5678") — each padded to 12 bytes with 0xFF.
        assertThat(stub.captured.get(1).data).isEqualTo(Hex.decode(
                "31323334ffffffffffffffff" + "35363738ffffffffffffffff"));
    }

    @Test
    public void changeCode_pin2_selectsQscdAndUsesPin2P2() throws Exception {
        CommandStubReader stub = new CommandStubReader();
        IdemiaWithPace token = new IdemiaWithPace(stub.build());

        token.changeCode(CodeType.PIN2, "12345".getBytes(), "98765".getBytes());

        CommandStubReader.assertHeader(stub.captured.get(0), 0x00, 0xA4, 0x04, 0x0C); // QSCD AID
        CommandStubReader.assertHeader(stub.captured.get(1), 0x00, 0x24, 0x00, 0x85); // P2 = 0x85 for PIN2
        // Data: code("12345") || code("98765").
        assertThat(stub.captured.get(1).data).isEqualTo(Hex.decode(
                "3132333435ffffffffffffff" + "3938373635ffffffffffffff"));
    }

    @Test
    public void changeCode_wrongCurrentCode_throwsCodeVerificationException() throws Exception {
        // Chip returns 63 C2 = "wrong code, 2 retries left".
        CommandStubReader stub = new CommandStubReader()
                .throwOn(0x00, 0x24, new ApduResponseException((byte) 0x63, (byte) 0xC2));
        IdemiaWithPace token = new IdemiaWithPace(stub.build());

        CodeVerificationException ex = assertThrows(CodeVerificationException.class,
                () -> token.changeCode(CodeType.PIN1, "0000".getBytes(), "1234".getBytes()));
        assertThat(ex.getType()).isEqualTo(CodeType.PIN1);
        assertThat(ex.getRetries()).isEqualTo(2);
    }

    @Test
    public void changeCode_unexpectedSw_rethrowsAsApduResponseException() throws Exception {
        // SW != 63xx / 6983 — exception should propagate untranslated.
        CommandStubReader stub = new CommandStubReader()
                .throwOn(0x00, 0x24, new ApduResponseException((byte) 0x6A, (byte) 0x82));
        IdemiaWithPace token = new IdemiaWithPace(stub.build());

        assertThrows(ApduResponseException.class,
                () -> token.changeCode(CodeType.PIN1, "0000".getBytes(), "1234".getBytes()));
    }

    // -------- unblockAndChangeCode --------

    @Test
    public void unblockAndChangeCode_pin1_selectsMainAidThenVerifyPukThenReset() throws Exception {
        CommandStubReader stub = new CommandStubReader();
        IdemiaWithPace token = new IdemiaWithPace(stub.build());

        token.unblockAndChangeCode("12345678".getBytes(), CodeType.PIN1, "1234".getBytes());

        // Sequence: SELECT MAIN AID, VERIFY PUK, RESET RETRY COUNTER (INS=2C)
        CommandStubReader.assertHeader(stub.captured.get(0), 0x00, 0xA4, 0x04, 0x0C);  // MAIN AID
        CommandStubReader.assertHeader(stub.captured.get(1), 0x00, 0x20, 0x00, 0x02);  // VERIFY PUK (P2=0x02)
        CommandStubReader.assertHeader(stub.captured.get(2), 0x00, 0x2C, 0x02, 0x01);  // RESET PIN1 (P1=2, P2=PIN1)
        // VERIFY PUK data: code("12345678").
        assertThat(stub.captured.get(1).data).isEqualTo(Hex.decode("3132333435363738ffffffff"));
        // RESET RETRY COUNTER data: code("1234") — new PIN1.
        assertThat(stub.captured.get(2).data).isEqualTo(Hex.decode("31323334ffffffffffffffff"));
    }

    @Test
    public void unblockAndChangeCode_pin2_switchesToQscdAfterPuk() throws Exception {
        CommandStubReader stub = new CommandStubReader();
        IdemiaWithPace token = new IdemiaWithPace(stub.build());

        token.unblockAndChangeCode("12345678".getBytes(), CodeType.PIN2, "12345".getBytes());

        // Sequence: SELECT MAIN AID, VERIFY PUK, SELECT QSCD AID, RESET PIN2
        CommandStubReader.assertHeader(stub.captured.get(0), 0x00, 0xA4, 0x04, 0x0C);  // MAIN AID
        CommandStubReader.assertHeader(stub.captured.get(1), 0x00, 0x20, 0x00, 0x02);  // VERIFY PUK
        CommandStubReader.assertHeader(stub.captured.get(2), 0x00, 0xA4, 0x04, 0x0C);  // QSCD AID
        CommandStubReader.assertHeader(stub.captured.get(3), 0x00, 0x2C, 0x02, 0x85);  // RESET PIN2 (P2=0x85)
        // VERIFY PUK data: code("12345678").
        assertThat(stub.captured.get(1).data).isEqualTo(Hex.decode("3132333435363738ffffffff"));
        // RESET RETRY COUNTER data: code("12345") — new PIN2.
        assertThat(stub.captured.get(3).data).isEqualTo(Hex.decode("3132333435ffffffffffffff"));
    }

    @Test
    public void unblockAndChangeCode_wrongPuk_throwsCodeVerificationExceptionForPuk() throws Exception {
        // VERIFY PUK fails — the translated error type must be PUK, regardless of which
        // PIN was being unblocked. (Tested separately for PIN1 and PIN2 in case the
        // code-translation logic ever differentiates.)
        CommandStubReader stub = new CommandStubReader()
                .throwOn(0x00, 0x20, new ApduResponseException((byte) 0x63, (byte) 0xC1));
        IdemiaWithPace token = new IdemiaWithPace(stub.build());

        CodeVerificationException ex = assertThrows(CodeVerificationException.class,
                () -> token.unblockAndChangeCode("BADPUK".getBytes(), CodeType.PIN1, "1234".getBytes()));
        assertThat(ex.getType()).isEqualTo(CodeType.PUK);
        assertThat(ex.getRetries()).isEqualTo(1);
    }

    // -------- certificate --------

    @Test
    public void certificate_authentication_selectsAuthCertEf() throws Exception {
        // Stub a small cert payload + 6B00 to end the READ BINARY loop.
        CommandStubReader stub = new CommandStubReader()
                .respondTo(0x00, 0xB0, new byte[] {0x30, 0x05, (byte) 0xAA, (byte) 0xBB, (byte) 0xCC})
                .throwOn(0x00, 0xB0, new ApduResponseException((byte) 0x6B, (byte) 0x00));
        IdemiaWithPace token = new IdemiaWithPace(stub.build());

        byte[] cert = token.certificate(CertificateType.AUTHENTICATION);

        assertThat(cert).hasLength(5);
        // SELECT MAIN AID, then SELECT cert EF by path (P1 = 0x09)
        CommandStubReader.assertHeader(stub.captured.get(0), 0x00, 0xA4, 0x04, 0x0C);
        CommandStubReader.Apdu certSelect = stub.captured.get(1);
        CommandStubReader.assertHeader(certSelect, 0x00, 0xA4, 0x09, 0x0C);
        // AUTH cert path: AD F1 34 01
        assertThat(certSelect.data).isEqualTo(Hex.decode("ADF13401"));
    }

    @Test
    public void certificate_signing_routesToSigningCertEf() throws Exception {
        CommandStubReader stub = new CommandStubReader()
                .respondTo(0x00, 0xB0, new byte[] {0x30, 0x03, 0x01, 0x02, 0x03})
                .throwOn(0x00, 0xB0, new ApduResponseException((byte) 0x6B, (byte) 0x00));
        IdemiaWithPace token = new IdemiaWithPace(stub.build());

        token.certificate(CertificateType.SIGNING);

        CommandStubReader.Apdu certSelect = stub.captured.get(1);
        CommandStubReader.assertHeader(certSelect, 0x00, 0xA4, 0x09, 0x0C);
        // SIGN cert path: AD F2 34 1F
        assertThat(certSelect.data).isEqualTo(Hex.decode("ADF2341F"));
    }

    /** GET DATA stub payload: {@code totalLen} bytes, retry byte at index 13. */
    private static byte[] padded(int totalLen, int retryByteAtIndex13) {
        byte[] out = new byte[totalLen];
        out[13] = (byte) retryByteAtIndex13;
        return out;
    }
}
