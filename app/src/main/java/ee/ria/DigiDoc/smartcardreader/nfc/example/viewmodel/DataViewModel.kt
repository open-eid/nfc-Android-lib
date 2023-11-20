package ee.ria.DigiDoc.smartcardreader.nfc.example.viewmodel

import androidx.lifecycle.ViewModel
import ee.ria.DigiDoc.idcard.PersonalData

class DataViewModel : ViewModel() {
    private var can: String = ""
    private lateinit var givenNames: String
    private lateinit var surname: String
    private lateinit var personalCode: String
    private lateinit var citizenship: String
    private lateinit var expiryDate: String
    private var containerName: String = ""

    private var pin1Counter: Int = 0
    private var pin2Counter: Int = 0

    fun setContainerName(containerName: String) {
        this.containerName = containerName
    }

    fun getContainerName(): String {
        return this.containerName
    }

    fun setCan(can: String) {
        this.can = can
    }

    fun clearCan() {
        this.can = ""
    }

    fun setUserValues(cardData: PersonalData) {
        this.givenNames = cardData.givenNames()
        this.surname = cardData.surname()
        this.personalCode = cardData.personalCode()
        this.citizenship = cardData.citizenship()
        this.expiryDate = cardData.expiryDate().toString()
    }

    fun getCan(): String {
        return can
    }

    fun getGivenNames(): String {
        return givenNames
    }

    fun getSurname(): String {
        return surname
    }

    fun getPersonalCode(): String {
        return personalCode
    }

    fun getCitizenship(): String {
        return citizenship
    }

    fun getExpiryDate(): String {
        return expiryDate
    }

    fun setPin1Counter(p1: Int) {
        pin1Counter = p1
    }

    fun setPin2Counter(p2: Int) {
        pin2Counter = p2
    }

    fun getPin1Counter(): Int {
        return pin1Counter
    }

    fun getPin2Counter(): Int {
        return pin2Counter
    }

}