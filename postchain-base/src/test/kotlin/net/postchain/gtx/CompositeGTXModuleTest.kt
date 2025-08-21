package net.postchain.gtx

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isEqualTo
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.common.exception.UserMistake
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvType
import net.postchain.gtx.data.ExtOpData
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

abstract class GTXModuleWithMetadata : GTXModule, MetadataProvider

class CompositeGTXModuleTest {

    @Test
    fun `test makeBlockBuilderExtensions aggregates extensions from all modules`() {
        val module1: GTXModule = mock()
        val module2: GTXModule = mock()
        val extension1: BaseBlockBuilderExtension = mock()
        val extension2: BaseBlockBuilderExtension = mock()

        whenever(module1.makeBlockBuilderExtensions()).thenReturn(listOf(extension1))
        whenever(module2.makeBlockBuilderExtensions()).thenReturn(listOf(extension2))

        val compositeModule = CompositeGTXModule(arrayOf(module1, module2), false)

        val result = compositeModule.makeBlockBuilderExtensions()

        assertThat(result).containsAll(extension1, extension2)
    }

    @Test
    fun `test makeTransactor returns transactor for known operation`() {
        val module: GTXModule = mock()
        val extOpData: ExtOpData = mock()
        val transactor: Transactor = mock()

        whenever(extOpData.opName).thenReturn("op1")
        whenever(module.getOperations()).thenReturn(setOf("op1"))
        whenever(module.makeTransactor(extOpData)).thenReturn(transactor)

        val compositeModule = CompositeGTXModule(arrayOf(module), false)
        compositeModule.initializeDB(mock<EContext>())

        val result = compositeModule.makeTransactor(extOpData)

        assertThat(result).isEqualTo(transactor)
    }

    @Test
    fun `test makeTransactor throws UnknownOperation for unknown operation`() {
        val module: GTXModule = mock()
        val extOpData: ExtOpData = mock()

        whenever(extOpData.opName).thenReturn("unknownOp")
        whenever(module.getOperations()).thenReturn(setOf("op1"))

        val compositeModule = CompositeGTXModule(arrayOf(module), false)
        compositeModule.initializeDB(mock<EContext>())

        assertThrows(UnknownOperation::class.java) {
            compositeModule.makeTransactor(extOpData)
        }
    }

    @Test
    fun `test query executes successfully for known query`() {
        val module: GTXModule = mock()
        val context: EContext = mock()
        val gtvArgs: Gtv = mock()
        val gtvResult: Gtv = mock()

        whenever(module.getQueries()).thenReturn(setOf("query1"))
        whenever(module.query(context, "query1", gtvArgs)).thenReturn(gtvResult)

        val compositeModule = CompositeGTXModule(arrayOf(module), false)
        compositeModule.initializeDB(context)

        val result = compositeModule.query(context, "query1", gtvArgs)

        assertThat(result).isEqualTo(gtvResult)
    }

    @Test
    fun `test query throws UnknownQuery for unknown query`() {
        val module: GTXModule = mock()
        val context: EContext = mock()
        val gtvArgs: Gtv = mock()

        whenever(module.getQueries()).thenReturn(setOf("query1"))

        val compositeModule = CompositeGTXModule(arrayOf(module), false)
        compositeModule.initializeDB(context)

        assertThrows(UnknownQuery::class.java) {
            compositeModule.query(context, "unknownQuery", gtvArgs)
        }
    }

    @Test
    fun `test initializeDB initializes all modules`() {
        val module1 = mock<GTXModule>()
        val module2 = mock<GTXModule>()
        val context = mock<EContext>()

        val compositeModule = CompositeGTXModule(arrayOf(module1, module2), true)
        compositeModule.initializeDB(context)

        verify(module1).initializeDB(context)
        verify(module2).initializeDB(context)
    }

