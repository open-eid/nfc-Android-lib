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
}