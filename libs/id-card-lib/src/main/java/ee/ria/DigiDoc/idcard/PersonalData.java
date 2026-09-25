// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

import java.time.LocalDate;

/**
 * Personal data file contents.
 */
@AutoValue
public abstract class PersonalData {

    public abstract String surname();

    public abstract String givenNames();

    public abstract String citizenship();

    @Nullable public abstract LocalDate dateOfBirth();

    public abstract String personalCode();

    public abstract String documentNumber();

    @Nullable public abstract LocalDate expiryDate();

    public abstract CardType cardType();

    static PersonalData create(String surname, String givenNames, String citizenship,
                               @Nullable LocalDate dateOfBirth, String personalCode,
                               String documentNumber, @Nullable LocalDate expiryDate, CardType cardType) {
        return new AutoValue_PersonalData(surname, givenNames, citizenship, dateOfBirth,
                personalCode, documentNumber, expiryDate, cardType);
    }
}
