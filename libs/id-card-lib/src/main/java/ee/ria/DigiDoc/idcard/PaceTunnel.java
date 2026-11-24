// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-FileCopyrightText: 2026 Stagnation OÜ
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import static ee.ria.DigiDoc.idcard.TLV.encodeTLV;
import static org.bouncycastle.util.Arrays.concatenate;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.WNafUtil;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import ee.ria.DigiDoc.smartcardreader.ApduResponseException;
import ee.ria.DigiDoc.smartcardreader.SmartCardReader;
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import ee.ria.DigiDoc.smartcardreader.nfc.ApduEncryptor;
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil;

/**
 * PACE key-exchange and Secure Messaging, shared by every card. It must implement the
 * ApduEncryptor interface so that NfcSmartCardReader class has an oracle to encrypt
 * C-APDUs and decrypt R-APDUs so that each card's APDU protocol can remain mostly
 * unchanged
 * @noinspection FieldCanBeLocal, SameParameterValue
 */
final class PaceTunnel implements ApduEncryptor {
    private static final String TAG = PaceTunnel.class.getName();

    /**
     * Last bytes for padding used in establishing encryption key,
     * MAC key and nonce decryption key
     */
    private static final byte PADDING_ENCK = 0x01;
    private static final byte PADDING_MACK = 0x02;
    private static final byte PADDING_NONCE_DECRYPTION = 0x03;

    /**
     * Padding indicator used in SM
     */
    private static final byte PADDING_SM = (byte) 0x80;

    /**
     * CLA, Plain
     */
    private static final byte CLA_ISO = (byte) 0x00;

    /**
     * CLA, Plain, command chaining
     */
    private static final byte CLA_CHAIN = (byte) 0x10;

    /**
     * INS, General Authenticate
     */
    private static final byte INS_GA = (byte) 0x86;

    /**
     * INS, Manage Security Environment
     */
    private static final byte INS_MSE = (byte) 0x22;

    /**
     * Supported SM data objects
     */
    private static final byte DO85 = (byte) 0x85;
    private static final byte DO87 = (byte) 0x87;
    private static final byte DO8E = (byte) 0x8E;
    private static final byte DO97 = (byte) 0x97;
    private static final byte DO99 = (byte) 0x99;

    /**
     * Length of MAC
     */
    private static final byte MAC_LENGTH = 8;

    /**
     * Block size used in padding secure messages
     */
    private static final int BLOCK_SIZE = 16;

    record Profile(String curveName, int nonceBytes, Integer generalAuthenticateLe,
                   byte[] mseAuthenticationTemplate) {}

    interface ProfileSource {
        Profile resolve() throws SmartCardReaderException;
    }

    private final SmartCardReader reader;
    private final Profile profile;

    /**
     * PACE send sequence counter
     */
    private final byte[] ssc = new byte[BLOCK_SIZE];

    /**
     * Shared encryption key agreed over PACE
     */
    private byte[] keyEnc;

    /**
     * Shared MAC key agreed over PACE
     */
    private byte[] keyMAC;

    PaceTunnel(SmartCardReader reader, Profile profile) {
        this.reader = reader;
        this.profile = profile;
    }

    /**
     * Start PACE key-exchange with CAN
     * @param can
     * @throws SmartCardReaderException
     */
    static PaceTunnel open(NfcSmartCardReader reader, ProfileSource source, String can)
            throws SmartCardReaderException {
        try {
            PaceTunnel tunnel = new PaceTunnel(reader, source.resolve());
            tunnel.establish(can.getBytes(StandardCharsets.UTF_8));
            // In case we were successful we notify the card that from now on
            // everything is encrypted
            reader.setApduEncryptor(tunnel);
            return tunnel;
        } catch (SmartCardReaderException ex) {
            if (ex instanceof ApduResponseException aex) {
                if ((aex.sw1 == (byte) 0x63) && (aex.sw2 == 0x00)) {
                    throw new PaceTunnelException(ex);
                }
            }
            throw ex;
        } catch (Exception ex) {
            throw new SmartCardReaderException("Could not establish tunnel", ex);
        }
    }

    // ---- handshake ----

