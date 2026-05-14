package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ee.ria.DigiDoc.smartcardreader.ApduResponseException;
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

/**
 * Mirror of {@link IdemiaCodeManagementTest} for {@link ThalesWithPace}.
 * Locks the post-PACE Token surface on the Thales card:
 * <ul>
 *   <li>{@code codeRetryCounter} — GET DATA with TLV-wrapped response
 *       (different from Idemia's positional byte at index 13).</li>
 *   <li>{@code pinChangedFlag} — GET DATA on PIN2 with tag 0xDF2F.</li>
 *   <li>{@code changeCode} — rejects PUK type; otherwise CHANGE REFERENCE DATA.</li>
 *   <li>{@code unblockAndChangeCode} — rejects PUK type; P1 toggles on null PUK.</li>
 *   <li>{@code certificate(AUTH/SIGN)} — SELECT FCI with size tag, then chunked READ BINARY.</li>
 * </ul>
 */
public final class ThalesCodeManagementTest {

    // -------- codeRetryCounter / pinChangedFlag --------

    @Test
    public void codeRetryCounter_pin1_emitsGetDataAndExtractsTagDf21() throws Exception {
        // Response shape: A0 04 DF 21 01 <retries>
        CommandStubReader stub = new CommandStubReader()
                .respondTo(0x00, 0xCB, Hex.decode("A004DF210105"));
        ThalesWithPace token = new ThalesWithPace(stub.build());

        assertThat(token.codeRetryCounter(CodeType.PIN1)).isEqualTo(5);
        // Single APDU: GET DATA with PIN1 reference (0x81) in the data body
        assertThat(stub.captured).hasSize(1);
        CommandStubReader.assertHeader(stub.captured.get(0), 0x00, 0xCB, 0x00, 0xFF);
        // Body encodes the PIN reference: A0 03 83 01 <ref>
        assertThat(stub.captured.get(0).data).isEqualTo(Hex.decode("A003830181"));
    }

    @Test
    public void codeRetryCounter_pin2_usesPin2Reference() throws Exception {
        CommandStubReader stub = new CommandStubReader()
                .respondTo(0x00, 0xCB, Hex.decode("A004DF210103"));
        ThalesWithPace token = new ThalesWithPace(stub.build());

        assertThat(token.codeRetryCounter(CodeType.PIN2)).isEqualTo(3);
        assertThat(stub.captured.get(0).data).isEqualTo(Hex.decode("A003830182"));
    }

    @Test
    public void codeRetryCounter_puk_usesPukReference() throws Exception {
        CommandStubReader stub = new CommandStubReader()
                .respondTo(0x00, 0xCB, Hex.decode("A004DF210100"));
        ThalesWithPace token = new ThalesWithPace(stub.build());

        assertThat(token.codeRetryCounter(CodeType.PUK)).isEqualTo(0);
        assertThat(stub.captured.get(0).data).isEqualTo(Hex.decode("A003830183"));
    }

    @Test
    public void pinChangedFlag_queriesPin2AndExtractsTagDf2f() throws Exception {
        // Response wraps DF 2F 01 01 — pinChangedFlag returns the inner byte (1)
        CommandStubReader stub = new CommandStubReader()
                .respondTo(0x00, 0xCB, Hex.decode("A004DF2F0101"));
        ThalesWithPace token = new ThalesWithPace(stub.build());

        assertThat(token.pinChangedFlag()).isEqualTo(1);
        // pinChangedFlag uses the PIN2 reference (0x82) in the GET DATA body
        assertThat(stub.captured.get(0).data).isEqualTo(Hex.decode("A003830182"));
    }

    // -------- changeCode --------

    @Test
    public void changeCode_pin1_emitsChangeReferenceDataWithPin1P2() throws Exception {
        CommandStubReader stub = new CommandStubReader();
        ThalesWithPace token = new ThalesWithPace(stub.build());

        token.changeCode(CodeType.PIN1, "1234".getBytes(), "5678".getBytes());

        // No AID select — Thales differs from Idemia here.
        assertThat(stub.captured).hasSize(1);
        CommandStubReader.assertHeader(stub.captured.get(0), 0x00, 0x24, 0x00, 0x81);
    }

