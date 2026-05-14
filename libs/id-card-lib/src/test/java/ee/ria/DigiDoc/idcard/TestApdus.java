package ee.ria.DigiDoc.idcard;

/**
 * Shared APDU header bytes and captured hash/signature constants used
 * by replay tests. Hex strings are formatted as full reconstructed
 * C-APDUs (CLA INS P1 P2 Lc Data) so test fixtures can drop them
 * straight into {@link ApduReplayReader#expect}.
 */
final class TestApdus {

    private TestApdus() {}

    /**
     * SELECT IDEMIA Oberthur AID — 13-byte applet ID prefixed by the
     * usual {@code 00 A4 04 0C 0D} SELECT-MF-with-Le=0 header.
     * Selected by {@code Idemia.authenticate} / {@code Idemia.decrypt}
     * before VERIFY PIN1. Same for LV and EE.
     */
    static final String SEL_OBERTHUR_AID =
            "00a4040c0de828bd080ff2504f5420415750";

    /**
     * SELECT IDEMIA QSCD AID — 16-byte ASCII "QSCD Application" prefixed
     * by {@code 00 A4 04 0C 10}. Selected by
     * {@code Idemia.calculateSignature} before VERIFY PIN2.
     */
    static final String SEL_QSCD_AID =
            "00a4040c1051534344204170706c69636174696f6e";

    /**
     * 48-byte SHA-384 hash that the demo NFC app hard-codes as its test
     * input — appears in every captured auth/sign session across LV, EE
     * and Thales card families.
     */
    static final String CAPTURED_AUTH_HASH =
            "592339cef290ada30eac71d4cbb0420b479c1a0936fe0225a0fbc6dc2e51ff58"
                    + "cfd5add29772de4368eb1b024f1cbc8a";

    /**
     * Synthetic 48-byte hash used for the document-sign replay tests.
     * The real chip input isn't visible in the SM-encrypted log, but
     * the chip's response IS captured — so we pass this stub through
     * the lib and assert on the (mocked) return value.
     */
    static final String SIGN_INPUT_HASH_48 =
            "1111111111111111222222222222222233333333333333334444444444444444"
                    + "5555555555555555aaaaaaaaaaaaaaaa";
}