    /**
     * PACE Key-Exchange
     *
     * @param can the card access number
     */
    private void establish(byte[] can) throws
            NoSuchPaddingException, InvalidAlgorithmParameterException,
            NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException,
            InvalidKeyException, SmartCardReaderException {

        setMSEAuthenticationTemplate();

        byte[] response = getGAGetNonce();

        int nonceBytes = profile.nonceBytes();
        byte[] gaGetNonceResponseHeader = new byte[] {
                0x7C, (byte) (2 + nonceBytes), (byte) 0x80, (byte) nonceBytes};
        validateHeader(response, gaGetNonceResponseHeader);
        // Catch malformed/truncated responses here so the failure surfaces with a
        // clear message rather than as a downstream IllegalBlockSizeException from
        // the AES decrypt.
        validateResponseLength(response, gaGetNonceResponseHeader.length + nonceBytes,
                "GA Get Nonce");
        byte[] decryptedNonce = decryptNonce(
                Arrays.copyOfRange(
                        response, gaGetNonceResponseHeader.length, response.length),
                can);

        // generate an EC keypair and exchange public keys with the chip
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec(profile.curveName());

        // Uncompressed point encoding: 0x04 || X || Y, both coords curve-sized.
        // 65 bytes for the 256-bit curves, 97 bytes for 384-bit.
        int pointBytes = 1 + 2 * ((spec.getCurve().getFieldSize() + 7) / 8);

        BigInteger privateKey = generateRandomPrivateKey(spec);

        ECPoint publicKey = spec.getG().multiply(privateKey).normalize();
        response = getGAMapNonce(publicKey.getEncoded(false));

        // Extract bytes from R-APDU to represent card public key
        byte[] gaMapNonceHeader = generalAuthenticateHeader((byte) 0x82, pointBytes);
        validateHeader(response, gaMapNonceHeader);
        validateResponseLength(response, gaMapNonceHeader.length + pointBytes, "GA Map Nonce");
        ECPoint cardPublicKey = spec.getCurve().decodePoint(
                Arrays.copyOfRange(response, gaMapNonceHeader.length, response.length));

        // calculate the new base point, use it to generate a new keypair
        // and exchange public keys
        ECPoint sharedSecret = cardPublicKey.multiply(privateKey);
        ECPoint mappedECBasePoint = spec.getG().multiply(
                new BigInteger(1, decryptedNonce)).add(sharedSecret).normalize();

        privateKey = generateRandomPrivateKey(spec);
        publicKey = mappedECBasePoint.multiply(privateKey).normalize();
        response = getGAKeyAgreement(publicKey.getEncoded(false));

        // Extract the curve-sized point from R-APDU to represent card public key
        byte[] gaKeyAgreementHeader = generalAuthenticateHeader((byte) 0x84, pointBytes);
        validateHeader(response, gaKeyAgreementHeader);
        validateResponseLength(response, gaKeyAgreementHeader.length + pointBytes,
                "GA Key Agreement");
        cardPublicKey = spec.getCurve().decodePoint(
                Arrays.copyOfRange(response, gaKeyAgreementHeader.length, response.length));

        // generate the session keys and exchange MACs to verify them
        byte[] secret = cardPublicKey.multiply(privateKey).
                normalize().getAffineXCoord().getEncoded();

        keyEnc = createKey(secret, PADDING_ENCK);
        keyMAC = createKey(secret, PADDING_MACK);
        byte[] MAC = getMAC(getDataForMac(cardPublicKey.getEncoded(false), pointBytes), keyMAC);
        response = getGAMutualAuthentication(MAC);

        byte[] gaMutualAuthenticationHeader = generalAuthenticateHeader((byte) 0x86, MAC_LENGTH);
        validateHeader(response, gaMutualAuthenticationHeader);
        validateResponseLength(response, gaMutualAuthenticationHeader.length + MAC_LENGTH,
                "GA Mutual Authentication");

        // verify chip's MAC
        MAC = getMAC(getDataForMac(publicKey.getEncoded(false), pointBytes), keyMAC);
        byte[] cardMac = Arrays.copyOfRange(response,
                gaMutualAuthenticationHeader.length,
                gaMutualAuthenticationHeader.length + MAC_LENGTH);
        if (!Arrays.equals(cardMac, MAC)) {
            throw new SmartCardReaderException("Could not verify chip's MAC.");
        }
    }

    private static byte[] generalAuthenticateHeader(byte dataObject, int payloadBytes) {
        return new byte[] {0x7C, (byte) (2 + payloadBytes), dataObject, (byte) payloadBytes};
    }

    /**
     * Set MSE Authentication Template
     */
    private void setMSEAuthenticationTemplate() throws SmartCardReaderException {
        reader.transmit(CLA_ISO, INS_MSE, 0xC1, 0xA4, profile.mseAuthenticationTemplate(), 0x00);
    }

