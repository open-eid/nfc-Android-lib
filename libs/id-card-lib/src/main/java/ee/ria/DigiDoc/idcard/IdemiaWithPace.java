// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-FileCopyrightText: 2026 Stagnation OÜ
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import static org.bouncycastle.util.Arrays.concatenate;

import org.bouncycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import ee.ria.DigiDoc.smartcardreader.ApduResponseException;
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil;

/**
 * IdemiaWithPace extends Idemia APDU protocol with Secure Messaging and PACE capabilities
 * enabling the use over NFC
 * @noinspection FieldCanBeLocal, SameParameterValue
 */
class IdemiaWithPace extends Idemia implements TokenWithPace {
    private static final String TAG = IdemiaWithPace.class.getName();

    private static final int PACE_NONCE_BYTES = 32;

    /**
     * NfcSmartCardReader to provide the encryption/decryption for
     */
    private final NfcSmartCardReader nfcReader;

    protected Byte authKeyRef = (byte) 0x81;
    protected Byte signKeyRef = (byte) 0x9F;

    /**
     * Initialize ID1 token with NfcSmartCardReader
     * @param reader
     */
    IdemiaWithPace(NfcSmartCardReader reader) {
        super(reader);
        nfcReader = reader;
    }

    /**
     * Start PACE key-exchange with CAN
     * @param can
     * @throws SmartCardReaderException
     */
    public void tunnel(String can) throws SmartCardReaderException {
        PaceTunnel.open(nfcReader, () -> {
            selectMainAid();
            return readPaceProfileFromCard();
        }, can);
    }

    /**
     * We have to override the main AID selection from the base class, since over NFC the APDU
     * with P2 = 0x00 does not yield a positive output. Therefore we have to use APDU, where
     * P2=0x0C. Although unfortunate - this kind of difference in behaviour over NFC vs. over
     * wire can be expected
     * @throws SmartCardReaderException
     */
    protected void selectMainAid() throws SmartCardReaderException {
        byte[] data = new byte[] {
                (byte) 0xA0, 0x00, 0x00, 0x00, 0x77, 0x01, 0x08, 0x00,
                0x07, 0x00, 0x00, (byte) 0xFE, 0x00, 0x00, 0x01, 0x00};
        reader.transmit(0x00, 0xA4, 0x04, 0x0C, data, null);
    }

    /**
     * Read PACE domain parameter and EC curve from EF.CardAccess.
     * Accessible before PACE, in plaintext.
     */
    private PaceTunnel.Profile readPaceProfileFromCard() throws SmartCardReaderException {
        reader.transmit(0x00, 0xA4, 0x02, 0x0C, new byte[]{0x01, 0x1C}, null);
        byte[] cardAccess = readBinaryFile();
        byte paramId = parsePaceParameterId(cardAccess);

        if (paramId == 0) {
            throw new SmartCardReaderException("Failed to read PACE parameters from EF.CardAccess");
        }

        String curveName = domainParamToCurveName(paramId);

        if (curveName == null) {
            throw new SmartCardReaderException(
                "Unsupported PACE domain parameter: 0x" + Integer.toHexString(paramId & 0xFF)
            );
        }

        LoggingUtil.Companion.debugLog(TAG,
            String.format("PACE parameters read from EF.CardAccess: paramId=0x%02X, curve=%s", paramId, curveName),
            null
        );

        byte[] mseAuthenticationTemplate = new byte[] {
                (byte) 0x80, 0x0A, 0x04, 0x00, 0x7F, 0x00, 0x07, 0x02,
                0x02, 0x04, 0x02, 0x04, (byte) 0x83, 0x01, 0x02, (byte) 0x84, 0x01, paramId};

        return new PaceTunnel.Profile(curveName, PACE_NONCE_BYTES, 0x00, mseAuthenticationTemplate);
    }

    /**
     * MSE algorithm-reference template (DO 80) for the auth / sign / decrypt
     * MSE SET commands. Defaults match Estonian IDEMIA cards (4-byte algo IDs);
     * subclasses override to swap in card-specific identifiers. The
     * {@code 84 01 <keyRef>} key-reference DO is appended by the caller.
     */
    protected byte[] authMseTemplate() {
        return new byte[] {(byte) 0x80, 0x04, (byte) 0xFF, 0x20, 0x08, 0x00};
    }

    protected byte[] signMseTemplate() {
        return new byte[] {(byte) 0x80, 0x04, (byte) 0xFF, 0x15, 0x08, 0x00};
    }

    protected byte[] decryptMseTemplate() {
        return new byte[] {(byte) 0x80, 0x04, (byte) 0xFF, 0x30, 0x04, 0x00};
    }

    @Override
    public byte[] authenticate(byte[] pin1, byte[] token) throws SmartCardReaderException {
        selectOberthurAid();
        verifyCode(CodeType.PIN1, pin1);
        byte keyRef = getAuthKeyRef();
        byte[] template = authMseTemplate();
        byte[] body = concatenate(template, new byte[] {(byte) 0x84, 0x01, keyRef});

        LoggingUtil.Companion.debugLog(TAG,
            String.format("MSE SET auth: template=%s, keyRef=0x%02x",
                Hex.toHexString(template), keyRef & 0xFF),
            null
        );

        reader.transmit(0x00, 0x22, 0x41, 0xA4, body, null);
        return reader.transmit(0x00, 0x88, 0x00, 0x00, token, 0x00);
    }

