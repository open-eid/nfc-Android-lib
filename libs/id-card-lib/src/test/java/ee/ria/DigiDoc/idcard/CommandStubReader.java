package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import ee.ria.DigiDoc.smartcardreader.ApduResponseException;
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;

import org.mockito.MockMakers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scriptable {@link NfcSmartCardReader} mock for the non-replay code-
 * management tests. Each test queues responses (or thrown exceptions)
 * keyed by {@code (CLA, INS)} and afterwards inspects {@link #captured}
 * to assert the lib emitted the expected APDU sequence.
 *
 * <p>Differs from {@link ApduReplayReader}: this matches by INS, not by
 * full reconstructed bytes — appropriate when the test cares about
 * "did the lib SELECT MAIN AID then GET DATA" not "exactly what payload
 * was on the wire".
 */
final class CommandStubReader {

    /** Captured APDU header + data; Le/response handled outside. */
    static final class Apdu {
        final int cla, ins, p1, p2;
        final byte[] data;
        Apdu(int cla, int ins, int p1, int p2, byte[] data) {
            this.cla = cla;
            this.ins = ins;
            this.p1 = p1;
            this.p2 = p2;
            this.data = data;
        }
    }

    final List<Apdu> captured = new ArrayList<>();
    // Override queues keyed by (cla << 16 | ins).
    private final Map<Integer, Deque<Object>> overrides = new HashMap<>();

    CommandStubReader respondTo(int cla, int ins, byte[] response) {
        push(cla, ins, response);
        return this;
    }

    /** Convenience overload — P1/P2 ignored, matches first overrideQueue for (cla,ins). */
    CommandStubReader respondTo(int cla, int ins, int p1, int p2, byte[] response) {
        push(cla, ins, response);
        return this;
    }

    CommandStubReader throwOn(int cla, int ins, ApduResponseException ex) {
        push(cla, ins, ex);
        return this;
    }

    private void push(int cla, int ins, Object value) {
        overrides.computeIfAbsent((cla << 16) | ins, k -> new ArrayDeque<>()).add(value);
    }

    NfcSmartCardReader build() {
        NfcSmartCardReader reader = mock(NfcSmartCardReader.class,
                withSettings().mockMaker(MockMakers.SUBCLASS));
        try {
            when(reader.transmit(anyInt(), anyInt(), anyInt(), anyInt(), any(), any()))
                    .thenAnswer(inv -> {
                        int cla = ((int) inv.getArgument(0)) & 0xFF;
                        int ins = ((int) inv.getArgument(1)) & 0xFF;
                        int p1 = ((int) inv.getArgument(2)) & 0xFF;
                        int p2 = ((int) inv.getArgument(3)) & 0xFF;
                        byte[] data = inv.getArgument(4);
                        captured.add(new Apdu(cla, ins, p1, p2, data));

                        Deque<Object> q = overrides.get((cla << 16) | ins);
                        if (q != null && !q.isEmpty()) {
                            Object next = q.poll();
                            if (next instanceof ApduResponseException) {
                                throw (ApduResponseException) next;
                            }
                            return (byte[]) next;
                        }
                        return new byte[0];
                    });
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return reader;
    }

    /** Truth-style assertion on a captured APDU's header. */
    static void assertHeader(Apdu apdu, int cla, int ins, int p1, int p2) {
        assertThat(apdu.cla).isEqualTo(cla);
        assertThat(apdu.ins).isEqualTo(ins);
        assertThat(apdu.p1).isEqualTo(p1);
        assertThat(apdu.p2).isEqualTo(p2);
    }
}