    /**
     * Get GA Nonce
     */
    private byte[] getGAGetNonce() throws SmartCardReaderException {
        byte[] data = new byte[] {0x7C, 0x00};
        return reader.transmit(CLA_CHAIN, INS_GA, 0x00, 0x00, data,
                profile.generalAuthenticateLe());
    }

    /**
     * GA Map Nonce
     */
    private byte[] getGAMapNonce(byte[] publicKey) throws SmartCardReaderException {
        return generalAuthenticate(CLA_CHAIN, (byte) 0x81, publicKey);
    }

    /**
     * GA Key Agreement
     */
    private byte[] getGAKeyAgreement(byte[] publicKey) throws SmartCardReaderException {
        return generalAuthenticate(CLA_CHAIN, (byte) 0x83, publicKey);
    }

    /**
     * GA Mutual Authentication
     */
    private byte[] getGAMutualAuthentication(byte[] mac) throws SmartCardReaderException {
        return generalAuthenticate(CLA_ISO, (byte) 0x85, mac);
    }

    private byte[] generalAuthenticate(byte cla, byte dataObject, byte[] payload)
            throws SmartCardReaderException {
        byte[] data = encodeTLV(0x7c, encodeTLV(dataObject, payload));
        return reader.transmit(cla, INS_GA, 0x00, 0x00, data, profile.generalAuthenticateLe());
    }

    /**
     * Get Data for MAC
     */
    private static byte[] getDataForMac(byte[] publicKey, int pointBytes) {
        byte[] prefix = new byte[] {
                0x7f, 0x49, (byte) (pointBytes + 14), 0x06, 0x0a, 0x04, 0x00, 0x7f,
                0x00, 0x07, 0x02, 0x02, 0x04, 0x02, 0x04, (byte) 0x86, (byte) pointBytes};
        return concatenate(prefix, publicKey);
    }

    /**
     * Creates a cipher key
     *
     * @param basis    the array to be used as the basis for the key
     * @param lastByte the last byte in the appended padding
     * @return the constructed key
     */
    private byte[] createKey(byte[] basis, byte lastByte) throws NoSuchAlgorithmException {
        byte[] padding = new byte[] {0x00, 0x00, 0x00, lastByte};
        byte[] padded = concatenate(basis, padding);
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        return messageDigest.digest(padded);
    }