    @Override
    public byte[] calculateSignature(byte[] pin2, byte[] hash, boolean ecc) throws SmartCardReaderException {
        selectQSCDAid();
        verifyCode(CodeType.PIN2, pin2);
        byte keyRef = getSignKeyRef();
        byte[] template = signMseTemplate();
        byte[] body = concatenate(template, new byte[] {(byte) 0x84, 0x01, keyRef});

        LoggingUtil.Companion.debugLog(TAG,
            String.format("MSE SET sign: template=%s, keyRef=0x%02x",
                Hex.toHexString(template), keyRef & 0xFF),
            null
        );

        reader.transmit(0x00, 0x22, 0x41, 0xB6, body, null);
        return reader.transmit(0x00, 0x2A, 0x9E, 0x9A, padWithZeroes(hash), 0x00);
    }

    @Override
    public byte[] decrypt(byte[] pin1, byte[] data, boolean ecc) throws SmartCardReaderException {
        selectOberthurAid();
        verifyCode(CodeType.PIN1, pin1);
        byte keyRef = getAuthKeyRef();
        byte[] template = decryptMseTemplate();
        byte[] body = concatenate(template, new byte[] {(byte) 0x84, 0x01, keyRef});

        LoggingUtil.Companion.debugLog(TAG,
            String.format("MSE SET decrypt: template=%s, keyRef=0x%02x",
                Hex.toHexString(template), keyRef & 0xFF),
            null
        );

        reader.transmit(0x00, 0x22, 0x41, 0xB8, body, null);
        return reader.transmit(0x00, 0x2A, 0x80, 0x86, concatenate(new byte[] {0x00}, data), 0x00);
    }

    /**
     * Get the authentication key reference. If null, attempts dynamic discovery from PrKDF.
     * Cached after first read.
     */
    protected byte getAuthKeyRef() throws SmartCardReaderException {
        if (authKeyRef == null) {
            byte ref = readKeyRefFromCurrentContext();
            if (ref != 0) {
                authKeyRef = ref;
                LoggingUtil.Companion.debugLog(TAG,
                    String.format("PrKDF auth key ref discovered: 0x%02x", authKeyRef & 0xFF),
                    null
                );
            } else {
                throw new SmartCardReaderException("Auth key reference not found in PrKDF");
            }
        }

        return authKeyRef;
    }

    /**
     * Get the signing key reference. If null, attempts dynamic discovery from PrKDF.
     * Cached after first read.
     */
    protected byte getSignKeyRef() throws SmartCardReaderException {
        if (signKeyRef == null) {
            byte ref = readKeyRefFromCurrentContext();
            if (ref != 0) {
                signKeyRef = ref;
                LoggingUtil.Companion.debugLog(TAG,
                    String.format("PrKDF sign key ref discovered: 0x%02x", signKeyRef & 0xFF),
                    null
                );
            } else {
                throw new SmartCardReaderException("Sign key reference not found in PrKDF");
            }
        }

        return signKeyRef;
    }

    /**
     * Read the first private key reference from the PrKDF in the currently selected AID context.
     *
     * Flow (matching Web eID libelectronic-id):
     * 1. Select and read EF_OD (0x5031) — Object Directory File
     * 2. Find tag 0xA0 (private key dir ref) → extract PrKDF file ID
     * 3. Read PrKDF file
     * 4. Return first keyReference INTEGER found
     *
     * @return key reference byte, or 0 if not found
     */
    private byte readKeyRefFromCurrentContext() throws SmartCardReaderException {
        // Read EF_OD (Object Directory File at 0x5031)
        reader.transmit(0x00, 0xA4, 0x02, 0x0C, new byte[] {0x50, 0x31}, null);
        byte[] efOd = readBinaryFile();

        // Find tag 0xA0 (private key directory reference)
        List<TLV> odEntries = TLV.parseAll(efOd);
        TLV privKeyDir = TLV.findByTag(odEntries, 0xA0);
        if (privKeyDir == null || privKeyDir.children == null) {
            return 0;
        }

        // Navigate: A0 → 30 → 04 to get PrKDF file ID
        TLV seq = privKeyDir.findByTag(0x30);
        if (seq == null) {
            return 0;
        }
        TLV fileIdTlv = seq.findByTag(0x04);
        if (fileIdTlv == null || fileIdTlv.getValue().length != 2) {
            return 0;
        }

        // Select and read PrKDF
        byte[] fileId = fileIdTlv.getValue();
        reader.transmit(0x00, 0xA4, 0x02, 0x0C, fileId, null);
        byte[] prKdfData = readBinaryFile();

        // Parse PrKDF entries and return first key reference found
        List<TLV> keyEntries = TLV.parseAll(prKdfData);
        for (TLV entry : keyEntries) {
            if (entry.children == null || entry.children.size() < 2) {
                continue;
            }
            byte keyRef = extractKeyReference(entry);
            if (keyRef != 0) {
                return keyRef;
            }
        }
        return 0;
    }

