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

import android.util.SparseArray;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil;

class LatviaPersonalDataParser {
    private static final String TAG = LatviaPersonalDataParser.class.getName();
    private static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("dd MM yyyy")
            .toFormatter();

    // VERIFY WITH CARD: record positions assumed same as Estonian IDEMIA
    private static final int SURNAME_POS = 1;
    private static final int GIVEN_NAMES_POS = 2;
    private static final int GENDER_POS = 3;
    private static final int CITIZENSHIP_POS = 4;
    private static final int DATE_AND_PLACE_OF_BIRTH_POS = 5;
    private static final int PERSONAL_CODE_POS = 6;
    private static final int DOCUMENT_NUMBER_POS = 7;
    private static final int EXPIRY_DATE_POS = 8;

    private LatviaPersonalDataParser() {}

    static PersonalData parse(SparseArray<String> data) {
        String surname = data.get(SURNAME_POS);
        String givenNames = data.get(GIVEN_NAMES_POS);
        String citizenship = data.get(CITIZENSHIP_POS);
        String dateAndPlaceOfBirthString = data.get(DATE_AND_PLACE_OF_BIRTH_POS);
        String personalCode = data.get(PERSONAL_CODE_POS);
        String documentNumber = data.get(DOCUMENT_NUMBER_POS);
        String expiryDateString = data.get(EXPIRY_DATE_POS);

        LocalDate dateOfBirth = parseDateOfBirth(personalCode, dateAndPlaceOfBirthString);
        LocalDate expiryDate = parseDate(expiryDateString);

        return PersonalData.create(surname, givenNames, citizenship, dateOfBirth,
                personalCode, documentNumber, expiryDate, CardType.LATVIA);
    }

    /**
     * Parse date of birth from Latvian personal code or from the card's DOB record.
     *
     * Old format (pre-2017): DDMMYY-CZZZQ where C is century digit (0=19xx, 1=20xx, 2=21xx)
     * New format (from 2017): starts with "32", DOB not encoded — fall back to card record.
     */
    private static LocalDate parseDateOfBirth(String personalCode, String dateAndPlaceOfBirthString) {
        if (personalCode != null && !personalCode.isEmpty()) {
            try {
                String codeDigits = personalCode.replace("-", "");
                if (!isNewFormatCode(codeDigits) && codeDigits.length() >= 7) {
                    return parseDateOfBirthFromOldCode(codeDigits);
                }
            } catch (Exception e) {
                LoggingUtil.Companion.errorLog(TAG,
                        String.format("Could not parse DOB from personal code %s", personalCode), e);
            }
        }

        // New format code or parsing failed — fall back to date record from card
        if (dateAndPlaceOfBirthString != null && !dateAndPlaceOfBirthString.isEmpty()) {
            return parseDateFromRecord(dateAndPlaceOfBirthString);
        }

        LoggingUtil.Companion.errorLog(TAG, "Could not determine date of birth", null);
        return null;
    }

    /**
     * Old Latvian personal code: DDMMYYCZZZQ (digits only, dash stripped)
     * Chars 0-1: day, 2-3: month, 4-5: year (2 digits), 6: century (0=19xx, 1=20xx, 2=21xx)
     */
    private static LocalDate parseDateOfBirthFromOldCode(String codeDigits) {
        int day = Integer.parseInt(codeDigits.substring(0, 2));
        int month = Integer.parseInt(codeDigits.substring(2, 4));
        int yearShort = Integer.parseInt(codeDigits.substring(4, 6));
        int centuryDigit = Character.getNumericValue(codeDigits.charAt(6));

        int century = switch (centuryDigit) {
            case 0 -> 1800;
            case 1 -> 1900;
            case 2 -> 2000;
            default -> throw new IllegalArgumentException(
                    "Invalid century digit in Latvian personal code: " + centuryDigit);
        };

        return LocalDate.of(century + yearShort, month, day);
    }

    /**
     * New format codes start with "32" and do not encode date of birth.
     */
    private static boolean isNewFormatCode(String codeDigits) {
        return codeDigits.length() >= 2 && codeDigits.startsWith("32");
    }

    /**
     * Parse date from the date and place of birth record (format: "dd MM yyyy" + place suffix).
     */
    private static LocalDate parseDateFromRecord(String dateAndPlaceOfBirthString) {
        try {
            // Record may contain place of birth after the date — strip trailing non-date chars
            // Date format is "dd MM yyyy" (10 chars), place follows after that
            String dateString = dateAndPlaceOfBirthString.length() > 10
                    ? dateAndPlaceOfBirthString.substring(0, dateAndPlaceOfBirthString.length() - 4)
                    : dateAndPlaceOfBirthString;
            return LocalDate.parse(dateString, DATE_FORMAT);
        } catch (Exception e) {
            LoggingUtil.Companion.errorLog(TAG,
                    String.format("Could not parse date from record %s", dateAndPlaceOfBirthString), e);
            return null;
        }
    }

    private static LocalDate parseDate(String dateString) {
        try {
            return LocalDate.parse(dateString, DATE_FORMAT);
        } catch (Exception e) {
            LoggingUtil.Companion.errorLog(TAG,
                    String.format("Could not parse date %s", dateString), e);
            return null;
        }
    }
}
