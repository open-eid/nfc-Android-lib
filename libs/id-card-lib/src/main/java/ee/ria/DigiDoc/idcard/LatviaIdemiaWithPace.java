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

    LatviaIdemiaWithPace(NfcSmartCardReader reader) {
        super(reader);
        authKeyRef = (byte) 0x82;
        signKeyRef = (byte) 0x9E;
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

            // No PII in logs — names / personal code / document number stay
            // out per project convention. Cert expiry isn't identifying on
            // its own and is useful for triaging "card expired" reports.
            LoggingUtil.Companion.debugLog(TAG,
                "LV personal data parsed, certExpiry=" + certExpiryDate, null);

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

}
