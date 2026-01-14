package com.alhussain.bulk_transfer.di

import android.content.Context
import com.alhussain.bulk_transfer.data.bluetooth.AndroidBluetoothController
import com.alhussain.bulk_transfer.domain.BluetoothController
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideBluetoothController(@ApplicationContext context: Context, moshi: Moshi): BluetoothController {
        return AndroidBluetoothController(context, moshi)
    }
}
