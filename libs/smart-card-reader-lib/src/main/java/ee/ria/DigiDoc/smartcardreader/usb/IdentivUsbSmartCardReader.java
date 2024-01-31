/*
 * Copyright 2017 - 2024 Riigi Infosüsteemi Amet
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

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.identive.libs.SCard;

import java.util.ArrayList;
import java.util.Arrays;

import ee.ria.DigiDoc.smartcardreader.usb.UsbSmartCardReader;
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

import static com.identive.libs.WinDefs.SCARD_LEAVE_CARD;
import static com.identive.libs.WinDefs.SCARD_PROTOCOL_TX;
import static com.identive.libs.WinDefs.SCARD_SHARE_EXCLUSIVE;
import static com.identive.libs.WinDefs.SCARD_SPECIFIC;

public final class IdentivUsbSmartCardReader extends UsbSmartCardReader {

    private static final int VENDOR_ID = 1254;

    private final Context context;
    private final UsbManager usbManager;
    private final SCard sCard;

    public IdentivUsbSmartCardReader(Context context, UsbManager usbManager) {
        this.context = context;
        this.usbManager = usbManager;
        sCard = new SCard();
    }

    @Override
    public boolean supports(UsbDevice usbDevice) {
        if (!usbManager.hasPermission(usbDevice) && usbDevice.getVendorId() == VENDOR_ID) {
            return true;
        }
        open(usbDevice);
        ArrayList<String> readers = new ArrayList<>();
        sCard.SCardListReaders(context, readers);
        return readers.size() > 0;
    }

    @Override
    public void open(UsbDevice usbDevice) {
        sCard.SCardEstablishContext(context);
    }

    @Override
    public void close() {
        sCard.SCardDisconnect(SCARD_LEAVE_CARD);
        sCard.SCardReleaseContext();
    }

    @Override
    public boolean connected() {
        sCard.SCardEstablishContext(context);

        SCard.SCardState state = sCard.new SCardState();
        sCard.SCardStatus(state);
        if (state.getnState() == SCARD_SPECIFIC) {
            return true;
        }

        ArrayList<String> readers = new ArrayList<>();
        sCard.SCardListReaders(context, readers);
        if (readers.size() > 0) {
            sCard.SCardConnect(readers.get(0), SCARD_SHARE_EXCLUSIVE, (int) SCARD_PROTOCOL_TX);
        }
        return false;
    }

    @Override
    public byte[] atr() {
        SCard.SCardState state = sCard.new SCardState();
        sCard.SCardStatus(state);
        return Arrays.copyOf(state.getAbyATR(), state.getnATRlen());
    }

    @Override
    protected byte[] transmit(byte[] apdu) throws SmartCardReaderException {
        SCard.SCardIOBuffer io = sCard.new SCardIOBuffer();
        io.setAbyInBuffer(apdu);
        io.setnInBufferSize(apdu.length);
        io.setAbyOutBuffer(new byte[0x8000]);
        io.setnOutBufferSize(0x8000);
        sCard.SCardTransmit(io);
        if (io.getnBytesReturned() == 0) {
            throw new SmartCardReaderException("Failed to send apdu");
        }
        String rstr = "";
        for (int k = 0; k < io.getnBytesReturned(); k++) {
            int temp = io.getAbyOutBuffer()[k] & 0xFF;
            if (temp < 16) {
                rstr = rstr.toUpperCase() + "0" + Integer.toHexString(io.getAbyOutBuffer()[k]);
            } else {
                rstr = rstr.toUpperCase() + Integer.toHexString(temp);
            }
        }
        return Arrays.copyOf(io.getAbyOutBuffer(), io.getnBytesReturned());
    }
}
