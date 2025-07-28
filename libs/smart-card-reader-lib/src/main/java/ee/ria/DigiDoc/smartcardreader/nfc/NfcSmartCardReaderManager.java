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

package ee.ria.DigiDoc.smartcardreader.nfc;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.Tag;

import androidx.annotation.Nullable;

import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException;

/**
 * Communication manager for NFC based readers
 */
public final class NfcSmartCardReaderManager implements NfcAdapter.ReaderCallback {

    /**
     * The actual NfcAdapter to use for comms
     */
    @Nullable
    private NfcAdapter nfcAdapter;

    /**
     * Disables reader mode, detaches this manager from activity.
     */
    public void disableNfcReaderMode() {
        if ((this.currentActivity != null) && (this.nfcAdapter != null)) {
            this.nfcAdapter.disableReaderMode(this.currentActivity);
        }
        this.nfcAdapter = null;
        this.currentActivity = null;
    }

    /**
     * Android NFC API is Activity bound
     */
    @Nullable
    private Activity currentActivity;

    /**
     * Client callback to implement the functionality to be achieved
     * once the NFC interface is connected
     */
    @Nullable
    private NfcSmartCardReaderCallback clientCallback;

    /**
     * Create the NfcSmartCardReaderManager
     */
    public NfcSmartCardReaderManager() {
    }

    /**
     * Detect the status of NfcAdapter on device.
     *
     * @param activity - active Activity for the NFC task
     * @return - status if the NFC is available / enabled / active
     */
    public NfcStatus detectNfcStatus(Activity activity) {
        NfcAdapter nfa = NfcAdapter.getDefaultAdapter(activity);

        if (nfa == null) {
            return NfcStatus.NFC_NOT_SUPPORTED;
        }

        if (!nfa.isEnabled()) {
            return NfcStatus.NFC_NOT_ACTIVE;
        }

        return NfcStatus.NFC_ACTIVE;
    }

    /**
     * Get NfcAdapter and enable the reader mode for NFC_A type of tags.
     *
     * @param activity - active Activity for the NFC task
     * @param callback - customer callback
     * @return - status if the NFC is available / enabled / active
     */
    public NfcStatus startDiscovery(Activity activity, NfcSmartCardReaderCallback callback) {
        this.currentActivity = activity;
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(this.currentActivity);

        if (this.nfcAdapter == null) {
            return NfcStatus.NFC_NOT_SUPPORTED;
        }

        if (!this.nfcAdapter.isEnabled()) {
            return NfcStatus.NFC_NOT_ACTIVE;
        }

        this.clientCallback = callback;
        this.nfcAdapter.enableReaderMode(
                this.currentActivity, this, NfcAdapter.FLAG_READER_NFC_A, null);
        return NfcStatus.NFC_ACTIVE;
    }

    /**
     * Internal NFC callback upon the tag detection. Creates the smart card reader and
     * calls the client callback and after success disables the reader mode.
     * @param tag
     */
    public void onTagDiscovered(Tag tag) {
        NfcSmartCardReader reader = null;
        SmartCardReaderException ex = null;
        try {
            reader = new NfcSmartCardReader(tag);
        } catch (SmartCardReaderException e) {
            ex = e;
        }
        if (clientCallback != null) {
            clientCallback.onNfcReader(reader, ex);
        }

        assert reader != null;
        reader.close();
        disableNfcReaderMode();
    }

    /**
     * NFC status codes
     */
    public enum NfcStatus {
        NFC_NOT_SUPPORTED,
        NFC_NOT_ACTIVE,
        NFC_ACTIVE
    }

    /**
     * Client callback interface for the implementing applications.
     */
    public interface NfcSmartCardReaderCallback {

        /**
         * The callback that the client must implement upon creation of the reader
         * @param reader - reader that was created
         * @param ex - exception that happened during reader creation
         * <p>
         * The implementer must first ensure that the exception did not take place and
         * only then the reader can be used
         */
        void onNfcReader(NfcSmartCardReader reader, SmartCardReaderException ex);
    }
}
