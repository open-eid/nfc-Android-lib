package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;

/**
 * Golden-vector tests for the IDEMIA secure-messaging / PACE helpers that
 * are pure functions or work off only fields without reaching the
 * card. The whole point of these tests is to be brittle in the right way:
 * any refactor that moves these helpers (e.g. into a shared
 * AbstractPaceWithSecureMessaging) MUST produce byte-identical output for
 * the same inputs, otherwise the wire-level conversation with the chip
 * changes and PACE breaks.
 *
 * <p>The helpers under test are intentionally {@code private} in production
 * code — we reach them via reflection here rather than relaxing the
 * library's public API surface for the sake of tests. A future refactor
 * that renames or moves a method will fail this test loudly via
 * {@link NoSuchMethodException}, which is the intent.
 */
public final class SecureMessagingHelpersTest {

    private IdemiaWithPace token;

    @BeforeEach
    public void setUp() {
        // Helpers tested here do not touch the reader.
        token = new IdemiaWithPace(mock(NfcSmartCardReader.class));
    }

    // -------- incrementSSC --------

    @Test
    public void incrementSSC_zeroCounter_lastByteBecomesOne() {
        byte[] ssc = new byte[16];
        invokeStaticVoid("incrementSSC", new Class<?>[] {byte[].class}, ssc);
        byte[] expected = new byte[16];
        expected[15] = 1;
        assertThat(ssc).isEqualTo(expected);
    }

    @Test
    public void incrementSSC_lastByte0xFF_carriesIntoSecondToLast() {
        byte[] ssc = new byte[16];
        ssc[15] = (byte) 0xFF;
        invokeStaticVoid("incrementSSC", new Class<?>[] {byte[].class}, ssc);
        byte[] expected = new byte[16];
        expected[14] = 0x01;
        expected[15] = 0x00;
        assertThat(ssc).isEqualTo(expected);
    }

    @Test
    public void incrementSSC_allOnes_rollsOverToAllZeros() {
        byte[] ssc = new byte[16];
        for (int i = 0; i < 16; i++) {
            ssc[i] = (byte) 0xFF;
        }
        invokeStaticVoid("incrementSSC", new Class<?>[] {byte[].class}, ssc);
        assertThat(ssc).isEqualTo(new byte[16]);
    }

    // -------- pad / unpad (ISO/IEC 7816-4) --------

    @Test
    public void pad_empty_appendsFullPaddingBlock() {
        byte[] padded = (byte[]) invoke("pad", new Class<?>[] {byte[].class, int.class}, new byte[0], 16);
        byte[] expected = new byte[16];
        expected[0] = (byte) 0x80;
        assertThat(padded).isEqualTo(expected);
    }

    @Test
    public void pad_oneByte_padsTo16() {
        byte[] padded = (byte[]) invoke("pad", new Class<?>[] {byte[].class, int.class}, new byte[] {0x01}, 16);
        byte[] expected = new byte[16];
        expected[0] = 0x01;
        expected[1] = (byte) 0x80;
        assertThat(padded).isEqualTo(expected);
    }

    @Test
    public void pad_blockAligned_appendsFullExtraBlock() {
        // ISO 7816-4 always appends padding, even on aligned input.
        byte[] data = new byte[16];
        byte[] padded = (byte[]) invoke("pad", new Class<?>[] {byte[].class, int.class}, data, 16);
        assertThat(padded).hasLength(32);
        assertThat(padded[16]).isEqualTo((byte) 0x80);
    }

    @Test
    public void unpad_isInverseOfPad_acrossLengths() {
        for (int len : new int[] {0, 1, 15, 16, 17, 31, 32, 33}) {
            byte[] data = new byte[len];
            for (int i = 0; i < len; i++) {
                data[i] = (byte) (i + 0x40);
            }
            byte[] padded = (byte[]) invoke("pad", new Class<?>[] {byte[].class, int.class}, data, 16);
            byte[] roundTripped = (byte[]) invoke("unpad", new Class<?>[] {byte[].class}, padded);
            assertThat(roundTripped).isEqualTo(data);
        }
    }

    // -------- createKey (SHA-256 key derivation) --------

