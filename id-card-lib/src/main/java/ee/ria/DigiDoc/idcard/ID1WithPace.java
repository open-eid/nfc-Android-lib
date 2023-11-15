package ee.ria.DigiDoc.idcard;

import static com.google.common.primitives.Bytes.concat;

import android.annotation.SuppressLint;
import android.util.Log;

import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
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

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import ee.ria.DigiDoc.smartcardreader.nfc.ApduEncryptor;
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;
import timber.log.Timber;

/**
 * ID1WithPace extends ID1 APDU protocol with PACE capabilities enabling
 * the use over NFC. It must implement the ApduEncryptor interface so that
 * NfcSmartCardReader class has an oracle to encrypt C-APDUs and decrypt R-APDUs
 * so that the ID1 APDU protocal can remain mostly unchanged
 */
class ID1WithPace extends ID1 implements TokenWithPace, ApduEncryptor {

    /**
     * Shared encryption key agreed over PACE
     */
    private byte[] keyEnc;

    /**
     * Shared MAC key agreed over PACE
     */
    private byte[] keyMAC;

    /**
     * PACE send sequence counter
     */
    private final byte[] ssc;

    /**
     * NfcSmartCardReader to provide the encryption/decryption for
     */
    private NfcSmartCardReader nfcReader;

    /**
     * Initialize ID1 token with NfcSmartCardReader
     * @param reader
     */
    ID1WithPace(NfcSmartCardReader reader) {
        super(reader);
        nfcReader = reader;
        ssc = new byte[16];
    }

    /**
     * Start PACE key-exchange with CAN
     * @param can
     * @throws SmartCardReaderException
     */
    public void tunnel(String can) throws SmartCardReaderException {
        try {
            byte[][] keys = establishPace(can.getBytes(StandardCharsets.UTF_8));
            keyEnc = keys[0];
            keyMAC = keys[1];
            // In case we were successful we notify the card that from now on
            // everythin is encrypted
            nfcReader.setApduEncryptor(this);
        } catch (Exception ex) {
            throw new SmartCardReaderException(ex);
        }
    }

    protected void selectMainAid() throws SmartCardReaderException {
        reader.transmit(0x00, 0xA4, 0x04, 0x0C, new byte[] {(byte) 0xA0, 0x00, 0x00, 0x00, 0x77, 0x01, 0x08, 0x00, 0x07, 0x00, 0x00, (byte) 0xFE, 0x00, 0x00, 0x01, 0x00}, null);
    }

    /**
     * Set MSE Authentication Template
     *
     * @throws SmartCardReaderException
     */
    private void setMSEAuthenticationTemplate() throws SmartCardReaderException {
        reader.transmit(0x00, 0x22, 0xC1, 0xA4, new byte[] {(byte)0x80, 0x0A, 0x04, 0x00, 0x7F, 0x00, 0x07, 0x02, 0x02, 0x04, 0x02, 0x04, (byte)0x83, 0x01, 0x02}, 0x00);
    }

    /**
     * Get GA Nonce
     * @return
     * @throws SmartCardReaderException
     */
    private byte[] getGAGetNonce() throws SmartCardReaderException {
        return reader.transmit(0x10, 0x86, 0x00, 0x00, new byte[] {0x7C, 0x00}, 0x00);
    }

    /**
     * GA Map Nonce
     * @param publicKey
     * @return
     * @throws SmartCardReaderException
     */
    private byte[] getGAMapNonce(byte[] publicKey) throws SmartCardReaderException {
        byte[] prefix = new byte[] {0x7c, 0x43, (byte)0x81, 0x41};
        return reader.transmit(0x10, 0x86, 0x00, 0x00, concat(prefix, publicKey), 0x00);
    }

    /**
     * GA Key Agreement
     *
     * @param publicKey
     * @return
     * @throws SmartCardReaderException
     */
    private byte[] getGAKeyAgreement(byte[] publicKey) throws SmartCardReaderException {
        byte[] prefix = new byte[] {0x7c, 0x43, (byte)0x83, 0x41};
        return reader.transmit(0x10, 0x86, 0x00, 0x00, concat(prefix, publicKey), 0x00);
    }