    @Test
    public void changeCode_pin2_usesPin2P2() throws Exception {
        CommandStubReader stub = new CommandStubReader();
        ThalesWithPace token = new ThalesWithPace(stub.build());

        token.changeCode(CodeType.PIN2, "12345".getBytes(), "98765".getBytes());

        CommandStubReader.assertHeader(stub.captured.get(0), 0x00, 0x24, 0x00, 0x82);
    }

    @Test
    public void changeCode_puk_throwsImmediately() {
        CommandStubReader stub = new CommandStubReader();
        ThalesWithPace token = new ThalesWithPace(stub.build());

        SmartCardReaderException ex = assertThrows(SmartCardReaderException.class,
                () -> token.changeCode(CodeType.PUK, "12345678".getBytes(), "87654321".getBytes()));
        assertThat(ex).hasMessageThat().contains("Cannot change PUK");
        // No APDU was emitted — the rejection happens before any I/O.
        assertThat(stub.captured).isEmpty();
    }

    @Test
    public void changeCode_wrongCurrentCode_translatesToCodeVerificationException() throws Exception {
        CommandStubReader stub = new CommandStubReader()
                .throwOn(0x00, 0x24, new ApduResponseException((byte) 0x63, (byte) 0xC2));
        ThalesWithPace token = new ThalesWithPace(stub.build());

        CodeVerificationException ex = assertThrows(CodeVerificationException.class,
                () -> token.changeCode(CodeType.PIN1, "0000".getBytes(), "1234".getBytes()));
        assertThat(ex.getType()).isEqualTo(CodeType.PIN1);
        assertThat(ex.getRetries()).isEqualTo(2);
    }

    @Test
    public void changeCode_recognises6983And6984AsCodeVerificationFailure() throws Exception {
        // Thales translates 6983 AND 6984 (in addition to 63xx) — Idemia only
        // translates 63xx + 6983. Lock the Thales-side broader handling.
        CommandStubReader stub = new CommandStubReader()
                .throwOn(0x00, 0x24, new ApduResponseException((byte) 0x69, (byte) 0x84));
        ThalesWithPace token = new ThalesWithPace(stub.build());

        CodeVerificationException ex = assertThrows(CodeVerificationException.class,
                () -> token.changeCode(CodeType.PIN1, "0000".getBytes(), "1234".getBytes()));
        assertThat(ex.getType()).isEqualTo(CodeType.PIN1);
        assertThat(ex.getRetries()).isEqualTo(0);
    }

    // -------- unblockAndChangeCode --------

    @Test
    public void unblockAndChangeCode_pin1_withPuk_usesP1Zero() throws Exception {
        CommandStubReader stub = new CommandStubReader();
        ThalesWithPace token = new ThalesWithPace(stub.build());

        token.unblockAndChangeCode("12345678".getBytes(), CodeType.PIN1, "1234".getBytes());

        // Single APDU: RESET RETRY COUNTER. P1=0x00 (PUK supplied), P2=0x81 (PIN1 ref).
        assertThat(stub.captured).hasSize(1);
        CommandStubReader.assertHeader(stub.captured.get(0), 0x00, 0x2C, 0x00, 0x81);
    }

    @Test
    public void unblockAndChangeCode_pin1_withoutPuk_npesInProductionCode() {
        // Thales.unblockAndChangeCode has a latent bug for the null-PUK path:
        // the P1 ternary `pukCode == null ? 0x02 : 0x00` claims to support
        // already-authenticated unblocking, but the body still calls
        // `concat(code(pukCode), ...)` and `code(null)` NPEs in Arrays.copyOf.
        // Locking the *current* (buggy) behaviour: the call NPEs, no APDU
        // is sent. A future fix should either accept null pukCode in `code()`
        // or short-circuit the data assembly when pukCode is null.
        CommandStubReader stub = new CommandStubReader();
        ThalesWithPace token = new ThalesWithPace(stub.build());

        assertThrows(NullPointerException.class,
                () -> token.unblockAndChangeCode(null, CodeType.PIN1, "1234".getBytes()));
        assertThat(stub.captured).isEmpty();
    }

