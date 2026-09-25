// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

/**
 * EstEID token interface.
 */
public interface Token {

    /**
     * Read personal information of the cardholder.
     *
     * @return Personal data of the cardholder.
     * @throws SmartCardReaderException When reading failed.
     */
    PersonalData personalData() throws SmartCardReaderException;

    /**
     * Change PIN1/PIN2/PUK code.
     *
     * @param type Code type.
     * @param currentCode Current code.
     * @param newCode New code.
     * @throws SmartCardReaderException When changing failed.
     * @throws CodeVerificationException When current code is wrong.
     */
    void changeCode(CodeType type, byte[] currentCode, byte[] newCode)
            throws SmartCardReaderException;

    /**
     * Unblock PIN1/PIN2 via PUK code and change it to a new value.
     * <p>
     * When PIN1/PIN2 is not blocked yet it will be blocked before unblocking.
     *
     * @param pukCode PUK code.
     * @param type Code type.
     * @param newCode New code.
     * @throws SmartCardReaderException When changing failed.
     * @throws CodeVerificationException When PUK code is wrong.
     */
    void unblockAndChangeCode(byte[] pukCode, CodeType type, byte[] newCode)
            throws SmartCardReaderException;

    int pinChangedFlag(CodeType type) throws SmartCardReaderException;

    /**
     * Read retry counter for PIN1/PIN2/PUK code.
     *
     * @param type Code type.
     * @return Code retry counter.
     */
    int codeRetryCounter(CodeType type) throws SmartCardReaderException;

    /**
     * Read certificate data of the cardholder.
     *
     * @param type Type of the certificate.
     * @return Certificate data.
     * @throws SmartCardReaderException When reading failed.
     */
    byte[] certificate(CertificateType type) throws SmartCardReaderException;

    /**
     * Calculate electronic signature with pre-calculated hash.
     *
     * @param pin2 PIN2 code.
     * @param hash Pre-calculated hash.
     * @param ecc Whether it's a elliptic curve certificate.
     * @return Signed data.
     * @throws SmartCardReaderException When calculating signature failed.
     * @throws CodeVerificationException When PIN2 code is wrong.
     */
    byte[] calculateSignature(byte[] pin2, byte[] hash, boolean ecc)
            throws SmartCardReaderException;

    /**
     * Signs the authentication token hash
     *
     * @param pin1 PIN1 code
     * @param token Authentication token
     * @return authentication token hash signature
     * @throws SmartCardReaderException When signing the token failed
     * @throws CodeVerificationException When PIN1 code is wrong
     */
    byte[] authenticate(byte[] pin1, byte[] token)
        throws SmartCardReaderException;

    /**
     * Decrypt data.
     *
     * @param pin1 PIN1 code.
     * @param data Data to decrypt.
     * @param ecc Whether it's a elliptic curve certificate.
     *
     * @return Decrypt result.
     * @throws SmartCardReaderException When decrypting failed.
     * @throws CodeVerificationException When PIN1 code is wrong.
     */
    byte[] decrypt(byte[] pin1, byte[] data, boolean ecc) throws SmartCardReaderException;

}
