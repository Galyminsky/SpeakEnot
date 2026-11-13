package com.galyaminsky.speakenot.di

import android.content.Context
import androidx.room.Room
import com.galyaminsky.speakenot.data.DictionaryRepositoryImpl
import com.galyaminsky.speakenot.data.FavoriteRepositoryImpl
import com.galyaminsky.speakenot.data.HistoryRepositoryImpl
import com.galyaminsky.speakenot.data.AndroidNetworkAvailability
import com.galyaminsky.speakenot.data.local.AppDatabase
import com.galyaminsky.speakenot.data.remote.SkyengApi
import com.galyaminsky.speakenot.data.remote.SkyengDataSource
import com.galyaminsky.speakenot.domain.DictionaryRepository
import com.galyaminsky.speakenot.domain.FavoriteRepository
import com.galyaminsky.speakenot.domain.HistoryRepository
import com.galyaminsky.speakenot.domain.NetworkAvailability
import com.galyaminsky.speakenot.ui.MeaningImageLoader
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val networkAvailability: NetworkAvailability = AndroidNetworkAvailability(context.applicationContext)
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val api = Retrofit.Builder()
        .baseUrl("https://dictionary.skyeng.ru/api/public/v1/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SkyengApi::class.java)

    val dictionaryRepository: DictionaryRepository = DictionaryRepositoryImpl(SkyengDataSource(api, networkAvailability))
    val imageLoader = MeaningImageLoader(client, networkAvailability)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "speakenot.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }
    val favoriteRepository: FavoriteRepository by lazy { FavoriteRepositoryImpl(database.favorites()) }
    val historyRepository: HistoryRepository by lazy { HistoryRepositoryImpl(database.history()) }
}
