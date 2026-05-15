package ee.ria.DigiDoc.smartcardreader.nfc;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockMakers;

import java.util.ArrayList;
import java.util.List;

/**
 * Locks in the SM-mode dispatch behaviour of
 * {@link NfcSmartCardReader#transmit(int, int, int, int, byte[], Integer)}.
 * The public form decides how to chunk the C-APDU before handing each chunk
 * to the {@link ApduEncryptor} for SM wrapping. We need to assert that
 * shape independent of any future internal refactoring (notably collapsing
 * the {@code data==null||empty} and {@code data.length<256} branches).
 *
 * <p>Constructed with Mockito's SUBCLASS mock-maker so we get a real-method
 * delegate without having to feed it an {@code IsoDep}/{@code Tag} pair.
 * The protected raw {@code transmit(byte[])} is stubbed to record wire
 * APDUs and return a {@code 90 00}-only response that bypasses the
 * {@code 0x61}/GET RESPONSE chain.
 */
public final class NfcSmartCardReaderTest {

    private NfcSmartCardReader reader;
    private ApduEncryptor encryptor;
    private List<byte[]> wireApdus;

    @BeforeEach
    public void setUp() throws Exception {
        reader = mock(NfcSmartCardReader.class,
                withSettings()
                        .mockMaker(MockMakers.SUBCLASS)
                        .defaultAnswer(Answers.CALLS_REAL_METHODS));

        wireApdus = new ArrayList<>();
        doAnswer(inv -> {
            wireApdus.add(inv.getArgument(0));
            // SW=9000, no body — short-circuits past the GET RESPONSE branch.
            return new byte[]{(byte) 0x90, 0x00};
        }).when(reader).transmit(any(byte[].class));

        encryptor = mock(ApduEncryptor.class);
        when(encryptor.encryptAndMac(anyInt(), anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new byte[]{0x0C, (byte) 0xA4, 0x04, 0x0C, 0x00, (byte) 0x90, 0x00});
        when(encryptor.decryptAndVerify(any())).thenReturn(new byte[0]);
    }

    // -------- no-encryptor path (pre-tunnel) bypasses encryptAndMac --------

    @Test
    public void transmit_withoutEncryptor_delegatesToSuper() throws Exception {
        // No apduEncryptor registered → falls through to plain SmartCardReader.transmit,
        // which dispatches to the protected transmit(byte[]) without SM wrapping.
        reader.transmit(0x00, 0xA4, 0x04, 0x0C, null, null);

        assertThat(wireApdus).hasSize(1);
        // First 4 bytes are the unencrypted CLA INS P1 P2.
        assertThat(wireApdus.get(0)[0]).isEqualTo((byte) 0x00);
        assertThat(wireApdus.get(0)[1]).isEqualTo((byte) 0xA4);
        verify(encryptor, times(0)).encryptAndMac(anyInt(), anyInt(), anyInt(), anyInt(), any(), any());
    }

    // -------- SM dispatch — covers the branches that the collapse merges --------

    @Test
    public void transmit_nullData_takesShortApduPath() throws Exception {
        reader.setApduEncryptor(encryptor);
        reader.transmit(0x00, 0xA4, 0x04, 0x0C, null, null);

        verify(encryptor).encryptAndMac(0x00, 0xA4, 0x04, 0x0C, null, null);
    }

    @Test
    public void transmit_emptyData_takesShortApduPath() throws Exception {
        reader.setApduEncryptor(encryptor);
        byte[] empty = new byte[0];
        reader.transmit(0x00, 0xA4, 0x04, 0x0C, empty, null);

        verify(encryptor).encryptAndMac(0x00, 0xA4, 0x04, 0x0C, empty, null);
    }

    @Test
    public void transmit_dataOneByte_takesShortApduPath() throws Exception {
        reader.setApduEncryptor(encryptor);
        byte[] one = new byte[]{0x42};
        reader.transmit(0x00, 0xA4, 0x04, 0x0C, one, 0x00);

        verify(encryptor).encryptAndMac(0x00, 0xA4, 0x04, 0x0C, one, 0x00);
    }

    @Test
    public void transmit_dataUnder256_takesShortApduPath() throws Exception {
        reader.setApduEncryptor(encryptor);
        byte[] mid = new byte[200];
        reader.transmit(0x00, 0xA4, 0x04, 0x0C, mid, 0x00);

        // One single call — no chaining for < 256 byte payloads.
        verify(encryptor, times(1))
                .encryptAndMac(anyInt(), anyInt(), anyInt(), anyInt(), any(), any());
        verify(encryptor).encryptAndMac(0x00, 0xA4, 0x04, 0x0C, mid, 0x00);
    }

    @Test
    public void transmit_data256Bytes_triggersChunking() throws Exception {
        reader.setApduEncryptor(encryptor);
        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i & 0xFF);
        }
        reader.transmit(0x00, 0xA4, 0x04, 0x0C, data, 0x00);

        // 256-byte boundary: one chained chunk (255B with CLA=0x10) + final chunk (1B).
        verify(encryptor, times(2))
                .encryptAndMac(anyInt(), anyInt(), anyInt(), anyInt(), any(), any());
        // First call: chained CLA=0x10
        verify(encryptor).encryptAndMac(eq(0x10), eq(0xA4), eq(0x04), eq(0x0C),
                any(), eq(0x00));
        // Last call: original CLA=0x00 with the remaining 1 byte
        verify(encryptor).encryptAndMac(eq(0x00), eq(0xA4), eq(0x04), eq(0x0C),
                any(), eq(0x00));
    }
}
