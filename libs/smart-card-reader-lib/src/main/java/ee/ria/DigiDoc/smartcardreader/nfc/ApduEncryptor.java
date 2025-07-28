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

package ee.ria.DigiDoc.smartcardreader.nfc;

import java.security.GeneralSecurityException;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

/**
 * ApduEncryptor interface is used by NfcSmartCardReader to encrypt C-APDUs
 * and decrypting R-APDUs in the context of Secure Messaging
 */
public interface ApduEncryptor {

    /**
     * encryptAndMac function takes plaintext C-APDU as input, it is expected to
     * apply encryption and MAC calculation and to provide a encrypted C-APDU as
     * byte array as output
     *
     * @param cla - APDU class
     * @param ins - INS instruction code
     * @param p1 - parameter P1
     * @param p2 - parameter P2
     * @param data - optional data
     * @param le - optional LE
     * @return - encrypted C-APDU according to Secure Messaging specification
     * @throws GeneralSecurityException
     */
    byte[] encryptAndMac(int cla, int ins, int p1, int p2, byte[] data, Integer le) throws GeneralSecurityException;

    /**
     * decryptAndVerify takes encrypted R-APDU as input it is expected to validate the
     * R-APDU, MAC, and yield the plaintext data as output
     *
     * @param response - complete encrypted R-APDU
     * @return - byte array with decrypted data or empty
     * @throws GeneralSecurityException
     * @throws SmartCardReaderException
     */
    byte[] decryptAndVerify(byte[] response) throws GeneralSecurityException, SmartCardReaderException;
}
