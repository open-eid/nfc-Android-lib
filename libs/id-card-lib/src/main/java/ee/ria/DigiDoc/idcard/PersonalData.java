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

    static PersonalData create(String surname, String givenNames, String citizenship,
                               @Nullable LocalDate dateOfBirth, String personalCode,
                               String documentNumber, @Nullable LocalDate expiryDate) {
        return new AutoValue_PersonalData(surname, givenNames, citizenship, dateOfBirth,
                personalCode, documentNumber, expiryDate);
    }
}
