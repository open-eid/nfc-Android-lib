package ee.ria.DigiDoc.idcard;

import static ee.ria.DigiDoc.idcard.ApduReplayReader.bytes;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.ok;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

/**
 * Byte-for-byte replay of a real Thales SE ID card's PACE session.
 * Thales is the EE "ESTEID2025" card family (PKCS-15-based applet on
 * a Thales JavaCard chip); its PACE uses brainpoolP384r1 (paramId 0x02),
 * not the secp256r1 / brainpoolP256r1 that IDEMIA uses.
 *
 * <p>Thales-vs-IDEMIA PACE differences:
 * <ul>
 *   <li>No EF.CardAccess <em>read</em> — Thales hardcodes the algorithm
 *       (paramId=0x02). The lib selects EF.CardAccess by file ID but
 *       never reads it; the MSE SET AT data is built from a constant.</li>
 *   <li>No SELECT MAIN AID <em>before</em> PACE — Thales selects the
 *       PKCS-15 applet only <em>after</em> the secure tunnel is up.</li>
 *   <li>Larger curve: P-384 (96-byte private scalars, 97-byte
 *       uncompressed points), driving 101-byte GA Map / Key Agreement
 *       data and 16-byte (not 32-byte) PACE nonce.</li>
 *   <li>{@code selectMainAid()} at the end of {@link ThalesWithPace#tunnel}
 *       — PKCS-15 AID {@code A0 00 00 00 63 50 4B 43 53 2D 31 35}, not
 *       IDEMIA's master AID.</li>
 * </ul>
 *
 * <p>Fixture is a real ESTEID2025 test card; identity is a synthetic
 * test value.
 */
public final class ThalesPaceReplayTest {

    static final String CAN = "654924";

    @Test
    public void tunnel_replaysCapturedThalesPaceSession_chipMacVerifies() throws Exception {
        ReplayFixture.thales().tunnel().assertAllConsumed();
    }

    /**
     * Thales PACE handshake + post-tunnel SELECT MAIN AID. Shared with the
     * downstream Thales replay tests (cert read, sign flows, PIN retry)
     * so they all chain off the same captured chip session.
     */
    static void loadPaceTranscript(ApduReplayReader r) {
        // SELECT EF.CardAccess (file 0x011C). Thales DOES NOT subsequently
        // READ this file — the lib hardcodes paramId=0x02 for the MSE SET.
        r.expect("00a4020c02011c", ok());
        // MSE SET AT — hardcoded data ends with 83 01 02 (paramId=0x02 =
        // brainpoolP384r1); Le=0 (vs IDEMIA's no-Le).
        r.expect("0022c1a40f800a04007f0007020204020483010200", ok());
        // GA Get Nonce — response carries 16-byte encrypted nonce
        // (vs IDEMIA's 32 — Thales uses a smaller nonce on P-384).
        r.expect("10860000027c0000",
                bytes("7c128010be60c8e792c229b5a31697858a34a80a"));
        // GA Map Nonce — host_priv_1, 97-byte uncompressed P-384 point
        r.expect("10860000657c638161044e661b4d37c1048f00f82cddcb7e5fd7358cbfddcec66fe17f3b2c6be9d70c6ce2d9f4c1e1ea665fd80956562586bd564b1cf07698dc5f580d768da3ed62775035871d74d6f92c1a0296b744943f6d133205e0f0016e67f4fb05492f16bde31100",
                bytes("7c63826104633ee6131263670b99b2ef8be1ddfc0b3662b1621fb8d22cc60e00b926c2b83eced28794961d72d0988b1a2e9178899d65819bbcc81d2888d1504edc174525aef16669b616a90ada23e1902d4f52943ffafc3875ad79021456629963345d2d62"));
        // GA Key Agreement — host_priv_2, second 97-byte uncompressed point
        r.expect("10860000657c6383610425ece46703d3e3b92274fda2d04cdbded3916179b5d02f87ad1851c0d3aca42c4d312786eb44c30298235b2a37ce932f146975c21fe2ceab22ffffbe394ab3db6218b3ed95b0d861371c1ec86ad6092308c1c318ac94a8d9b967c95e8bed929700",
                bytes("7c6384610474f628c55e7c8c26f3c656a8ad5d6f45763f820758dc7f2c9683370ad9bf504ae1aec5acc9037336bd39e54627df238515c38c7784a46907085cdf62fdd82ef9387e99f9db91b1afb455b009cee423cb16cfa93d6a38312db352ae63c345e175"));
        // GA Mutual Auth — chip MAC 80242e060c55e1ce
        r.expect("008600000c7c0a8508386ebdf97488f8f100",
                bytes("7c0a860880242e060c55e1ce"));
        // Post-tunnel SELECT MAIN AID — PKCS-15 ASCII tail "PKCS-15".
        // {@link ThalesWithPace#selectMainAid} runs at the end of tunnel(),
        // not before, because the PACE handshake takes place against the
        // MF (master file) and only switches to PKCS-15 once SM is up.
        r.expect("00a4040c0ca000000063504b43532d3135", ok());
    }

    /**
     * Host ephemeral private-key bytes captured during the Thales
     * session #1 PACE handshake. 48-byte (P-384) values — used with
     * {@link EphemeralKeyStub} to make the chip's Mutual Auth MAC verify.
     */
    static byte[][] capturedSession1HostPrivateBytes() {
        return new byte[][]{
                Hex.decode("1785aaafb5f4199bd06dd0997245c503371ed6f167e95fdaa66b9c38479ee786009f0846f1fd02b09d2750dcea44465e"),
                Hex.decode("6b4dde51623a7698fd501a9b74e1267858a50d706f6ad2a6154d076d41df5f7be62dced77735103a3f5ee13039b48ca9"),
        };
    }
}
