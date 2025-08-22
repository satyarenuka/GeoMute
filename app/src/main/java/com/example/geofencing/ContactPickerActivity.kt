package com.example.geofencing

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity


class ContactPickerActivity : AppCompatActivity() {

    private val PICK_CONTACT_REQUEST = 1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        startActivityForResult(intent, PICK_CONTACT_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_CONTACT_REQUEST && resultCode == Activity.RESULT_OK) {
            val contactUri: Uri? = data?.data
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)

            contactUri?.let {
                contentResolver.query(it, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val number = cursor.getString(numberIndex)

                        // Normalize and store
                        val cleanedNumber = number.replace("\\s|-".toRegex(), "")
                        saveNumberToPreferences(cleanedNumber)
                        Toast.makeText(this, "Saved: $cleanedNumber", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        finish() // Close activity after selection
    }

    private fun saveNumberToPreferences(number: String) {
        val prefs = getSharedPreferences("EMERGENCY_CONTACTS", MODE_PRIVATE)
        val existing = prefs.getStringSet("contacts", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        existing.add(number)
        prefs.edit().putStringSet("contacts", existing).apply()
    }
    private fun getPhoneNumberFromUri(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex >= 0) {
                    return it.getString(numberIndex)
                }
            }
        }
        return null
    }
}
