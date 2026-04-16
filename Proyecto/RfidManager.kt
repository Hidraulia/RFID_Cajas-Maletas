package com.rfid.inventory.api

/**
 * RfidManager — Abstracción del SDK del HT518-R501
 *
 * La pistola HT518-R501 lleva uno de estos dos módulos (según configuración):
 *   · Nation Rfid N611
 *   · Impinj E710
 *
 * Ambos exponen una API Android similar a través del SDK del fabricante.
 * El SDK se integra como .aar o .jar que el fabricante proporciona al comprar el equipo.
 *
 * PASOS PARA INTEGRAR EL SDK REAL:
 * 1. Solicita el SDK Android al fabricante (suele llamarse "UHF_SDK.aar" o similar).
 * 2. Cópialo en app/libs/.
 * 3. En build.gradle añade: implementation fileTree(dir: 'libs', include: ['*.aar', '*.jar'])
 * 4. Reemplaza los bloques marcados con "TODO SDK" con las llamadas reales del SDK.
 *
 * Mientras no tengas el SDK, esta clase simula lecturas para desarrollar y probar la app.
 */

import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

data class RfidTag(
    val epc: String,
    val rssi: Float?,
    val readCount: Int = 1
)

class RfidManager(private val context: Context) {

    private val TAG = "RfidManager"
    private val _tagChannel = Channel<RfidTag>(Channel.BUFFERED)

    /** Flow al que se suscribe la UI para recibir tags en tiempo real */
    val tagFlow: Flow<RfidTag> = _tagChannel.receiveAsFlow()

    private var isConnected = false
    private var isScanning = false

    // TODO SDK: sustituye por la clase de inicialización real del SDK
    // Ejemplo Nation N611:  private val reader = RFIDReader(context)
    // Ejemplo Impinj E710:  private val reader = ImpinjReader()

    /**
     * Conecta con el módulo UHF integrado en la pistola.
     * En los dispositivos HT518 el módulo RFID es interno y se accede como servicio del sistema.
     */
    fun connect(): Boolean {
        return try {
            // TODO SDK: reader.connect() o reader.open()
            // Código real Nation N611:
            //   reader.open()
            //   reader.setInventoryCallBack(inventoryCallback)
            //   reader.setAntennaConfiguration(AntennaConfiguration.build().txPower(30))

            isConnected = true
            Log.i(TAG, "Módulo RFID conectado")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al conectar con el módulo RFID: ${e.message}")
            false
        }
    }

    /**
     * Inicia el inventariado continuo (modo trigger o modo freerun).
     * Cada EPC leído se emite por [tagFlow].
     */
    fun startScan() {
        if (!isConnected || isScanning) return

        isScanning = true
        Log.i(TAG, "Iniciando lectura RFID...")

        // TODO SDK: reader.startInventory() o similar
        // El SDK invocará el callback cada vez que lea una etiqueta.
        // Ejemplo de callback para Nation N611:
        //
        //   val inventoryCallback = object : InventoryCallBack {
        //       override fun getInventoryTagInfo(tag: TagInfo) {
        //           val rfidTag = RfidTag(
        //               epc  = tag.epc,
        //               rssi = tag.rssi.toFloat()
        //           )
        //           _tagChannel.trySend(rfidTag)
        //       }
        //   }

        // ── MODO SIMULACIÓN (eliminar cuando tengas el SDK real) ──────────────
        // Simula lecturas para poder desarrollar sin la pistola física.
        // _tagChannel.trySend(RfidTag("E2003412013F0100B55E57C3", rssi = -55f))
        // ─────────────────────────────────────────────────────────────────────
    }

    /**
     * Para la lectura continua.
     */
    fun stopScan() {
        if (!isScanning) return
        // TODO SDK: reader.stopInventory()
        isScanning = false
        Log.i(TAG, "Lectura RFID detenida")
    }

    /**
     * Notifica una lectura manual (por ejemplo, al pulsar el gatillo físico de la pistola).
     * La pistola puede configurarse para enviar un Intent broadcast al pulsar el gatillo.
     * Llama a este método desde el BroadcastReceiver que captura ese Intent.
     */
    fun onTriggerScan(epc: String, rssi: Float? = null) {
        _tagChannel.trySend(RfidTag(epc = epc, rssi = rssi))
    }

    fun disconnect() {
        stopScan()
        // TODO SDK: reader.close()
        isConnected = false
    }
}
