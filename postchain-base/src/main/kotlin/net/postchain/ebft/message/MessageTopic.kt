package net.postchain.ebft.message

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
    SIGNEDMESSAGE(13);

    fun toLong() = value.toLong()
}