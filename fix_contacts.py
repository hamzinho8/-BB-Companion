with open('app/src/main/java/com/hamza/blackberrybridge/contacts/ContactManager.kt', 'r') as f:
    content = f.read()

bad_snippet = """object ContactManager {    private const val TAG    @SuppressLint("Range")    fun getContactNameByNumber(context: Context, phoneNumber: String): String {        if (phoneNumber.isEmpty() || phoneNumber == "Inconnu" || phoneNumber == "Unknown") return phoneNumber        try {            val uri = android.net.Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(phoneNumber))            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)            cursor?.use {                if (it.moveToFirst()) {                    return it.getString(it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)) ?: phoneNumber                }            }        } catch (e: Exception) {            Log.e(TAG, "Error looking up contact name", e)        }        return phoneNumber    } = "ContactManager\""""

good_snippet = """object ContactManager {
    private const val TAG = "ContactManager"
    
    @SuppressLint("Range")
    fun getContactNameByNumber(context: Context, phoneNumber: String): String {
        if (phoneNumber.isEmpty() || phoneNumber == "Inconnu" || phoneNumber == "Unknown") return phoneNumber
        try {
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
    }"""

content = content.replace(bad_snippet, good_snippet)

with open('app/src/main/java/com/hamza/blackberrybridge/contacts/ContactManager.kt', 'w') as f:
    f.write(content)
