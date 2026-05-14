package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

/**
 * Locks in the ATR → concrete-token dispatch in {@link TokenWithPace#create}.
 * A refactor that loses an entry or reorders branches would surface here.
 */
public final class TokenWithPaceCreateTest {

    @Test
    public void create_estonianIdemiaNewerAts_returnsIdemiaWithPace() throws Exception {
        TokenWithPace token = TokenWithPace.create(
                readerWithAts("0012233f536549440f9000"));
        assertThat(token).isInstanceOf(IdemiaWithPace.class);
        assertThat(token).isNotInstanceOf(LatviaIdemiaWithPace.class);
        assertThat(token).isNotInstanceOf(ThalesWithPace.class);
        assertThat(token.cardType()).isEqualTo(CardType.ID1);
    }

    @Test
    public void create_estonianIdemiaOlderAts_returnsIdemiaWithPace() throws Exception {
        TokenWithPace token = TokenWithPace.create(
                readerWithAts("0012233f54654944320f9000"));
        assertThat(token).isInstanceOf(IdemiaWithPace.class);
        assertThat(token).isNotInstanceOf(LatviaIdemiaWithPace.class);
        assertThat(token.cardType()).isEqualTo(CardType.ID1);
    }

    @Test
    public void create_thalesAts_returnsThalesWithPace() throws Exception {
        TokenWithPace token = TokenWithPace.create(
                readerWithAts("8031d85365494464b085051012233f"));
        assertThat(token).isInstanceOf(ThalesWithPace.class);
        assertThat(token.cardType()).isEqualTo(CardType.THALES);
    }

    @Test
    public void create_latvianIdemiaNewerAts_returnsLatviaIdemiaWithPace() throws Exception {
        TokenWithPace token = TokenWithPace.create(
                readerWithAts("0012428f536549440f9000"));
        assertThat(token).isInstanceOf(LatviaIdemiaWithPace.class);
        // Sanity: confirm cardType resolves to LATVIA_IDEMIA (not ID1 from parent).
        assertThat(token.cardType()).isEqualTo(CardType.LATVIA_IDEMIA);
    }

    @Test
    public void create_latvianIdemiaOlderAts_returnsLatviaIdemiaWithPace() throws Exception {
        TokenWithPace token = TokenWithPace.create(
                readerWithAts("0012428f54654944320f9000"));
        assertThat(token).isInstanceOf(LatviaIdemiaWithPace.class);
        assertThat(token.cardType()).isEqualTo(CardType.LATVIA_IDEMIA);
    }

    @Test
    public void create_nullAts_throwsNotSupported() {
        NfcSmartCardReader reader = mock(NfcSmartCardReader.class,
                withSettings().mockMaker(MockMakers.SUBCLASS));
        when(reader.atr()).thenReturn(null);

        NotSupportedException ex = assertThrows(NotSupportedException.class,
                () -> TokenWithPace.create(reader));
        assertThat(ex).hasMessageThat().contains("ATR/ATS cannot be null");
    }

    @Test
    public void create_unknownAts_throwsNotSupported() {
        NfcSmartCardReader reader = readerWithAts("DEADBEEF");
        NotSupportedException ex = assertThrows(NotSupportedException.class,
                () -> TokenWithPace.create(reader));
        assertThat(ex).hasMessageThat().contains("ATS not supported");
    }

    @Test
    public void create_unknownAts_isAlsoSmartCardReaderException() {
        // Catch-block compatibility: NotSupportedException must remain a
        // SmartCardReaderException subclass so existing callers' broader
        // catch blocks keep working.
        NfcSmartCardReader reader = readerWithAts("CAFEBABE");
        assertThrows(SmartCardReaderException.class,
                () -> TokenWithPace.create(reader));
    }

    private static NfcSmartCardReader readerWithAts(String atsHex) {
        NfcSmartCardReader reader = mock(NfcSmartCardReader.class,
                withSettings().mockMaker(MockMakers.SUBCLASS));
        when(reader.atr()).thenReturn(Hex.decode(atsHex));
        return reader;
    }
}
