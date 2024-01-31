# Rakenduse integreerimine ID-kaardiga NFC abil

[TOC]

## Ülevaade

ID-kaardi tugi Android rakendustele rajaneb kahel teegil: `id-card-lib` ja `smart-card-reader-lib`.

`smart-card-reader-lib` võimaldab kasutada ID-kaarti üle USB või NFC liidese. `id-card-lib` realiseerib APDU-põhised suhtlusprotokolli erinevat tüüpi ID-kaartidele põhifunktsionaalsuse kasutamiseks. ID-kaardi toe integreerimine Android rakendusega toimub järgmiselt:

* Arendaja kirjeldab rakenduse manifestis soovitud liidestumise jaoks vajalikud õigused.
* Arendaja loob soovitud liidestamise jaoks spetsiifilise halduri.
* Arendaja tuvastab halduri abil ID-kaardi ning loob vastava `factory`-meetodi abil kaardi instantsi.
* Arendaja loob kaardi ja seadme vahel turvalise suhtluskanali kasutades kaardile vastavat CAN koodi.
* Arendaja suhtleb kaardiinstantsiga soovitud funktsionaalsuste - autentimine, allkirjastamine, vm - kasutamiseks.

NFC-liidese kasutamise näidis on leitav demorakendusest: `CardReaderFragment.kt`. Näide on keeles Kotlin, kuid teek on kasutatav ka Java rakenduses. Näiterakendus sõltub täiendavalt teegist `libdigidocpp`, mis võimaldab allkirjastatud ASiCE konteineri loomist, sellest teegist sõltumine ei ole ID-kaardi kasutamiseks NFC vahendusel vajalik.

## NFC liides

### Suhtluse üldskeem

Rakendus, mis soovib ID-kaarti üle NFC liidese kasutada, peab oma manifestis deklareerima õigused NFC kasutamiseks:

````
<uses-permission android:name="android.permission.NFC" />
````

Suhtlus ID-kaardiga käib klassi `android.nfc.tech.IsoDep` vahendusel kasutades NFC-A tehnoloogiat. Androidi NFC liidese spetsiifika on realiseeritud teegi poolt.

```
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReaderManager
...
private lateinit var nfcSmartCardReaderManager: NfcSmartCardReaderManager
...
nfcSmartCardReaderManager = NfcSmartCardReaderManager()
```

ID-kaardiga suhtlemiseks tuleb luua NFC halduri `ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReaderManager` instants ning kasutada klassi poolt pakutavaid meetodeid.

```
public NfcStatus detectNfcStatus(Activity activity)
public NfcStatus startDiscovery(Activity activity, NfcSmartCardReaderCallback callback)
public void onTagDiscovered(Tag tag)
public void disableNfcReaderMode() 
```

* Meetodi `detectNfcStatus` abil on võimalik tuvastada, kas seade toetab NFCd ning kas NFC tugi on sisse lülitatud.
* Meetodi `startDiscovery` abil hakkab konkreetne `Activity` monitoorima NFC liidest. Kui sobiva tehnoloogiaga NFC-tag tuvastatakse, siis rakendub `callback`-meeton `onTagDiscovered`. Seda meetodit ei defineeri teegi integreerija, kes defineerib hoopis `NfcSmartCardReaderCallback` tüüpi liidese, mille `onNfcReader` meetod saab sisendiks kas suhtlusvalmis NFC-tagi või erindi.
* Meetodi `disableNfcReaderMode` abil loobub rakendus NFC liidese edasisest monitoorimisest.

    public interface NfcSmartCardReaderCallback {
        void onNfcReader(NfcSmartCardReader reader, SmartCardReaderException ex);
    }

