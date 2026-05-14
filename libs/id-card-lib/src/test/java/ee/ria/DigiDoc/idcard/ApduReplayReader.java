package ee.ria.DigiDoc.idcard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import ee.ria.DigiDoc.smartcardreader.ApduResponseException;
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;

import org.bouncycastle.util.encoders.Hex;
import org.mockito.MockMakers;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test utility: builds a Mockito subclass mock of {@code NfcSmartCardReader}
 * that replays a captured APDU transcript. Stubs the public
 * {@code transmit(int,int,int,int,byte[],Integer)} form because the
 * protected {@code transmit(byte[])} chokepoint isn't reachable across
 * package boundaries.
 *
 * <p>Each captured C-APDU is keyed by its hex bytes (header + Lc + data
 * + optional Le, matching {@code SmartCardReader.transmit}'s wire format),
 * and carries an ordered queue of outcomes — so repeated calls to the
 * same APDU (e.g., SELECT MAIN AID at PACE and again post-PACE) can
 * return different things. Use {@link #expect} to push outcomes in the
 * order they should be consumed.
 *
 * <p>For post-PACE responses captured from logcat "Decrypted data:" lines,
 * include the ISO 7816-4 padding (0x80 + 0x00*n); {@link #okPadded} will
 * strip it before returning. The lib's real {@code decryptAndVerify}
 * strips padding in production, so this faithfully matches what callers
 * actually see.
 */
final class ApduReplayReader {

    private final Map<String, Deque<Outcome>> expectations = new LinkedHashMap<>();

    /** Push an outcome onto the queue for the given hex C-APDU. */
    void expect(String capduHex, Outcome outcome) {
        expectations.computeIfAbsent(capduHex, k -> new ArrayDeque<>()).add(outcome);
    }

    /** Returns C-APDUs that were in the fixture but never sent. */
    Map<String, Integer> unconsumed() {
        HashMap<String, Integer> leftover = new HashMap<>();
        for (Map.Entry<String, Deque<Outcome>> e : expectations.entrySet()) {
            if (!e.getValue().isEmpty()) {
                leftover.put(e.getKey(), e.getValue().size());
            }
        }
        return leftover;
    }

    NfcSmartCardReader build() throws Exception {
        NfcSmartCardReader reader = mock(NfcSmartCardReader.class,
                withSettings().mockMaker(MockMakers.SUBCLASS));

        when(reader.transmit(anyInt(), anyInt(), anyInt(), anyInt(), any(), any()))
                .thenAnswer(inv -> {
                    int cla = ((int) inv.getArgument(0)) & 0xFF;
                    int ins = ((int) inv.getArgument(1)) & 0xFF;
                    int p1 = ((int) inv.getArgument(2)) & 0xFF;
                    int p2 = ((int) inv.getArgument(3)) & 0xFF;
                    byte[] data = inv.getArgument(4);
                    Integer le = inv.getArgument(5);
                    String hex = Hex.toHexString(buildApdu(cla, ins, p1, p2, data, le));

                    Deque<Outcome> queue = expectations.get(hex);
                    if (queue == null || queue.isEmpty()) {
                        throw new AssertionError("Unexpected APDU: " + hex
                                + "\nFixture keys: " + expectations.keySet());
                    }
                    Outcome next = queue.removeFirst();
                    if (next.error != null) {
                        throw next.error;
                    }
                    return next.payload;
                });

        return reader;
    }

    // ---- outcome factories ----

    /** Empty 9000-equivalent payload (SW stripped by SmartCardReader.transmit on success). */
    static Outcome ok() {
        return new Outcome(new byte[0], null);
    }

    /** Plain bytes from a hex string (already post-SW-strip, no padding). */
    static Outcome bytes(String hex) {
        return new Outcome(Hex.decode(hex), null);
    }

    /**
     * Plain bytes after ISO 7816-4 padding strip. Use when copying hex
     * straight from a logcat "Decrypted data:" line; the trailing
     * 0x80 + 0x00*n is removed before the bytes hand off to the lib.
     */
    static Outcome okPadded(String paddedHex) {
        return new Outcome(stripIso7816Padding(Hex.decode(paddedHex)), null);
    }

    /** READ-past-EOF / cert-end terminator — the lib catches this and loops out. */
    static Outcome err6B00() {
        return new Outcome(null, new ApduResponseException((byte) 0x6B, (byte) 0x00));
    }

    /** Arbitrary error status word (for negative-path tests). */
    static Outcome err(int sw1, int sw2) {
        return new Outcome(null, new ApduResponseException((byte) sw1, (byte) sw2));
    }

    /** Outcome of a single APDU call — either bytes or an exception, never both. */
    static final class Outcome {
        final byte[] payload;
        final ApduResponseException error;
        Outcome(byte[] payload, ApduResponseException error) {
            this.payload = payload;
            this.error = error;
        }
    }

    // ---- helpers ----

    /**
     * Mirrors {@code SmartCardReader.transmit}'s wire-byte assembly:
     * header (CLA INS P1 P2), then Lc + data when data is non-empty,
     * then Le when non-null. All captured LV APDUs fit under 256 bytes.
     */
    private static byte[] buildApdu(int cla, int ins, int p1, int p2,
                                    byte[] data, Integer le) {
        int len = 4 + (data == null || data.length == 0 ? 0 : 1 + data.length)
                + (le == null ? 0 : 1);
        byte[] out = new byte[len];
        out[0] = (byte) cla;
        out[1] = (byte) ins;
        out[2] = (byte) p1;
        out[3] = (byte) p2;
        int pos = 4;
        if (data != null && data.length > 0) {
            out[pos++] = (byte) data.length;
            System.arraycopy(data, 0, out, pos, data.length);
            pos += data.length;
        }
        if (le != null) {
            out[pos] = le.byteValue();
        }
        return out;
    }

    /**
     * Find the last 0x80 byte (assuming all bytes after are 0x00) and
     * truncate there. Returns the array unchanged when no marker is found.
     */
    private static byte[] stripIso7816Padding(byte[] padded) {
        int i = padded.length - 1;
        while (i >= 0 && padded[i] == 0x00) {
            i--;
        }
        if (i >= 0 && padded[i] == (byte) 0x80) {
            return Arrays.copyOf(padded, i);
        }
        return padded;
    }
}
