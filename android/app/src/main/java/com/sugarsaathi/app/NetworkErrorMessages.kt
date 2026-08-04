package com.sugarsaathi.app

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun friendlyNetworkMessage(e: Exception): String {
    return when (e) {
        is ConnectException, is UnknownHostException -> "Can't reach the server right now. Your tracker, history, and reminders still work as normal — try this again once you're reconnected."
        is SocketTimeoutException -> "The server is taking too long to respond. Please try again."
        else -> "Something went wrong. Please try again in a moment."
    }
}