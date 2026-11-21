package com.grocerylist.app.ui

import android.content.Context
import android.content.Intent

/** Opens the share sheet (Messages, email, …) with [text]; [subject] becomes the email subject. */
fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
}