    @Test
    public void createKey_isSha256OfBasisAndTrailingPadding() throws Exception {
        // createKey(basis, lastByte) = SHA-256(basis || 00 00 00 lastByte).
        byte[] basis = "123456".getBytes("UTF-8");
        byte[] key = (byte[]) invoke("createKey",
                new Class<?>[] {byte[].class, byte.class}, basis, (byte) 0x03);

        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        md.update(basis);
        md.update(new byte[] {0x00, 0x00, 0x00, 0x03});
        byte[] expected = md.digest();

        assertThat(key).isEqualTo(expected);
        assertThat(key).hasLength(32);
    }

    @Test
    public void createKey_paddingByteAffectsOutput() {
        // Different last-byte → different key. Sanity: createKey is used to
        // derive distinct ENC / MAC / nonce-decrypt keys from the same CAN.
        byte[] basis = "654321".getBytes();
        byte[] enc = (byte[]) invoke("createKey",
                new Class<?>[] {byte[].class, byte.class}, basis, (byte) 0x01);
        byte[] mac = (byte[]) invoke("createKey",
                new Class<?>[] {byte[].class, byte.class}, basis, (byte) 0x02);
        byte[] nonce = (byte[]) invoke("createKey",
                new Class<?>[] {byte[].class, byte.class}, basis, (byte) 0x03);

        assertThat(enc).isNotEqualTo(mac);
        assertThat(mac).isNotEqualTo(nonce);
        assertThat(enc).isNotEqualTo(nonce);
    }

    // -------- getMAC (AES-CMAC, truncated to 8 bytes) --------

    @Test
    public void getMAC_matchesRfc4493_emptyMessage() {
        // RFC 4493 §4 test vector 1, K = 2b7e1516 28aed2a6 abf71588 09cf4f3c.
        // Full AES-CMAC = bb1d6929 e9593728 7fa37d12 9b756746.
        // getMAC returns the first 8 bytes.
        byte[] key = Hex.decode("2b7e151628aed2a6abf7158809cf4f3c");
        byte[] mac = (byte[]) invoke("getMAC",
                new Class<?>[] {byte[].class, byte[].class}, new byte[0], key);
        assertThat(mac).isEqualTo(Hex.decode("bb1d6929e9593728"));
    }

    @Test
    public void getMAC_matchesRfc4493_16ByteMessage() {
        // RFC 4493 §4 test vector 2.
        byte[] key = Hex.decode("2b7e151628aed2a6abf7158809cf4f3c");
        byte[] data = Hex.decode("6bc1bee22e409f96e93d7e117393172a");
        byte[] mac = (byte[]) invoke("getMAC",
                new Class<?>[] {byte[].class, byte[].class}, data, key);
        // Full = 070a16b46b4d4144f79bdd9dd04a287c. First 8 = 070a16b46b4d4144.
        assertThat(mac).isEqualTo(Hex.decode("070a16b46b4d4144"));
    }

    // -------- decryptNonce (round-trip via createKey-derived key) --------

    @Test
    public void decryptNonce_roundTripsThroughAesCbcZeroIv() throws Exception {
        byte[] can = "123456".getBytes("UTF-8");
        byte[] key = (byte[]) invoke("createKey",
                new Class<?>[] {byte[].class, byte.class}, can, (byte) 0x03);
        byte[] plaintextNonce = Hex.decode(
            "00112233445566778899aabbccddeeff" +
            "ffeeddccbbaa99887766554433221100"); // 32 bytes, two AES blocks

        javax.crypto.Cipher enc = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
        enc.init(javax.crypto.Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(key, "AES"),
                new javax.crypto.spec.IvParameterSpec(new byte[16]));
        byte[] encryptedNonce = enc.doFinal(plaintextNonce);

        byte[] recovered = (byte[]) invoke("decryptNonce",
                new Class<?>[] {byte[].class, byte[].class}, encryptedNonce, can);
        assertThat(recovered).isEqualTo(plaintextNonce);
    }

    // -------- getDo97 (BER-TLV encoding of expected response length) --------

