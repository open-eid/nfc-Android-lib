package ee.ria.DigiDoc.smartcardreader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Test fake for {@link SmartCardReader} — records raw APDUs sent to
 * {@code transmit(byte[])} and replies from a queue of canned responses
 * (defaulting to {@code 90 00} when empty).
 */
public final class FakeReader extends SmartCardReader {

    public final List<byte[]> requests = new ArrayList<>();
    private final Deque<byte[]> responses = new ArrayDeque<>();

    public FakeReader respondWith(byte[]... canned) {
        for (byte[] r : canned) {
            responses.add(r);
        }
        return this;
    }

    @Override
    public boolean connected() {
        return true;
    }

    @Override
    public byte[] atr() {
        return new byte[0];
    }

    @Override
    public void close() {
    }

    @Override
    protected byte[] transmit(byte[] apdu) {
        requests.add(apdu);
        if (responses.isEmpty()) {
            return new byte[]{(byte) 0x90, 0x00};
        }
        return responses.removeFirst();
    }
}
