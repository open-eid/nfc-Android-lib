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

import android.util.SparseArray;

import com.google.common.primitives.Bytes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import ee.ria.DigiDoc.smartcardreader.ApduResponseException;
import ee.ria.DigiDoc.smartcardreader.SmartCardReader;
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil;

abstract class Idemia implements Token {

    private static final String TAG = Idemia.class.getName();

    private static final int READ_BINARY_CHUNK_LE = 0xE5;

    private static final Map<CertificateType, byte[]> CERT_MAP = new HashMap<>();
    static {
        CERT_MAP.put(CertificateType.AUTHENTICATION, new byte[] {(byte) 0xAD, (byte) 0xF1, 0x34, 0x01});
        CERT_MAP.put(CertificateType.SIGNING, new byte[] {(byte) 0xAD, (byte) 0xF2, 0x34, (byte) 0x1F});
    }

    private static final Map<CodeType, Byte> PIN_MAP = new HashMap<>();
    static {
        PIN_MAP.put(CodeType.PIN1, (byte) 0x01);
        PIN_MAP.put(CodeType.PIN2, (byte) 0x05);
        PIN_MAP.put(CodeType.PUK, (byte) 0x02);
    }

    protected static final Map<CodeType, Byte> VERIFY_PIN_MAP = new HashMap<>();
    static {
        VERIFY_PIN_MAP.put(CodeType.PIN1, (byte) 0x01);
        VERIFY_PIN_MAP.put(CodeType.PIN2, (byte) 0x85);
        VERIFY_PIN_MAP.put(CodeType.PUK, (byte) 0x02);
    }

    protected final SmartCardReader reader;

    Idemia(SmartCardReader reader) {
        this.reader = reader;
    }

    @Override
    public CardType cardType() {
        return CardType.ID1;
    }

    @Override
    public PersonalData personalData() throws SmartCardReaderException {
        selectMainAid();
        reader.transmit(0x00, 0xA4, 0x01, 0x0C, new byte[] {0x50, 0x00}, null);
        SparseArray<String> data = new SparseArray<>();
        for (int i = 1; i <= 8; i++) {
            reader.transmit(0x00, 0xA4, 0x02, 0x0C, new byte[] {0x50, (byte) i}, null);
            byte[] record = reader.transmit(0x00, 0xB0, 0x00, 0x00, null, 0x00);
            data.put(i, new String(record, StandardCharsets.UTF_8).trim());
        }
        return IdemiaPersonalDataParser.parse(data);
    }

    /**
     * SELECT cert EF with P2 = 0x04 to request the FCP template, then either
     * <ul>
     *   <li>FCI fast path: if the FCI declares a file size in tag
     *       {@code 80}/{@code 81}, READ BINARY exactly that many bytes
     *       (no 6B 00 probe).</li>
     *   <li>Canonical fallback: if the FCI lacks a parseable size tag,
     *       READ BINARY chunked until the card returns SW {@code 6B 00}.</li>
     * </ul>
     * Both LV and EE IDEMIA cards return a standard ISO 7816-4 FCP template
     * containing the size, so the fast path is the default. The fallback
     * keeps us correct on card variants that respond to {@code P2 = 0x04}
     * but omit the size tag — would otherwise silently truncate large files.
     */
    @Override
    public byte[] certificate(CertificateType type) throws SmartCardReaderException {
        selectMainAid();
        byte[] fci = reader.transmit(0x00, 0xA4, 0x09, 0x04, CERT_MAP.get(type), null);

        Integer size = parseFciSize(fci);
        if (size != null) {
            LoggingUtil.Companion.debugLog(TAG, String.format(
                    "certificate(%s): FCI fast path, declared size=%d bytes",
                    type, size), null);
            return readBoundedByFciSize(size);
        }
        LoggingUtil.Companion.debugLog(TAG, String.format(
                "certificate(%s): FCI lacks tag 0x80/0x81 size — falling back to 6B 00 loop",
                type), null);
        return readUntilEof();
    }

