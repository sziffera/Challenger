package com.sziffer.challenger.utils.bluetooth

import java.util.*

object GattHeartRateAttributes {
    private val attributes: HashMap<String?, String?> = HashMap()

    // Heart Rate Service UUIDs
    var UUID_HEART_RATE_SERVICE = "0000180d-0000-1000-8000-00805f9b34fb"
    var UUID_HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb"
    var UUID_BODY_SENSOR_LOCATION = "00002a38-0000-1000-8000-00805f9b34fb"
    var UUID_HEART_RATE_CONTROL_POINT = "00002a39-0000-1000-8000-00805f9b34fb"

    // Descriptor for enabling notification on HEART_RATE_MEASUREMENT characteristic
    var UUID_CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"

    // Battery Service
    var UUID_BATTERY_SERVICE = "0000180f-0000-1000-8000-00805f9b34fb"
    var UUID_BATTERY_LEVEL = "00002a19-0000-1000-8000-00805f9b34fb"
    fun lookup(uuid: String?, defaultName: String): String {
        val name = attributes[uuid]
        return name ?: defaultName
    }

    init {
        /**
         * Heart Rate device GATT services
         */
        attributes[UUID_HEART_RATE_SERVICE] = "Servicio cardiovascular"
        attributes["0000180a-0000-1000-8000-00805f9b34fb"] = "Servicio de info del dispositivo"
        attributes["00001800-0000-1000-8000-00805f9b34fb"] = "Acceso genérico"
        attributes["00001801-0000-1000-8000-00805f9b34fb"] = "Atributo general"
        attributes[UUID_BATTERY_SERVICE] = "Servicio de batería"
        /**
         * Heart Rate device GATT characteristics
         */

        // Generic Access
        attributes["00002a00-0000-1000-8000-00805f9b34fb"] = "Nombre del dispositivo"
        attributes["00002a01-0000-1000-8000-00805f9b34fb"] = "Apariencia"
        attributes["00002a02-0000-1000-8000-00805f9b34fb"] = "Peripheral Privacy Flag"
        attributes["00002a04-0000-1000-8000-00805f9b34fb"] = "Peripheral Preferred ConParam"


        // Heart Rate Service
        attributes[UUID_HEART_RATE_MEASUREMENT] = "Medida del ritmo cardíaco"
        attributes[UUID_BODY_SENSOR_LOCATION] = "Ubicación del sensor"
        attributes[UUID_HEART_RATE_CONTROL_POINT] =
            "Punto de control"

        // Device Information Service
        attributes["00002a29-0000-1000-8000-00805f9b34fb"] = "Nombre del manufacturador"
        attributes["00002a24-0000-1000-8000-00805f9b34fb"] = "Numero de modelo"
        attributes["00002a26-0000-1000-8000-00805f9b34fb"] = "Revisión del firmware"
        attributes["00002a27-0000-1000-8000-00805f9b34fb"] = "Revisión del hardware"
        attributes["00002a2a-0000-1000-8000-00805f9b34fb"] = "Lista de datos de registro"
        attributes["00002a50-0000-1000-8000-00805f9b34fb"] = "PnP ID"


        // Battery Service
        attributes[UUID_BATTERY_LEVEL] = "Nivel de batería"
    }
}