    /**
     * GA Mutual Authentication
     * @param mac
     * @return
     * @throws SmartCardReaderException
     */
    private byte[] getGAMutualAuthentication(byte[] mac) throws SmartCardReaderException {
        byte[] prefix = new byte[] {0x7C, 0x0A, (byte)0x85, 0x08};
        return reader.transmit(0x00, 0x86, 0x00, 0x00, concat(prefix, mac), 0x00);
    }

    /**
     * Get Data for MAC
     * @param publicKey
     * @return
     */
    private byte[] getDataForMac(byte[] publicKey) {
        byte[] prefix = new byte[] {0x7f, 0x49, 0x4f, 0x06, 0x0a, 0x04, 0x00, 0x7f, 0x00, 0x07, 0x02, 0x02, 0x04, 0x02, 0x04, (byte)0x86, 0x41};
        return concat(prefix, publicKey);
    }

    /**
     * Creates a cipher key
     *
     * @param basis the array to be used as the basis for the key
     * @param last     the last byte in the appended padding
     * @return the constructed key
     */
    private byte[] createKey(byte[] basis, byte last) throws NoSuchAlgorithmException {
        byte[] padded = Arrays.copyOf(basis, basis.length + 4);
        padded[padded.length - 1] = last;
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
    private byte[] decryptNonce(byte[] encryptedNonce, byte[] CAN) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        byte[] decryptionKey = createKey(CAN, (byte) 3);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decryptionKey, "AES"), new IvParameterSpec(new byte[16]));
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
        BlockCipher blockCipher = new AESEngine();
        CMac cmac = new CMac(blockCipher);
        cmac.init(new KeyParameter(keyMAC));
        cmac.update(data, 0, data.length);
        byte[] MAC = new byte[cmac.getMacSize()];
        cmac.doFinal(MAC, 0);
        return Arrays.copyOf(MAC, 8);
    }

    /**
     * PACE Key-Exchange with ID1
     *
     * @param can the card access number
     */
    private byte[][] establishPace(byte[] can) throws NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, SmartCardReaderException {

        // TODO: 00a4040c10a000000077010800070000fe00000100  P2 0c vs 00 why?
        // select the IAS-ECC application on the chip
        selectMainAid();

        // TODO: Should verify PACE params supported

        // initiate PACE
        setMSEAuthenticationTemplate();

        // get nonce
        byte[] response = getGAGetNonce();

        // TODO R-APDU header verification, SW is removed
        byte[] decryptedNonce = decryptNonce(Arrays.copyOfRange(response, 4, response.length), can);

        // generate an EC keypair and exchange public keys with the chip
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256r1");

        // TODO: should be in [1, spec.getN()-1]
        BigInteger privateKey = new BigInteger(255, new SecureRandom()).add(BigInteger.ONE);

        ECPoint publicKey = spec.getG().multiply(privateKey).normalize();
        response = getGAMapNonce(publicKey.getEncoded(false));

        // Extract bytes from R-APDU to represent card public key
        // TODO: validate R-APDU header
        ECPoint cardPublicKey = spec.getCurve().decodePoint(Arrays.copyOfRange(response, 4, response.length));

        // calculate the new base point, use it to generate a new keypair, and exchange public keys
        ECPoint sharedSecret = cardPublicKey.multiply(privateKey);
        ECPoint mappedECBasePoint = spec.getG().multiply(new BigInteger(1, decryptedNonce)).add(sharedSecret).normalize();

        // TODO: should be in [1, spec.getN()-1]
        privateKey = new BigInteger(255, new SecureRandom()).add(BigInteger.ONE);
        publicKey = mappedECBasePoint.multiply(privateKey).normalize();
        response = getGAKeyAgreement(publicKey.getEncoded(false));

        // Extract 65 bytes from R-APDU to represent card public key
        // TODO: validate R-APDU header
        cardPublicKey = spec.getCurve().decodePoint(Arrays.copyOfRange(response, 4, response.length));

        // generate the session keys and exchange MACs to verify them
        byte[] secret = cardPublicKey.multiply(privateKey).normalize().getAffineXCoord().getEncoded();
        byte[] keyEnc = createKey(secret, (byte) 1);
        byte[] keyMAC = createKey(secret, (byte) 2);
        byte[] MAC = getMAC(getDataForMac(cardPublicKey.getEncoded(false)), keyMAC);
        response = getGAMutualAuthentication(MAC);

        // verify chip's MAC and return session keys
        MAC = getMAC(getDataForMac(publicKey.getEncoded(false)), keyMAC);
        if (!Hex.toHexString(response, 4, 8).equals(Hex.toHexString(MAC))) {
            throw new RuntimeException("Could not verify chip's MAC."); // *Should* never happen.
        }
        return new byte[][]{keyEnc, keyMAC};

    }

    /**
     * Encrypt/Decrypt data with the agreed key
     *
     * @param data - data to be encrypted/decrypted
     * @param mode - encrypt or decrypt?
     * @return
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws BadPaddingException
     * @throws IllegalBlockSizeException
     * @throws InvalidAlgorithmParameterException
     */
    private byte[] encryptDecryptData(byte[] data, int mode) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyEnc, "AES");
        @SuppressLint("GetInstance") Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        byte[] iv = Arrays.copyOf(cipher.doFinal(ssc), 16);
        cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(mode, secretKeySpec, new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    /**
     * Pad data to the block-size with 8000*
     *
     * @param data
     * @param blockSize
     * @return
     */
    private byte[] pad(byte[] data, int blockSize) {
        byte[] result = new byte[data.length + (blockSize - data.length % blockSize)];
        System.arraycopy(data, 0, result, 0, data.length);
        result[data.length] = (byte) 0x80;
        return result;
    }

    /**
     * Unpad data that has been padded to the block size by 8000*
     *
     * @param data
     * @return
     */
    private byte[] unpad(byte[] data) {
        for (int i = data.length - 1; i >= 0; i--) {
            if (data[i] == (byte)0x80) {
                byte[] ret = new byte[i];
                System.arraycopy(data, 0, ret, 0,i);
                return ret;
            }
        }

        return data;
    }

    /**
     * For Debugging - bouncy does not survive nulls
     * @param data
     * @return
     */
    private String toHexString(byte[] data) {
        if (data == null) {
            return "NULL";
        }
        return Hex.toHexString(data);
    }

    /**
     * Encrypt C-APDU and protect with MAC
     *
     * @param cla
     * @param ins
     * @param p1
     * @param p2
     * @param data
     * @param le
     * @return
     * @throws GeneralSecurityException
     */
    public byte[] encryptAndMac(int cla, int ins, int p1, int p2, byte[] data, Integer le) throws GeneralSecurityException {
        Timber.log(Log.DEBUG, "C-APDU to encrypt: 0x%02X 0x%02X 0x%02X 0x%02X %s %s", (byte)cla, (byte)ins, (byte)p1, (byte)p2, toHexString(data), le);

        incrementSSC(ssc);

        byte[] maskedHeader = new byte[] { (byte)((byte)cla | 0x0C), (byte) ins, (byte) p1, (byte) p2 };

        byte[] do8587 = new byte[]{};

        if (data != null && data.length > 0) {
            byte[] paddedData;
            paddedData = pad(data, 16);
            byte[] dataEncrypted = encryptDecryptData(paddedData, Cipher.ENCRYPT_MODE);
            if (ins % 2 == 0) {
                do8587 = concat(new byte[]{(byte) 0x87, (byte) (dataEncrypted.length + 1), 0x01}, dataEncrypted);
            } else {
                do8587 = concat(new byte[]{(byte) 0x85, (byte) dataEncrypted.length}, dataEncrypted);
            }
        }

        byte[] do97 = new byte[]{};
        if (le != null) {
            do97 = new byte[]{(byte)0x97, 0x01, le.byteValue()};
        }

        byte[] paddedMaskedHeader = pad(maskedHeader, 16);
        byte[] macData = concat(ssc, paddedMaskedHeader, do8587, do97);
        byte[] paddedMacData = pad(macData, 16);
        byte[] do8e = concat(new byte[] {(byte)0x8E, 0x08}, getMAC(paddedMacData, keyMAC));

        byte newLength = 0;
        newLength += do8587.length;
        newLength += do97.length;
        newLength += do8e.length;

        byte[] result = concat(
                maskedHeader,
                new byte[] {newLength},
                do8587,
                do97,
                do8e,
                new byte[] {0x00}
        );
        incrementSSC(ssc);
        Timber.log(Log.DEBUG, "Encrypted C-APDU: %s", toHexString(result));
        return result;
    }

    /**
     * Validate R-APDU, check MAC and decrypt data if present. Only return data.
     *
     * @param response
     * @return
     * @throws GeneralSecurityException
     * @throws SmartCardReaderException
     */
    public byte[] decryptAndVerify(byte[] response) throws GeneralSecurityException, SmartCardReaderException {

        Timber.log(Log.DEBUG, "Encrypted R-APDU: %s", Hex.toHexString(response));

        // result shall be decrypted data or empty
        byte[] result = new byte[]{};

        int currentByte = 0;
        if ((response[currentByte] == (byte)0x87) || (response[currentByte] == (byte)0x85)) {

            boolean skip87header = (response[currentByte] == (byte)0x87);

            currentByte += 1;

            int size = response[currentByte] & 0xFF;
            Timber.log(Log.DEBUG, "Encrypted data size %d ", size);
            currentByte += 1;

            if (size > 0x80) {
                int sizeLen = size & 0x0F;
                byte[] sizeBytes = new byte[sizeLen];
                System.arraycopy(response, currentByte, sizeBytes, 0, sizeLen);
                size = new BigInteger(1, sizeBytes).intValue();
                Timber.log(Log.DEBUG, "size bytes %d, size %d", sizeLen, size);
                currentByte += sizeLen;
            }

            if (skip87header) {
                if (response[currentByte] != (byte) 0x01) {
                    throw new SmartCardReaderException("Invalid encryption header");
                }
                currentByte += 1; // skip encryption header
                size -= 1;
            }

            result = encryptDecryptData(Arrays.copyOfRange(response, currentByte, currentByte + size), Cipher.DECRYPT_MODE);
            currentByte += size;
        }

        if (response[currentByte] == (byte)0x99) {
            if (!Hex.toHexString(response, currentByte, 4).equals("99029000")) {
                throw new SmartCardReaderException("Invalid status");
            }
            currentByte += 4;
        }

        int macStart = currentByte;
        if (response[currentByte] != (byte)0x8E) {
            Timber.log(Log.DEBUG, "0x%02X, %d", response[currentByte], currentByte);
            throw new SmartCardReaderException("Missing MAC");
        }
        currentByte += 1;

        if (response[currentByte] != (byte)0x08) {
            throw new SmartCardReaderException("Unsupported MAC length");
        }
        currentByte += 1;

        byte[] cardMac = Arrays.copyOfRange(response, currentByte, currentByte + 8);
        currentByte += 8;

        byte[] rdata = Arrays.copyOfRange(response, 0, macStart);
        byte[] macData = pad(concat(ssc, rdata), 16);
        byte[] ourMac = getMAC(macData, keyMAC);
        Timber.log(Log.DEBUG, "Card MAC: %s, our MAC: %s", Hex.toHexString(cardMac), Hex.toHexString(ourMac));

        if (!Hex.toHexString(cardMac).equals(Hex.toHexString(ourMac))) {
            throw new RuntimeException("Could not verify chip's MAC.");
        }

        if (response.length - currentByte != 2) {
            throw new SmartCardReaderException("Malformed R-APDU");
        }

        Timber.log(Log.DEBUG, "Decrypted data: %s", Hex.toHexString(result));
        return unpad(result);
    }

    /**
     * Increment send sequence counter
     * @param ssc
     */
    public static void incrementSSC(byte[] ssc) {
        for (int i = ssc.length - 1; i >= 0; i--) {
            ssc[i]++;
            if (ssc[i] != 0) {
                break;
            }
        }
    }

}
