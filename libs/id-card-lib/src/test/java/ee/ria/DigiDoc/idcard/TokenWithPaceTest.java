// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

public final class TokenWithPaceTest {

    @Test
    public void create_withoutConfig_estonianIdemiaAts_returnsIdemiaWithPace() throws Exception {
        TokenWithPace token = TokenWithPace.create(readerWithAts("0012233f536549440f9000"));

        assertThat(token).isInstanceOf(IdemiaWithPace.class);
        assertThat(token.cardType()).isEqualTo(CardType.ID1);
    }

    @Test
    public void create_withoutConfig_thalesAts_returnsThalesWithPace() throws Exception {
        TokenWithPace token = TokenWithPace.create(readerWithAts("8031d85365494464b085051012233f"));

        assertThat(token).isInstanceOf(ThalesWithPace.class);
        assertThat(token.cardType()).isEqualTo(CardType.THALES);
    }

    @Test
    public void create_withoutConfig_latvianIdemiaAts_throwsNotSupported() {
        NfcSmartCardReader reader = readerWithAts("0012428f536549440f9000");

        assertThrows(NotSupportedException.class, () -> TokenWithPace.create(reader));
    }

    @Test
    public void create_withoutConfig_unknownAts_throwsNotSupported() {
        NfcSmartCardReader reader = readerWithAts("DEADBEEF");

        assertThrows(NotSupportedException.class, () -> TokenWithPace.create(reader));
    }

    private static NfcSmartCardReader readerWithAts(String atsHex) {
        NfcSmartCardReader reader = mock(NfcSmartCardReader.class,
                withSettings().mockMaker(MockMakers.SUBCLASS));
        when(reader.atr()).thenReturn(Hex.decode(atsHex));
        return reader;
    }
}
