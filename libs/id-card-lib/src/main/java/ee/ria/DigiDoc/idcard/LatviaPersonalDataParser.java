// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-FileCopyrightText: 2026 Stagnation OÜ
// SPDX-License-Identifier: LGPL-2.1-or-later

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
            LoggingUtil.Companion.errorLog(TAG,
                "Could not parse DOB from personal code " + personalCode, e);
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
