package com.adl.cafe.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "$4.50" from 450. */
fun Int.asMoney(): String = "\$%,d.%02d".format(this / 100, this % 100)

fun Long.asOrderDate(): String =
    SimpleDateFormat("d MMM yyyy 'at' HH:mm", Locale.getDefault()).format(Date(this))
