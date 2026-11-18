package com.example.yesmin.data

import android.Manifest
import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import com.example.yesmin.database.AssistantCallLogDao
import com.example.yesmin.database.AssistantCallLogEntity
import com.example.yesmin.security.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ContactsRepository(
    private val context: Context,
    private val assistantCallLogDao: AssistantCallLogDao
) {
    val assistantCallLogs: Flow<List<AssistantCallLogEntity>> = assistantCallLogDao.observeCallLogs()

    suspend fun getContacts(query: String = ""): List<ContactInfo> = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasPermission(context, Manifest.permission.READ_CONTACTS)) {
            return@withContext emptyList()
        }
        val results = mutableListOf<ContactInfo>()
        val seenNumbers = HashSet<String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        val selection = if (query.isNotBlank()) {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        } else null
        val selectionArgs = if (query.isNotBlank()) {
            arrayOf("%$query%", "%$query%")
        } else null

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)

            while (cursor.moveToNext() && results.size < 250) {
                val id = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx) ?: continue
                val number = cursor.getString(numIdx)?.trim() ?: continue
                val normalized = number.replace(Regex("[\\s\\-()]"), "")
                if (normalized.isNotEmpty() && seenNumbers.add("$name|$normalized")) {
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        cursor.getInt(typeIdx),
                        "Mobile"
                    ).toString()
                    results.add(ContactInfo(id = id, name = name, phoneNumber = number, label = label))
                }
            }
        }
        results
    }

    suspend fun findBestContactMatch(spokenName: String): ContactInfo? {
        val cleaned = spokenName.trim().lowercase()
        if (cleaned.isEmpty()) return null
        val all = getContacts()
        return all.firstOrNull { it.name.lowercase() == cleaned }
            ?: all.firstOrNull { it.name.lowercase().startsWith(cleaned) }
            ?: all.firstOrNull { it.name.lowercase().contains(cleaned) }
    }

    suspend fun resolveCallerName(phoneNumber: String): String? = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasPermission(context, Manifest.permission.READ_CONTACTS) || phoneNumber.isBlank()) {
            return@withContext null
        }
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(phoneNumber)
        )
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return@withContext cursor.getString(0)
            }
        }
        null
    }

    suspend fun getDeviceCallLog(limit: Int = 30): List<DeviceCallLogEntry> = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasPermission(context, Manifest.permission.READ_CALL_LOG)) {
            return@withContext emptyList()
        }
        val list = mutableListOf<DeviceCallLogEntry>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val numIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (cursor.moveToNext() && list.size < limit) {
                list.add(
                    DeviceCallLogEntry(
                        id = cursor.getString(idIdx) ?: "",
                        name = cursor.getString(nameIdx),
                        number = cursor.getString(numIdx) ?: "Unknown",
                        type = cursor.getInt(typeIdx),
                        timestamp = cursor.getLong(dateIdx),
                        durationSeconds = cursor.getLong(durIdx)
                    )
                )
            }
        }
        list
    }

    suspend fun recordAssistantCallLog(
        displayName: String?,
        phoneNumber: String,
        direction: String,
        durationSeconds: Long,
        answeredByYesmin: Boolean
    ) {
        assistantCallLogDao.insertCallLog(
            AssistantCallLogEntity(
                displayName = displayName,
                phoneNumber = phoneNumber,
                direction = direction,
                durationSeconds = durationSeconds,
                answeredByYesmin = answeredByYesmin
            )
        )
    }

    suspend fun clearAssistantCallLogs() {
        assistantCallLogDao.clearCallLogs()
    }
}
