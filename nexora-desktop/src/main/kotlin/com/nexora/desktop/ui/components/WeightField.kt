package com.nexora.desktop.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nexora.desktop.util.s

/** Text field for kilograms: ` kg` suffix, max 3 decimals and 9 integer digits (NUMERIC(12,3)). */
@Composable
fun WeightField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    tag: String? = null,
) = DecimalField(label, value, onValueChange, 3, 9, null, s("unit.kg"), modifier, error, enabled, tag)

/** Hours of rolling work: 2 decimals, 6 integer digits (05 `Hours+`). */
@Composable
fun HoursField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    tag: String? = null,
) = DecimalField(label, value, onValueChange, 2, 6, null, s("unit.hours"), modifier, error, true, tag)

/** A percentage such as an interest rate: 2 decimals, up to 3 integer digits. */
@Composable
fun PercentField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, error: String? = null) =
    DecimalField(label, value, onValueChange, 2, 3, null, s("unit.percent"), modifier, error, true, null)
