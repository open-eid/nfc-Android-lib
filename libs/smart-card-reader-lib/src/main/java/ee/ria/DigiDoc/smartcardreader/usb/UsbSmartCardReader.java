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
abstract class UsbSmartCardReader extends SmartCardReader {

    public abstract boolean supports(UsbDevice usbDevice);

    public abstract void open(UsbDevice usbDevice);

}