    @Test
    fun `test initializeDB throws UserMistake on duplicated operations without overrides`() {
        val module1: GTXModule = mock()
        val module2: GTXModule = mock()

        whenever(module1.getOperations()).thenReturn(setOf("op1"))
        whenever(module2.getOperations()).thenReturn(setOf("op1"))

        val compositeModule = CompositeGTXModule(arrayOf(module1, module2), false)

        assertThrows(UserMistake::class.java) {
            compositeModule.initializeDB(mock<EContext>())
        }
    }

    @Test
    fun `test getOperations returns all operations`() {
        val module1: GTXModule = mock()
        val module2: GTXModule = mock()

        whenever(module1.getOperations()).thenReturn(setOf("op1"))
        whenever(module2.getOperations()).thenReturn(setOf("op2"))

        val compositeModule = CompositeGTXModule(arrayOf(module1, module2), false)
        compositeModule.initializeDB(mock<EContext>())

        val result = compositeModule.getOperations()

        assertThat(result).isEqualTo(setOf("op1", "op2"))
    }

    @Test
    fun `test getOperations overrides operations`() {
        val module1: GTXModule = mock()
        val module2: GTXModule = mock()

        whenever(module1.getOperations()).thenReturn(setOf("op1", "op2"))
        whenever(module2.getOperations()).thenReturn(setOf("op2", "op3"))

        val compositeModule = CompositeGTXModule(arrayOf(module1, module2), true)
        compositeModule.initializeDB(mock<EContext>())

        val result = compositeModule.getOperations()

        assertThat(result).isEqualTo(setOf("op1", "op2", "op3"))
    }

    @Test
    fun `test shutdown shuts down all modules`() {
        val module1 = mock<GTXModule>()
        val module2 = mock<GTXModule>()

        val compositeModule = CompositeGTXModule(arrayOf(module1, module2), true)
        compositeModule.shutdown()

        verify(module1).shutdown()
        verify(module2).shutdown()
    }

    @Test
    fun `test getMetadata aggregates metadata from all modules`() {
        val module1 = mock<GTXModuleWithMetadata>()
        val module2 = mock<GTXModuleWithMetadata>()

        val op1 = OperationMetadata(args = listOf(ArgumentMetadata(name = "arg1", gtvTypes = setOf(GtvType.INTEGER))))
        val op2a = OperationMetadata(args = listOf(ArgumentMetadata(name = "arg2a", gtvTypes = setOf(GtvType.BIGINTEGER))))
        val op2b = OperationMetadata(args = listOf(ArgumentMetadata(name = "arg2b", gtvTypes = setOf(GtvType.DICT))))
        val op3 = OperationMetadata(args = listOf(ArgumentMetadata(name = "arg3", gtvTypes = setOf(GtvType.ARRAY))))
        val query1 = QueryMetadata(args = listOf(ArgumentMetadata(name = "qarg1", gtvTypes = setOf(GtvType.INTEGER))),
                returnType = ReturnMetadata(gtvTypes = setOf(GtvType.STRING)))
        val query2 = QueryMetadata(args = listOf(ArgumentMetadata(name = "qarg2", gtvTypes = setOf(GtvType.BIGINTEGER))),
                returnType = ReturnMetadata(gtvTypes = setOf(GtvType.STRING)))
        val metadata1 = GTXModuleMetadata(
                mapOf("op1" to op1, "op2" to op2a),
                mapOf("query1" to query1)
        )
        val metadata2 = GTXModuleMetadata(
                mapOf("op2" to op2b, "op3" to op3),
                mapOf("query2" to query2)
        )

        whenever(module1.getMetadata()).thenReturn(metadata1)
        whenever(module2.getMetadata()).thenReturn(metadata2)

        val compositeModule = CompositeGTXModule(arrayOf(module1, module2), true)
        compositeModule.initializeDB(mock<EContext>())

        val metadata = compositeModule.getMetadata()

        assertThat(metadata.operations).isEqualTo(mapOf("op1" to op1, "op2" to op2b, "op3" to op3))
        assertThat(metadata.queries).isEqualTo(mapOf("query1" to query1, "query2" to query2))
    }
}
