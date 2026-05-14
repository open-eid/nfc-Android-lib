package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import ee.ria.DigiDoc.smartcardreader.ApduResponseException;
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;

import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDate;
import java.util.Date;

/**
 * End-to-end test of {@link LatviaIdemiaWithPace#personalData()} against
 * a synthetic auth certificate and a mocked card. Confirms the surname /
 * given-name / personal-code / document-number / issuing-country
 * extraction from the cert subject, and that the date-of-birth is derived
 * from the personal code rather than read off the card.
 *
 * <p>Builds the cert fresh in each test via BouncyCastle so there is no
 * fixture file to keep in sync. Any change to the cert subject parsing
 * (e.g. switching SERIALNUMBER → CN, dropping a field, changing the
 * personal-code source) breaks this test loudly.
 */
public final class LatviaIdemiaPersonalDataTest {

    static {
        // BC may not be registered as a JCE provider in the test JVM — the
        // signer builder uses BC's content signer factory, which needs the
        // provider available. Safe to install once, harmless if already there.
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
    }

    @Test
    public void personalData_oldFormatCode_parsesAllFieldsFromCertSubject() throws Exception {
        String surname = "Berzins";
        String givenName = "Janis";
        String personalCode = "150385-12345"; // 1985-03-15 DOB
        String documentNumber = "LV1234567";
        String country = "LV";

        X509Certificate cert = buildCert(surname, givenName, "PNOLV-" + personalCode,
                documentNumber, country,
                LocalDate.of(2030, 5, 1)); // notAfter

        NfcSmartCardReader reader = stubReaderFor(personalCode, cert.getEncoded());
        LatviaIdemiaWithPace token = new LatviaIdemiaWithPace(reader);

        PersonalData pd = token.personalData();

        assertThat(pd.surname()).isEqualTo(surname);
        assertThat(pd.givenNames()).isEqualTo(givenName);
        assertThat(pd.personalCode()).isEqualTo(personalCode);
        assertThat(pd.documentNumber()).isEqualTo("PNOLV-" + personalCode);
        // citizenship is intentionally empty for LV (not exposed over NFC)
        assertThat(pd.citizenship()).isEmpty();
        assertThat(pd.issuingCountry()).isEqualTo(country);
        assertThat(pd.dateOfBirth()).isEqualTo(LocalDate.of(1985, 3, 15));
        assertThat(pd.certExpiryDate()).isEqualTo(LocalDate.of(2030, 5, 1));
        // documentExpiryDate is intentionally null for LV (not on card)
        assertThat(pd.documentExpiryDate()).isNull();
        assertThat(pd.cardType()).isEqualTo(CardType.LATVIA_IDEMIA);
    }

    @Test
    public void personalData_newFormatCode_dobIsNull() throws Exception {
        // New-format LV personal code starts with "32"; DOB is not encoded.
        String personalCode = "321234-56789";
        X509Certificate cert = buildCert("Smith", "John", "PNOLV-" + personalCode,
                "LV9999999", "LV", LocalDate.of(2030, 1, 1));

        NfcSmartCardReader reader = stubReaderFor(personalCode, cert.getEncoded());
        LatviaIdemiaWithPace token = new LatviaIdemiaWithPace(reader);

        PersonalData pd = token.personalData();

        assertThat(pd.personalCode()).isEqualTo(personalCode);
        assertThat(pd.dateOfBirth()).isNull();
    }

    @Test
    public void personalData_emptyCountryRdn_issuingCountryIsNull() throws Exception {
        // If the cert subject doesn't carry C, issuingCountry must be null
        // (not the empty string) — that's the contract callers branch on.
        String personalCode = "150385-12345";
        X509Certificate cert = buildCert("Doe", "Jane", "PNOLV-" + personalCode,
                "LV0000001", /*country*/ null, LocalDate.of(2030, 1, 1));

        NfcSmartCardReader reader = stubReaderFor(personalCode, cert.getEncoded());
        LatviaIdemiaWithPace token = new LatviaIdemiaWithPace(reader);

        PersonalData pd = token.personalData();
        assertThat(pd.issuingCountry()).isNull();
    }

