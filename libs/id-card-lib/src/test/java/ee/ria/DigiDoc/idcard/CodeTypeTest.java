// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public final class CodeTypeTest {

    @Test
    public void minLength_isFourForPin1FiveForPin2EightForPuk() {
        assertThat(CodeType.PIN1.minLength).isEqualTo(4);
        assertThat(CodeType.PIN2.minLength).isEqualTo(5);
        assertThat(CodeType.PUK.minLength).isEqualTo(8);
    }

    @Test
    public void maxLength_isTwelveForEveryCodeType() {
        for (CodeType type : CodeType.values()) {
            assertThat(type.maxLength).isEqualTo(12);
        }
    }

    @Test
    public void isLengthValid_acceptsBothBoundaries() {
        for (CodeType type : CodeType.values()) {
            assertThat(type.isLengthValid(type.minLength)).isTrue();
            assertThat(type.isLengthValid(type.maxLength)).isTrue();
        }
    }

    @Test
    public void isLengthValid_rejectsJustOutsideBothBoundaries() {
        for (CodeType type : CodeType.values()) {
            assertThat(type.isLengthValid(type.minLength - 1)).isFalse();
            assertThat(type.isLengthValid(type.maxLength + 1)).isFalse();
        }
    }

    @Test
    public void isLengthValid_rejectsEmptyAndNegative() {
        for (CodeType type : CodeType.values()) {
            assertThat(type.isLengthValid(0)).isFalse();
            assertThat(type.isLengthValid(-1)).isFalse();
        }
    }

    @Test
    public void apduValues_areUnchanged() {
        assertThat(CodeType.PIN1.value).isEqualTo((byte) 0x01);
        assertThat(CodeType.PIN2.value).isEqualTo((byte) 0x02);
        assertThat(CodeType.PUK.value).isEqualTo((byte) 0x00);
        assertThat(CodeType.PIN1.retryValue).isEqualTo((byte) 0x01);
        assertThat(CodeType.PIN2.retryValue).isEqualTo((byte) 0x02);
        assertThat(CodeType.PUK.retryValue).isEqualTo((byte) 0x03);
    }
}
