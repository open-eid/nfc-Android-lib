package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * TLV is the parser feeding both EF.CardAccess (PACE parameter ID) and
 * PrKDF (key-reference discovery) on PACE-tunnelled card reads.
 */
public final class TLVTest {

    @Test
    public void parseAll_shortFormLength_singleEntry() {
        byte[] data = Hex.decode("020103"); // INTEGER 0x03
        List<TLV> tlvs = TLV.parseAll(data);

        assertThat(tlvs).hasSize(1);
        assertThat(tlvs.get(0).getTag()).isEqualTo(0x02);
        assertThat(tlvs.get(0).getValue()).isEqualTo(new byte[] {0x03});
    }

    @Test
    public void parseAll_longFormLength81_overSevenBitsLength() {
        // Tag 0x04 (OCTET STRING), length 0x81 0x80 (= 128), 128 bytes of 0xAA.
        // 0x81 is the BER long-form indicator for "next 1 byte is the length".
        StringBuilder hex = new StringBuilder("048180");
        for (int i = 0; i < 128; i++) {
            hex.append("AA");
        }
        byte[] data = Hex.decode(hex.toString());

        List<TLV> tlvs = TLV.parseAll(data);

        assertThat(tlvs).hasSize(1);
        assertThat(tlvs.get(0).getTag()).isEqualTo(0x04);
        assertThat(tlvs.get(0).getValue()).hasLength(128);
    }

    @Test
    public void parseAll_longFormLength82_overEightBitsLength() {
        // Tag 0x04, length 0x82 0x01 0x00 (= 256), 256 bytes.
        StringBuilder hex = new StringBuilder("04820100");
        for (int i = 0; i < 256; i++) {
            hex.append("BB");
        }
        byte[] data = Hex.decode(hex.toString());

        List<TLV> tlvs = TLV.parseAll(data);

        assertThat(tlvs).hasSize(1);
        assertThat(tlvs.get(0).getTag()).isEqualTo(0x04);
        assertThat(tlvs.get(0).getValue()).hasLength(256);
    }

    @Test
    public void parseAll_unsupportedLengthForm_stopsLeniently() {
        // 0x83 length form (3 length bytes) is valid DER but intentionally not
        // supported by our parser — input should yield zero parsed entries, not
        // throw, so PrKDF / EF.CardAccess callers can fall through to defaults.
        byte[] data = Hex.decode("0483010000FF");

        List<TLV> tlvs = TLV.parseAll(data);

        assertThat(tlvs).isEmpty();
    }

    @Test
    public void parseAll_truncatedAfterTag_returnsEmpty() {
        byte[] data = Hex.decode("02");

        List<TLV> tlvs = TLV.parseAll(data);

        assertThat(tlvs).isEmpty();
    }

    @Test
    public void parseAll_truncated81Length_stopsBeforeReading() {
        // Tag 0x04, length-byte 0x81, but the second length byte is missing.
        byte[] data = Hex.decode("0481");

        List<TLV> tlvs = TLV.parseAll(data);

        assertThat(tlvs).isEmpty();
    }

    @Test
    public void parseAll_declaredLengthOverrunsBuffer_stops() {
        // Tag 0x04, length 0x05, but only 2 bytes follow.
        byte[] data = Hex.decode("0405AABB");

        List<TLV> tlvs = TLV.parseAll(data);

        assertThat(tlvs).isEmpty();
    }

    @Test
    public void parseAll_constructedTag_recursesIntoChildren() {
        // SEQUENCE { INTEGER 1, INTEGER 2 }
        byte[] data = Hex.decode("3006020101020102");

        List<TLV> tlvs = TLV.parseAll(data);

        assertThat(tlvs).hasSize(1);
        TLV seq = tlvs.get(0);
        assertThat(seq.getTag()).isEqualTo(0x30);
        assertThat(seq.children).hasSize(2);
        assertThat(seq.children.get(0).getTag()).isEqualTo(0x02);
        assertThat(seq.children.get(0).getValue()).isEqualTo(new byte[] {0x01});
        assertThat(seq.children.get(1).getValue()).isEqualTo(new byte[] {0x02});
    }

    @Test
    public void findByTag_static_returnsFirstMatchOrNull() {
        // INTEGER 1, OCTET STRING 0xAA, INTEGER 3
        byte[] data = Hex.decode("0201010401AA020103");
        List<TLV> tlvs = TLV.parseAll(data);

        TLV found = TLV.findByTag(tlvs, 0x04);
        assertThat(found).isNotNull();
        assertThat(found.getValue()).isEqualTo(new byte[] {(byte) 0xAA});

        // Returns first match — there are two 0x02 entries, expect 0x01.
        assertThat(TLV.findByTag(tlvs, 0x02).getValue()).isEqualTo(new byte[] {0x01});
        assertThat(TLV.findByTag(tlvs, 0x99)).isNull();
    }

    @Test
    public void findByTag_instance_searchesChildren() {
        // SEQUENCE { OCTET STRING 0xCC, INTEGER 0x05 }
        byte[] data = Hex.decode("30060401CC020105");
        TLV seq = TLV.parseAll(data).get(0);

        assertThat(seq.findByTag(0x04).getValue()).isEqualTo(new byte[] {(byte) 0xCC});
        assertThat(seq.findByTag(0x02).getValue()).isEqualTo(new byte[] {0x05});
        assertThat(seq.findByTag(0x99)).isNull();
    }

    @Test
    public void parseAll_efCardAccessLikeFixture_yieldsExpectedShape() {
        // The exact bytes returned by the PACE flow test's LV stub for
        // EF.CardAccess (paramId 0x0D = brainpoolP256r1). Walked here to
        // confirm the parser surfaces SET → SEQUENCE → [OID, INT, INT(0x0D)]
        // — i.e. the "paramId is reachable" property the PACE setup depends on.
        byte[] data = Hex.decode("3114301206" + "0a" + "04007f0007020204020402010202010d");

        List<TLV> entries = TLV.parseAll(data);
        assertThat(entries).hasSize(1);

        TLV set = entries.get(0);
        assertThat(set.getTag()).isEqualTo(0x31);
        assertThat(set.children).hasSize(1);

        TLV seq = set.children.get(0);
        assertThat(seq.getTag()).isEqualTo(0x30);
        assertThat(seq.children).hasSize(3);

        TLV oid = seq.children.get(0);
        assertThat(oid.getTag()).isEqualTo(0x06);
        assertThat(oid.getValue()).isEqualTo(Hex.decode("04007f000702020402" + "04"));

        // paramId = third child, INTEGER, length 1, value 0x0D.
        TLV paramIdTlv = seq.children.get(2);
        assertThat(paramIdTlv.getTag()).isEqualTo(0x02);
        assertThat(paramIdTlv.getValue()).isEqualTo(new byte[] {(byte) 0x0D});
    }

    @Test
    public void parseAll_pkcs15LikeNestedShape_descendsForKeyReference() {
        // Approximation of a PKCS#15 PrKDF entry shape that
        // LatviaIdemiaWithPace navigates via findByTag(0x30) then findByTag(0x04).
        //
        // [A0] {
        //   30 {
        //     04 02 50 31     <- file ID 5031
        //   }
        // }
        byte[] data = Hex.decode("A006300404025031");

        TLV root = TLV.parseAll(data).get(0);
        TLV seq = root.findByTag(0x30);
        TLV fileId = seq.findByTag(0x04);

        assertThat(fileId.getValue()).isEqualTo(new byte[] {0x50, 0x31});
    }
}
