# Integrating an Application with the ID Card via NFC

- [Using the Library in the Sample Application](#using-the-library-in-the-sample-application) 
  - [Define the Android SDK Location](#define-the-android-sdk-location)   
  - [Installing the Sample Application on a Device or Emulator](#installing-the-sample-application-on-a-device-or-emulator)  
  - [Building only the Sample App APK](#building-only-the-sample-app-apk) 
  - [Testing with the Demo App](#testing-with-the-demo-app)  
  - [Using the Libraries in Other Applications](#using-the-libraries-in-other-applications) 
    - [Building the AAR](#building-the-aar) 
- [Overview](#overview) 
- [NFC Interface](#nfc-interface) 
  - [General Communication Scheme](#general-communication-scheme) 
  - [NFC Interface Status](#nfc-interface-status) 
  - [Exception Handling](#exception-handling) 
  - [Specifics of the NFC Interface](#specifics-of-the-nfc-interface) 

## Using the Library in the Sample Application

### Define the Android SDK Location

```shell
echo "sdk.dir=/path/to/android-sdk" > local.properties
```

### Installing the Sample Application on a Device or Emulator

To install on a physical device, ensure the device is connected to your computer via a USB cable.

```shell
./gradlew installDebug
```

> [!TIP]
> See also the official Android documentation for
> [installing an app on a physical device](https://developer.android.com/studio/run/device)
> or [in an emulator](https://developer.android.com/studio/run/emulator).

### Building only the Sample App APK

```shell
./gradlew assembleDebug
```

The generated APK file is located at `/demoapp/app/build/outputs/apk/debug`.

### Testing with the Demo App
The demo application (`demoapp/app`) provides a complete reference implementation for NFC ID card integration. It demonstrates:
* **Reading personal data** from the ID card
* **Digital signing** with PIN2 (creates ASiC-E containers using [libdigidocpp](https://github.com/open-eid/libdigidocpp))
* **Authentication** with PIN1 (mock Web-eID implementation)
* **Exception handling** for various error scenarios
**Prerequisites for testing:**
* An Android device with NFC capability (NFC-enabled emulators do not support physical NFC cards)
* An Estonian ID card with NFC support (IDEMIA or Thales)
* The card's CAN code (6-digit number printed on the card)
* PIN1 and/or PIN2 codes (depending on the functionality being tested)
**Testing steps:**
1. Install the demo app using `./gradlew installDebug`
2. Enable NFC on your Android device (Settings → NFC and contactless payments)
3. Launch the demo app
4. Choose the desired operation (read card info, sign document, or authenticate)
5. Enter the required codes (CAN, PIN1, or PIN2) when prompted
6. Hold your ID card against the device's NFC antenna
7. Keep the card steady until the operation completes
> [!TIP]
> The exact location of the NFC antenna varies by device model. It's typically near the camera on the back of the phone.

### Using the Libraries in Other Applications

#### Building the AAR

```shell
./gradlew -p libs assemble
```

* The `.aar` files are located in:
  * `libs/id-card-lib/build/outputs/aar`
  * `libs/smart-card-reader-lib/build/outputs/aar`
* Move the resulting `.aar` files to your project's `/libs` directory.
* Add the corresponding dependencies to your application's `build.gradle` file:
    * `implementation files('app/libs/aar')`

## Overview

ID card support for Android applications is based on two libraries: `id-card-lib` and `smart-card-reader-lib`.

* `smart-card-reader-lib` enables the use of the ID card over USB or NFC. It provides the low-level smart card reader interfaces and communication layer.
* `id-card-lib` implements the APDU-based communication protocols required to use the core functionality across different types of ID cards (IDEMIA and Thales).
Integrating ID card support into an Android application proceeds as follows:

* The developer declares the permissions required for the chosen integration method in the application manifest.
* The developer creates a manager specific to the selected integration method.
* Using the manager, the developer detects the ID card and creates a card instance via the appropriate `factory` method.
* The developer establishes a secure communication channel between the card and the device using the card’s corresponding CAN code.
* The developer communicates with the card instance to use the desired functionalities—authentication, digital signing, etc.

An example of using the NFC interface can be found in the demo application: `CardReaderFragment.kt`. The example is written in Kotlin, but the library can also be used in Java applications. The sample app additionally depends on the `libdigidocpp` library, which enables the creation of signed ASiC-E containers; however, this dependency is not required for using the ID card via NFC. 

## NFC Interface

### General Communication Scheme

An application that wants to use the ID card via the NFC interface must declare the NFC permission in its manifest:

````xml
<uses-permission android:name="android.permission.NFC" />
````

Communication with the ID card takes place through the `android.nfc.tech.IsoDep` class using **NFC-A** technology.  
The Android NFC interface specifics are implemented by the library.

```kotlin
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReaderManager
...
private lateinit var nfcSmartCardReaderManager: NfcSmartCardReaderManager
...
nfcSmartCardReaderManager = NfcSmartCardReaderManager()
```

To communicate with the ID card, create an instance of the NFC manager  
`ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReaderManager` and use the methods provided by this class.

```java
public NfcStatus detectNfcStatus(Activity activity) {}
public NfcStatus startDiscovery(Activity activity, NfcSmartCardReaderCallback callback) {}
public void onTagDiscovered(Tag tag) {}
public void disableNfcReaderMode() {}
```

* The `detectNfcStatus` method can be used to determine whether the device supports NFC and whether NFC is enabled.  
* The `startDiscovery` method makes the specified `Activity` monitor the NFC interface. When an NFC tag with a compatible technology is detected, the `onTagDiscovered` **callback method** is invoked. This method is **not** implemented by the library integrator; instead, the integrator implements the `NfcSmartCardReaderCallback` interface, whose `onNfcReader` method receives either a ready-to-use NFC reader or an exception.  
* The `disableNfcReaderMode` method stops the application from further monitoring the NFC interface.

```java
public interface NfcSmartCardReaderCallback {
    void onNfcReader(NfcSmartCardReader reader, SmartCardReaderException ex);
}
```

The implementation of the `NfcSmartCardReaderCallback` interface is responsible for invoking ID card functionality and handling errors.

```java
public interface TokenWithPace extends Token {
    void tunnel(String can) throws SmartCardReaderException;
    static TokenWithPace create(NfcSmartCardReader reader) throws SmartCardReaderException {
    }
}
```

The `TokenWithPace` interface enables NFC communication with the ID card. An instance is obtained via
its `create` factory method, which selects the correct implementation based on the card's ATS (*Answer To Select*). 
Currently, two NFC-enabled ID card types are supported: IDEMIA (ID1) and Thales, implemented as `ID1WithPace` and `ThalesWithPace`.

After creating the instance, establish the communication channel using the card’s `CAN` code and the `tunnel` method.

```java
public interface Token {
    PersonalData personalData() throws SmartCardReaderException;
    int codeRetryCounter(CodeType type) throws SmartCardReaderException;
    byte[] certificate(CertificateType type) throws SmartCardReaderException;
    byte[] calculateSignature(byte[] pin2, byte[] hash, boolean ecc) throws SmartCardReaderException, CodeVerificationException;
    byte[] authenticate(byte[] pin1, byte[] token) throws SmartCardReaderException, CodeVerificationException;
    byte[] decrypt(byte[] pin1, byte[] data, boolean ecc) throws SmartCardReaderException, CodeVerificationException;
    void changeCode(CodeType type, byte[] currentCode, byte[] newCode) throws SmartCardReaderException, CodeVerificationException;
    void unblockAndChangeCode(byte[] pukCode, CodeType type, byte[] newCode) throws SmartCardReaderException, CodeVerificationException;
    int pinChangedFlag() throws SmartCardReaderException;
    byte[] certificate(CertificateType type);
    byte[] calculateSignature(byte[] pin2, byte[] hash, boolean ecc);
    byte[] authenticate(byte[] pin1, byte[] token);
}
```

If the tunnel is created successfully, ID card functionality becomes available over NFC.  
The functions are defined in the `Token` interface.  

Below is an example of reading the personal data file from the ID card when an instance of `NfcSmartCardReaderManager` has already been created.

```kotlin
private fun readCardData() {
    checkNfcStatus(nfcSmartCardReaderManager.startDiscovery(requireActivity()) { nfcReader, exc ->
        if ((nfcReader != null) && (exc == null)) {
            try {
                val card = TokenWithPace.create(nfcReader)
                card.tunnel(dataViewModel.getCan())
                val cardData = card.personalData()
            } catch (ex: SmartCardReaderException) {
                ...
            } finally {
                nfcSmartCardReaderManager.disableNfcReaderMode()
            }
        }
    })
}
```

* **Line 2:** Uses the `startDiscovery` method, which internally uses `android.nfc.NfcAdapter.enableReaderMode` to detect NFC tags. The returned `NfcStatus` value is processed.  
* **Line 3:** The `startDiscovery` method relies on the `NfcSmartCardReaderCallback` interface; if no exception is present and an `NfcSmartCardReader` instance exists, processing continues.  
* **Line 5:** Creates an instance implementing the `TokenWithPace` interface.  
* **Line 6:** Uses the card’s `CAN` code to establish a secure NFC connection between the card and the device.  
* **Line 7:** Uses ID card functionality to read the personal data file.  
* **Line 8:** Any of the above operations may result in a `SmartCardReaderException`.  
* **Line 11:** Stops waiting for NFC tags. 

### NFC Interface Status

Depending on the Android device, the NFC interface may or may not be available.  
If present, it can be either enabled or disabled.  
These states are described by the `enum NfcStatus`, which is returned by the manager method `startDiscovery` but can also be detected using the `detectNfcStatus` method, allowing the application to respond accordingly.

```kotlin
private fun checkNfcStatus(status: NfcStatus) {
    when (status) {
        NfcStatus.NFC_NOT_SUPPORTED -> communicationTextView.text = getString(R.string.nfc_not_supported)
        NfcStatus.NFC_NOT_ACTIVE -> communicationTextView.text = getString(R.string.nfc_not_turned_on)
        NfcStatus.NFC_ACTIVE -> communicationTextView.text = getString(R.string.card_detect_info)
    }
}
```

* `NFC_NOT_SUPPORTED` – the device does not have an NFC interface.  
* `NFC_NOT_ACTIVE` – the device has an NFC interface, but it is turned off.  
* `NFC_ACTIVE` – the device has an active NFC interface.  

---

### Exception Handling

When communicating with the ID card via the NFC interface, various error situations may occur —  
at the NFC communication level, the APDU protocol level, or within card-specific functionalities.  
The following example demonstrates exception handling during the ID card communication process.

```kotlin
private fun exceptionHandler(ex: SmartCardReaderException) {
    if (ex is CodeVerificationException) {
        ...
    } else if (ex is PaceTunnelException) {
        ...
    } else if (ex is IdCardException) {
        ...
    } else if (ex is ApduResponseException) {
        ...
    } else {
        if (ex.cause is TagLostException) {
            ...
        } else {
            ...
        }
    }
}
```

* **Line 1:** `ee.ria.DigiDoc.smartcardreader.SmartCardReaderException` – base exception from which all ID card–related exceptions inherit.
* **Line 2:** `ee.ria.DigiDoc.idcard.CodeVerificationException` – specific exception indicating that the PIN1 or PIN2 used for authorization was incorrect.
  The exception includes information on how many attempts remain before the PIN becomes locked.
* **Line 4:** `ee.ria.DigiDoc.idcard.PaceTunnelException` – specific exception indicating that the establishment of a secure communication channel between the card and the device has failed.
  Most likely, the issue is caused by an incorrect CAN code.
* **Line 6:** `ee.ria.DigiDoc.idcard.IdCardException` – general exception class for ID card-specific errors that don't fall into other categories.
* **Line 8:** `ee.ria.DigiDoc.smartcardreader.ApduResponseException` – exception indicating an error in the ID card's APDU communication protocol.
* **Line 11:** `android.nfc.TagLostException` – exception indicating that the NFC connection between the card and the device was lost.
* **Line 13:** Any other unexpected exception that triggered the `SmartCardReaderException`.   

---

### Specifics of the NFC Interface

In general, using the ID card over **USB** and **NFC** interfaces is quite similar —  
the main functions are the same, and the APDU protocol is largely identical.  
However, the NFC interface has certain characteristics that may require adjustments in the application UI or communication flow.

1. When the ID card is connected to a smart card reader via the device’s USB port,  
   it becomes immediately visible to the system, and certain functionalities — such as reading personal data or checking PIN retry counters — are available right away.  
   The card can remain inserted for long periods and does not require the user to hold it in place.  
   The user has time to interact with the device and enter any necessary information.

   When using the ID card via NFC, the user must know the NFC antenna’s location on the device and physically hold the card against it.  
   This is often inconvenient and unstable.  
   Additionally, the card cannot be accessed before a secure channel is established using the correct CAN code —  
   for example, it is not possible to read PIN retry counters or display warnings to the user beforehand.  
   While the user is holding the card near the NFC interface, it can be difficult to operate the device otherwise (e.g., to enter PIN or CAN codes).

2. When using the ID card via a USB smart card reader, the APDU protocol operates in plaintext.  
   Over the NFC interface, the APDU protocol is **encrypted and authenticated**.  
   Furthermore, the card may enforce additional restrictions — not all functionalities are available over NFC.

3. The **CAN** is a six-digit, card-specific number required for communication with the card over NFC.  
   Unlike PIN codes, the CAN cannot be changed or locked.  
   As a protection mechanism against brute-force attempts, IDEMIA cards introduce a delay:  
   after 10 consecutive incorrect CAN entries, the card enforces a **30-second delay** before the next CAN validation.  
   Once the correct CAN is entered, normal operation resumes immediately. 
