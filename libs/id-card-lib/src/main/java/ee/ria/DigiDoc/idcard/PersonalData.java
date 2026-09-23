// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;

/**
 * Personal data file contents.
 */
public record PersonalData(
        @NonNull String surname,
        @NonNull String givenNames,
        @NonNull String citizenship,
        @Nullable LocalDate dateOfBirth,
        @NonNull String personalCode,
        @NonNull String documentNumber,
        @Nullable LocalDate expiryDate,
        @NonNull CardType cardType) {

    static PersonalData create(@NonNull String surname, @NonNull String givenNames,
                               @NonNull String citizenship, @Nullable LocalDate dateOfBirth,
                               @NonNull String personalCode, @NonNull String documentNumber,
                               @Nullable LocalDate expiryDate, @NonNull CardType cardType) {
        return new PersonalData(surname, givenNames, citizenship, dateOfBirth,
                personalCode, documentNumber, expiryDate, cardType);
    }
}
