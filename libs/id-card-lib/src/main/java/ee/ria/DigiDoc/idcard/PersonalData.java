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

    /**
     * Cardholder's actual citizenship as read from the on-card personal-data
     * file. Populated for cards that expose this field (Estonian IDEMIA /
     * Thales). Empty string for cards that don't expose authoritative
     * citizenship over NFC (e.g. Latvian IDEMIA — see {@link #issuingCountry()}).
     */
    public abstract String citizenship();

    /**
     * ISO 3166-1 alpha-2 country code of the certificate issuer (cert subject
     * RDN {@code C}). Populated for cards where this is parsed during
     * personalData() — currently Latvian IDEMIA. Null for cards that don't
     * read the cert as part of personal-data extraction. Note: this is the
     * issuing country, NOT the cardholder's citizenship; in practice they
     * coincide for citizen-issued eID, but a foreign resident's card would
     * still carry the issuer's country code here.
     */
    @Nullable public abstract String issuingCountry();

    @Nullable public abstract LocalDate dateOfBirth();

    public abstract String personalCode();

    public abstract String documentNumber();

    /**
     * Expiry date of the physical document (printed on the card), read from the
     * personal-data EF. Populated for cards that expose this field — Estonian
     * IDEMIA / Thales. Null for cards that don't (e.g. Latvian IDEMIA, which
     * does not expose document expiry over NFC).
     */
    @Nullable public abstract LocalDate documentExpiryDate();

    /**
     * Expiry date of the on-card authentication certificate ({@code notAfter}).
     * Populated for cards where this is parsed during personalData() — currently
     * Latvian IDEMIA. Null for cards that don't read the cert as part of
     * personal-data extraction (Estonian IDEMIA / Thales). Note that the cert
     * expiry is typically shorter than the document expiry.
     */
    @Nullable public abstract LocalDate certExpiryDate();

    public abstract CardType cardType();

    static PersonalData create(String surname, String givenNames, String citizenship,
                               @Nullable String issuingCountry, @Nullable LocalDate dateOfBirth,
                               String personalCode, String documentNumber,
                               @Nullable LocalDate documentExpiryDate,
                               @Nullable LocalDate certExpiryDate, CardType cardType) {
        return new AutoValue_PersonalData(surname, givenNames, citizenship, issuingCountry,
                dateOfBirth, personalCode, documentNumber, documentExpiryDate, certExpiryDate,
                cardType);
    }
}
