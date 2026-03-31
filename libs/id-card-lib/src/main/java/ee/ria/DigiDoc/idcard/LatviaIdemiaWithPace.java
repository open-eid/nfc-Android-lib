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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;

import javax.security.auth.x500.X500Principal;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil;

/**
 * Latvian eID card NFC token.
 * <p>
 * Extends IdemiaWithPace since the Latvian eID uses the same IDEMIA ID-One Cosmo v8
 * platform (IAS-ECC Oberthur) as the Estonian card. PACE, SM, certificate, and PIN
 * operations are inherited.
 * <p>
 * Overrides personalData() for Latvian personal code parsing, provides AID selection
 * fallback, and reads key references from the card's PrKDF for authentication/signing
 * since the Latvian card uses different key slot assignments.
 */
class LatviaIdemiaWithPace extends IdemiaWithPace {
    private static final String TAG = LatviaIdemiaWithPace.class.getName();

    private static final byte ECC_AUTH_ALGO = 0x04;
    private static final byte ECC_SIGN_ALGO = 0x54;

    LatviaIdemiaWithPace(NfcSmartCardReader reader) {
        super(reader);
        authKeyRef = (byte) 0x82;
        signKeyRef = (byte) 0x9E;
    }

    /**
     * Read personal data from the auth certificate subject and EF 0x5001 (personal code).
     *
     * Latvian eID cards store only the personal code in EF files (DF 0x5000 / EF 0x5001).
     * Name, citizenship, and document number are extracted from the auth certificate subject:
     *   - OID 2.5.4.4  (surname)
     *   - OID 2.5.4.42 (givenName)
     *   - OID 2.5.4.5  (serialNumber) — format "PNOLV-{personalCode}"
     *   - C (2.5.4.6)  (citizenship)
     * Expiry date comes from the certificate's notAfter.
     */
    @Override
    public PersonalData personalData() throws SmartCardReaderException {
        // Read personal code from EF 0x5001
        selectMainAid();
        reader.transmit(0x00, 0xA4, 0x01, 0x0C, new byte[]{0x50, 0x00}, null);
        reader.transmit(0x00, 0xA4, 0x02, 0x0C, new byte[]{0x50, 0x01}, null);
        byte[] record = reader.transmit(0x00, 0xB0, 0x00, 0x00, null, 0x00);
        String personalCode = new String(record, StandardCharsets.UTF_8).trim();
        LoggingUtil.Companion.debugLog(TAG,
            "EF 0x5001 personal code: " + personalCode, null);

        // Parse auth certificate for remaining fields
        try {
            byte[] certBytes = certificate(CertificateType.AUTHENTICATION);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate x509 = (X509Certificate)
                cf.generateCertificate(new ByteArrayInputStream(certBytes));

            String subjectDn = x509.getSubjectX500Principal().getName(X500Principal.RFC2253);
            LoggingUtil.Companion.debugLog(TAG,
                "Auth cert subject DN: " + subjectDn, null);

            String surname = extractOid(subjectDn, "2.5.4.4");
            String givenName = extractOid(subjectDn, "2.5.4.42");
            String citizenship = extractOid(subjectDn, "C");
            if (citizenship.isEmpty()) {
                citizenship = extractOid(subjectDn, "2.5.4.6");
            }
            String serialNumber = extractOid(subjectDn, "2.5.4.5");
            LoggingUtil.Companion.debugLog(TAG,
                "Extracted: surname=" + surname + ", givenName=" + givenName
                    + ", citizenship=" + citizenship + ", serialNumber=" + serialNumber, null);

            // Document number: not available separately, use cert serialNumber field
            String documentNumber = serialNumber.isEmpty() ? "" : serialNumber;

            LocalDate expiryDate = x509.getNotAfter().toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDate();

            LocalDate dateOfBirth = LatviaPersonalDataParser.parseDateOfBirth(personalCode);
            LoggingUtil.Companion.debugLog(TAG,
                "Parsed: documentNumber=" + documentNumber + ", expiryDate=" + expiryDate
                    + ", dateOfBirth=" + dateOfBirth, null);

            return PersonalData.create(surname, givenName, citizenship, dateOfBirth,
                personalCode, documentNumber, expiryDate, CardType.LATVIA);
        } catch (SmartCardReaderException e) {
            throw e;
        } catch (Exception e) {
            throw new SmartCardReaderException("Failed to parse auth certificate", e);
        }
    }

