package ee.ria.DigiDoc.idcard;

import java.util.ArrayList;
import java.util.List;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

public class TLV {
    private final int tag;
    private final byte[] value;

    public TLV(int tag, byte[] value) {
        this.tag = tag;
        this.value = value;
    }

    public int getTag() {
        return tag;
    }

    public byte[] getValue() {
        return value;
    }

    public static TLV from(byte[] data) {
        if (data == null || data.length < 2) return null;

        int tag = data[0] & 0xFF;
        int length = data[1] & 0xFF;

        if (data.length < 2 + length) return null;

        byte[] value = new byte[length];
        System.arraycopy(data, 2, value, 0, length);

        return new TLV(tag, value);
    }

    public static List<TLV> sequenceOfRecords(byte[] data) {
        List<TLV> result = new ArrayList<>();
        if (data == null) return result;

        int index = 0;
        while (index + 2 <= data.length) {
            int tag = data[index] & 0xFF;
            int length = data[index + 1] & 0xFF;

            if (index + 2 + length > data.length) break;

            byte[] value = new byte[length];
            System.arraycopy(data, index + 2, value, 0, length);
            result.add(new TLV(tag, value));

            index += 2 + length;
        }
        return result;
    }

    public byte[] encode() throws SmartCardReaderException {
        if (value.length > 255) {
            throw new SmartCardReaderException("Only single-byte length supported");
        }
        byte[] result = new byte[2 + value.length];
        result[0] = (byte) tag;
        result[1] = (byte) value.length;
        System.arraycopy(value, 0, result, 2, value.length);
        return result;
    }

    public static byte[] encodeTLV(int tag, byte[] value) throws SmartCardReaderException {
        return new TLV(tag, value).encode();
    }
}
