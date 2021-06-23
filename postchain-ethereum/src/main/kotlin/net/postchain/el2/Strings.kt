package net.postchain.el2

class Strings {
    companion object {
        fun isEmpty(s: String?): Boolean {
            return s == null || s.isEmpty()
        }
    }
}