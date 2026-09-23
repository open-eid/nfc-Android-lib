// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later
package ee.ria.DigiDoc.smartcardreader;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.io.IOException;

public final class CardConnectionLostExceptionTest {

    @Test
    public void preservesTheCauseSoConsumersCanStillUnwrapIt() {
        IOException cause = new IOException("tag left the field");
        assertThat(new CardConnectionLostException(cause).getCause()).isSameInstanceAs(cause);
    }

    @Test
    public void messageStillReportsTheUnderlyingCause() {
        assertThat(new CardConnectionLostException(new IOException("tag left the field")).getMessage())
                .isEqualTo("java.io.IOException: tag left the field");
    }

    @Test
    public void isCaughtByExistingSmartCardReaderExceptionHandlers() {
        assertThat(new CardConnectionLostException(new IOException()))
                .isInstanceOf(SmartCardReaderException.class);
    }

    @Test
    public void survivesACauseWithNoMessage() {
        assertThat(new CardConnectionLostException(new IOException()).getMessage())
                .isEqualTo("java.io.IOException");
    }
}
