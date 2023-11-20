package ee.ria.DigiDoc.smartcardreader.nfc.example.configuration

import ee.ria.libdigidocpp.DigiDocConf
import java.io.File
import java.util.Base64
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists

class ContainerConfiguration {

    private var data: ConfigurationData = ConfigurationData()

    fun setupTSLFiles(schemaDir: String) {
        val eetPath = Path(schemaDir + File.separator + "EE_T.xml")
        if (!eetPath.exists()) {
            eetPath.createFile()
        }
    }

    fun increaseLogLevel() {
        DigiDocConf.instance().setLogLevel(4)
    }

    fun overrideTSLUrl() {
        DigiDocConf.instance().setTSLUrl(data.TSL_URL)
    }

    fun overrideTSLCert() {
        DigiDocConf.instance().setTSLCert(ByteArray(0))
        for (cert in data.TSL_CERTS) {
            DigiDocConf.instance().addTSLCert(Base64.getDecoder().decode(cert))
        }
    }

    fun initTsaUrl() {
        DigiDocConf.instance().setTSUrl(data.TSA_URL)
    }
}