    /**
     * Walk the FCI (possibly wrapped in a {@code 6F}/{@code 62} template,
     * possibly bare) for the first tag {@code 80} or {@code 81} carrying a
     * 2-byte big-endian file size. Returns {@code null} if neither tag is
     * present with a usable value — caller falls back to a {@code 6B 00}-
     * terminated read.
     */
    private Integer parseFciSize(byte[] fci) {
        TLV r = findTagRecursive(TLV.parseAll(fci), 0x80);
        if (r == null) {
            r = findTagRecursive(TLV.parseAll(fci), 0x81);
        }
        if (r == null) {
            return null;
        }
        byte[] value = r.getValue();
        if (value == null || value.length < 2) {
            return null;
        }
        return ((value[0] & 0xFF) << 8) | (value[1] & 0xFF);
    }

    /**
     * FCI fast path: read exactly {@code size} bytes in chunks of up to
     * {@value #READ_BINARY_CHUNK_LE}. A 6B 00 mid-read or zero-length
     * response is treated as graceful EOF — the card decided to deliver
     * less than declared, which we tolerate rather than throw.
     */
    private byte[] readBoundedByFciSize(int size) throws SmartCardReaderException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        while (stream.size() < size) {
            int offset = stream.size();
            int le = Math.min(READ_BINARY_CHUNK_LE, size - offset);
            try {
                byte[] response = reader.transmit(0x00, 0xB0,
                        offset >> 8, offset & 0xFF, null, le);
                stream.write(response);
                if (response.length == 0) {
                    break;
                }
            } catch (ApduResponseException e) {
                if (e.sw1 == (byte) 0x6B && e.sw2 == (byte) 0x00) {
                    break;
                }
                throw new SmartCardReaderException(e);
            } catch (IOException e) {
                throw new SmartCardReaderException(e);
            }
        }
        return stream.toByteArray();
    }

    /**
     * Canonical fallback when FCI lacks a usable size: chunked
     * {@code READ BINARY} with {@code Le = 0x00} until the card returns
     * {@code 6B 00} (or {@code 6A 82} — file not found, treated as empty).
     */
    private byte[] readUntilEof() throws SmartCardReaderException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        while (true) {
            int offset = stream.size();
            try {
                byte[] response = reader.transmit(0x00, 0xB0,
                        offset >> 8, offset & 0xFF, null, 0x00);
                stream.write(response);
                if (response.length == 0) {
                    break;
                }
            } catch (ApduResponseException e) {
                if (e.sw1 == (byte) 0x6B && e.sw2 == (byte) 0x00) {
                    break;
                }
                if (e.sw1 == (byte) 0x6A && e.sw2 == (byte) 0x82) {
                    break;
                }
                throw new SmartCardReaderException(e);
            } catch (IOException e) {
                throw new SmartCardReaderException(e);
            }
        }
        return stream.toByteArray();
    }

    @Override
    public int codeRetryCounter(CodeType type) throws SmartCardReaderException {
        if (type.equals(CodeType.PIN2)) {
            selectQSCDAid();
        } else {
            selectMainAid();
        }
        return reader.transmit(0x00, 0xCB, 0x3F, 0xFF, new byte[] {0x4D, 0x08, 0x70, 0x06, (byte) 0xBF, (byte) 0x81, Objects.requireNonNull(PIN_MAP.get(type)), 0x02, (byte) 0xA0, (byte) 0x80}, 0x00)[13];
    }

    @Override
    public void changeCode(CodeType type, byte[] currentCode, byte[] newCode) throws SmartCardReaderException {
        if (type.equals(CodeType.PIN2)) {
            selectQSCDAid();
        } else {
            selectMainAid();
        }
        try {
            reader.transmit(0x00, 0x24, 0x00, Objects.requireNonNull(VERIFY_PIN_MAP.get(type)), Bytes.concat(code(currentCode), code(newCode)), null);
        } catch (ApduResponseException e) {
            handleApduResponseException(type, e);
        }
    }

    @Override
    public void unblockAndChangeCode(byte[] pukCode, CodeType type, byte[] newCode) throws SmartCardReaderException {
        // PUK is associated with the MAIN AID context, so VERIFY PUK must run there.
        // Make this self-contained (matching the rest of the Token API) instead of
        // relying on the caller leaving MAIN active — otherwise calls composed after
        // codeRetryCounter(PIN2)/calculateSignature/etc. would silently fail with a
        // "wrong PUK" error.
        selectMainAid();
        verifyCode(CodeType.PUK, pukCode);
        if (type.equals(CodeType.PIN2)) {
            selectQSCDAid();
        }
        try {
            reader.transmit(0x00, 0x2C, 0x02, Objects.requireNonNull(VERIFY_PIN_MAP.get(type)), code(newCode), null);
        } catch (ApduResponseException e) {
            handleApduResponseException(CodeType.PUK, e);
        }
    }

    @Override
    public int pinChangedFlag(CodeType type) {
        return 1;
    }

    @Override
    public abstract byte[] calculateSignature(byte[] pin2, byte[] hash, boolean ecc)
            throws SmartCardReaderException;

    @Override
    public abstract byte[] authenticate(byte[] pin1, byte[] token)
            throws SmartCardReaderException;

    @Override
    public abstract byte[] decrypt(byte[] pin1, byte[] data, boolean ecc)
            throws SmartCardReaderException;

    protected void verifyCode(CodeType type, byte[] code) throws SmartCardReaderException {
        try {
            reader.transmit(0x00, 0x20, 0x00, Objects.requireNonNull(VERIFY_PIN_MAP.get(type)), code(code), null);
        } catch (ApduResponseException e) {
            handleApduResponseException(type, e);
        }
    }

    private void handleApduResponseException(CodeType type, ApduResponseException e) throws SmartCardReaderException {
        if (e.sw1 == 0x63 || (e.sw1 == 0x69 && e.sw2 == (byte) 0x83)) {
            if (e.sw2 == (byte) 0xC2) {
                throw new CodeVerificationException(type, 2);
            } else if (e.sw2 == (byte) 0xC1) {
                throw new CodeVerificationException(type, 1);
            }
            throw new CodeVerificationException(type, 0);
        }
        throw e;
    }

    protected void selectMainAid() throws SmartCardReaderException {
        reader.transmit(0x00, 0xA4, 0x04, 0x00, new byte[] {(byte) 0xA0, 0x00, 0x00, 0x00, 0x77, 0x01, 0x08, 0x00, 0x07, 0x00, 0x00, (byte) 0xFE, 0x00, 0x00, 0x01, 0x00}, null);
    }

    protected void selectQSCDAid() throws SmartCardReaderException {
        reader.transmit(0x00, 0xA4, 0x04, 0x0C, new byte[] {0x51, 0x53, 0x43, 0x44, 0x20, 0x41, 0x70, 0x70, 0x6C, 0x69, 0x63, 0x61, 0x74, 0x69, 0x6F, 0x6E}, null);
    }

    protected void selectOberthurAid() throws SmartCardReaderException {
        reader.transmit(0x00, 0xA4, 0x04, 0x0C, new byte[] {(byte) 0xE8, 0x28, (byte) 0xBD, 0x08, 0x0F, (byte) 0xF2, 0x50, 0x4F, 0x54, 0x20, 0x41, 0x57, 0x50}, null);
    }

    private static byte[] code(byte[] code) {
        byte[] padded = Arrays.copyOf(code, 12);
        Arrays.fill(padded, code.length, padded.length, (byte) 0xFF);
        return padded;
    }

    /**
     * Walk {@code records} (and constructed children) depth-first and return
     * the first TLV matching {@code targetTag}, or {@code null}. Lets us find
     * the FCI size tag whether the card wraps it in a {@code 6F}/{@code 62}
     * template or sends it bare.
     */
    private static TLV findTagRecursive(List<TLV> records, int targetTag) {
        if (records == null) {
            return null;
        }
        for (TLV record : records) {
            if (record.getTag() == targetTag) {
                return record;
            }
            TLV found = findTagRecursive(record.children, targetTag);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * ID1 only has ECC keys so we don't need to pad it as we do RSA hashes,
     * but we need to pad hashes that are smaller than the key size with zeroes in front to resize
     * them to 48bytes in length because of API restrictions on the chip
     *
     * @param hash that needs to be signed
     * @return zero padded hash with 48 byte length or same hash if it's longer than 48 bytes
     * @throws IdCardException when padding the hash fails
     */
    protected static byte[] padWithZeroes(byte[] hash) throws IdCardException {
        if (hash.length >= 48) {
            return hash;
        }
        try (ByteArrayOutputStream toSign = new ByteArrayOutputStream()) {
            toSign.write(new byte[48 - hash.length]);
            toSign.write(hash);
            return toSign.toByteArray();
        } catch (IOException e) {
            throw new IdCardException("Failed to Add padding to hash", e);
        }
    }
}
