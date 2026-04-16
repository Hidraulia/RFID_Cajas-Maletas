package com.rfid.inventory.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rfid.inventory.api.ApiClient
import com.rfid.inventory.api.RfidManager
import com.rfid.inventory.model.Location
import com.rfid.inventory.model.ScanRequest
import com.rfid.inventory.model.ScanResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScanUiState(
    val isScanning: Boolean = false,
    val recentScans: List<ScanResponse> = emptyList(),
    val locations: List<Location> = emptyList(),
    val selectedLocationId: Int? = null,
    val selectedAction: String = "INVENTARIO",
    val error: String? = null,
    val lastScan: ScanResponse? = null,
    // Lecturas pendientes de envío (modo offline)
    val pendingCount: Int = 0
)

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val api = ApiClient.service
    val rfidManager = RfidManager(app)

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    // Cola offline: si no hay red, guardamos aquí los scans pendientes
    private val pendingScans = mutableListOf<ScanRequest>()
    private val READER_ID = "HT518-R501"

    init {
        loadLocations()
        observeRfidTags()
        rfidManager.connect()
    }

    private fun observeRfidTags() {
        viewModelScope.launch {
            rfidManager.tagFlow.collect { tag ->
                if (_uiState.value.isScanning) {
                    sendScan(
                        epc = tag.epc,
                        rssi = tag.rssi
                    )
                }
            }
        }
    }

    private fun loadLocations() {
        viewModelScope.launch {
            try {
                val response = api.getLocations()
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        locations = response.body() ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Sin conexión con la API")
            }
        }
    }

    fun toggleScanning() {
        val scanning = !_uiState.value.isScanning
        _uiState.value = _uiState.value.copy(isScanning = scanning)
        if (scanning) rfidManager.startScan() else rfidManager.stopScan()
    }

    fun setLocation(locationId: Int?) {
        _uiState.value = _uiState.value.copy(selectedLocationId = locationId)
    }

    fun setAction(action: String) {
        _uiState.value = _uiState.value.copy(selectedAction = action)
    }

    /**
     * Envía una lectura a la API.
     * Si no hay conexión, la guarda en la cola pendiente para enviarla después.
     */
    fun sendScan(epc: String, rssi: Float? = null) {
        val state = _uiState.value
        val request = ScanRequest(
            epc = epc,
            readerId = READER_ID,
            locationId = state.selectedLocationId,
            action = state.selectedAction,
            rssi = rssi
        )

        viewModelScope.launch {
            try {
                val response = api.registerScan(request)
                if (response.isSuccessful) {
                    val scan = response.body()!!
                    val updated = listOf(scan) + _uiState.value.recentScans.take(49)
                    _uiState.value = _uiState.value.copy(
                        recentScans = updated,
                        lastScan = scan,
                        error = null
                    )
                    // Si teníamos pendientes, intentar enviarlos ahora
                    if (pendingScans.isNotEmpty()) flushPending()
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Error API: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                // Sin red: encolar para envío posterior
                pendingScans.add(request)
                _uiState.value = _uiState.value.copy(
                    pendingCount = pendingScans.size,
                    error = "Sin red — ${pendingScans.size} scan(s) en cola"
                )
            }
        }
    }

    /** Envía todos los scans pendientes (modo batch) */
    fun flushPending() {
        if (pendingScans.isEmpty()) return
        val batch = pendingScans.toList()
        viewModelScope.launch {
            try {
                val response = api.registerBatch(
                    com.rfid.inventory.model.ScanBatchRequest(batch)
                )
                if (response.isSuccessful) {
                    pendingScans.clear()
                    _uiState.value = _uiState.value.copy(
                        pendingCount = 0,
                        error = null
                    )
                }
            } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        rfidManager.disconnect()
    }
}
