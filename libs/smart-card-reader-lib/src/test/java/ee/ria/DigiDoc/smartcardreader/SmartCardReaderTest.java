package ee.ria.DigiDoc.smartcardreader;

import static com.google.common.primitives.Bytes.concat;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.copyOfRange;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link SmartCardReader}'s APDU framing and SW1-SW2 handling on the
 * boundary between the concrete public {@code transmit(int,int,int,int,byte[],Integer)}
 * and the abstract protected {@code transmit(byte[])}. A {@link FakeReader} subclass
 * records every raw APDU and supplies canned responses.
 */
public final class SmartCardReaderTest {

    private static final byte CLA = 0x00;
    private static final byte INS = 0x01;
    private static final byte P1 = 0x02;
    private static final byte P2 = 0x03;

    private FakeReader reader;

    @BeforeEach
    public void before() {
        reader = new FakeReader();
    }

    // -------- short-data framing --------

    @Test
    public void transmit_dataNull_leNull() throws Exception {
        reader.transmit(CLA, INS, P1, P2, null, null);

        assertThat(reader.requests).hasSize(1);
        assertThat(reader.requests.get(0)).isEqualTo(new byte[]{CLA, INS, P1, P2});
    }

    @Test
    public void transmit_dataEmpty_leNull() throws Exception {
        reader.transmit(CLA, INS, P1, P2, new byte[0], null);

        assertThat(reader.requests).hasSize(1);
        assertThat(reader.requests.get(0)).isEqualTo(new byte[]{CLA, INS, P1, P2});
    }

    @Test
    public void transmit_dataNull_lePresent() throws Exception {
        reader.transmit(CLA, INS, P1, P2, null, 0x00);

        assertThat(reader.requests).hasSize(1);
        assertThat(reader.requests.get(0)).isEqualTo(new byte[]{CLA, INS, P1, P2, 0x00});
    }

    // -------- chunked-data framing --------

    @Test
    public void transmit_data255Bytes_leNull() throws Exception {
        byte[] data = sequenceBytes(255);
        reader.transmit(CLA, INS, P1, P2, data, null);

        assertThat(reader.requests).hasSize(1);
        assertThat(reader.requests.get(0))
                .isEqualTo(concat(new byte[]{CLA, INS, P1, P2, (byte) 0xFF}, data));
    }

    @Test
    public void transmit_data255Bytes_lePresent() throws Exception {
        byte[] data = sequenceBytes(255);
        reader.transmit(CLA, INS, P1, P2, data, 0x00);

        assertThat(reader.requests).hasSize(1);
        assertThat(reader.requests.get(0))
                .isEqualTo(concat(new byte[]{CLA, INS, P1, P2, (byte) 0xFF}, data, new byte[]{0x00}));
    }

    @Test
    public void transmit_data510Bytes_leNull() throws Exception {
        byte[] data = sequenceBytes(510);
        reader.transmit(CLA, INS, P1, P2, data, null);

        // 510 = 255 chained + 255 final.
        assertThat(reader.requests).hasSize(2);
        assertThat(reader.requests.get(0)).isEqualTo(
                concat(new byte[]{0x10, INS, P1, P2, (byte) 0xFF}, copyOfRange(data, 0, 255)));
        assertThat(reader.requests.get(1)).isEqualTo(
                concat(new byte[]{CLA, INS, P1, P2, (byte) 0xFF}, copyOfRange(data, 255, 510)));
    }

    @Test
    public void transmit_data510Bytes_lePresent() throws Exception {
        byte[] data = sequenceBytes(510);
        reader.transmit(CLA, INS, P1, P2, data, 0x01);

        // Le is appended to every chunk, including the chained ones.
        assertThat(reader.requests).hasSize(2);
        assertThat(reader.requests.get(0)).isEqualTo(concat(
                new byte[]{0x10, INS, P1, P2, (byte) 0xFF}, copyOfRange(data, 0, 255), new byte[]{0x01}));
        assertThat(reader.requests.get(1)).isEqualTo(concat(
                new byte[]{CLA, INS, P1, P2, (byte) 0xFF}, copyOfRange(data, 255, 510), new byte[]{0x01}));
    }

