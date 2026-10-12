package com.sapphire.hawe.core.net

class ApiException(val status: Int, val code: String, message: String) : Exception(message)

class NetworkException(cause: Throwable) : Exception(cause)
