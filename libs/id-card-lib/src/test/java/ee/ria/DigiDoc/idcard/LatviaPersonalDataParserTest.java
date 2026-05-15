package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

/**
 * The Latvian personal-code → DOB mapping is the one piece of LV-specific
 * derivation that has no test on real cards (DOB comes from the code itself,
 * not from any EF). Lock its semantics down here.
 */
public final class LatviaPersonalDataParserTest {

    @Test
    public void parseDateOfBirth_oldFormat_century19xx() {
        // Old format: DDMMYY-CZZZQ, century digit 1 → 19xx
        LocalDate dob = LatviaPersonalDataParser.parseDateOfBirth("150385-12345");
        assertThat(dob).isEqualTo(LocalDate.of(1985, 3, 15));
    }

    @Test
    public void parseDateOfBirth_oldFormat_century20xx() {
        LocalDate dob = LatviaPersonalDataParser.parseDateOfBirth("010100-21234");
        assertThat(dob).isEqualTo(LocalDate.of(2000, 1, 1));
    }

    @Test
    public void parseDateOfBirth_oldFormat_century18xx() {
        LocalDate dob = LatviaPersonalDataParser.parseDateOfBirth("310199-01234");
        assertThat(dob).isEqualTo(LocalDate.of(1899, 1, 31));
    }

    @Test
    public void parseDateOfBirth_withoutDash_alsoParses() {
        // The parser strips dashes before slicing.
        LocalDate dob = LatviaPersonalDataParser.parseDateOfBirth("15038512345");
        assertThat(dob).isEqualTo(LocalDate.of(1985, 3, 15));
    }

    @Test
    public void parseDateOfBirth_newFormat_returnsNull() {
        // PMLP spec: 1st digit "3", 2nd digit "2"-"9". DOB not encoded.
        assertThat(LatviaPersonalDataParser.parseDateOfBirth("321234-56789")).isNull();
        assertThat(LatviaPersonalDataParser.parseDateOfBirth("32123456789")).isNull();
        // Whole 3[2-9] range is reserved for the updated format.
        assertThat(LatviaPersonalDataParser.parseDateOfBirth("331234-56789")).isNull();
        assertThat(LatviaPersonalDataParser.parseDateOfBirth("391234-56789")).isNull();
        // Demo-card code observed in the field also resolves to null (no DOB encoded).
        assertThat(LatviaPersonalDataParser.parseDateOfBirth("326305-17052")).isNull();
    }

    @Test
    public void parseDateOfBirth_unparseablePrefix_returnsNull() {
        // 4X-9X aren't valid old-format days (DD<=31) and aren't updated-format
        // prefixes (PMLP reserves 3[2-9]) — legacy parser fails, surfaces as null.
        assertThat(LatviaPersonalDataParser.parseDateOfBirth("411234-56789")).isNull();
        assertThat(LatviaPersonalDataParser.parseDateOfBirth("991234-56789")).isNull();
    }

    @Test
    public void parseDateOfBirth_nullOrEmpty_returnsNull() {
        assertThat(LatviaPersonalDataParser.parseDateOfBirth(null)).isNull();
        assertThat(LatviaPersonalDataParser.parseDateOfBirth("")).isNull();
    }

    @Test
    public void parseDateOfBirth_tooShort_returnsNull() {
        assertThat(LatviaPersonalDataParser.parseDateOfBirth("12345")).isNull();
    }

    @Test
    public void parseDateOfBirth_invalidMonth_returnsNull() {
        // Month 13 — LocalDate.of throws, parser catches and returns null.
        assertThat(LatviaPersonalDataParser.parseDateOfBirth("011385-12345")).isNull();
    }

    @Test
    public void parseDateOfBirth_invalidCenturyDigit_returnsNull() {
        // Century digit 5 → not in {0, 1, 2} → parser throws internally → null.
        assertThat(LatviaPersonalDataParser.parseDateOfBirth("010100-51234")).isNull();
    }
}
