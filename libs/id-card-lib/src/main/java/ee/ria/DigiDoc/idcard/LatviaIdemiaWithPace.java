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

import static com.google.common.primitives.Bytes.concat;

import android.util.SparseArray;

import java.nio.charset.StandardCharsets;

import ee.ria.DigiDoc.smartcardreader.ApduResponseException;
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil;

/**
 * Latvian eID card NFC token.
 *
 * Extends IdemiaWithPace since the Latvian eID uses the same IDEMIA ID-One Cosmo v8
 * platform (IAS-ECC Oberthur) as the Estonian card. PACE, SM, certificate, and PIN
 * operations are inherited.
 *
 * Overrides personalData() for Latvian personal code parsing, provides AID selection
 * fallback, and reads key references from the card's PrKDF for authentication/signing
 * since the Latvian card uses different key slot assignments.
 */
class LatviaIdemiaWithPace extends IdemiaWithPace {
    private static final String TAG = LatviaIdemiaWithPace.class.getName();

    private static final byte ECC_AUTH_ALGO = 0x04;
    private static final byte ECC_SIGN_ALGO = 0x54;

    private Boolean qscdAidSupported = null;
    private Boolean oberthurAidSupported = null;

    LatviaIdemiaWithPace(NfcSmartCardReader reader) {
        super(reader);
    }

    @Override
    public PersonalData personalData() throws SmartCardReaderException {
        selectMainAid();
        reader.transmit(0x00, 0xA4, 0x01, 0x0C, new byte[] {0x50, 0x00}, null);
        SparseArray<String> data = new SparseArray<>();
        for (int i = 1; i <= 8; i++) {
            try {
                reader.transmit(0x00, 0xA4, 0x02, 0x0C, new byte[] {0x50, (byte) i}, null);
                byte[] record = reader.transmit(0x00, 0xB0, 0x00, 0x00, null, 0x00);
                String value = new String(record, StandardCharsets.UTF_8).trim();
                data.put(i, value);
                LoggingUtil.Companion.debugLog(TAG,
                        String.format("Latvia EF record %d: %s", i, value), null);
            } catch (ApduResponseException e) {
                LoggingUtil.Companion.errorLog(TAG,
                        String.format("Latvia EF record %d read failed: sw1=%02x sw2=%02x",
                                i, e.sw1, e.sw2), null);
                data.put(i, "");
                if (e.sw1 == 0x6A) {
                    break;
                }
            }
        }
        return LatviaPersonalDataParser.parse(data);
    }

    @Override
    public byte[] authenticate(byte[] pin1, byte[] token) throws SmartCardReaderException {
        selectOberthurAid();
        verifyCode(CodeType.PIN1, pin1);
        byte keyRef = getAuthKeyRef();
        LoggingUtil.Companion.debugLog(TAG,
                String.format("MSE SET auth: algo=0x%02x, keyRef=0x%02x", ECC_AUTH_ALGO & 0xFF, keyRef & 0xFF), null);
        reader.transmit(0x00, 0x22, 0x41, 0xA4,
                new byte[] {(byte) 0x80, 0x01, ECC_AUTH_ALGO, (byte) 0x84, 0x01, keyRef}, null);
        return reader.transmit(0x00, 0x88, 0x00, 0x00, token, 0x00);
    }

    @Override
    public byte[] calculateSignature(byte[] pin2, byte[] hash, boolean ecc) throws SmartCardReaderException {
        selectQSCDAid();
        verifyCode(CodeType.PIN2, pin2);
        byte keyRef = getSignKeyRef();
        LoggingUtil.Companion.debugLog(TAG,
                String.format("MSE SET sign: algo=0x%02x, keyRef=0x%02x", ECC_SIGN_ALGO & 0xFF, keyRef & 0xFF), null);
        reader.transmit(0x00, 0x22, 0x41, 0xB6,
                new byte[] {(byte) 0x80, 0x01, ECC_SIGN_ALGO, (byte) 0x84, 0x01, keyRef}, null);
        return reader.transmit(0x00, 0x2A, 0x9E, 0x9A, padWithZeroes(hash), 0x00);
    }

    @Override
    public byte[] decrypt(byte[] pin1, byte[] data, boolean ecc) throws SmartCardReaderException {
        selectOberthurAid();
        verifyCode(CodeType.PIN1, pin1);
        byte keyRef = getAuthKeyRef();
        LoggingUtil.Companion.debugLog(TAG,
                String.format("MSE SET decrypt: algo=0x%02x, keyRef=0x%02x", ECC_AUTH_ALGO & 0xFF, keyRef & 0xFF), null);
        reader.transmit(0x00, 0x22, 0x41, 0xB8,
                new byte[] {(byte) 0x80, 0x01, ECC_AUTH_ALGO, (byte) 0x84, 0x01, keyRef}, null);
        return reader.transmit(0x00, 0x2A, 0x80, 0x86, concat(new byte[] {0x00}, data), 0x00);
    }

    @Override
    protected void selectQSCDAid() throws SmartCardReaderException {
        if (qscdAidSupported == null) {
            try {
                super.selectQSCDAid();
                qscdAidSupported = true;
                return;
            } catch (ApduResponseException e) {
                if (e.sw1 == 0x6A && e.sw2 == (byte) 0x82) {
                    qscdAidSupported = false;
                    LoggingUtil.Companion.debugLog(TAG,
                            "QSCD AID not found on Latvian card, falling back to main AID", null);
                } else {
                    throw e;
                }
            }
        }
        if (Boolean.TRUE.equals(qscdAidSupported)) {
            super.selectQSCDAid();
        } else {
            selectMainAid();
        }
    }

    @Override
    protected void selectOberthurAid() throws SmartCardReaderException {
        if (oberthurAidSupported == null) {
            try {
                super.selectOberthurAid();
                oberthurAidSupported = true;
                return;
            } catch (ApduResponseException e) {
                if (e.sw1 == 0x6A && e.sw2 == (byte) 0x82) {
                    oberthurAidSupported = false;
                    LoggingUtil.Companion.debugLog(TAG,
                            "Oberthur AID not found on Latvian card, falling back to main AID", null);
                } else {
                    throw e;
                }
            }
        }
        if (Boolean.TRUE.equals(oberthurAidSupported)) {
            super.selectOberthurAid();
        } else {
            selectMainAid();
        }
    }

}
