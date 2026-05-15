package ee.ria.DigiDoc.idcard;

import static ee.ria.DigiDoc.idcard.ApduReplayReader.bytes;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.err6B00;
import static ee.ria.DigiDoc.idcard.ApduReplayReader.ok;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

/**
 * Byte-for-byte replay of a real EE IDEMIA (ESTEID2018) test card's
 * PACE session. EE's PACE uses secp256r1 (paramId 0x0C),
 * unlike LV which uses brainpoolP256r1 (paramId 0x0D); the same
 * {@link IdemiaWithPace#tunnel} code path must work on both curves.
 *
 * <p>EE-vs-LV differences:
 * <ul>
 *   <li>secp256r1 generator point vs brainpoolP256r1 — different curve
 *       params and ECDH math; same protocol shape.</li>
 *   <li>MSE SET AT data trailer {@code 84 01 0C} (vs LV's {@code 84 01 0D}).</li>
 *   <li>EE EF.CardAccess has a single SEQUENCE in the SET (LV has two),
 *       trailing 0x00 padding to file boundary, EOF at offset 0x40
 *       (vs LV's 0x44).</li>
 * </ul>
 *
 * <p>Fixture is a test card; the personal data ("JÕERG, JAAK-KRISTJAN,
 * 38001085718") is synthetic.
 */
public final class EstoniaIdemiaPaceReplayTest {

    static final String CAN = "571500";

    @Test
    public void tunnel_replaysCapturedEePaceSession_chipMacVerifies() throws Exception {
        ReplayFixture.ee().tunnel().assertAllConsumed();
    }

    /**
     * EE PACE handshake transcript. Shared with the higher-level EE replay
     * tests (cert read, sign flows, PIN retry) — same chip session, same
     * ephemerals, so all post-PACE tests can prepend this.
     */
    static void loadPaceTranscript(ApduReplayReader r) {
        // SELECT MAIN AID (Idemia EE/LV eID application — same AID as LV)
        r.expect("00a4040c10a000000077010800070000fe00000100", ok());
        // SELECT EF.CardAccess (file id 0x011C)
        r.expect("00a4020c02011c", ok());
        // READ BINARY EF.CardAccess — single SET { SEQUENCE { OID, INT v=2,
        // INT paramId=0x0C } } (22 bytes TLV) followed by 0x00 padding to
        // the file boundary at 0x40. Total 64 data bytes — readBinaryFile
        // advances stream.size() to 0x40 and asks for more, getting 6B00.
        r.expect("00b0000000",
                bytes("31143012060a04007f0007020204020402010202010c000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));
        // READ BINARY past EOF at offset 0x40 — terminates the read loop.
        r.expect("00b0004000", err6B00());
        // MSE SET AT — PACE protocol OID + paramId 0x0C (secp256r1)
        r.expect("0022c1a412800a04007f0007020204020483010284010c00", ok());
        // GA Get Nonce
        r.expect("10860000027c0000",
                bytes("7c228020a26790e6bffa45ff87adc82a9d35b0dc956ae305c8029a0732cb8ff90c351e15"));
        // GA Map Nonce — host ephemeral_1 public point on secp256r1
        r.expect("10860000457c438141041178c1d67d5ea6b2f174b31ce94f7600c7d7a3d713024f6f189bd5988faab6f60a4af769083238b1d685bf034f4d72aba9374fe89c95dcabda18a1df0f5f6d8800",
                bytes("7c438241042b681df3a932deb0921aa33ab25965fa602e41ad4be20c0284df5b012b90d9cc6d40db0dcd866fa12cc8264ee859981573e9db640eb57a5101fa1c63474f7fed"));
        // GA Key Agreement — host ephemeral_2 public point on secp256r1
        r.expect("10860000457c43834104f65454fb78db86c3b6a4d34a444226f3c1888a2cf656326b2debfdc52151392006078516c384b327fe9e90d313e3efb1f6d7f89e896c7d6d10833370fd66fb1400",
                bytes("7c4384410412154e1ae4865b8e6162df9771235d16fc39af471f9c0a166a22535ef5272810f779c3d906f965e37a2d8fdef1fae1ad4b1fc30fcbe023ecbfef8f638a219214"));
        // GA Mutual Auth — chip MAC 7f42fe3f19e2e6ef
        r.expect("008600000c7c0a8508f6522ff6e83a02c600",
                bytes("7c0a86087f42fe3f19e2e6ef"));
    }

    /**
     * EE session #1 host ephemeral private-key bytes (secp256r1, 32 bytes
     * each). Used with {@link EphemeralKeyStub} by the cert-read and
     * auth-sign replay tests so they chain off this PACE transcript.
     */
    static byte[][] capturedSession1HostPrivateBytes() {
        return new byte[][]{
                Hex.decode("dbc4f477eb1dc90734459e686a0f2b8d99f3e9ffaad8d9ce883c254570ccc407"),
                Hex.decode("6e4c5a8364475940472bbd109fdbd72f0684f63ca7093d489a49ceeef895fac9"),
        };
    }
}
