package net.postchain.base

class Strings {
    companion object {
        fun isEmpty(s: String?): Boolean {
            return s == null || s.isEmpty()
        }
    }
}