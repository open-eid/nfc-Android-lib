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

import java.time.LocalDate;

import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil;

class LatviaPersonalDataParser {
    private static final String TAG = LatviaPersonalDataParser.class.getName();

    private LatviaPersonalDataParser() {}

    /**
     * Parse date of birth from Latvian personal code.
     *
     * Old format (pre-2017): DDMMYY-CZZZQ where C is century digit (0=18xx, 1=19xx, 2=20xx)
     * New format (from 2017): starts with "32", DOB not encoded — returns null.
     */
    static LocalDate parseDateOfBirth(String personalCode) {
        if (personalCode == null || personalCode.isEmpty()) {
            return null;
        }
        try {
            String codeDigits = personalCode.replace("-", "");
            if (isNewFormatCode(codeDigits) || codeDigits.length() < 7) {
                return null;
            }
            return parseDateOfBirthFromOldCode(codeDigits);
        } catch (Exception e) {
            // Personal code is PII — never log the value itself, only that
            // parsing failed. The exception trace is enough for triage.
            LoggingUtil.Companion.errorLog(TAG, "Could not parse DOB from personal code", e);
            return null;
        }
    }

    /**
     * Old Latvian personal code: DDMMYYCZZZQ (digits only, dash stripped)
     * Chars 0-1: day, 2-3: month, 4-5: year (2 digits), 6: century (0=18xx, 1=19xx, 2=20xx)
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
}
