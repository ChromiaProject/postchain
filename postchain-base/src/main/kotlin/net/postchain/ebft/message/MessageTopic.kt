package net.postchain.ebft.message

import net.postchain.gtv.GtvFactory.gtv

enum class MessageTopic(val value: Int) {
    ID(0),
    STATUS(1),
    TX(2),
    SIG(3),
    BLOCKSIG(4),
    BLOCKDATA(5),
    UNFINISHEDBLOCK(6),
    GETBLOCKSIG(7),
    COMPLETEBLOCK(8),
    GETBLOCKATHEIGHT(9),
    GETUNFINISHEDBLOCK(10),
    GETBLOCKHEADERANDBLOCK(11),
    BLOCKHEADER(12),
    GETBLOCKRANGE(13),
    BLOCKRANGE(14),
    APPLIEDCONFIG(15),
    EBFTVERSION(16);

    fun toGtv() = gtv(value.toLong())
}