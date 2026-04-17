# 📡 RFID Inventory — Android App

App Android para la pistola **HT518-R501** (Android 13, módulo UHF Nation N611 / Impinj E710).
Lee etiquetas RFID y sincroniza las lecturas con una API FastAPI + MySQL.

---

## Pantallas

| Escaneo | Inventario | Productos |
|---|---|---|
| Lectura en tiempo real, acción (ENTRADA/SALIDA/INVENTARIO), historial | Stock por ubicación, filtro de bajo mínimo, pull-to-refresh | Catálogo, crear producto desde la pistola |

---

## Arquitectura

```
app/
├── RfidApp.kt                         ← Application: inicializa DB y API
├── rfid/
│   └── RfidManager.kt                 ← Abstracción del SDK UHF del fabricante
├── data/
│   ├── remote/
│   │   └── ApiService.kt              ← Retrofit — todos los endpoints FastAPI
│   └── local/
│       └── AppDatabase.kt             ← Room — cola offline + historial local
├── repository/
│   └── ScanRepository.kt             ← Lógica online/offline, flush batch
└── ui/
    ├── scan/   ScanActivity · ScanViewModel · ScanAdapter
    ├── inventory/ InventoryActivity · InventoryViewModel · InventoryAdapter
    └── product/   ProductActivity · ProductViewModel · ProductAdapter
```

---

## Configuración

### 1. IP del servidor FastAPI

Abre `app/build.gradle` y cambia la IP por la de tu PC en la red local:

```gradle
buildConfigField "String", "API_BASE_URL", '"http://TU_IP:8000/api/v1/"'
```

Para ver tu IP en Kubuntu:
```bash
ip a | grep "inet " | grep -v 127
```

La pistola y el PC deben estar en la **misma red WiFi**.

### 2. ID del lector (opcional)

Si tienes varias pistolas, asigna un ID único a cada una:

```gradle
buildConfigField "String", "READER_ID", '"HT518-R501-01"'
```

---

## Integración del SDK RFID

El módulo UHF está integrado en la pistola. El SDK (.aar) lo proporciona el fabricante.

### Pasos

1. Copia el `.aar` en `app/libs/`

2. En `app/build.gradle` añade:
   ```gradle
   implementation fileTree(dir: 'libs', include: ['*.aar', '*.jar'])
   ```

3. Abre `app/src/main/java/com/rfid/inventory/rfid/RfidManager.kt`
   y sustituye los bloques `// TODO SDK` con las llamadas reales.

**Ejemplo para Nation Rfid N611:**
```kotlin
private lateinit var reader: RFIDReader

fun connect(): Boolean {
    reader = RFIDReader(context)
    reader.open()
    reader.setInventoryCallBack { tag ->
        _tagChannel.trySend(RfidTag(epc = tag.epc, rssi = tag.rssi.toFloat()))
    }
    return true
}

fun startScan()  { reader.startInventory() }
fun stopScan()   { reader.stopInventory() }
fun disconnect() { reader.close() }
```

### Gatillo físico

La pistola envía un `BroadcastIntent` al pulsar el gatillo.
`ScanActivity` ya tiene el receiver registrado. Solo actualiza el action
en `AndroidManifest.xml` con el valor exacto de tu SDK:

```xml
<action android:name="com.rfid.ACTION_SCAN_RESULT" />
```

---

## Modo offline

Si la pistola pierde WiFi, los scans se guardan automáticamente en Room (SQLite local).
Cuando vuelve la conexión, aparece el botón **"Enviar X pendientes"** que los manda
todos en un único `POST /scans/batch`.

---

## Compilar e instalar

```bash
# Desde Android Studio:  Run → Run 'app'

# O desde terminal (con ADB conectado):
./gradlew installDebug
```

La pistola necesita **Depuración USB** activada:
`Ajustes → Opciones de desarrollador → Depuración USB`

---

## Backend (FastAPI + MySQL)

El servidor está en el repositorio [`rfid-inventory`](../rfid-inventory).

```bash
cd rfid-inventory
bash setup.sh     # instala MySQL, Python, crea la BD
nano .env         # ajusta credenciales
bash start.sh     # arranca en http://0.0.0.0:8000
```

Documentación interactiva: `http://TU_IP:8000/docs`

---

## Tecnologías

- **Kotlin** + Coroutines + StateFlow
- **Retrofit 2** — HTTP a la API
- **Room** — base de datos local (offline)
- **Material Design 3** — UI
- **ViewModel** + **Repository pattern**
