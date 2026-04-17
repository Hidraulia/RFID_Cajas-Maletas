package com.rfid.inventory.repository

import android.util.Log
import com.rfid.inventory.data.local.AppDatabase
import com.rfid.inventory.data.local.PendingScanEntity
import com.rfid.inventory.data.local.ScanHistoryEntity
import com.rfid.inventory.data.remote.ApiService
import com.rfid.inventory.model.ScanBatchRequest
import com.rfid.inventory.model.ScanRequest
import com.rfid.inventory.model.ScanResponse

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

class ScanRepository(
    private val api: ApiService,
    private val db: AppDatabase
) {

    private val TAG = "ScanRepository"

    /**
     * Envía una lectura a la API.
     * Si falla por red, la guarda localmente para enviarla después.
     */
    suspend fun sendScan(request: ScanRequest): Result<ScanResponse> {
        return try {
            val response = api.registerScan(request)
            if (response.isSuccessful) {
                val scan = response.body()!!
                // Guardar en historial local
                db.scanHistoryDao().insertAll(listOf(scan.toHistoryEntity()))
                Result.Success(scan)
            } else {
                val msg = "Error servidor: ${response.code()}"
                Log.w(TAG, msg)
                savePending(request)
                Result.Error(msg)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sin red, guardando localmente: ${e.message}")
            savePending(request)
            Result.Error("Sin conexión — lectura guardada localmente")
        }
    }

    /**
     * Envía todos los scans pendientes en un batch.
     * Retorna el número de scans enviados con éxito.
     */
    suspend fun flushPending(): Int {
        val pending = db.pendingScanDao().getAll()
        if (pending.isEmpty()) return 0

        return try {
            val requests = pending.map { it.toRequest() }
            val response = api.registerBatch(ScanBatchRequest(requests))
            if (response.isSuccessful) {
                val sent = response.body()!!
                // Guardar en historial y limpiar pendientes
                db.scanHistoryDao().insertAll(sent.map { it.toHistoryEntity() })
                db.pendingScanDao().deleteAll()
                Log.i(TAG, "Flush OK: ${sent.size} scans enviados")
                sent.size
            } else {
                Log.w(TAG, "Flush error: ${response.code()}")
                0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Flush sin red: ${e.message}")
            0
        }
    }

    suspend fun getPendingCount(): Int = db.pendingScanDao().count()

    suspend fun getLocalHistory(): List<ScanHistoryEntity> =
        db.scanHistoryDao().getRecent()

    private suspend fun savePending(request: ScanRequest) {
        db.pendingScanDao().insert(
            PendingScanEntity(
                epc = request.epc,
                readerId = request.readerId,
                locationId = request.locationId,
                action = request.action,
                rssi = request.rssi
            )
        )
    }

    private fun PendingScanEntity.toRequest() = ScanRequest(
        epc = epc,
        readerId = readerId,
        locationId = locationId,
        action = action,
        rssi = rssi
    )

    private fun ScanResponse.toHistoryEntity() = ScanHistoryEntity(
        id = id,
        epc = epc,
        productName = productName,
        action = action,
        locationId = locationId,
        scannedAt = scannedAt
    )
}
