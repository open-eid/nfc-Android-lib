package ee.ria.DigiDoc.idcard;

import static ee.ria.DigiDoc.idcard.ApduReplayReader.bytes;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.err6B00;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.ok;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

/**
 * Byte-for-byte replay of a real LV IDEMIA test card's PACE session.
 *
 * <p>Feeds {@link IdemiaWithPace#tunnel(String)} the exact C-APDU /
 * R-APDU bytes captured from an LV IDEMIA test card and injects
 * (via Mockito's {@code mockConstruction} hook on
 * {@link SecureRandom}) the same two ephemeral host private keys the
 * device used during that session. Because every byte matches the real
 * chip, the computed session keys reproduce the captured Mutual Auth
 * MAC {@code ad787a62b8a7037e} and tunnel() returns cleanly.
 *
 * <p>Fixture is a test card; "PARAUDZIŅA MĀRA" is a synthetic test
 * identity, no real personal data.
 */
public final class LatviaIdemiaPaceReplayTest {

    static final String CAN = "011207";

    @Test
    public void tunnel_replaysCapturedLvPaceSession_chipMacVerifies() throws Exception {
        ReplayFixture.lv().tunnel().assertAllConsumed();
    }

    /**
     * LV PACE handshake transcript. Loads in send-order so unconsumed() drops
     * in order; the replay reader looks up by C-APDU bytes regardless.
     */
    static void loadPaceTranscript(ApduReplayReader r) {
        // SELECT MAIN AID (Idemia LV/EE eID application)
        r.expect("00a4040c10a000000077010800070000fe00000100", ok());
        // SELECT EF.CardAccess (file id 0x011C)
        r.expect("00a4020c02011c", ok());
        // READ BINARY EF.CardAccess at offset 0x0000
        r.expect("00b0000000", bytes("31423012060a04007f0007020204020402010202010d"
                + "3015060904007f000702020c01300302010130030301" + "00"
                + "3015060904007f000702020c02300302010230030301" + "00"));
        // READ BINARY past EOF — terminates the read loop with 6B00
        r.expect("00b0004400", err6B00());
        // MSE SET AT — PACE protocol OID + paramId 0x0D (brainpoolP256r1)
        r.expect("0022c1a412800a04007f0007020204020483010284010d00", ok());
        // GA Get Nonce (CLA=10 chained, INS=86)
        r.expect("10860000027c0000",
                bytes("7c2280202b275a1baddbb7bcb902159eda3fc56f655b936f7bf8269ba0702654438133d4"));
        // GA Map Nonce — host ephemeral_1 public point
        r.expect("10860000457c438141046fe78d8e294d4f16d2a9c57dde351df16d62831dcd94d322a3d57601df89618634fa335cc96a12497f5a3998fd12814c8d52e19532ff4b115ffe7156dd8f462100",
                bytes("7c4382410429d2f4cf348654fc86e245232bc625971f1ec0e10c8171976cb795129a39c509512726f66cc2aa1ec9dbb7660cf6d53fd3880d1e8056c11f6c4592b7bd6e0c2d"));
        // GA Key Agreement — host ephemeral_2 public point
        r.expect("10860000457c438341049e747436ac4d330201e7467ebc39d88858e49b57022d587e82fa66dd4564752232320bf5be17d53d081490caf7c2b7ad678630d65de32cc9bb4e9fcbd0f596e300",
                bytes("7c43844104973474bd0c51b91bf6cacacd5ff1a741044ae1b4fa28eb2afae9d849ecd42ffd63e8a00c7bc808b17b357e954571cc9f003b6fc05f665a24b217f105aaf95cce"));
        // GA Mutual Auth (CLA=00 unchained) — chip MAC ad787a62b8a7037e
        r.expect("008600000c7c0a850867548c9b0248591e00",
                bytes("7c0a8608ad787a62b8a7037e"));
    }

    /**
     * Host ephemeral private keys captured during the LV session #1 PACE
     * handshake — both 32 bytes (brainpoolP256r1). Used with
     * {@link EphemeralKeyStub} to make the chip's Mutual Auth MAC verify.
     */
    static byte[][] capturedSession1HostPrivateBytes() {
        return new byte[][]{
                Hex.decode("51bee8f33c44d5bd2546aee04daf37ab766b8dddfb38dddbda929330d87f4ec9"),
                Hex.decode("4adc3b146422756fe2ed694495a35edde0f2fbdb0db594996ea61c94afbe1178"),
        };
    }

}
