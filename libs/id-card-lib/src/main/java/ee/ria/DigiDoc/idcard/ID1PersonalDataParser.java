/*
 * Copyright 2017 - 2024 Riigi Infosüsteemi Amet
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

import android.util.Log;
import android.util.SparseArray;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;

import timber.log.Timber;

class ID1PersonalDataParser {

    private static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("dd MM yyyy")
            .toFormatter();
    private static final int SURNAME_POS = 1;
    private static final int GIVEN_NAMES_POS = 2;
    private static final int GENDER_POS = 3;
    private static final int CITIZENSHIP_POS = 4;
    private static final int DATE_AND_PLACE_OF_BIRTH_POS = 5;
    private static final int PERSONAL_CODE_POS = 6;
    private static final int DOCUMENT_NUMBER_POS = 7;
    private static final int EXPIRY_DATE_POS = 8;

    private ID1PersonalDataParser() {}

    static PersonalData parse(SparseArray<String> data) {
        String surname = data.get(SURNAME_POS);
        String givenNames = data.get(GIVEN_NAMES_POS);
        String citizenship = data.get(CITIZENSHIP_POS);
        String dateAndPlaceOfBirthString = data.get(DATE_AND_PLACE_OF_BIRTH_POS);
        String personalCode = data.get(PERSONAL_CODE_POS);
        String documentNumber = data.get(DOCUMENT_NUMBER_POS);
        String expiryDateString = data.get(EXPIRY_DATE_POS);

        if (data.get(GENDER_POS).isEmpty() && !personalCode.isEmpty()) {
            data.set(GENDER_POS, parseDigiIdGender(personalCode));
        }

        LocalDate dateOfBirth;

        if (!dateAndPlaceOfBirthString.isEmpty()) {
            dateOfBirth = parseDateOfBirth(dateAndPlaceOfBirthString);
        } else if (!personalCode.isEmpty()) {
            dateOfBirth = parseDigiIdDateOfBirth(personalCode);
        } else {
            throw new IllegalArgumentException("Personal code not found");
        }

        LocalDate expiryDate = parseExpiryDate(expiryDateString);

        return PersonalData.create(surname, givenNames, citizenship, dateOfBirth,
                personalCode, documentNumber, expiryDate);
    }

    private static LocalDate parseExpiryDate(String expiryDateString) {
        try {
            return LocalDate.parse(expiryDateString, DATE_FORMAT);
        } catch (Exception e) {
            Timber.log(Log.ERROR, e, "Could not parse expiry date %s", expiryDateString);
            return null;
        }
    }

    private static LocalDate parseDateOfBirth(String dateAndPlaceOfBirthString) {
        if (dateAndPlaceOfBirthString == null) {
            Timber.log(Log.ERROR, "Could not parse date of birth: no data");
            return null;
        }
        try {
            String dateOfBirthString = dateAndPlaceOfBirthString
                    .substring(0, dateAndPlaceOfBirthString.length() - 4);

            return LocalDate.parse(dateOfBirthString, DATE_FORMAT);
        } catch (Exception e) {
            Timber.log(Log.ERROR, e, "Could not parse date of birth %s", dateAndPlaceOfBirthString);
            return null;
        }
    }

    private static String parseDigiIdGender(String personalCode) {
        int genderNumber = Character.getNumericValue(personalCode.charAt(0));
        List<Integer> males = List.of(1, 3, 5, 7);
        List<Integer> females = List.of(2, 4, 6, 8);

        if (males.contains(genderNumber)) {
            return "M";
        } else if (females.contains(genderNumber)) {
            return "F";
        }

        throw new IllegalArgumentException("Invalid personal code");
    }

    private static LocalDate parseDigiIdDateOfBirth(String personalCode) {
        try {
//            return DateOfBirthUtil.parseDateOfBirth(personalCode);
            return null;
        } catch (DateTimeException e) {
            Timber.log(Log.ERROR, "Invalid personal code birth of date", e);
            throw new IllegalArgumentException("Invalid personal code");
        }
    }
}
