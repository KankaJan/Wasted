package com.nexttimeemail.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import com.nexttimeemail.R

/**
 * Opens the user's email app pre-filled with the meeting verdict. We deliberately use
 * a mailto Intent: no credentials, no network permission, the user stays in control and
 * confirms the send themselves.
 *
 * @return true if an email app was launched, false if there was nothing to send or no app.
 */
fun sendMeetingEmail(
    context: Context,
    recipients: List<String>,
    dateLabel: String,
    costSummary: String,
): Boolean {
    if (recipients.isEmpty()) {
        Toast.makeText(context, R.string.no_recipients, Toast.LENGTH_LONG).show()
        return false
    }

    val subject = context.getString(R.string.email_subject, dateLabel, costSummary)
    val body = context.getString(R.string.email_body, dateLabel, costSummary)

    // mailto: with no address opens a chooser of email apps; recipients/subject/body
    // are passed as extras so any compliant mail client picks them up.
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
        putExtra(Intent.EXTRA_EMAIL, recipients.toTypedArray())
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }

    return if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
        true
    } else {
        Toast.makeText(context, R.string.no_email_app, Toast.LENGTH_LONG).show()
        false
    }
}
