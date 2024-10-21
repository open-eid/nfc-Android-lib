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

import android.hardware.usb.UsbDevice;

import static java.util.Arrays.copyOf;
import static java.util.Arrays.copyOfRange;

import ee.ria.DigiDoc.smartcardreader.SmartCardReader;

/**
 * Base class for smart card readers.
 *
 * TODO Log all transmit commands in hex (request and response)
 */
public abstract class UsbSmartCardReader extends SmartCardReader {

    public abstract boolean supports(UsbDevice usbDevice);

    public abstract void open(UsbDevice usbDevice);

}
