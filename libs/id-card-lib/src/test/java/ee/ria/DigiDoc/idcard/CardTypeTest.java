package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;

import org.junit.jupiter.api.Test;

/**
 * Locks the {@link Token#cardType()} contract for each implementation.
 * No card I/O happens here, so a mocked reader with no stubbing suffices.
 */
public final class CardTypeTest {

    private final NfcSmartCardReader reader = mock(NfcSmartCardReader.class);

    @Test
    public void idemiaWithPace_reportsId1() {
        assertThat(new IdemiaWithPace(reader).cardType()).isEqualTo(CardType.ID1);
    }

    @Test
    public void thalesWithPace_reportsThales() {
        assertThat(new ThalesWithPace(reader).cardType()).isEqualTo(CardType.THALES);
    }

    @Test
    public void latviaIdemiaWithPace_overridesToLatvia() {
        // Important: LatviaIdemiaWithPace extends IdemiaWithPace, so the
        // default cardType() inherited from Idemia is CardType.ID1. The
        // override must win.
        assertThat(new LatviaIdemiaWithPace(reader).cardType())
                .isEqualTo(CardType.LATVIA_IDEMIA);
    }
}
