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