    @Test
    public void getDo97_null_returnsEmpty() {
        assertThat((byte[]) invoke("getDo97", new Class<?>[] {Integer.class}, (Object) null))
                .isEqualTo(new byte[0]);
    }

    @Test
    public void getDo97_value_returnsTlvWithSingleLengthByte() {
        // 0x97 = DO97 tag, 0x01 = length, then the value byte.
        assertThat((byte[]) invoke("getDo97", new Class<?>[] {Integer.class}, Integer.valueOf(0x00)))
                .isEqualTo(new byte[] {(byte) 0x97, 0x01, 0x00});
        assertThat((byte[]) invoke("getDo97", new Class<?>[] {Integer.class}, Integer.valueOf(0xFF)))
                .isEqualTo(new byte[] {(byte) 0x97, 0x01, (byte) 0xFF});
        // ISO 7816 short-form Le=256 encodes as 0x00 byte; getDo97 takes the
        // low byte, matching that convention.
        assertThat((byte[]) invoke("getDo97", new Class<?>[] {Integer.class}, Integer.valueOf(0x100)))
                .isEqualTo(new byte[] {(byte) 0x97, 0x01, 0x00});
    }

    // -------- getDo8587 short-circuit on empty data --------

    @Test
    public void getDo8587_nullOrEmptyData_returnsEmpty() throws Exception {
        // The non-empty path requires keyEnc to be populated (post-PACE);
        // covered indirectly by PaceFlowTest. Here we lock in the
        // short-circuit so callers can pass null data without triggering
        // encryption setup.
        assertThat((byte[]) invoke("getDo8587",
                new Class<?>[] {int.class, byte[].class}, 0x00, (Object) null))
                .isEqualTo(new byte[0]);
        assertThat((byte[]) invoke("getDo8587",
                new Class<?>[] {int.class, byte[].class}, 0x00, new byte[0]))
                .isEqualTo(new byte[0]);
    }

    // -------- generateRandomPrivateKey (bounds) --------

    @Test
    public void generateRandomPrivateKey_isInRangeForSecp256r1() {
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256r1");
        for (int i = 0; i < 32; i++) {
            BigInteger d = invokeGenerateRandomPrivateKey(spec);
            assertThat(d.compareTo(BigInteger.ONE)).isAtLeast(0);
            assertThat(d.compareTo(spec.getN())).isLessThan(0);
        }
    }

    @Test
    public void generateRandomPrivateKey_isInRangeForBrainpoolP256r1() {
        // Critical for LV cards.
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("brainpoolP256r1");
        for (int i = 0; i < 32; i++) {
            BigInteger d = invokeGenerateRandomPrivateKey(spec);
            assertThat(d.compareTo(BigInteger.ONE)).isAtLeast(0);
            assertThat(d.compareTo(spec.getN())).isLessThan(0);
        }
    }

    // -------- reflection plumbing --------

    private Object invoke(String name, Class<?>[] paramTypes, Object... args) {
        try {
            Method m = IdemiaWithPace.class.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m.invoke(token, args);
        } catch (InvocationTargetException e) {
            // Surface the *real* exception, not the reflection wrapper.
            throw new AssertionError("Helper " + name + " threw", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to reach helper " + name
                    + " — was it renamed/moved by a refactor?", e);
        }
    }

    private void invokeStaticVoid(String name, Class<?>[] paramTypes, Object... args) {
        try {
            Method m = IdemiaWithPace.class.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            m.invoke(null, args);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Helper " + name + " threw", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to reach helper " + name
                    + " — was it renamed/moved by a refactor?", e);
        }
    }

    private static BigInteger invokeGenerateRandomPrivateKey(ECNamedCurveParameterSpec spec) {
        try {
            Method m = IdemiaWithPace.class.getDeclaredMethod(
                    "generateRandomPrivateKey", ECNamedCurveParameterSpec.class);
            m.setAccessible(true);
            return (BigInteger) m.invoke(null, spec);
        } catch (InvocationTargetException e) {
            throw new AssertionError("generateRandomPrivateKey threw", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to reach generateRandomPrivateKey "
                    + "— was it renamed/moved by a refactor?", e);
        }
    }
}
