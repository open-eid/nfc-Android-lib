package ee.ria.DigiDoc.idcard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Test utility for byte-exact PACE replay: intercepts {@code new
 * SecureRandom()} in the lib under test and feeds it pre-recorded host
 * ephemeral private-key bytes.
 *
 * <p>{@code IdemiaWithPace#generateRandomPrivateKey} and
 * {@code ThalesWithPace#generateRandomPrivateKey} are private statics
 * that allocate a fresh {@code SecureRandom} each call, then pass it to
 * {@code BigIntegers.createRandomBigInteger(nBitLength, random)} —
 * which in turn calls {@code nextBytes(byte[])} once per rejection
 * sample. By Mockito-intercepting the constructor and stubbing
 * {@code nextBytes}, we feed in the exact 32- or 48-byte private scalar
 * captured from the chip session. Because those scalars came from a
 * real session, they always pass the in-range / NAF-weight checks on
 * the first iteration of the rejection loop, so the deterministic
 * ephemeral propagates cleanly through the PACE math.
 *
 * <p>Usage: wrap the lib call in try-with-resources.
 *
 * <pre>{@code
 *   try (var stub = EphemeralKeyStub.of(priv1Bytes, priv2Bytes)) {
 *       token.tunnel(CAN);
 *   }
 * }</pre>
 *
 * <p>This avoids opening a test seam in the production class for the
 * replay tests' benefit.
 */
final class EphemeralKeyStub {

    private EphemeralKeyStub() {}

    /**
     * Returns a {@link MockedConstruction} that intercepts every
     * {@code new SecureRandom()} call from the calling thread and yields
     * the supplied private-key bytes in order — first call gets
     * {@code keys[0]}, second gets {@code keys[1]}, etc.
     *
     * <p>Each intercepted instance returns the same bytes on every
     * {@code nextBytes(byte[])} call (the rejection loop accepts on the
     * first iteration with a real captured value).
     *
     * @param keys ephemeral private-key bytes in send order. For
     *             secp256r1 / brainpoolP256r1 these are 32 bytes; for
     *             brainpoolP384r1 (Thales) they are 48 bytes.
     */
    static MockedConstruction<SecureRandom> of(byte[]... keys) {
        Deque<byte[]> queue = new ArrayDeque<>(Arrays.asList(keys));
        // Real SecureRandom for unrelated construction sites (BouncyCastle's
        // ECPoint.normalize uses internal blinding that creates its own
        // SecureRandom and pulls random bytes — we shouldn't drain the
        // queue for those).
        SecureRandom fallback = new SecureRandom();
        return Mockito.mockConstruction(SecureRandom.class, (mock, context) -> {
            boolean fromPaceEphemeralGen = false;
            for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                if ("generateRandomPrivateKey".equals(e.getMethodName())) {
                    fromPaceEphemeralGen = true;
                    break;
                }
            }
            if (fromPaceEphemeralGen && !queue.isEmpty()) {
                byte[] priv = queue.removeFirst();
                doAnswer(inv -> {
                    byte[] buf = inv.getArgument(0);
                    if (buf.length != priv.length) {
                        throw new AssertionError("SecureRandom.nextBytes asked for "
                                + buf.length + " bytes; stub has " + priv.length
                                + " (curve-size mismatch?)");
                    }
                    System.arraycopy(priv, 0, buf, 0, buf.length);
                    return null;
                }).when(mock).nextBytes(any(byte[].class));
            } else {
                // Pass-through to a real RNG for everything else.
                doAnswer(inv -> {
                    fallback.nextBytes(inv.getArgument(0));
                    return null;
                }).when(mock).nextBytes(any(byte[].class));
            }
        });
    }
}
