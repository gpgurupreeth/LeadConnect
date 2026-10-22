package com.example.mobileapp.telecom

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.mobileapp.domain.model.CallOutcome

interface ICaller {
    fun placeCall(phoneNumber: String): Boolean
}

class AndroidIntentCaller(private val context: Context) : ICaller {
    override fun placeCall(phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
