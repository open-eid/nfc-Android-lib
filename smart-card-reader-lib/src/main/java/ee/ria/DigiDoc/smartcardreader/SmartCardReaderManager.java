package ee.ria.DigiDoc.smartcardreader;

import io.reactivex.rxjava3.core.Observable;

public interface SmartCardReaderManager {

    Observable<SmartCardReaderStatus> status();

    SmartCardReader connectedReader() throws SmartCardReaderException;
}
