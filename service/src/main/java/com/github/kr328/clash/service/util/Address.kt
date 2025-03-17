package com.github.kr328.clash.service.util

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

fun InetAddress.asSocketAddressText(port: Int): String {
    return when (this) {
        is Inet6Address ->
            "[${this.hostAddress}]:$port"
        is Inet4Address ->
            "${this.hostAddress}:$port"
        else -> throw IllegalArgumentException("Unsupported Inet type ${this.javaClass}")
    }
}
