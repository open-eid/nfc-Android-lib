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

package ee.ria.DigiDoc.smartcardreader.nfc.example.configuration

class ConfigurationData {


    // time-stamp service
    var TSA_URL = "http://dd-at.ria.ee/tsa"

    // The European Commission's TSL (Trust Service List) - to verify the trustworthiness of certificates
    // when creating and validating signatures in ASICE and BDOC format
    var TSL_URL = "https://open-eid.github.io/test-TL/tl-mp-test-EE.xml"

    var TSL_CERTS = arrayOf(
        "MIIEvDCCAqQCCQCL/COUVyiGjTANBgkqhkiG9w0BAQUFADAgMQswCQYDVQQGEwJFRTERMA8GA1UEAwwIVGVzdCBUU0wwHhcNMTgxMTE1MTI1MjU1WhcNMjgxMTEyMTI1MjU1WjAgMQswCQYDVQQGEwJFRTERMA8GA1UEAwwIVGVzdCBUU0wwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQDfFK0fYeGrdngMZXZndDEpcl9pjGGNpbie3+ch5mDqObUe+OL45b4+SfPapriVRNBa+m5T1TuijP7Kb8sTNS9U3WQYvY8bEstPZnaEvdQSSVRf4j9eVg+RTJ8Y4jjZ02GbLwrpELD2Qs+ohCl8e64G29qutchv6nJqOdbL5U+d6DKyrzSpZyMRPA+UmB78KsBTs0o3wME7IA9J37YgtpUZifcC4LdgTWrX2eBICGPqi7GGKzdnI5LDhCJZnHwzva+6lBwa8fW5aXQG69uPTFmd/pNNF6+8f2FcYGljQiD6FYVKAUfYRBlw9ymKaIbNmyh9bs71ezPrI4ltOLjmZLZFRouSIaeExfzj2FkWERNG/iAuEIRolyyXjqjiQIuiEi8uo6sg1cPrD59EuWtTcMzTxuhVU8Ra37F6DrMEipqh84zQcnT0i/RNk4K723aB9uWwHJgJ5Y2/6cbta7ZkYsfQfjBC4nBRVyUlBCpEFYNePbKttYF5Cf5FraMlGzAY0W/MSIUxvRmlkjCzBod4LA+K/hQxEiw7Xa8OAZfw9l9lmSnia+fgRz3fLKxg3yklw6rA/2aISb83uVRvxgqKym3EeJ/+CsOQpwOblEBxWfQizah1Ct4NhsuKLmBbopxAXLqz25E+3BvvsM4nuwWVfoyvTVXYQ+k4V/hj2iS5buJ5twIDAQABMA0GCSqGSIb3DQEBBQUAA4ICAQAGTw5MJurTeeWy+jQikGfivrxt9lzqt+uSV8D6V1GBzBAl8m4SqSY0U8KM/gtqh9bhmQwm0qgx/mKcDKzCUKajXPKm/NbR+pjZD9Lcx4Iy0iqi9rsxSKECGM2dYAmm7GXnXvz9QUxZjteTgYoRP2s6GfosvTQiUEr/cIrYAU3wC0/94pRb9/FLVVon/aVdsh+Dqb4j7BhKLzXNCNjkv1Sv/YL1zpe/2SPxe0Bfymys97lcu1DB01e/MLfqQJThYOblMte/zGNZO24HcvROIkyoUtYy5/H4F5rsamSGMNdBfauTtYxz7lOT7qQoDNyGMN9bfjWnkVi/lV2CVooeiHIs7wLWEhYmU9DiAzcmODU9uMRRBlGOWK8UQg05exc518heICmudSbgSyQLGqzVoI4kybhmBA3w93KEXJSXlnU7hBzoYDP2d1g46Ay59UtvLycS1kxe0jVjxxRnh/f9aPbMwUYBzEC0naUzMeJtElHLHgW4HT6PLgFImgLLFh8dnYJUzn35wz10g3YBA61YUJuODpapKHixn/2X/t/8Vf1vqr/VwiwUglNQj+P78Fdb3T56JsYRG1bdf6nz5dvv4qtLoG+OjPI/tiLjh2ktqaMjeVmlQFchy/C5Lr48d9IGmo+x2ECYSWVvwzxI7PIbYBI4oaPjh2zKIrz/AlY2RmqMMA=="
    )
}