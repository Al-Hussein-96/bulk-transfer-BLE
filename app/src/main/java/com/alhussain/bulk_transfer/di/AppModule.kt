package com.alhussain.bulk_transfer.di

import android.content.Context
import com.alhussain.bulk_transfer.data.bluetooth.AndroidBluetoothController
import com.alhussain.bulk_transfer.data.bluetooth.TransferMessageJsonAdapter
import com.alhussain.bulk_transfer.domain.BluetoothController
import com.alhussain.bulk_transfer.domain.model.PinOrder
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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
        val tempMoshi =
            Moshi
                .Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

        val voucherListType =
            Types.newParameterizedType(
                List::class.java,
                PinOrder::class.java,
            )

        val voucherListAdapter = tempMoshi.adapter<List<PinOrder>>(voucherListType)

        return Moshi
            .Builder()
            .add(TransferMessageJsonAdapter(voucherListAdapter)) // ✅ your adapter
            .addLast(KotlinJsonAdapterFactory()) // ✅ keep last
            .build()
    }

    @Provides
    @Singleton
    fun provideBluetoothController(
        @ApplicationContext context: Context,
        moshi: Moshi,
    ): BluetoothController = AndroidBluetoothController(context, moshi)
}
