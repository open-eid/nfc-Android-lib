// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-FileCopyrightText: 2026 Stagnation OÜ
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import static org.bouncycastle.util.Arrays.concatenate;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

    private static final byte[] EF_OD = {0x50, 0x31};
    private static final int TAG_CERTIFICATE_DIRECTORY = 0xA4;
    private static final int TAG_PRIVATE_KEY_DIRECTORY = 0xA0;
    private static final int TAG_PRIVATE_EC_KEY = 0xA0;

    private static final byte[] ADF1 = {(byte) 0xAD, (byte) 0xF1};
    private static final byte[] ADF2 = {(byte) 0xAD, (byte) 0xF2};

    private final Map<CertificateType, byte[]> certificateFiles =
            new EnumMap<>(CertificateType.class);
    private final Map<CertificateType, Byte> keyReferences =
            new EnumMap<>(CertificateType.class);

    LatviaIdemiaWithPace(NfcSmartCardReader reader) {
        super(reader);
    }

    @Override
    public CardType cardType() {
        return CardType.LATVIA_IDEMIA;
    }

    /**
     * LV uses 1-byte algorithm identifiers in the MSE algorithm-reference DO,
     * vs the 4-byte {@code FF xx xx xx} form used by Estonian IDEMIA:
     * <ul>
     *   <li>{@code 0x04} — ECC auth / decrypt</li>
     *   <li>{@code 0x54} — ECC sign</li>
     * </ul>
     */
    @Override
    protected byte[] authMseTemplate() {
        return new byte[] {(byte) 0x80, 0x01, 0x04}; // ECC_AUTH_ALGO
    }

    @Override
    protected byte[] signMseTemplate() {
        return new byte[] {(byte) 0x80, 0x01, 0x54}; // ECC_SIGN_ALGO
    }

    @Override
    protected byte[] decryptMseTemplate() {
        return new byte[] {(byte) 0x80, 0x01, 0x04}; // ECC_AUTH_ALGO
    }

    /**
     * Read personal data from the auth certificate subject and EF 0x5001 (personal code).
     * Latvian eID cards store only the personal code in EF files (DF 0x5000 / EF 0x5001).
     * Name, issuing country, and document number are extracted from the auth certificate subject:
     *   - OID 2.5.4.4  (surname)
     *   - OID 2.5.4.42 (givenName)
     *   - OID 2.5.4.5  (serialNumber) — format "PNOLV-{personalCode}"
     *   - C (2.5.4.6)  — issuing country of the certificate authority. Mapped
     *     to {@link PersonalData#issuingCountry()}, NOT to citizenship: the
     *     value is the CA's country (always "LV" here), and a foreign resident
     *     could in principle hold an LV-issued card. Citizenship is left empty
     *     because the LV card does not expose it over NFC.
     * Cert expiry comes from the certificate's notAfter; document expiry is
     * not exposed by Latvian IDEMIA cards over NFC, so it is left null.
     */
    @Override
    public PersonalData personalData() throws SmartCardReaderException {
        // Read personal code from EF 0x5001
        selectMainAid();
        reader.transmit(0x00, 0xA4, 0x01, 0x0C, new byte[]{0x50, 0x00}, null);
        reader.transmit(0x00, 0xA4, 0x02, 0x0C, new byte[]{0x50, 0x01}, null);
        byte[] record = reader.transmit(0x00, 0xB0, 0x00, 0x00, null, 0x00);
        // Strip trailing 0xFF (CardOS unused-space marker on fixed-size EFs)
        // before UTF-8 decoding — String.trim() only strips ASCII whitespace.
        int len = record.length;
        while (len > 0 && record[len - 1] == (byte) 0xFF) {
            len--;
        }
        String personalCode = new String(record, 0, len, StandardCharsets.UTF_8).trim();

        // Parse auth certificate for remaining fields
        try {
            byte[] certBytes = certificate(CertificateType.AUTHENTICATION);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate x509 = (X509Certificate)
                cf.generateCertificate(new ByteArrayInputStream(certBytes));

            X500Name subject = X500Name.getInstance(
                x509.getSubjectX500Principal().getEncoded());

            String surname = rdnString(subject, BCStyle.SURNAME);
            String givenName = rdnString(subject, BCStyle.GIVENNAME);
            String issuingCountryRaw = rdnString(subject, BCStyle.C);
            String issuingCountry = issuingCountryRaw.isEmpty() ? null : issuingCountryRaw;
            String serialNumber = rdnString(subject, BCStyle.SERIALNUMBER);

            // X.509 notAfter is a UTC instant — interpret in UTC so the displayed
            // date is the same regardless of device timezone, matching openssl
            // and other PKI-tooling conventions.
            LocalDate certExpiryDate = x509.getNotAfter().toInstant()
                .atZone(ZoneOffset.UTC).toLocalDate();

            LocalDate dateOfBirth = LatviaPersonalDataParser.parseDateOfBirth(personalCode);
            LoggingUtil.Companion.debugLog(TAG, "Latvian personal data parsed", null);

            return PersonalData.create(surname, givenName, "", issuingCountry, dateOfBirth,
                personalCode, serialNumber, null, certExpiryDate, CardType.LATVIA_IDEMIA);
        } catch (SmartCardReaderException e) {
            // NFC / SM / card-status errors from certificate(): propagate with
            // their original message and stack so the cause is visible upstream.
            throw e;
        } catch (CertificateException e) {
            // Specific message when DER parsing actually fails — distinct from
            // every-other-failure case below.
            throw new SmartCardReaderException("Failed to parse auth certificate", e);
        } catch (Exception e) {
            // Catch-all for anything else (BC IllegalArgumentException, NPE, etc.)
            // so nothing escapes uncaught onto the NFC binder thread. The throwable
            // class name in the message keeps logs readable.
            throw new SmartCardReaderException(
                "Unexpected error reading personal data: " + e.getClass().getSimpleName(), e);
        }
    }

    /**
     * Extract a single RDN value from an X.500 subject by OID, returning "" when absent.
     * Delegates to BouncyCastle so UTF-8 strings, multi-valued RDNs, escaped commas, and
     * tag/length variations of DirectoryString are handled correctly.
     */
    private static String rdnString(X500Name name, ASN1ObjectIdentifier oid) {
        RDN[] rdns = name.getRDNs(oid);
        if (rdns == null || rdns.length == 0) {
            return "";
        }
        return IETFUtils.valueToString(rdns[0].getFirst().getValue());
    }

    @Override
    protected byte[] certificateFile(CertificateType type) throws SmartCardReaderException {
        byte[] cached = certificateFiles.get(type);
        if (cached != null) {
            return cached;
        }

        byte[] certificateDirectory = readDirectory(type, TAG_CERTIFICATE_DIRECTORY, "EF.CD");
        byte[] file = descriptorValue(certificateDirectory,
                new int[] {0x30, 0xA1, 0x30, 0x30, 0x04}, "EF.CD");
        if (file.length != 2) {
            String message = "EF.CD: certificate reference is "
                    + file.length + " bytes, expected 2";
            LoggingUtil.Companion.errorLog(TAG, message, null);
            throw new SmartCardReaderException(message);
        }

        byte[] path = concatenate(type == CertificateType.SIGNING ? ADF2 : ADF1, file);
        certificateFiles.put(type, path);
        return path;
    }

    @Override
    protected byte getAuthKeyRef() throws SmartCardReaderException {
        return keyReference(CertificateType.AUTHENTICATION);
    }

    @Override
    protected byte getSignKeyRef() throws SmartCardReaderException {
        return keyReference(CertificateType.SIGNING);
    }

    private byte keyReference(CertificateType type) throws SmartCardReaderException {
        Byte cached = keyReferences.get(type);
        if (cached != null) {
            return cached;
        }

        byte[] privateKeyDirectory = readDirectory(type, TAG_PRIVATE_KEY_DIRECTORY, "EF.PrKD");
        List<TLV> keys = TLV.parseAll(privateKeyDirectory);
        if (keys.isEmpty()) {
            String message = "EF.PrKD: no private key record";
            LoggingUtil.Companion.errorLog(TAG, message, null);
            throw new SmartCardReaderException(message);
        }
        TLV key = keys.get(0);
        if (key.getTag() != TAG_PRIVATE_EC_KEY) {
            String message = "EF.PrKD: private key is not an EC key";
            LoggingUtil.Companion.errorLog(TAG, message, null);
            throw new NotSupportedException(message);
        }

        byte reference = extractKeyReference(key);
        if (reference == 0) {
            reference = defaultKeyReference(type);
        }
        keyReferences.put(type, reference);
        return reference;
    }

    private byte[] readDirectory(CertificateType type, int tag, String file)
            throws SmartCardReaderException {
        if (type == CertificateType.SIGNING) {
            selectQSCDAid();
        } else {
            selectOberthurAid();
        }
        byte[] objects = readEf(EF_OD);
        return readEf(directoryReference(objects, tag, file));
    }

    private static byte defaultKeyReference(CertificateType type) {
        return type == CertificateType.SIGNING ? (byte) 0x9E : (byte) 0x82;
    }

    private static byte[] directoryReference(byte[] objectDirectory, int tag, String file)
            throws SmartCardReaderException {
        byte[] reference = descriptorValue(objectDirectory, new int[] {tag, 0x30, 0x04}, "EF.OD");
        if (reference.length != 2) {
            String message = "EF.OD: " + file + " reference is "
                    + reference.length + " bytes, expected 2";
            LoggingUtil.Companion.errorLog(TAG, message, null);
            throw new SmartCardReaderException(message);
        }
        return reference;
    }

    private byte[] readEf(byte[] fileId) throws SmartCardReaderException {
        reader.transmit(0x00, 0xA4, 0x02, 0x0C, fileId, null);
        return readBinaryFile();
    }

    private static byte[] descriptorValue(byte[] data, int[] path, String file)
            throws SmartCardReaderException {
        List<TLV> records = TLV.parseAll(data);
        TLV record = null;
        for (int tag : path) {
            record = record == null ? TLV.findByTag(records, tag) : record.findByTag(tag);
            if (record == null) {
                String message = String.format("%s: missing PKCS#15 tag 0x%02X", file, tag);
                LoggingUtil.Companion.errorLog(TAG, message, null);
                throw new SmartCardReaderException(message);
            }
        }
        return record.getValue();
    }

    private static byte extractKeyReference(TLV entry) {
        if (entry.children == null || entry.children.size() < 2) {
            return 0;
        }

        TLV commonKeyAttributes = entry.children.get(1);
        if (commonKeyAttributes.children == null) {
            return 0;
        }

        for (TLV child : commonKeyAttributes.children) {
            if (child.getTag() == 0x02) {
                byte[] value = child.getValue();
                if (value.length == 2) {
                    return value[1];
                } else if (value.length == 1) {
                    return value[0];
                }
            }
        }
        return 0;
    }
}
