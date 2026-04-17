package com.rfid.inventory.rfid

import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

data class RfidTag(
    val epc: String,
    val rssi: Float? = null,
    val readCount: Int = 1
)

/**
 * RfidManager — Capa de abstracción sobre el SDK del HT518-R501.
 *
 * El módulo UHF puede ser Nation Rfid N611 o Impinj E710 según configuración.
 * El SDK en formato .aar lo proporciona el fabricante al adquirir el equipo.
 *
 * ─── INTEGRACIÓN DEL SDK REAL ──────────────────────────────────────────────
 * 1. Copia el .aar en app/libs/
 * 2. En app/build.gradle añade dentro de dependencies:
 *      implementation fileTree(dir: 'libs', include: ['*.aar', '*.jar'])
 * 3. Sustituye los bloques "TODO SDK" de esta clase con las llamadas reales.
 *
 * Ejemplo de implementación con Nation Rfid N611:
 *
 *   private lateinit var reader: RFIDReader   // clase del SDK
 *
 *   fun connect(): Boolean {
 *       reader = RFIDReader(context)
 *       reader.open()
 *       reader.setInventoryCallBack { tag ->
 *           _tagChannel.trySend(RfidTag(epc = tag.epc, rssi = tag.rssi.toFloat()))
 *       }
 *       return true
 *   }
 *
 *   fun startScan() { reader.startInventory() }
 *   fun stopScan()  { reader.stopInventory() }
 *   fun disconnect() { reader.close() }
 *
 * ─── TRIGGER FÍSICO ────────────────────────────────────────────────────────
 * La pistola lanza un broadcast Intent al pulsar el gatillo.
 * Registra un BroadcastReceiver en ScanActivity para capturarlo:
 *
 *   val filter = IntentFilter("com.rfid.ACTION_SCAN_RESULT")
 *   registerReceiver(triggerReceiver, filter)
 *
 *   val triggerReceiver = object : BroadcastReceiver() {
 *       override fun onReceive(ctx: Context, intent: Intent) {
 *           val epc = intent.getStringExtra("epc") ?: return
 *           val rssi = intent.getFloatExtra("rssi", 0f)
 *           viewModel.rfidManager.onTriggerScan(epc, rssi)
 *       }
 *   }
 *
 * El nombre del extra y del action exacto viene en la documentación del SDK.
 */
class RfidManager(private val context: Context) {

    private val TAG = "RfidManager"
    private val _tagChannel = Channel<RfidTag>(Channel.BUFFERED)
    val tagFlow: Flow<RfidTag> = _tagChannel.receiveAsFlow()

    private var connected = false
    private var scanning = false

    // TODO SDK: private lateinit var reader: RFIDReader

    fun connect(): Boolean {
        return try {
            // TODO SDK: reader = RFIDReader(context); reader.open()
            connected = true
            Log.i(TAG, "Módulo RFID conectado")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error conectando RFID: ${e.message}")
            false
        }
    }

    fun startScan() {
        if (!connected || scanning) return
        scanning = true
        // TODO SDK: reader.startInventory()
        Log.i(TAG, "Escaneo RFID iniciado")
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        // TODO SDK: reader.stopInventory()
        Log.i(TAG, "Escaneo RFID detenido")
    }

    /**
     * Llamado desde el BroadcastReceiver del gatillo físico.
     */
    fun onTriggerScan(epc: String, rssi: Float? = null) {
        if (epc.isBlank()) return
        _tagChannel.trySend(RfidTag(epc = epc.uppercase().trim(), rssi = rssi))
        Log.d(TAG, "Tag recibido: $epc (rssi=$rssi)")
    }

    fun isConnected() = connected
    fun isScanning() = scanning

    fun disconnect() {
        stopScan()
        // TODO SDK: reader.close()
        connected = false
    }
}
