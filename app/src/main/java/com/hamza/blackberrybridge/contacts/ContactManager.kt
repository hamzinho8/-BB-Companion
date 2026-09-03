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
    fun searchContacts(context: Context, query: String) {
        GlobalScope.launch(Dispatchers.IO) {
            val service = context as? BluetoothService ?: return@launch
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
                        val id = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
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
