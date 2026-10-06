// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

public class CodeNotActivatedException extends IdCardException {

    private final CodeType type;

    public CodeNotActivatedException(CodeType type) {
        super(type + " has not been changed, operation not allowed");
        this.type = type;
    }

    public CodeType getType() {
        return type;
    }
}
