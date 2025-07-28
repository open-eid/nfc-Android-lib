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

package ee.ria.DigiDoc.smartcardreader.usb;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.acs.smartcard.Reader;
import com.acs.smartcard.ReaderException;

import java.util.Arrays;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil;

public final class AcsUsbSmartCardReader extends UsbSmartCardReader {
    private static final String TAG = AcsUsbSmartCardReader.class.getName();
    private static final int SLOT = 0;

    private final Reader reader;

    public AcsUsbSmartCardReader(UsbManager usbManager) {
        reader = new Reader(usbManager);
    }

    @Override
    public boolean supports(UsbDevice usbDevice) {
        return reader.isSupported(usbDevice);
    }

    @Override
    public void open(UsbDevice usbDevice) {
        reader.open(usbDevice);
    }

    @Override
    public void close() {
        reader.close();
    }

    @Override
    public boolean connected() {
        if (reader.isOpened() && reader.getState(SLOT) == Reader.CARD_PRESENT) {
            try {
                reader.power(SLOT, Reader.CARD_WARM_RESET);
                reader.setProtocol(SLOT, Reader.PROTOCOL_TX);
            } catch (ReaderException e) {
                LoggingUtil.Companion.errorLog(TAG, "Connecting to ACS reader exception: " + e.getMessage(), e);
            }
        }
        return reader.isOpened() && reader.getState(SLOT) == Reader.CARD_SPECIFIC;
    }

    @Override
    public byte[] atr() {
        return reader.getAtr(SLOT);
    }

    @Override
    protected byte[] transmit(byte[] apdu) throws SmartCardReaderException {
        byte[] recv = new byte[1024];
        int len;
        try {
            len = reader.transmit(SLOT, apdu, apdu.length, recv, recv.length);
        } catch (ReaderException e) {
            throw new SmartCardReaderException(e);
        }
        return Arrays.copyOf(recv, len);
    }
}
