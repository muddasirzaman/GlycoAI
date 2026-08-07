package com.sugarsaathi.app

import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Turns a network failure into something a patient can act on.
 *
 * HTTP status codes are handled first: since the backend started enforcing
 * authentication and rate limits, a 401 or 429 arrives as a retrofit2
 * HttpException, which would otherwise fall through to the generic
 * "something went wrong" and leave the user with no idea what to do.
 */
fun friendlyNetworkMessage(e: Exception): String {
    if (e is HttpException) {
        return when (e.code()) {
            401, 403 ->
                "Your session has expired. Please sign out and sign in again to continue."

            413 ->
                "That file is too large to send. Please try a smaller photo or document."

            429 ->
                "You've sent a lot of messages in a short time. Please wait a minute and try again."

            in 500..599 ->
                "The server is having trouble right now. Please try again in a few minutes."

            else ->
                "Something went wrong. Please try again in a moment."
        }
    }

    return when (e) {
        is ConnectException, is UnknownHostException ->
            "Can't reach the server right now. Your tracker, history, and reminders " +
                    "still work as normal — try this again once you're reconnected."

        is SocketTimeoutException ->
            "The server is taking too long to respond. Please try again."

        // Catches the rest of the IO family: SSL errors, connection resets,
        // and connections dropped mid-request on a weak mobile signal.
        is IOException ->
            "The connection dropped. Please check your internet and try again."

        else ->
            "Something went wrong. Please try again in a moment."
    }
}