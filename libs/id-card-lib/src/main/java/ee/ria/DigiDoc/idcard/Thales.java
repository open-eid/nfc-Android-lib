/*
 * Copyright 2017 - 2025 Riigi Infosüsteemi Amet
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package ee.ria.DigiDoc.idcard;

import static com.google.common.primitives.Bytes.concat;
import static ee.ria.DigiDoc.idcard.TLV.parseTLVRecursive;

import android.util.SparseArray;

import com.google.common.base.Charsets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import ee.ria.DigiDoc.smartcardreader.ApduResponseException;
import ee.ria.DigiDoc.smartcardreader.SmartCardReader;
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

class Thales implements Token {

    private static final Map<CertificateType, byte[]> CERT_MAP = new HashMap<>();
    static {
        CERT_MAP.put(CertificateType.AUTHENTICATION, new byte[] {(byte) 0xAD, (byte) 0xF1, 0x34, 0x11});
        CERT_MAP.put(CertificateType.SIGNING, new byte[] {(byte) 0xAD, (byte) 0xF2, 0x34, (byte) 0x21});
    }

    protected static final Map<CodeType, Byte> VERIFY_PIN_MAP = new HashMap<>();
    static {
        VERIFY_PIN_MAP.put(CodeType.PIN1, (byte) 0x81);
        VERIFY_PIN_MAP.put(CodeType.PIN2, (byte) 0x82);
        VERIFY_PIN_MAP.put(CodeType.PUK, (byte) 0x83);
    }

    protected final SmartCardReader reader;

    Thales(SmartCardReader reader) {
        this.reader = reader;
    }

    @Override
    public PersonalData personalData() throws SmartCardReaderException {
        selectMainAid();
        byte[] bytes = new byte[] {(byte) 0xDF,(byte) 0xDD};
        reader.transmit(0x00, 0xA4, 0x08, 0x0C, bytes, null);
        SparseArray<String> data = new SparseArray<>();
        for (int i = 1; i <= 8; i++) {
            byte[] record = readFile(0x02, new byte[] {0x50, (byte) i});
            data.put(i, new String(record, Charsets.UTF_8).trim());
        }
        return ThalesPersonalDataParser.parse(data);
    }

    @Override
    public byte[] certificate(CertificateType type) throws SmartCardReaderException {
        selectMainAid();
        return readFile(0x08, CERT_MAP.get(type));
    }

    private byte[] readFile(int p1, byte[] bytes) throws SmartCardReaderException {
        int size = 0xE5;
        byte[] fci = reader.transmit(0x00, 0xA4, p1, 0x04, bytes, null);

        List<TLV> records = parseTLVRecursive(fci);

        for (TLV record : records) {
            int tag = record.getTag();
            if (tag == 0x80 || tag == 0x81) {
                byte[] value = record.getValue();
                if (value != null && value.length >= 2) {
                    size = ((value[0] & 0xFF) << 8) | (value[1] & 0xFF);
                }
            }
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        while (stream.size() < size) {
            int offset = stream.size();
            int remaining = size - offset;
            int le = Math.min(0xE5, remaining); // read max 0xE7 bytes or what's left
            try {
                byte[] response = reader.transmit(0x00, 0xB0, offset >> 8, offset & 0xFF, null, le);
                stream.write(response);
            } catch (ApduResponseException e) {
                if (e.sw1 == (byte)0x6B && e.sw2 == (byte)0x00) {
                    break;
                } else {
                    throw new SmartCardReaderException(e);
                }
            } catch (IOException e) {
                throw new SmartCardReaderException(e);
            }
        }
        return stream.toByteArray();
    }

    private static int extractDf21Value(byte[] data) {
        TLV info = TLV.from(data);
        if (info != null && (info.getTag() & 0xFF) == 0xA0) {
            List<TLV> records = parseTLVRecursive(data);
            for (TLV record : records) {
                if ((record.getTag() & 0xFFFF) == 0xDF21 && record.getValue().length > 0) {
                    return record.getValue()[0] & 0xFF;
                }
            }
        }
        return 0;
    }

    @Override
    public int codeRetryCounter(CodeType type) throws SmartCardReaderException {
        selectMainAid();
        byte[] data = reader.transmit(0x00, 0xCB, 0x00, 0xFF, new byte[] {(byte) 0xA0, 0x03, (byte) 0x83, 0x01, Objects.requireNonNull(VERIFY_PIN_MAP.get(type))}, 0x00);

        return extractDf21Value(data);
    }

    @Override
    public void changeCode(CodeType type, byte[] currentCode, byte[] newCode) throws SmartCardReaderException {
        selectMainAid();
        if (type.equals(CodeType.PUK)) {
            throw new SmartCardReaderException("Cannot change PUK code");
        }
        verifyCode(type, currentCode);
        reader.transmit(0x00, 0x24, 0x00, Objects.requireNonNull(VERIFY_PIN_MAP.get(type)), concat(code(currentCode), code(newCode)), null);
    }

    @Override
    public void unblockAndChangeCode(byte[] pukCode, CodeType type, byte[] newCode) throws SmartCardReaderException {
        selectMainAid();
        if (type.equals(CodeType.PUK)) {
            throw new SmartCardReaderException("Cannot unblock and change PUK code");
        }
        reader.transmit(0x00, 0x2C, pukCode == null ? 0x02 : 0x00, Objects.requireNonNull(VERIFY_PIN_MAP.get(type)), concat(code(pukCode), code(newCode)), null);
    }

    private void setSecEnv(byte mode, byte[] algo, byte keyRef) throws SmartCardReaderException {
        byte[] data = algo != null ? TLV.encodeTLV(0x80, algo) : new byte[] {};
        reader.transmit(0x00, 0x22, 0x41, mode, concat(data, TLV.encodeTLV(0x84, new byte[] {keyRef})), null);
    }

    private byte[] sign(CodeType type, byte[] pin, byte keyRef, byte[] hash) throws SmartCardReaderException {
        verifyCode(type, pin);
        setSecEnv((byte) 0xB6, new byte[] {(byte) (0x24 + hash.length)}, keyRef);

        reader.transmit(0x00, 0x2A, 0x90, 0xA0, TLV.encodeTLV(0x90, hash), null);
        return reader.transmit(0x00, 0x2A, 0x9E, 0x9A, null, 0x00);
    }

    @Override
    public byte[] calculateSignature(byte[] pin2, byte[] hash, boolean ecc) throws SmartCardReaderException {
        return sign(CodeType.PIN2, pin2, (byte) 0x05, hash);
    }

    @Override
    public byte[] authenticate(byte[] pin1, byte[] token) throws SmartCardReaderException {
        return sign(CodeType.PIN1, pin1, (byte) 0x01, token);
    }

    @Override
    public byte[] decrypt(byte[] pin1, byte[] data, boolean ecc) throws SmartCardReaderException {
        verifyCode(CodeType.PIN1, pin1);
        setSecEnv((byte) 0xB8, null, (byte) 0x01);
        byte[] prefix = new byte[] {0x00};
        return reader.transmit(0x00, 0x2A, 0x80, 0x86, concat(prefix, data), 0x00);
    }

    private void verifyCode(CodeType type, byte[] code) throws SmartCardReaderException {
        try {
            reader.transmit(0x00, 0x20, 0x00, Objects.requireNonNull(VERIFY_PIN_MAP.get(type)), code(code), null);
        } catch (ApduResponseException e) {
            if (e.sw1 == 0x63 || (e.sw1 == 0x69 && e.sw2 == (byte) 0x83)) {
                if (e.sw2 == (byte)0xC2) {
                    throw new CodeVerificationException(type, 2);
                } else if (e.sw2 == (byte)0xC1) {
                    throw new CodeVerificationException(type, 1);
                }
                throw new CodeVerificationException(type, 0);
            }
            throw e;
        }
    }

    protected void selectMainAid() throws SmartCardReaderException {
        reader.transmit(0x00, 0xA4, 0x04, 0x00, new byte[] {(byte)0xA0, 0x00, 0x00, 0x00, 0x63, 0x50, 0x4B, 0x43, 0x53, 0x2D, 0x31, 0x35}, null);
    }

    private static byte[] code(byte[] code) {
        byte[] padded = Arrays.copyOf(code, 12);
        Arrays.fill(padded, code.length, padded.length, (byte) 0x00);
        return padded;
    }
}
