// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

import org.junit.Test;

public final class CodeNotActivatedExceptionTest {

    @Test
    public void message_matchesTheLegacyLiteralConsumersStringMatchOn() {
        assertThat(new CodeNotActivatedException(CodeType.PIN2).getMessage())
                .isEqualTo("PIN2 has not been changed, operation not allowed");
    }

    @Test
    public void getType_returnsTheCodeTypeItWasRaisedFor() {
        assertThat(new CodeNotActivatedException(CodeType.PIN2).getType())
                .isEqualTo(CodeType.PIN2);
    }

    @Test
    public void isCaughtByExistingSmartCardReaderExceptionHandlers() {
        assertThat(new CodeNotActivatedException(CodeType.PIN2))
                .isInstanceOf(SmartCardReaderException.class);
    }
}
