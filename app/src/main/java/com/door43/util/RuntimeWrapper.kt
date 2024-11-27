package com.door43.util

object RuntimeWrapper {

    fun availableProcessors(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    fun maxMemory(): Long {
        return Runtime.getRuntime().maxMemory()
    }

    fun exit(status: Int) {
        Runtime.getRuntime().exit(status)
    }

    fun exec(command: Array<String>): Process {
        return Runtime.getRuntime().exec(command)
    }
}