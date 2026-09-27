package com.pgigi.pumpkincampus

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform