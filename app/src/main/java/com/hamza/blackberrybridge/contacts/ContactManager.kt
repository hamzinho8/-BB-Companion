package com.hamza.blackberrybridge.contacts

import android.annotation.SuppressLint
import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.hamza.blackberrybridge.bluetooth.BluetoothService
import com.hamza.blackberrybridge.protocol.BSBPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object ContactManager {
    private const val TAG = "ContactManager"
    private const val MAX_RESULTS = 50 // Limit to avoid overloading Bluetooth buffer
    
    @SuppressLint("Range")
    fun getContactNameByNumber(context: Context, phoneNumber: String): String {
        if (phoneNumber.isEmpty() || phoneNumber == "Inconnu" || phoneNumber == "Unknown") return phoneNumber
        try {
            // First check in VIP contacts (fastest)
            VipContactManager.init(context)
            val vips = VipContactManager.vipContacts.value
            val matchVip = vips.firstOrNull { 
                val clean1 = it.number.replace(" ", "").replace("-", "")
                val clean2 = phoneNumber.replace(" ", "").replace("-", "")
                clean1.endsWith(clean2) || clean2.endsWith(clean1)
            }
            if (matchVip != null) {
                return matchVip.name
            }

            val uri = android.net.Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(phoneNumber))
            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)) ?: phoneNumber
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact name", e)
        }
        return phoneNumber
    }

    @SuppressLint("Range")
    fun searchContacts(context: Context, query: String) {
        GlobalScope.launch(Dispatchers.IO) {
            val service = context as? BluetoothService ?: return@launch
            VipContactManager.init(context)
            val vips = VipContactManager.vipContacts.value

            // If user configured selective VIP contacts, and query is empty/all or matches VIPs:
            if (vips.isNotEmpty()) {
                val cleanQuery = query.trim().lowercase()
                val matchingVips = if (cleanQuery.isEmpty() || cleanQuery == "all" || cleanQuery == "vip" || cleanQuery == "*") {
                    vips
                } else {
                    vips.filter { it.name.lowercase().contains(cleanQuery) || it.number.contains(cleanQuery) }
                }

                if (matchingVips.isNotEmpty() || cleanQuery.isEmpty() || cleanQuery == "all") {
                    service.sendPacket(BSBPacket("CONTACTS_START", listOf(matchingVips.size.toString(), "VIP")))
                    for (c in matchingVips) {
                        service.sendPacket(BSBPacket("CONTACT", listOf(c.id, c.name, c.number)))
                    }
                    service.sendPacket(BSBPacket("CONTACTS_END", listOf(matchingVips.size.toString())))
                    Log.d(TAG, "Returned ${matchingVips.size} selective VIP contacts")
                    return@launch
                }
            }

            // Otherwise, fallback to system contacts search
            try {
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$query%"),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC LIMIT $MAX_RESULTS"
                )
                
                cursor?.use {
                    var count = 0
                    while (it.moveToNext()) {
                        val id = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)) ?: ""
                        val name = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: "Unknown"
                        val number = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                        
                        service.sendPacket(BSBPacket("CONTACT", listOf(id, name, number)))
                        count++
                    }
                    service.sendPacket(BSBPacket("CONTACTS_END", listOf(count.toString())))
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing READ_CONTACTS permission", e)
                service.sendPacket(BSBPacket("ERROR", listOf("RESTRICTED_PERMISSION_MISSING")))
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching contacts", e)
            }
        }
    }
}
