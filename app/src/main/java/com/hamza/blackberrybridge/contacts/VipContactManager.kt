package com.hamza.blackberrybridge.contacts

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.ContactsContract
import android.util.Log
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket
import com.hamza.blackberrybridge.state.BridgeStateManager
import com.hamza.blackberrybridge.state.EventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VipContact(
    val id: String,
    val name: String,
    val number: String
)

object VipContactManager {
    private const val TAG = "VipContactManager"
    private const val PREFS_NAME = "bb_vip_contacts_prefs"
    private const val KEY_VIP_LIST = "vip_contacts_json"
    private const val KEY_LAST_SYNC = "last_sync_time"
    const val MAX_VIP_CONTACTS = 10

    private val _vipContacts = MutableStateFlow<List<VipContact>>(emptyList())
    val vipContacts: StateFlow<List<VipContact>> = _vipContacts.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow<String?>(null)
    val lastSyncTimestamp: StateFlow<String?> = _lastSyncTimestamp.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = getPrefs(context)
        loadFromPrefs(prefs)
        _lastSyncTimestamp.value = prefs.getString(KEY_LAST_SYNC, null)
        isInitialized = true
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun loadFromPrefs(prefs: SharedPreferences) {
        val jsonString = prefs.getString(KEY_VIP_LIST, null)
        if (!jsonString.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(jsonString)
                val list = mutableListOf<VipContact>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        VipContact(
                            id = obj.optString("id", ""),
                            name = obj.optString("name", "Inconnu"),
                            number = obj.optString("number", "")
                        )
                    )
                }
                _vipContacts.value = list
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing saved VIP contacts", e)
                _vipContacts.value = emptyList()
            }
        } else {
            _vipContacts.value = emptyList()
        }
    }

    private fun saveToPrefs(context: Context, list: List<VipContact>) {
        val prefs = getPrefs(context)
        try {
            val jsonArray = JSONArray()
            for (c in list) {
                val obj = JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("number", c.number)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_VIP_LIST, jsonArray.toString()).apply()
            _vipContacts.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error saving VIP contacts", e)
        }
    }

    fun isVip(contactId: String, number: String): Boolean {
        return _vipContacts.value.any { it.id == contactId || (number.isNotEmpty() && it.number == number) }
    }

    /**
     * Toggles VIP status.
     * Returns true if successfully added or removed.
     * Returns false if attempting to add beyond MAX_VIP_CONTACTS.
     */
    fun toggleVip(context: Context, contact: VipContact): Boolean {
        init(context)
        val current = _vipContacts.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == contact.id || it.number == contact.number }

        if (existingIndex >= 0) {
            current.removeAt(existingIndex)
            saveToPrefs(context, current)
            BridgeStateManager.logEvent("Contact retiré des VIP : ${contact.name}", EventType.INFO)
            return true
        } else {
            if (current.size >= MAX_VIP_CONTACTS) {
                return false // Limit reached
            }
            current.add(contact)
            saveToPrefs(context, current)
            BridgeStateManager.logEvent("Contact ajouté aux VIP : ${contact.name}", EventType.SUCCESS)
            return true
        }
    }

    fun removeVip(context: Context, contactId: String) {
        init(context)
        val current = _vipContacts.value.toMutableList()
        val removed = current.removeAll { it.id == contactId }
        if (removed) {
            saveToPrefs(context, current)
        }
    }

    /**
     * Fetches contacts from the Android phone provider with an optional search query.
     */
    @SuppressLint("Range")
    fun fetchPhoneContacts(context: Context, query: String = ""): List<VipContact> {
        val results = mutableListOf<VipContact>()
        val seenNumbers = HashSet<String>()
        try {
            val selection = if (query.isBlank()) {
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} IS NOT NULL"
            } else {
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? AND ${ContactsContract.CommonDataKinds.Phone.NUMBER} IS NOT NULL"
            }
            val selectionArgs = if (query.isBlank()) null else arrayOf("%$query%")

            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC LIMIT 200"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)) ?: ""
                    val name = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: "Sans nom"
                    val number = (it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: "").trim()

                    val cleanNumber = number.replace(" ", "").replace("-", "")
                    if (cleanNumber.isNotEmpty() && !seenNumbers.contains(cleanNumber)) {
                        seenNumbers.add(cleanNumber)
                        results.add(VipContact(id = id, name = name, number = number))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission READ_CONTACTS missing", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching phone contacts", e)
        }
        return results
    }

    /**
     * Sends the selected VIP contacts to the connected BlackBerry device via Bluetooth SPP.
     */
    fun syncVipContactsToBlackBerry(context: Context): Boolean {
        init(context)
        val service = BluetoothService.instance
        if (service == null || !BridgeStateManager.isConnected.value) {
            BridgeStateManager.logEvent("Échec sync contacts: BlackBerry non connecté", EventType.WARNING)
            return false
        }

        val vips = _vipContacts.value
        if (vips.isEmpty()) {
            // Inform BlackBerry that VIP list is empty
            service.sendPacket(BSBPacket("CONTACTS_CLEAR", emptyList()))
            service.sendPacket(BSBPacket("CONTACTS_START", listOf("0", "VIP")))
            service.sendPacket(BSBPacket("CONTACTS_END", listOf("0")))
            BridgeStateManager.logEvent("Synchronisation : Aucun contact VIP sélectionné", EventType.INFO)
            return true
        }

        // 1. Send Clear / Start
        service.sendPacket(BSBPacket("CONTACTS_CLEAR", emptyList()))
        service.sendPacket(BSBPacket("CONTACTS_START", listOf(vips.size.toString(), "VIP")))

        // 2. Transmit each VIP contact
        for (contact in vips) {
            service.sendPacket(BSBPacket("CONTACT", listOf(contact.id, contact.name, contact.number)))
        }

        // 3. Finalize
        service.sendPacket(BSBPacket("CONTACTS_END", listOf(vips.size.toString())))

        // Save timestamp
        val timeStr = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault()).format(Date())
        _lastSyncTimestamp.value = timeStr
        getPrefs(context).edit().putString(KEY_LAST_SYNC, timeStr).apply()

        BridgeStateManager.logEvent("👤 ${vips.size} contacts VIP synchronisés vers le BlackBerry", EventType.SUCCESS)
        return true
    }
}
