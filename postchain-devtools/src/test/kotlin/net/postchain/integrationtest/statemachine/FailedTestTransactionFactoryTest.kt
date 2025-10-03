package net.postchain.integrationtest.statemachine

import net.postchain.common.exception.UserMistake
import net.postchain.core.MockEContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class FailedTestTransactionFactoryTest {

    @Test
    fun testFailedFactory() {
        val strategy = FailedTestTransactionFactory()

        val encodedTx0 = encodedTx(0)
        val tx0 = strategy.decodeTransaction(encodedTx0)
        tx0.checkCorrectness(MockEContext(0))

        val encodedTx1 = encodedTx(1)
        val tx1 = strategy.decodeTransaction(encodedTx1)
        assertThrows<UserMistake> {
            tx1.checkCorrectness(MockEContext(0))
        }

        val encodedTx2 = encodedTx(2)
        val tx2 = strategy.decodeTransaction(encodedTx2)
        tx2.checkCorrectness(MockEContext(0))

        val encodedTx3 = encodedTx(3)
        val tx3 = strategy.decodeTransaction(encodedTx3)
        assertThrows<UserMistake> {
            tx3.checkCorrectness(MockEContext(0))
        }
    }

    @Test
    fun testNotFailedFactory() {
        val strategy = NotFailedTestTransactionFactory()

        val encodedTx0 = encodedTx(0)
        val tx0 = strategy.decodeTransaction(encodedTx0)
        tx0.checkCorrectness(MockEContext(0))

        val encodedTx1 = encodedTx(1)
        val tx1 = strategy.decodeTransaction(encodedTx1)
        tx1.checkCorrectness(MockEContext(0))

        val encodedTx2 = encodedTx(2)
        val tx2 = strategy.decodeTransaction(encodedTx2)
        tx2.checkCorrectness(MockEContext(0))

        val encodedTx3 = encodedTx(3)
        val tx3 = strategy.decodeTransaction(encodedTx3)
        tx3.checkCorrectness(MockEContext(0))
    }

    private fun encodedTx(id: Int): ByteArray {
        val byteStream = ByteArrayOutputStream(4)
        val dataStream = DataOutputStream(byteStream)
        dataStream.writeInt(id)
        dataStream.flush()
        return byteStream.toByteArray()
    }

}