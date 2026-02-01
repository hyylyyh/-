package com.jungleadventure.shared

interface SaveStore {
    fun save(slot: Int, data: String)
    fun load(slot: Int): String?
}

expect fun defaultSaveStore(): SaveStore