    @Test
    public void personalData_personalCodeWithTrailingFF_stripsCardOsPadding() throws Exception {
        // CardOS pads fixed-size EFs with 0xFF; the parser must strip these
        // before UTF-8 decoding — String.trim() doesn't (it's whitespace only).
        String personalCode = "150385-12345";
        X509Certificate cert = buildCert("X", "Y", "PNOLV-" + personalCode,
                "LV0000002", "LV", LocalDate.of(2030, 1, 1));

        // Build the EF payload with the personal code followed by 0xFF padding.
        byte[] codeBytes = personalCode.getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[codeBytes.length + 4];
        System.arraycopy(codeBytes, 0, padded, 0, codeBytes.length);
        for (int i = codeBytes.length; i < padded.length; i++) {
            padded[i] = (byte) 0xFF;
        }

        NfcSmartCardReader reader = stubReaderForRawBytes(padded, cert.getEncoded());
        LatviaIdemiaWithPace token = new LatviaIdemiaWithPace(reader);

        PersonalData pd = token.personalData();
        assertThat(pd.personalCode()).isEqualTo(personalCode);
    }

    // -------- helpers --------

    /**
     * Build a minimal self-signed X.509 cert with surname / givenName /
     * serialNumber / C in the subject DN. Issuer is the same DN (self-signed).
     */
    private static X509Certificate buildCert(String surname, String givenName,
                                             String serialNumberRdn, String documentNumber,
                                             String countryOrNull, LocalDate notAfter)
            throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();

        X500NameBuilder nb = new X500NameBuilder(BCStyle.INSTANCE);
        nb.addRDN(BCStyle.SURNAME, surname);
        nb.addRDN(BCStyle.GIVENNAME, givenName);
        nb.addRDN(BCStyle.SERIALNUMBER, serialNumberRdn);
        if (countryOrNull != null) {
            nb.addRDN(BCStyle.C, countryOrNull);
        }
        // documentNumber isn't part of the LV cert subject in the production
        // parser (it actually uses SERIALNUMBER for documentNumber too) — so
        // ignore the documentNumber parameter for the DN itself. Keeping the
        // signature for caller clarity.
        @SuppressWarnings("unused") String unusedDoc = documentNumber;

        Date from = new Date(System.currentTimeMillis() - 86_400_000L);
        Date to = Date.from(notAfter.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());

        X509v3CertificateBuilder cb = new JcaX509v3CertificateBuilder(
                nb.build(), BigInteger.ONE, from, to, nb.build(), kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA")
                .setProvider("BC").build(kp.getPrivate());
        return new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(cb.build(signer));
    }

    /** Stub a reader that returns the given personal code on EF read and the cert DER on cert read. */
    private static NfcSmartCardReader stubReaderFor(String personalCode, byte[] certDer) {
        return stubReaderForRawBytes(personalCode.getBytes(StandardCharsets.UTF_8), certDer);
    }

    /**
     * Stub a reader that responds to the small set of APDUs personalData()
     * issues. Tracks selected EF to disambiguate the two READ BINARY contexts
     * (personal-code file vs auth certificate).
     */
    private static NfcSmartCardReader stubReaderForRawBytes(byte[] efPayload, byte[] certDer) {
        NfcSmartCardReader reader = mock(NfcSmartCardReader.class,
                withSettings().mockMaker(MockMakers.SUBCLASS));

        final boolean[] inCertContext = {false};
        final int[] certReadCallNo = {0};

        try {
            when(reader.transmit(anyInt(), anyInt(), anyInt(), anyInt(), any(), any()))
                    .thenAnswer(inv -> {
                        int cla = ((int) inv.getArgument(0)) & 0xFF;
                        int ins = ((int) inv.getArgument(1)) & 0xFF;
                        int p1 = ((int) inv.getArgument(2)) & 0xFF;
                        int p2 = ((int) inv.getArgument(3)) & 0xFF;
                        byte[] data = inv.getArgument(4);

                        // SELECT family (A4): A4 04 0C = AID, A4 01 0C = DF, A4 02 0C = EF,
                        // A4 09 0C = path-based file ref (used for certs).
                        if (cla == 0x00 && ins == 0xA4) {
                            // P1 = 0x09 path is how certificate() locates the auth cert.
                            inCertContext[0] = (p1 == 0x09);
                            return new byte[0];
                        }
                        // READ BINARY: branch on which file we last selected.
                        if (cla == 0x00 && ins == 0xB0) {
                            if (inCertContext[0]) {
                                certReadCallNo[0]++;
                                if (certReadCallNo[0] == 1) {
                                    return certDer;
                                }
                                throw new ApduResponseException((byte) 0x6B, (byte) 0x00);
                            }
                            return efPayload;
                        }
                        throw new AssertionError(String.format(
                                "unexpected APDU CLA=%02X INS=%02X P1=%02X P2=%02X",
                                cla, ins, p1, p2));
                    });
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return reader;
    }
}
