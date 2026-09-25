// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

@file:Suppress("PackageName")

package ee.ria.DigiDoc.utilsLib.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil
import java.util.logging.Logger
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class UtilModules {
    @Provides
    @Singleton
    fun provideLoggingUtil(context: Context): LoggingUtil =
        LoggingUtil().apply {
            LoggingUtil.initialize(context, Logger.getLogger(UtilModules::class.java.name), false)
        }
}
