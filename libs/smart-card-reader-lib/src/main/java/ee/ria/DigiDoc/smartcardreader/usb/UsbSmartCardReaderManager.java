package ee.ria.DigiDoc.smartcardreader.usb;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import androidx.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import java.util.concurrent.TimeUnit;

import ee.ria.DigiDoc.smartcardreader.SmartCardReader;
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderStatus;
import io.reactivex.rxjava3.core.Observable;

/**
 * Manage smart card readers on Android platform.
 */
public final class UsbSmartCardReaderManager implements ee.ria.DigiDoc.smartcardreader.SmartCardReaderManager {

    /**
     * Card connected retry interval in milliseconds.
     */
    private static final int CONNECT_RETRY = 2500;

    private final UsbManager usbManager;
    private final ImmutableList<UsbSmartCardReader> readers;

    private final Observable<SmartCardReaderStatus> statusObservable;

    private SmartCardReader reader;

    public UsbSmartCardReaderManager(Context context, UsbManager usbManager,
                                  ImmutableList<UsbSmartCardReader> readers) {
        this.usbManager = usbManager;
        this.readers = readers;
        statusObservable = Observable
                .create(new UsbSmartCardReaderOnSubscribe(context, this))
                .switchMap(readerOptional -> {
                    if (readerOptional.isPresent()) {
                        return Observable
                                .fromCallable(() -> {
                                    readerOptional.get().connected();
                                    return readerOptional;
                                })
                                .repeatWhen(completed ->
                                        completed.delay(CONNECT_RETRY, TimeUnit.MILLISECONDS));
                    } else {
                        return Observable.just(readerOptional);
                    }
                })
                .map(readerOptional -> {
                    if (!readerOptional.isPresent()) {
                        reader = null;
                        return SmartCardReaderStatus.IDLE;
                    }
                    reader = readerOptional.get();
                    if (reader.connected()) {
                        return SmartCardReaderStatus.CARD_DETECTED;
                    } else {
                        return SmartCardReaderStatus.READER_DETECTED;
                    }
                })
                .replay(1)
                .refCount();
    }

    protected boolean supports(UsbDevice usbDevice) {
        return reader(usbDevice) != null;
    }

    @Nullable
    protected SmartCardReader reader(UsbDevice usbDevice) {
        for (UsbSmartCardReader reader : readers) {
            if (reader.supports(usbDevice)) {
                if (usbManager.hasPermission(usbDevice)) {
                    reader.open(usbDevice);
                }
                return reader;
            }
        }
        return null;
    }

    @Override
    public Observable<SmartCardReaderStatus> status() {
        return statusObservable;
    }

    @Override
    public SmartCardReader connectedReader() throws SmartCardReaderException {
        if (reader == null || !reader.connected()) {
            throw new SmartCardReaderException("Reader or card is not connected");
        }
        return reader;
    }
}
