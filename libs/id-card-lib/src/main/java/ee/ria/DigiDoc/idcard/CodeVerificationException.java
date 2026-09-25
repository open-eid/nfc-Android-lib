// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

/**
 * PIN1/PIN2/PUK code verification failed.
 */
public class CodeVerificationException extends IdCardException {

    private final CodeType type;
    private final int retries;

    public CodeVerificationException(CodeType type, int retries) {
        super(type + " verification failed. Retries left: " + retries);
        this.type = type;
        this.retries = retries;
    }

    public CodeType getType() {
        return type;
    }

    public int getRetries() { return retries; }

}
