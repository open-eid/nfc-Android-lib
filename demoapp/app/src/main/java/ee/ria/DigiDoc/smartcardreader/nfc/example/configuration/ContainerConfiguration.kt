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