    /**
     * Decrypts the nonce
     *
     * @param encryptedNonce the encrypted nonce received from the chip
     * @param CAN            the card access number provided by the user
     * @return the decrypted nonce
     */
    private byte[] decryptNonce(byte[] encryptedNonce, byte[] CAN) throws
            NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        byte[] decryptionKey = createKey(CAN, PADDING_NONCE_DECRYPTION);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(decryptionKey, "AES"),
                new IvParameterSpec(new byte[BLOCK_SIZE]));
        return cipher.doFinal(encryptedNonce);
    }

    /**
     * Calculates the message authentication code
     *
     * @param data   the byte array on which the CMAC algorithm is performed
     * @param keyMAC the key for performing CMAC
     * @return MAC
     */
    private byte[] getMAC(byte[] data, byte[] keyMAC) {
        BlockCipher blockCipher = AESEngine.newInstance();
        CMac cmac = new CMac(blockCipher);
        cmac.init(new KeyParameter(keyMAC));
        cmac.update(data, 0, data.length);
        byte[] MAC = new byte[cmac.getMacSize()];
        cmac.doFinal(MAC, 0);
        return Arrays.copyOf(MAC, MAC_LENGTH);
    }

    /**
     * Check that response has specific header bytes
     */
    private static void validateHeader(byte[] response, byte[] header) throws
            SmartCardReaderException {

        if (header.length > response.length) {
            throw new SmartCardReaderException(
                    "Response length is shorter than expected header length");
        }

        for (int i = 0; i < header.length; i++) {
            if (header[i] != response[i]) {
                throw new SmartCardReaderException(
                        "Unexpected byte at index " + i + " in the APDU header");
            }
        }
    }

    /**
     * Assert a response is exactly the expected length. Used after validateHeader
     * to catch malformed PACE GA responses early with a clear message, instead of
     * surfacing as opaque crypto errors from downstream decode/decrypt steps.
     */
    private static void validateResponseLength(byte[] response, int expected, String stage)
            throws SmartCardReaderException {
        if (response.length != expected) {
            throw new SmartCardReaderException(
                    "Unexpected " + stage + " response length: " + response.length
                            + " (expected " + expected + ")");
        }
    }

    /**
     * Generate private key in range [1, N-1]
     */
    private static BigInteger generateRandomPrivateKey(ECNamedCurveParameterSpec spec) {
        SecureRandom random = new SecureRandom();
        BigInteger n = spec.getN();
        int nBitLength = n.bitLength();
        int minWeight = nBitLength >>> 2;

        BigInteger d;
        for (; ; ) {
            d = BigIntegers.createRandomBigInteger(nBitLength, random);

            if (isOutOfRangeD(d, n)) {
                continue;
            }

            if (WNafUtil.getNafWeight(d) < minWeight) {
                continue;
            }

            break;
        }

        return d;
    }

    private static boolean isOutOfRangeD(BigInteger d, BigInteger n) {
        return d.compareTo(BigInteger.ONE) < 0 || (d.compareTo(n) >= 0);
    }

    // ---- secure messaging ----

    /**
     * Encrypt/Decrypt data with the agreed key
     *
     * @param data - data to be encrypted/decrypted
     * @param mode - encrypt or decrypt?
     */
    private byte[] encdecData(byte[] data, int mode) throws
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyEnc, "AES");
        @SuppressLint("GetInstance") Cipher cipher = Cipher.getInstance(
                "AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        byte[] iv = Arrays.copyOf(cipher.doFinal(ssc), BLOCK_SIZE);
        cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(mode, secretKeySpec, new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    private byte[] encryptData(byte[] data) throws
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        return encdecData(data, Cipher.ENCRYPT_MODE);
    }

    private byte[] decryptData(byte[] data) throws
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        return encdecData(data, Cipher.DECRYPT_MODE);
    }

    /**
     * Pad data to the block-size with 8000*
     */
    private byte[] pad(byte[] data, int blockSize) {
        int padLen = (blockSize - data.length % blockSize);
        byte[] result = new byte[data.length + padLen];
        System.arraycopy(data, 0, result, 0, data.length);
        result[data.length] = PADDING_SM;
        return result;
    }

    /**
     * Unpad data that has been padded to the block size by 8000*
     */
    private byte[] unpad(byte[] data) {
        for (int i = data.length - 1; i >= 0; i--) {
            if (data[i] == PADDING_SM) {
                byte[] ret = new byte[i];
                System.arraycopy(data, 0, ret, 0, i);
                return ret;
            }
        }

        return data;
    }

    /**
     * For Debugging - bouncy does not survive nulls
     */
    private String toHexString(byte[] data) {
        if (data == null) {
            return "NULL";
        }
        return Hex.toHexString(data);
    }

    /**
     * Encrypt C-APDU and protect with MAC
     */
    public byte[] encryptAndMac(int cla, int ins, int p1, int p2, byte[] data, Integer le)
            throws GeneralSecurityException {
        LoggingUtil.Companion.debugLog(TAG, String.format("C-APDU to encrypt: 0x%02X 0x%02X 0x%02X 0x%02X",
                (byte)cla, (byte)ins, (byte)p1, (byte)p2), null);

        incrementSSC(ssc);
        byte[] maskedHeader = new byte[] {
                (byte)((byte)cla | 0x0C), (byte) ins, (byte) p1, (byte) p2 };
        byte[] do8587 = getDo8587(ins, data);
        byte[] do97 = getDo97(le);
        byte[] do8e = getDo8e(maskedHeader, do8587, do97);

        byte newLength = 0;
        newLength += (byte) do8587.length;
        newLength += (byte) do97.length;
        newLength += (byte) do8e.length;

        byte[] result = concatenate(new byte[][] {
                maskedHeader,
                new byte[] {newLength},
                do8587,
                do97,
                do8e,
                new byte[] {0x00}
        });
        incrementSSC(ssc);
        LoggingUtil.Companion.debugLog(TAG, String.format("Encrypted C-APDU: %s", toHexString(result)), null);
        return result;
    }

    /**
     * Calculate DO8E object
     */
    @NonNull
    private byte[] getDo8e(byte[] maskedHeader, byte[] do8587, byte[] do97) {
        byte[] paddedMaskedHeader = pad(maskedHeader, BLOCK_SIZE);
        byte[] macData = concatenate(ssc, paddedMaskedHeader, do8587, do97);
        byte[] paddedMacData = macData;
        if (macData.length % BLOCK_SIZE != 0) {
            paddedMacData = pad(macData, BLOCK_SIZE);
        }
        return concatenate(new byte[] {DO8E, MAC_LENGTH}, getMAC(paddedMacData, keyMAC));
    }

    /**
     * Calculate DO97 object
     */
    @NonNull
    private byte[] getDo97(Integer le) {
        byte[] do97 = new byte[]{};
        if (le != null) {
            do97 = new byte[]{DO97, 0x01, le.byteValue()};
        }
        return do97;
    }

    /**
     * Calculate DO85/DO87 object
     */
    @NonNull
    private byte[] getDo8587(int ins, byte[] data) throws
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        byte[] do8587 = new byte[]{};

        if (data != null && data.length > 0) {
            byte[] paddedData;
            paddedData = pad(data, BLOCK_SIZE);
            byte[] dataEncrypted = encryptData(paddedData);
            if (ins % 2 == 0) {
                do8587 = concatenate(new byte[]{DO87, (byte) (dataEncrypted.length + 1), 0x01},
                        dataEncrypted);
            } else {
                do8587 = concatenate(new byte[]{DO85, (byte) dataEncrypted.length}, dataEncrypted);
            }
        }
        return do8587;
    }

    /**
     * Validate R-APDU, check MAC and decrypt data if present. Only return data.
     */
    public byte[] decryptAndVerify(byte[] response) throws
            GeneralSecurityException, SmartCardReaderException {

        LoggingUtil.Companion.debugLog(TAG, String.format("Encrypted R-APDU: %s", Hex.toHexString(response)), null);

        // result shall be decrypted data or empty
        byte[] result = new byte[]{};

        int currentByte = 0;
        if ((response[currentByte] == DO87) || (response[currentByte] == DO85)) {

            boolean skip87header = (response[currentByte] == DO87);

            currentByte += 1;

            int size = response[currentByte] & 0xFF;
            LoggingUtil.Companion.debugLog(TAG, String.format(Locale.ENGLISH, "Encrypted data size %d ", size), null);

            currentByte += 1;

            if (size > 0x80) {
                int sizeLen = size & 0x0F;
                byte[] sizeBytes = new byte[sizeLen];
                System.arraycopy(response, currentByte, sizeBytes, 0, sizeLen);
                size = new BigInteger(1, sizeBytes).intValue();
                LoggingUtil.Companion.debugLog(TAG, String.format(Locale.ENGLISH,"size bytes %d, size %d", sizeLen, size), null);
                currentByte += sizeLen;
            }

            if (skip87header) {
                if (response[currentByte] != (byte) 0x01) {
                    throw new SmartCardReaderException("Invalid encryption header");
                }
                currentByte += 1; // skip encryption header
                size -= 1;
            }

            result = decryptData(
                    Arrays.copyOfRange(response, currentByte, currentByte + size));
            currentByte += size;
        }

        if (response[currentByte] == DO99) {
            if (!Hex.toHexString(response, currentByte, 4).equals("99029000")) {
                throw new SmartCardReaderException("Invalid status");
            }
            currentByte += 4;
        }

        int macStart = currentByte;
        if (response[currentByte] != DO8E) {
            LoggingUtil.Companion.debugLog(TAG, String.format(Locale.ENGLISH, "0x%02X, %d", response[currentByte], currentByte), null);
            throw new SmartCardReaderException("Missing MAC");
        }
        currentByte += 1;

        if (response[currentByte] != MAC_LENGTH) {
            throw new SmartCardReaderException("Unsupported MAC length");
        }
        currentByte += 1;

        byte[] cardMac = Arrays.copyOfRange(
                response, currentByte, currentByte + MAC_LENGTH);
        currentByte += MAC_LENGTH;

        byte[] rdata = Arrays.copyOfRange(response, 0, macStart);
        byte[] macData = pad(concatenate(ssc, rdata), BLOCK_SIZE);
        byte[] ourMac = getMAC(macData, keyMAC);
        LoggingUtil.Companion.debugLog(TAG, String.format("Card MAC: %s, our MAC: %s",
                Hex.toHexString(cardMac), Hex.toHexString(ourMac)), null);

        if (!Arrays.equals(cardMac, ourMac)) {
            throw new SmartCardReaderException("Could not verify chip's MAC.");
        }

        if (response.length - currentByte != 2) {
            throw new SmartCardReaderException("Malformed R-APDU");
        }

        LoggingUtil.Companion.debugLog(TAG, String.format("Decrypted data: %s", Hex.toHexString(result)), null);
        return unpad(result);
    }

    /**
     * Increment send sequence counter
     */
    private static void incrementSSC(byte[] ssc) {
        for (int i = ssc.length - 1; i >= 0; i--) {
            ssc[i]++;
            if (ssc[i] != 0) {
                break;
            }
        }
    }
}
