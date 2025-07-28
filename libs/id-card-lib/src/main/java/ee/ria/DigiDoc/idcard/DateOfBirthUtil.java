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
