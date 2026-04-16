package com.rfid.inventory.api

import com.rfid.inventory.BuildConfig
import com.rfid.inventory.model.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// ─── Endpoints de la API ──────────────────────────────────────────────────────

interface RfidApiService {

    // Scans
    @POST("scans")
    suspend fun registerScan(@Body scan: ScanRequest): Response<ScanResponse>

    @POST("scans/batch")
    suspend fun registerBatch(@Body batch: ScanBatchRequest): Response<List<ScanResponse>>

    @GET("scans")
    suspend fun getScans(
        @Query("epc") epc: String? = null,
        @Query("location_id") locationId: Int? = null,
        @Query("limit") limit: Int = 50
    ): Response<List<ScanResponse>>

    // Productos
    @GET("products")
    suspend fun getProducts(@Query("category") category: String? = null): Response<List<Product>>

    // Inventario
    @GET("inventory")
    suspend fun getInventory(
        @Query("location_id") locationId: Int? = null,
        @Query("low_stock_only") lowStockOnly: Boolean = false
    ): Response<List<InventoryItem>>

    // Ubicaciones
    @GET("locations")
    suspend fun getLocations(): Response<List<Location>>
}

// ─── Cliente Retrofit (singleton) ────────────────────────────────────────────

object ApiClient {

    val service: RfidApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RfidApiService::class.java)
    }
}
