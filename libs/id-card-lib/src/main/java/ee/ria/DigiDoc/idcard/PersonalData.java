// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;

/**
 * Personal data file contents.
 *
 * @param citizenship Cardholder's actual citizenship as read from the on-card personal-data
 *                    file. Populated for cards that expose this field (Estonian IDEMIA /
 *                    Thales). Empty string for cards that don't expose authoritative
 *                    citizenship over NFC (e.g. Latvian IDEMIA — see {@link #issuingCountry()}).
 * @param issuingCountry ISO 3166-1 alpha-2 country code of the certificate issuer (cert subject
 *                       RDN {@code C}). Populated for cards where this is parsed during
 *                       personalData() — currently Latvian IDEMIA. Null for cards that don't
 *                       read the cert as part of personal-data extraction. Note: this is the
 *                       issuing country, NOT the cardholder's citizenship; in practice they
 *                       coincide for citizen-issued eID, but a foreign resident's card would
 *                       still carry the issuer's country code here.
 * @param documentExpiryDate Expiry date of the physical document (printed on the card), read from
 *                           the personal-data EF. Populated for cards that expose this field —
 *                           Estonian IDEMIA / Thales. Null for cards that don't (e.g. Latvian
 *                           IDEMIA, which does not expose document expiry over NFC).
 * @param certExpiryDate Expiry date of the on-card authentication certificate ({@code notAfter}).
 *                       Populated for cards where this is parsed during personalData() —
 *                       currently Latvian IDEMIA. Null for cards that don't read the cert as
 *                       part of personal-data extraction (Estonian IDEMIA / Thales). Note that
 *                       the cert expiry is typically shorter than the document expiry.
 */
public record PersonalData(
        @NonNull String surname,
        @NonNull String givenNames,
        @NonNull String citizenship,
        @Nullable String issuingCountry,
        @Nullable LocalDate dateOfBirth,
        @NonNull String personalCode,
        @NonNull String documentNumber,
        @Nullable LocalDate documentExpiryDate,
        @Nullable LocalDate certExpiryDate,
        @NonNull CardType cardType) {

    static PersonalData create(@NonNull String surname, @NonNull String givenNames,
                               @NonNull String citizenship, @Nullable String issuingCountry,
                               @Nullable LocalDate dateOfBirth, @NonNull String personalCode,
                               @NonNull String documentNumber,
                               @Nullable LocalDate documentExpiryDate,
                               @Nullable LocalDate certExpiryDate, @NonNull CardType cardType) {
        return new PersonalData(surname, givenNames, citizenship, issuingCountry, dateOfBirth,
                personalCode, documentNumber, documentExpiryDate, certExpiryDate, cardType);
    }
}
