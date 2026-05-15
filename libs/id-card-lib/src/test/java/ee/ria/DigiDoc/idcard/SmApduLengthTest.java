package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

import org.junit.jupiter.api.Test;

public final class SmApduLengthTest {

    // -------- requireShortBerLength --------

    @Test
    public void requireShortBerLength_zero_accepted() throws Exception {
        SmApduLength.requireShortBerLength(0, "DO87");
    }

    @Test
    public void requireShortBerLength_maxShortForm_accepted() throws Exception {
        // 0x7F is the last value cleanly encodable in a single BER length byte.
        SmApduLength.requireShortBerLength(0x7F, "DO87");
    }

    @Test
    public void requireShortBerLength_oneOverMax_throws() {
        // 0x80 would be interpreted by the card as "indefinite length" — must reject.
        SmartCardReaderException ex = assertThrows(SmartCardReaderException.class,
                () -> SmApduLength.requireShortBerLength(0x80, "DO87"));
        // Detail message is on the IAE cause, preserved for diagnostics.
        assertThat(ex).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
        assertThat(ex.getCause()).hasMessageThat().contains("DO87");
        assertThat(ex.getCause()).hasMessageThat().contains("0x7F");
    }

    @Test
    public void requireShortBerLength_singleByteOverflow_throws() {
        // Past 0xFF the cast to byte would silently lose the high bit; this is the
        // scenario the report flagged (≥ 241 byte plaintext → DO87 length wraparound).
        assertThrows(SmartCardReaderException.class,
                () -> SmApduLength.requireShortBerLength(0x100, "DO87"));
        assertThrows(SmartCardReaderException.class,
                () -> SmApduLength.requireShortBerLength(259, "DO87"));
    }

    @Test
    public void requireShortBerLength_negative_throws() {
        assertThrows(SmartCardReaderException.class,
                () -> SmApduLength.requireShortBerLength(-1, "DO85"));
    }

    @Test
    public void requireShortBerLength_labelAppearsInMessage() {
        // Label is part of the IAE message so a thrown error tells you which TLV.
        SmartCardReaderException ex = assertThrows(SmartCardReaderException.class,
                () -> SmApduLength.requireShortBerLength(200, "DO85"));
        assertThat(ex.getCause()).hasMessageThat().contains("DO85");
    }

    // -------- requireSingleByteLc --------

    @Test
    public void requireSingleByteLc_zero_accepted() throws Exception {
        SmApduLength.requireSingleByteLc(0);
    }

    @Test
    public void requireSingleByteLc_max_accepted() throws Exception {
        // 0xFF is the largest value encodable in a single-byte short-APDU Lc.
        SmApduLength.requireSingleByteLc(0xFF);
    }

    @Test
    public void requireSingleByteLc_oneOverMax_throws() {
        // 0x100 would silently truncate to 0x00 when cast to byte — the original bug.
        SmartCardReaderException ex = assertThrows(SmartCardReaderException.class,
                () -> SmApduLength.requireSingleByteLc(0x100));
        assertThat(ex).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
        assertThat(ex.getCause()).hasMessageThat().contains("Lc");
        assertThat(ex.getCause()).hasMessageThat().contains("0xFF");
    }

    @Test
    public void requireSingleByteLc_largeOverflow_throws() {
        assertThrows(SmartCardReaderException.class,
                () -> SmApduLength.requireSingleByteLc(272));
    }

    @Test
    public void requireSingleByteLc_negative_throws() {
        assertThrows(SmartCardReaderException.class,
                () -> SmApduLength.requireSingleByteLc(-1));
    }
}
