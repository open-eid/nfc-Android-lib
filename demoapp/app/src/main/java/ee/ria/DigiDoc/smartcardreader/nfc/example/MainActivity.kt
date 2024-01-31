/*
 * Copyright 2017 - 2024 Riigi Infosüsteemi Amet
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

package ee.ria.DigiDoc.smartcardreader.nfc.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import ee.ria.DigiDoc.smartcardreader.nfc.example.configuration.ContainerConfiguration
import ee.ria.DigiDoc.smartcardreader.nfc.example.databinding.ActivityMainBinding
import ee.ria.libdigidocpp.Conf
import ee.ria.libdigidocpp.DigiDocConf
import ee.ria.libdigidocpp.DigiDocWrapperImpl
import ee.ria.libdigidocpp.digidoc
import timber.log.Timber
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var navigationController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Timber.plant(Timber.DebugTree())

        // install schema files
        val digidocWrapperImpl = DigiDocWrapperImpl(this)
        digidocWrapperImpl.install()

        // container configuration
        initLibDigiDocConfiguration(digidocWrapperImpl.schemaDirectory())

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navigationController = navHostFragment.navController

    }

    private fun initLibDigiDocConfiguration(schemaAbsolutePath: String) {

        try {
            Os.setenv("HOME", schemaAbsolutePath, true)
        } catch (ex: ErrnoException) {
            ex.printStackTrace()
            Timber.log(Log.ERROR, "Setting HOME environment variable failed")
        }
        val digiDocConf = DigiDocConf(schemaAbsolutePath)
        Conf.init(digiDocConf.transfer())

        val conf = ContainerConfiguration()
        conf.increaseLogLevel()
        conf.setupTSLFiles(schemaAbsolutePath)
        conf.overrideTSLUrl()
        conf.overrideTSLCert()
        conf.initTsaUrl()

        digidoc.initializeLib(getUserAgent(), schemaAbsolutePath)
    }

    private fun getUserAgent(): String {
        val message = StringBuilder()
        message.append("nfxexample/").append(getAppVersion(this))
        message.append(" (Android ").append(Build.VERSION.RELEASE).append(")")
        message.append(" Lang: ").append(Locale.getDefault().language)
        return message.toString()
    }

    private fun getAppVersion(context: Context): StringBuilder {
        val versionName = StringBuilder()
        try {
            versionName.append(
                context.packageManager.getPackageInfo(
                    context.packageName,
                    0
                ).versionName
            )
                .append(".")
                .append(context.packageManager.getPackageInfo(context.packageName, 0).versionCode)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return versionName
    }
}