Liidese `NfcSmartCardReaderCallback` realisatsioon vastutab ID-kaardi funktsionaalsuse kasutamise ja veatöötluse eest.

    public interface TokenWithPace extends Token {
    	void tunnel(String can) throws SmartCardReaderException;
    	static TokenWithPace create(NfcSmartCardReader reader) throws SmartCardReaderException {
    }
ID-kaardiga üle NFC liidese suhtlemiseks kasutatakse liidest `TokenWithPace`. `factory`-meetodi `create` abil luuakse antud liidest realiseeriv instants - konkreetse alamklassi valikul on aluseks kaardi poolt tagastatav ATS (*answer to select*). Hetkel on olemas ainult ühte liiki ID-kaardid NFC liidesega - IDEMIA kaardid.

Peale instantsi loomist tuleb suhtluskanal luua kasutades vastava kaardi `CAN` koodi ning meetodit `tunnel`. 

```
public interface Token {
    PersonalData personalData();
    int codeRetryCounter(CodeType type);
    byte[] certificate(CertificateType type);
    byte[] calculateSignature(byte[] pin2, byte[] hash, boolean ecc);
    byte[] authenticate(byte[] pin1, byte[] token);
}
```

Kui tunneli loomine oli edukas, saab võimalikuks ID-kaardi funktsioonide kasutamine üle NFC. Funktsioonid on kirjeldatud liideses `Token`. Tuleb panna tähele, et mitte kogu ID-kaardi funktsionaalsus, mis on saadaval üle kontaktühendus, ei ole saadaval üle NFC ühenduse. Näites on üles loetud 5 meetodit, mis kõik on NFC liidese vahendusel kasutatavad. NB! kõik need meetodid võivad visata erindi `SmartCardReaderException`.

Vaatame näidet ID-kaardi isikuandmete faili lugemiseks olukorras, kus klassi `NfcSmartCardReaderManager` instants on loodud.


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

* Rida 2: kasutame meetodit `startDiscovery`, mis sisemiselt kasutab `android.nfc.NfcAdapter` meetodit `enableReaderMode` NFC-tag'ide tuvastamiseks. Töötleme meetodi poolt tagastatavat `NfcStatus` väärtust.
* Rida 3: meetod `startDiscovery` kasutab `NfcSmartCardReaderCallback` liidest, juhul kui erindit ei ole ja `NfcSmartCardReader` instants on olemas toimub töötlemine.
* Rida 5: loome liidest `TokenWithPace` realiseeriva instantsi.
* Rida 6: kasutame kaardile vastavat `CAN` koodi kaardi ja seadme vahelise turvatut NFC ühenduse loomiseks.
* Rida 7: kasutame ID-kaardi funktsionaalsust isikuandmete faili lugemiseks.
* Rida 8: kõik eelnevad operatsioonid võivad lõppeda `SmartCardReaderException` tüüpi erindiga.
* Rida 11: lõpetame NFC-tagide ootamise. 

### NFC liidese olek

Olenevalt Android seadmest võib NFC liides olla olemas või mitte. Olemasolev liides võib olla sisselülitatud või väljalülitatud. Vastavaid olekuid kirjeldab `enum NfcStatus`, mida tagastab halduri meetod `startDiscovery`, kuid mida on võimalik tuvastada ka meetodiga `detectNfcStatus` ning rakenduses vastavalt reageerida.


	private fun checkNfcStatus(status: NfcStatus) {
		when (status) {
			NfcStatus.NFC_NOT_SUPPORTED -> communicationTextView.text = getString(R.string.nfc_not_supported)
			NfcStatus.NFC_NOT_ACTIVE -> communicationTextView.text = getString(R.string.nfc_not_turned_on)
			NfcStatus.NFC_ACTIVE -> communicationTextView.text = getString(R.string.card_detect_info)
		}
	}

* `NFC_NOT_SUPPORTED` - seadmel puudub NFC liides.

* `NFC_NOT_ACTIVE` - seadmel on NFC liides olemas, kuid välja lülitatud.

* `NFC_ACTIVE` - seadmel on aktiivne NFC liides.

### Erinditöötlus

Suheldes ID-kaardiga üle NFC liidese võib esineda mitmeid veasituatsioone nii NFC liidestumise tasemel, APDU protokolli tasemel kui kaardi funktsionaalsuste tasemel. Vaatame näidet erindite töötlemisest ID-kaardi suhtlusprotsessi vältel.


	private fun exceptionHandler(ex: SmartCardReaderException) {
		if (ex is CodeVerificationException) {
			...
		} else if (ex is PaceTunnelException) {
			...
		} else if (ex is ApduResponseException) {
			...
		}
		else {
			if (ex.cause is TagLostException) {
				...
			} else {
				...
			}
		}
	}

* Rida 1: `ee.ria.DigiDoc.smartcardreader.SmartCardReaderException` - baaserind, millest tulenevad kõik ID-kaardi kasutamisega seotud erindid.
* Rida 2: `ee.ria.DigiDoc.idcard.CodeVerificationException` - spetsiifiline erind, mis viitab et operatsiooni autoriseerimiseks kasutatud PIN1 või PIN2 kood oli vale. Erind sisaldab infot, kui mitu korda saab PINi enne lukustumist veel sisestada.
* Rida 4: `ee.ria.DigiDoc.idcard.PaceTunnelException` - spetsiifiline erind, mis viitab kaardi ja seadme vahelise turvalise kanali loomise ebaõnnestumisele. Suure tõenäosusega on probleemi põhjuseks vigane CAN.
* Rida 6: `ee.ria.DigiDoc.smartcardreader.ApduResponseException` - erind, mis viitab veale ID-kaardi APDU suhtlusprotokollis.
* Rida 10: `android.nfc.TagLostException` - erind, mis viitab NFC ühenduse katkemisele kaardi ja seadme vahel.
* Rida 12: Suvaline muu erind, mis on põhjustanud `SmartCardReaderExceptioni`.

### NFC liidesega seotud eripärad

Üldjoontes on ID-kaardi kasutamisel üle USB ja NFC liidese üksjagu sarnasusi - põhifunktsioonid on samad, APDU protokoll on valdavalt sama. Siiski on NFC liidesel oma eripärad, mis võivad tingida erisusi nii nt. rakenduse kasutajaliideses kui suhtluse haldamises.

1. Kui ID-kaart ühendada kiipkaardilugejaga seadme USB porti, siis on ta seadmele nähtav ning mõned funktsioonid - isikuandmete faili lugemine, PIN loendurite lugemine - koheselt kasutatavad. Kaart võib lugejas olla pikemat aega ning ei nõua kasutajalt füüsilist pingutust selle paigal hoidmiseks. Kasutajal on aega seadmega tegutseda ning nt. vajalikku infot sisestada.

   Kui ID-kaarti kasutada NFC liidese vahendusel, siis tuleb teada, kus antud seadmel NFC liides paikneb ning kaarti tuleb liidese kohal füüsiliselt paigal hoida, mis on ebamugav ja ebakindel tegevus. Lisaks ei ole kaart enne õige CAN abil turvalise suhtluskanali loomist kasutatav, nt. ei saa tuvastada PIN loendurite olekut ning kuvada kasutajale hoiatust. Samal ajal kui kasutaja hoiab kaarti NFC liidese läheduses on tal ebamugav seadet muul moel opereerida ning nt. PIN ja CAN koode sisestada.

2. Kasutades ID-kaarti kiipkaardilugejaga toimub APDU protokoll avakujul. NFC liidese vahendusel on APDU protokoll krüpteeritud ja autenditud. Täiendavalt võib kaart rakendada piiranguid - mitte kõik funktsioonid ei ole üle NFC liidese kättesaadavad.

3. CAN on kuuekohaline kaardispetsiifiline number, mille olemasolul on võimalik kaardiga üle NFC liidese suhelda. Erinevalt PIN koodidest ei saa CAN koodi ei muuta ega lukustada. Äraarvamise vastase meetmena on IDEMIA kaartidel rakendatud viivitus - kui 10 korda järjest on sisestatud vale CAN kood, siis oodatakse järgmise CANi valideerimisel täiendavad 30 sekundit. Kui sisestada õige CAN, siis taastub viivituseta töö.
