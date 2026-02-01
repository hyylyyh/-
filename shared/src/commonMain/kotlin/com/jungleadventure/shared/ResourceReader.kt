package com.jungleadventure.shared

interface ResourceReader {
    fun readText(path: String): String
}
