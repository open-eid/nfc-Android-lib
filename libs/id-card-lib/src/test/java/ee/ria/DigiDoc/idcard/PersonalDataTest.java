package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

/**
 * Pins the {@link PersonalData} schema: every accessor returns what
 * {@link PersonalData#create} was given. Catches accidental field
 * renames or argument-order changes in the factory.
 */
public final class PersonalDataTest {

    @Test
    public void create_preservesAllFields() {
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

    @Test
    public void create_acceptsNullableFieldsAsNull() {
        // EE / Thales path: documentExpiryDate populated, certExpiryDate / issuingCountry null.
        PersonalData pd = PersonalData.create(
                "X", "Y", "EST", null,
                LocalDate.of(1990, 1, 1), "00000000000", "XX000000",
                /*documentExpiryDate*/ LocalDate.of(2030, 1, 1),
                /*certExpiryDate*/ null,
                CardType.ID1);

        assertThat(pd.issuingCountry()).isNull();
        assertThat(pd.certExpiryDate()).isNull();
        assertThat(pd.documentExpiryDate()).isEqualTo(LocalDate.of(2030, 1, 1));
    }
}
