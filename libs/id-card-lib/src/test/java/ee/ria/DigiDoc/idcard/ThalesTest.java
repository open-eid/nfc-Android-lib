// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later
package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import ee.ria.DigiDoc.smartcardreader.SmartCardReader;
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockMakers;

public final class ThalesTest {

    private SmartCardReader reader;
    private Thales thales;

    @Before
    public void before() {
        reader = mock(SmartCardReader.class, withSettings().mockMaker(MockMakers.SUBCLASS));
        thales = new Thales(reader);
    }

    private static byte[] pinChangedResponse(int value) {
        return new byte[] {(byte) 0xA0, 0x04, (byte) 0xDF, 0x2F, 0x01, (byte) value};
    }

    private void stubGetData(byte[] response) throws SmartCardReaderException {
        when(reader.transmit(anyInt(), eq(0xCB), anyInt(), anyInt(), any(), anyInt()))
                .thenReturn(response);
    }

    @Test
    public void calculateSignature_throwsCodeNotActivated_whenTheFlagIsReadAsZero() throws Exception {
        stubGetData(pinChangedResponse(0));

        CodeNotActivatedException ex = assertThrows(CodeNotActivatedException.class,
                () -> thales.calculateSignature(new byte[] {1, 2, 3, 4, 5}, new byte[32], true));

        assertThat(ex.getType()).isEqualTo(CodeType.PIN2);
        assertThat(ex).hasMessageThat()
                .isEqualTo("PIN2 has not been changed, operation not allowed");
    }

    @Test
    public void calculateSignature_doesNotClaimNotActivated_whenTheFlagCannotBeRead() throws Exception {
        stubGetData(new byte[] {(byte) 0x90, 0x00});

        SmartCardReaderException ex = assertThrows(SmartCardReaderException.class,
                () -> thales.calculateSignature(new byte[] {1, 2, 3, 4, 5}, new byte[32], true));

        assertThat(ex).isNotInstanceOf(CodeNotActivatedException.class);
        assertThat(ex).hasMessageThat().contains("Could not read the PIN2 changed flag");
    }

    @Test
    public void calculateSignature_readsTheChangedFlagWithASingleGetData() throws Exception {
        stubGetData(pinChangedResponse(0));

        assertThrows(CodeNotActivatedException.class,
                () -> thales.calculateSignature(new byte[] {1, 2, 3, 4, 5}, new byte[32], true));

        verify(reader, times(1)).transmit(anyInt(), eq(0xCB), anyInt(), anyInt(), any(), anyInt());
    }

    @Test
    public void pinChangedFlag_stillReportsZeroWhenTheTagIsAbsent() throws Exception {
        stubGetData(new byte[] {(byte) 0x90, 0x00});

        assertThat(thales.pinChangedFlag(CodeType.PIN2)).isEqualTo(0);
    }

    @Test
    public void pinChangedFlag_reportsTheTagValueWhenPresent() throws Exception {
        stubGetData(pinChangedResponse(1));

        assertThat(thales.pinChangedFlag(CodeType.PIN2)).isEqualTo(1);
    }
}
