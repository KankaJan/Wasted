package com.nexttimeemail.ui.roster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexttimeemail.R
import com.nexttimeemail.data.Attendee
import com.nexttimeemail.data.RateType
import com.nexttimeemail.domain.CostCalculator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendeeEditDialog(
    initial: Attendee?,
    onDismiss: () -> Unit,
    onSave: (Attendee) -> Unit,
    onDelete: ((Attendee) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var rateType by remember { mutableStateOf(initial?.rateType ?: RateType.HOURLY) }
    var rateText by remember {
        mutableStateOf(initial?.rateValue?.takeIf { it > 0 }?.let { trimAmount(it) } ?: "")
    }
    var currency by remember {
        mutableStateOf(initial?.currencyCode ?: CostCalculator.defaultCurrencyCode())
    }

    var nameError by remember { mutableStateOf(false) }
    var rateError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.add_attendee else R.string.edit_attendee))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.error_name_required)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = rateType == RateType.HOURLY,
                        onClick = { rateType = RateType.HOURLY },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(stringResource(R.string.rate_hourly)) }
                    SegmentedButton(
                        selected = rateType == RateType.MANDAY,
                        onClick = { rateType = RateType.MANDAY },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(stringResource(R.string.rate_manday)) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it.filterAmount(); rateError = false },
                        label = { Text(stringResource(R.string.rate_value)) },
                        singleLine = true,
                        isError = rateError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = {
                            Text(
                                stringResource(
                                    if (rateType == RateType.HOURLY) {
                                        R.string.rate_hint_hourly
                                    } else {
                                        R.string.rate_hint_manday
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )

                    OutlinedTextField(
                        value = currency,
                        // Free-text currency: keep it short and upper-cased as the user types.
                        onValueChange = { currency = it.take(8).uppercase(Locale.ROOT) },
                        label = { Text(stringResource(R.string.currency)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.width(120.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.manday_note),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedRate = rateText.replace(',', '.').toDoubleOrNull() ?: 0.0
                val currencyCode = currency.trim().uppercase(Locale.ROOT)
                nameError = name.isBlank()
                rateError = parsedRate <= 0.0
                if (!nameError && !rateError) {
                    onSave(
                        (initial ?: Attendee(name = "")).copy(
                            name = name.trim(),
                            email = email.trim().takeIf { it.isNotEmpty() },
                            rateType = rateType,
                            rateValue = parsedRate,
                            currencyCode = currencyCode.ifEmpty { CostCalculator.defaultCurrencyCode() },
                        ),
                    )
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (initial != null && onDelete != null) {
                    TextButton(
                        onClick = { onDelete(initial) },
                        modifier = Modifier.padding(end = 4.dp),
                    ) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
