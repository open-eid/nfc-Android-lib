// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import java.time.DateTimeException;
import java.time.LocalDate;

public class DateOfBirthUtil {

    public static LocalDate parseDateOfBirth(String personalCode) throws DateTimeException {
        int firstNumber = Character.getNumericValue(personalCode.charAt(0));

        int century = switch (firstNumber) {
            case 1, 2 -> 1800;
            case 3, 4 -> 1900;
            case 5, 6 -> 2000;
            case 7, 8 -> 2100;
            default -> throw new IllegalArgumentException("Invalid personal code");
        };

        int year = Integer.parseInt(personalCode.substring(1, 3)) + century;
        int month = Integer.parseInt(personalCode.substring(3, 5));
        int day = Integer.parseInt(personalCode.substring(5, 7));

        return LocalDate.of(year, month, day);
    }
}
