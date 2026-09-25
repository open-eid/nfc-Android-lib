// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.libdigidocpp.di

import dagger.Binds
import dagger.Module
import ee.ria.libdigidocpp.DigiDocWrapper
import ee.ria.libdigidocpp.DigiDocWrapperImpl
import javax.inject.Singleton

@Module(includes = [DigiDocModule.Definitions::class])
object DigiDocModule {

    @Module
    internal interface Definitions {

        @Binds
        @Singleton
        fun bindDigiDoc(digiDoc: DigiDocWrapperImpl): DigiDocWrapper
    }
}
