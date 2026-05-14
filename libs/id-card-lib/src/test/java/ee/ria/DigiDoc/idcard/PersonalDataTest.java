package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

/**
 * Locks the contract of the deprecated {@link PersonalData#expiryDate()}
 * shim: prefer document expiry, fall back to cert expiry, null if neither.
 * This shim exists so pre-LV callers keep observing the same value after
 * the schema was split.
 */
public final class PersonalDataTest {

    @Test
    public void expiryDate_prefersDocumentExpiryWhenPresent() {
        LocalDate doc = LocalDate.of(2030, 5, 1);
        LocalDate cert = LocalDate.of(2027, 3, 15);
        PersonalData pd = PersonalData.create(
                "Surname", "Given Names", "EST", null,
                LocalDate.of(1990, 1, 1), "39001011234", "AB1234567",
                /*documentExpiryDate*/ doc, /*certExpiryDate*/ cert,
                CardType.ID1);

        assertThat(pd.expiryDate()).isEqualTo(doc);
    }

    @Test
    public void expiryDate_fallsBackToCertExpiryWhenNoDocumentExpiry() {
        // Latvian path: documentExpiry is null, certExpiry carries notAfter.
        LocalDate cert = LocalDate.of(2027, 3, 15);
        PersonalData pd = PersonalData.create(
                "Berzins", "Janis", "", "LV",
                LocalDate.of(1985, 3, 15), "150385-12345", "LV1234567",
                /*documentExpiryDate*/ null, /*certExpiryDate*/ cert,
                CardType.LATVIA_IDEMIA);

        assertThat(pd.expiryDate()).isEqualTo(cert);
    }

    @Test
    public void expiryDate_returnsNullWhenNeitherPresent() {
        PersonalData pd = PersonalData.create(
                "X", "Y", "EST", null,
                LocalDate.of(1990, 1, 1), "00000000000", "XX000000",
                null, null, CardType.ID1);

        assertThat(pd.expiryDate()).isNull();
    }

    @Test
    public void create_preservesAllFields() {
        // Round-trip every accessor — locks the schema so a future field
        // rename or argument-order change in create() fails here.
        LocalDate dob = LocalDate.of(1985, 3, 15);
        LocalDate doc = LocalDate.of(2030, 5, 1);
        LocalDate cert = LocalDate.of(2027, 3, 15);
        PersonalData pd = PersonalData.create(
                "Tamm", "Mari", "EST", "LV",
                dob, "48503150000", "AB1234567",
                doc, cert, CardType.LATVIA_IDEMIA);

        assertThat(pd.surname()).isEqualTo("Tamm");
        assertThat(pd.givenNames()).isEqualTo("Mari");
        assertThat(pd.citizenship()).isEqualTo("EST");
        assertThat(pd.issuingCountry()).isEqualTo("LV");
        assertThat(pd.dateOfBirth()).isEqualTo(dob);
        assertThat(pd.personalCode()).isEqualTo("48503150000");
        assertThat(pd.documentNumber()).isEqualTo("AB1234567");
        assertThat(pd.documentExpiryDate()).isEqualTo(doc);
        assertThat(pd.certExpiryDate()).isEqualTo(cert);
        assertThat(pd.cardType()).isEqualTo(CardType.LATVIA_IDEMIA);
    }
}
