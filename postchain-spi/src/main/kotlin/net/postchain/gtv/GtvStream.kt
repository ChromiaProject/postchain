package net.postchain.gtv

import net.postchain.common.exception.UserMistake
import net.postchain.gtv.gtvmessages.RawGtv
import java.io.InputStream

data class GtvStream(val stream: InputStream, val length: Long?) : AbstractGtv() {
    override val type: GtvType
        get() = throw UserMistake("Don't call this method on GtvInputStream")

    override fun getRawGtv(): RawGtv {
        throw UserMistake("Don't call this method on GtvInputStream")
    }

    override fun asPrimitive(): Any? {
        throw UserMistake("Don't call this method on GtvInputStream")
    }

    override fun nrOfBytes(): Int {
        throw UserMistake("Don't call this method on GtvInputStream")
    }

    override fun equals(other: Any?): Boolean {
        throw UserMistake("You cannot compare a GtvInputStream with something else.")
    }

    override fun hashCode(): Int {
        throw UserMistake("Don't call this method on GtvInputStream")
    }
}
