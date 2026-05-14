package ee.ria.DigiDoc.idcard;

/**
 * Shared PIN / PUK byte values for replay tests. IDEMIA pads codes to 12
 * bytes with 0xFF; Thales pads with 0x00 — so the same logical PIN
 * yields two different hex strings on the wire. Constants here include
 * both variants where applicable.
 */
final class TestPins {

    private TestPins() {}

    // ASCII bytes the test passes to the lib (12-byte padding is applied
    // inside the lib's code() helper).
    static final byte[] PIN1 = "1234".getBytes();
    static final byte[] PIN2 = "12345".getBytes();
    static final byte[] WRONG_PIN1 = "0000".getBytes();
    static final byte[] PUK = "12345678".getBytes();
    static final byte[] NEW_PIN1 = "9999".getBytes();

    // IDEMIA wire formats — 0xFF padded to 12 bytes.
    static final String PIN1_PADDED_FF = "31323334ffffffffffffffff";
    static final String PIN2_PADDED_FF = "3132333435ffffffffffffff";
    static final String WRONG_PIN1_PADDED_FF = "30303030ffffffffffffffff";
    static final String PUK_PADDED_FF = "3132333435363738ffffffff";
    static final String NEW_PIN1_PADDED_FF = "39393939ffffffffffffffff";

    // Thales wire formats — 0x00 padded to 12 bytes.
    static final String PIN1_PADDED_00 = "313233340000000000000000";
    static final String PIN2_PADDED_00 = "313233343500000000000000";
    static final String WRONG_PIN1_PADDED_00 = "303030300000000000000000";
    static final String PUK_PADDED_00 = "313233343536373800000000";
    static final String NEW_PIN1_PADDED_00 = "393939390000000000000000";
}