    /**
     * Extract key reference from a PKCS#15 PrivateKeyType entry.
     *
     * Structure: SEQUENCE { CommonObjectAttributes, CommonKeyAttributes, TypeAttributes }
     * CommonKeyAttributes contains: iD, usage, ..., keyReference (INTEGER)
     *
     * In DER, key refs >= 0x80 are encoded as 2-byte INTEGERs (e.g., 02 02 00 82).
     */
    protected static byte extractKeyReference(TLV entry) {
        if (entry.children == null || entry.children.size() < 2) {
            return 0;
        }

        // Second child SEQUENCE = CommonKeyAttributes
        TLV commonKeyAttrs = entry.children.get(1);
        if (commonKeyAttrs.children == null) {
            return 0;
        }

        // Find INTEGER (tag 0x02) — this is the keyReference
        for (TLV child : commonKeyAttrs.children) {
            if (child.getTag() == 0x02) {
                byte[] val = child.getValue();
                if (val.length == 2) {
                    // 2-byte DER INTEGER (e.g., 00 82 → key ref 0x82)
                    return val[1];
                } else if (val.length == 1) {
                    return val[0];
                }
            }
        }
        return 0;
    }

    /**
     * Read a binary file from the currently selected EF.
     */
    protected byte[] readBinaryFile() throws SmartCardReaderException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        while (true) {
            try {
                stream.write(reader.transmit(0x00, 0xB0, stream.size() >> 8, stream.size(), null, 0x00));
            } catch (ApduResponseException e) {
                if (e.sw1 == 0x6B && e.sw2 == 0x00) {
                    break; // offset out of range = end of file
                } else if (e.sw1 == 0x6A && e.sw2 == (byte) 0x82) {
                    break; // file not found
                } else {
                    throw e;
                }
            } catch (IOException e) {
                throw new SmartCardReaderException(e);
            }
        }
        return stream.toByteArray();
    }

    /**
     * Parse PACEInfo parameterId from EF.CardAccess.
     *
     * EF.CardAccess is ASN.1: SET OF SecurityInfo
     * PACEInfo ::= SEQUENCE { OID protocol, INTEGER version, INTEGER parameterId }
     *
     * Multiple SecurityInfo entries may share the { OID, INTEGER, INTEGER } shape
     * (e.g. ChipAuthenticationInfo); only the entry whose protocol OID matches
     * id-PACE-ECDH-GM-AES-CBC-CMAC-256 (the mechanism we use in MSE SET AT) is
     * a valid source for parameterId.
     *
     * @return parameterId byte, or 0 if not found
     */
    private static final byte[] PACE_PROTOCOL_OID = {
            0x04, 0x00, 0x7F, 0x00, 0x07, 0x02, 0x02, 0x04, 0x02, 0x04
    };

    private static byte parsePaceParameterId(byte[] cardAccess) {
        List<TLV> entries = TLV.parseAll(cardAccess);
        for (TLV entry : entries) {
            if (entry.children == null) {
                continue;
            }
            // SET contains SEQUENCEs (SecurityInfo entries)
            for (TLV child : entry.children) {
                byte param = readPaceParamFromSecurityInfo(child);
                if (param != 0) {
                    return param;
                }
            }
        }
        // Also check top-level SEQUENCEs (if no outer SET wrapper)
        for (TLV entry : entries) {
            byte param = readPaceParamFromSecurityInfo(entry);
            if (param != 0) {
                return param;
            }
        }
        return 0;
    }

    private static byte readPaceParamFromSecurityInfo(TLV securityInfo) {
        if (securityInfo.children == null || securityInfo.children.size() < 3) {
            return 0;
        }
        TLV oidTlv = securityInfo.children.get(0);
        if (oidTlv.getTag() != 0x06 || !Arrays.equals(oidTlv.getValue(), PACE_PROTOCOL_OID)) {
            return 0;
        }
        TLV paramTlv = securityInfo.children.get(2);
        if (paramTlv.getTag() == 0x02 && paramTlv.getValue().length == 1) {
            return paramTlv.getValue()[0];
        }
        return 0;
    }

    /**
     * Map a PACE parameterId to a JCA curve name.
     *
     * <p>Only 256-bit curves are wired up: every BER prefix and response-header
     * validator in {@link #establishPace} hard-codes 65-byte point lengths
     * (`0x41`) and a 79-byte CMAC input (`0x4F`). 384-bit support would
     * require derive-from-{@code pointBytes} encoding throughout, plus a card
     * to test against — neither is in scope here.
     *
     * <p>For 384-bit parameterIds (`0x0F` `secp384r1`, `0x10`
     * `brainpoolP384r1`), this returns {@code null}; the caller surfaces the
     * canonical "Unsupported PACE domain parameter" error.
     */
    private static String domainParamToCurveName(byte paramId) {
        return switch (paramId & 0xFF) {
            case 0x0C -> "secp256r1";
            case 0x0D -> "brainpoolP256r1";
            default -> null;
        };
    }

}