    @Test
    public void unblockAndChangeCode_pin2_usesPin2P2() throws Exception {
        CommandStubReader stub = new CommandStubReader();
        ThalesWithPace token = new ThalesWithPace(stub.build());

        token.unblockAndChangeCode("12345678".getBytes(), CodeType.PIN2, "12345".getBytes());

        CommandStubReader.assertHeader(stub.captured.get(0), 0x00, 0x2C, 0x00, 0x82);
    }

    @Test
    public void unblockAndChangeCode_puk_throwsImmediately() {
        CommandStubReader stub = new CommandStubReader();
        ThalesWithPace token = new ThalesWithPace(stub.build());

        SmartCardReaderException ex = assertThrows(SmartCardReaderException.class,
                () -> token.unblockAndChangeCode("12345678".getBytes(), CodeType.PUK, "x".getBytes()));
        assertThat(ex).hasMessageThat().contains("Cannot unblock and change PUK");
        assertThat(stub.captured).isEmpty();
    }

    @Test
    public void unblockAndChangeCode_wrongPuk_throwsForPuk() throws Exception {
        CommandStubReader stub = new CommandStubReader()
                .throwOn(0x00, 0x2C, new ApduResponseException((byte) 0x63, (byte) 0xC1));
        ThalesWithPace token = new ThalesWithPace(stub.build());

        CodeVerificationException ex = assertThrows(CodeVerificationException.class,
                () -> token.unblockAndChangeCode("BADPUK".getBytes(), CodeType.PIN1, "1234".getBytes()));
        assertThat(ex.getType()).isEqualTo(CodeType.PUK);
        assertThat(ex.getRetries()).isEqualTo(1);
    }

    // -------- certificate --------

    @Test
    public void certificate_authentication_selectsAuthCertEfAndReadsBody() throws Exception {
        // FCI must be a constructed template — `parseTLVRecursive` returns the
        // CHILDREN of top-level TLVs flattened, so a bare `80 02 …` would be
        // ignored and `readFile` would fall back to the default size (0xE5)
        // and loop forever. Use the proper FCI shape:
        //   62 04            FCI template (constructed) length 4
        //     80 02 00 05    file size = 5 (tag 0x80 inside)
        byte[] fci = Hex.decode("620480020005");
        byte[] certBody = new byte[] {0x30, 0x03, 0x01, 0x02, 0x03};
        CommandStubReader stub = new CommandStubReader()
                .respondTo(0x00, 0xA4, fci)
                .respondTo(0x00, 0xB0, certBody);
        ThalesWithPace token = new ThalesWithPace(stub.build());

        byte[] cert = token.certificate(CertificateType.AUTHENTICATION);

        assertThat(cert).isEqualTo(certBody);
        // SELECT with P1=0x08 (Thales-specific path-based EF selection), P2=0x04.
        CommandStubReader.Apdu sel = stub.captured.get(0);
        CommandStubReader.assertHeader(sel, 0x00, 0xA4, 0x08, 0x04);
        assertThat(sel.data).isEqualTo(Hex.decode("ADF13411"));  // AUTH cert path
        // READ BINARY at offset 0.
        CommandStubReader.Apdu read = stub.captured.get(1);
        CommandStubReader.assertHeader(read, 0x00, 0xB0, 0x00, 0x00);
    }

    @Test
    public void certificate_signing_routesToSigningCertEf() throws Exception {
        byte[] fci = Hex.decode("620480020003");
        byte[] certBody = new byte[] {0x01, 0x02, 0x03};
        CommandStubReader stub = new CommandStubReader()
                .respondTo(0x00, 0xA4, fci)
                .respondTo(0x00, 0xB0, certBody);
        ThalesWithPace token = new ThalesWithPace(stub.build());

        byte[] cert = token.certificate(CertificateType.SIGNING);

        assertThat(cert).isEqualTo(certBody);
        CommandStubReader.Apdu sel = stub.captured.get(0);
        assertThat(sel.data).isEqualTo(Hex.decode("ADF23421"));  // SIGN cert path
    }

}