    /**
     * Extract a value from an RFC2253 DN string by OID.
     * Handles both plain text values and hex-encoded (#-prefixed) UTF8String values.
     */
    private static String extractOid(String dn, String oid) {
        String prefix = oid + "=";
        int start = dn.indexOf(prefix);
        while (start >= 0) {
            if (start == 0 || dn.charAt(start - 1) == ',' || dn.charAt(start - 1) == ' ') {
                break;
            }
            start = dn.indexOf(prefix, start + 1);
        }
        if (start < 0) {
            return "";
        }
        start += prefix.length();
        if (start < dn.length() && dn.charAt(start) == '#') {
            // Hex-encoded DER value: skip tag+length bytes, decode the rest as UTF-8
            int end = dn.indexOf(',', start);
            String hex = (end < 0) ? dn.substring(start + 1) : dn.substring(start + 1, end);
            // DER: first byte = tag (0c = UTF8String), second byte = length, rest = value
            if (hex.length() >= 4) {
                try {
                    byte[] der = hexToBytes(hex);
                    int valueOffset = 2; // tag + 1-byte length
                    if (der.length > 2 && (der[1] & 0xFF) == 0x81) {
                        valueOffset = 3;
                    }
                    return new String(der, valueOffset, der.length - valueOffset, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return hex;
                }
            }
            return "";
        }
        int end = dn.indexOf(',', start);
        return (end < 0) ? dn.substring(start) : dn.substring(start, end);
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    @Override
    public byte[] authenticate(byte[] pin1, byte[] token) throws SmartCardReaderException {
        selectOberthurAid();
        verifyCode(CodeType.PIN1, pin1);
        byte keyRef = getAuthKeyRef();

        LoggingUtil.Companion.debugLog(TAG,
            String.format("MSE SET auth: algo=0x%02x, keyRef=0x%02x", ECC_AUTH_ALGO & 0xFF, keyRef & 0xFF),
            null
        );

        reader.transmit(0x00, 0x22, 0x41, 0xA4,
                new byte[] {(byte) 0x80, 0x01, ECC_AUTH_ALGO, (byte) 0x84, 0x01, keyRef}, null);
        return reader.transmit(0x00, 0x88, 0x00, 0x00, token, 0x00);
    }

    @Override
    public byte[] calculateSignature(byte[] pin2, byte[] hash, boolean ecc) throws SmartCardReaderException {
        selectQSCDAid();
        verifyCode(CodeType.PIN2, pin2);
        byte keyRef = getSignKeyRef();

        LoggingUtil.Companion.debugLog(TAG,
            String.format("MSE SET sign: algo=0x%02x, keyRef=0x%02x", ECC_SIGN_ALGO & 0xFF, keyRef & 0xFF),
            null
        );

        reader.transmit(0x00, 0x22, 0x41, 0xB6,
                new byte[] {(byte) 0x80, 0x01, ECC_SIGN_ALGO, (byte) 0x84, 0x01, keyRef}, null);
        return reader.transmit(0x00, 0x2A, 0x9E, 0x9A, padWithZeroes(hash), 0x00);
    }

    @Override
    public byte[] decrypt(byte[] pin1, byte[] data, boolean ecc) throws SmartCardReaderException {
        selectOberthurAid();
        verifyCode(CodeType.PIN1, pin1);
        byte keyRef = getAuthKeyRef();

        LoggingUtil.Companion.debugLog(TAG,
            String.format("MSE SET decrypt: algo=0x%02x, keyRef=0x%02x", ECC_AUTH_ALGO & 0xFF, keyRef & 0xFF),
            null
        );

        reader.transmit(0x00, 0x22, 0x41, 0xB8,
                new byte[] {(byte) 0x80, 0x01, ECC_AUTH_ALGO, (byte) 0x84, 0x01, keyRef}, null);
        return reader.transmit(0x00, 0x2A, 0x80, 0x86, concat(new byte[] {0x00}, data), 0x00);
    }

}