    @Test
    public void transmit_data700Bytes_leNull() throws Exception {
        byte[] data = sequenceBytes(700);
        reader.transmit(CLA, INS, P1, P2, data, null);

        // 700 = 255 chained + 255 chained + 190 final (0xBE).
        assertThat(reader.requests).hasSize(3);
        assertThat(reader.requests.get(0)).isEqualTo(
                concat(new byte[]{0x10, INS, P1, P2, (byte) 0xFF}, copyOfRange(data, 0, 255)));
        assertThat(reader.requests.get(1)).isEqualTo(
                concat(new byte[]{0x10, INS, P1, P2, (byte) 0xFF}, copyOfRange(data, 255, 510)));
        assertThat(reader.requests.get(2)).isEqualTo(
                concat(new byte[]{CLA, INS, P1, P2, (byte) 0xBE}, copyOfRange(data, 510, 700)));
    }

    @Test
    public void transmit_data700Bytes_lePresent() throws Exception {
        byte[] data = sequenceBytes(700);
        reader.transmit(CLA, INS, P1, P2, data, 0x03);

        assertThat(reader.requests).hasSize(3);
        assertThat(reader.requests.get(0)).isEqualTo(concat(
                new byte[]{0x10, INS, P1, P2, (byte) 0xFF}, copyOfRange(data, 0, 255), new byte[]{0x03}));
        assertThat(reader.requests.get(1)).isEqualTo(concat(
                new byte[]{0x10, INS, P1, P2, (byte) 0xFF}, copyOfRange(data, 255, 510), new byte[]{0x03}));
        assertThat(reader.requests.get(2)).isEqualTo(concat(
                new byte[]{CLA, INS, P1, P2, (byte) 0xBE}, copyOfRange(data, 510, 700), new byte[]{0x03}));
    }

    // -------- SW1 / SW2 handling --------

    @Test
    public void transmit_response9000() throws Exception {
        // FakeReader's default response is 90 00 with no body.
        assertThat(reader.transmit(0x00, 0x00, 0x00, 0x00, null, null)).isEqualTo(new byte[0]);
    }

    @Test
    public void transmit_responseDataAnd9000() throws Exception {
        reader.respondWith(new byte[]{0x01, 0x03, 0x05, (byte) 0x90, 0x00});

        assertThat(reader.transmit(0x00, 0x00, 0x00, 0x00, null, null))
                .isEqualTo(new byte[]{0x01, 0x03, 0x05});
    }

    @Test
    public void transmit_responseDataAnd61XX() throws Exception {
        // 61 05 → caller follows up with GET RESPONSE; second canned reply ends in 90 00.
        reader.respondWith(
                new byte[]{0x01, 0x03, 0x05, 0x61, 0x05},
                new byte[]{0x05, 0x04, 0x03, 0x02, 0x01, (byte) 0x90, 0x00});

        assertThat(reader.transmit(0x00, 0x00, 0x00, 0x00, null, null))
                .isEqualTo(new byte[]{0x01, 0x03, 0x05, 0x05, 0x04, 0x03, 0x02, 0x01});
    }

    @Test
    public void transmit_responseDataNot9000Nor61XX() throws Exception {
        reader.respondWith(new byte[]{0x67, 0x69});

        ApduResponseException ex = assertThrows(ApduResponseException.class,
                () -> reader.transmit(0x00, 0x00, 0x00, 0x00, null, null));
        assertThat(ex).isEqualTo(new ApduResponseException((byte) 0x67, (byte) 0x69));
    }

    /** Generates {@code 0,1,2,…,9,0,1,2,…} sequence bytes for predictable chunked-input tests. */
    private static byte[] sequenceBytes(int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = (byte) (i % 10);
        }
        return result;
    }
}
