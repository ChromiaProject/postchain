package net.postchain.gtv.builder

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.builder.GtvBuilder.GtvArrayMerge
import net.postchain.gtv.builder.GtvBuilder.GtvArrayNode
import net.postchain.gtv.builder.GtvBuilder.GtvDictMerge
import net.postchain.gtv.builder.GtvBuilder.GtvDictNode
import net.postchain.gtv.make_gtv_gson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals

class GtvBuilderTest {
    val gson = make_gtv_gson()

    @Test
    fun testBuilder() {
        val builder = GtvBuilder(gtv(mapOf("first" to gtv("first"), "second" to gtv(2))))
        builder.update(gtv(mapOf("third" to gtv(listOf(gtv("one"), gtv("two"), gtv("three"))))))
        assertEquals(
                """{"first":"first","second":2,"third":["one","two","three"]}""",
                json(builder.build()))
    }

    @Test
    fun testPath() {
        val builder = GtvBuilder()
        builder.update(gtv(mapOf("first" to gtv(mapOf("second" to gtv(mapOf("third" to gtv("here"))))))))
        builder.update(gtv(mapOf("other" to gtv(mapOf("stuff" to gtv("there"))))), "first", "second")
        assertEquals(
                """{"first":{"second":{"other":{"stuff":"there"},"third":"here"}}}""",
                json(builder.build()))
    }

    @Test
    fun testPureArrayMerge() {
        val builder = GtvBuilder(gtv(listOf(gtv("foo"), gtv("bar"))))
        builder.update(gtv(listOf(gtv("baz"), gtv("buzz"))))
        assertEquals(
                """["foo","bar","baz","buzz"]""",
                json(builder.build()))
    }

    @Test
    fun testArrayMerge() {
        val builder = GtvBuilder()
        builder.update(gtv(mapOf("first" to gtv(listOf(gtv("foo"), gtv("bar"))))))
        builder.update(gtv(listOf(gtv("baz"), gtv("buzz"))), "first")
        assertEquals(
                """{"first":["foo","bar","baz","buzz"]}""",
                json(builder.build()))
    }

    @Test
    fun testArrayMergeAppend() {
        val builder = GtvBuilder()
        builder.update(gtv(mapOf("first" to gtv(listOf(gtv("foo"), gtv("bar"))))))
        builder.update(GtvArrayNode(gtv(gtv("baz"), gtv("buzz")), GtvArrayMerge.APPEND), "first")
        assertEquals(
                """{"first":["foo","bar","baz","buzz"]}""",
                json(builder.build()))
    }

    @Test
    fun testArrayMergePrepend() {
        val builder = GtvBuilder()
        builder.update(gtv(mapOf("first" to gtv(listOf(gtv("foo"), gtv("bar"))))))
        builder.update(GtvArrayNode(gtv(gtv("baz"), gtv("buzz")), GtvArrayMerge.PREPEND), "first")
        assertEquals(
                """{"first":["baz","buzz","foo","bar"]}""",
                json(builder.build()))
    }

    @Test
    fun testArrayMergeReplace() {
        val builder = GtvBuilder()
        builder.update(gtv(mapOf("first" to gtv(listOf(gtv("foo"), gtv("bar"))))))
        builder.update(GtvArrayNode(gtv(gtv("baz"), gtv("buzz")), GtvArrayMerge.REPLACE), "first")
        assertEquals(
                """{"first":["baz","buzz"]}""",
                json(builder.build()))
    }

    @Test
    fun testDictMerge() {
        val builder = GtvBuilder()
        builder.update(gtv(mapOf("first" to gtv(mapOf("second" to gtv("here"))))))
        builder.update(gtv(mapOf("other" to gtv(mapOf("stuff" to gtv("there"))))), "first")
        assertEquals(
                """{"first":{"other":{"stuff":"there"},"second":"here"}}""",
                json(builder.build()))
    }

    @Test
    fun testDictMergeKeepNew() {
        val builder = GtvBuilder()
        builder.update(gtv(mapOf("first" to gtv(mapOf("second" to gtv("stuff" to gtv("here")))))))
        builder.update(GtvDictNode(gtv(mapOf("second" to gtv(mapOf("stuff" to gtv("there"))), "other" to gtv("whatever"))), GtvDictMerge.KEEP_NEW), "first")
        assertEquals(
                """{"first":{"other":"whatever","second":{"stuff":"there"}}}""",
                json(builder.build()))
    }

    @Test
    fun testDictMergeKeepOld() {
        val builder = GtvBuilder()
        builder.update(gtv(mapOf("first" to gtv(mapOf("second" to gtv("stuff" to gtv("here")))))))
        builder.update(GtvDictNode(gtv(mapOf("second" to gtv(mapOf("stuff" to gtv("there"))), "other" to gtv("whatever"))), GtvDictMerge.KEEP_OLD), "first")
        assertEquals(
                """{"first":{"other":"whatever","second":{"stuff":"here"}}}""",
                json(builder.build()))
    }

    @Test
    fun testDictMergeReplace() {
        val builder = GtvBuilder()
        builder.update(gtv(mapOf("first" to gtv(mapOf("second" to gtv("stuff" to gtv("here")))))))
        builder.update(GtvDictNode(gtv(mapOf("second" to gtv(mapOf("stuff" to gtv("there"))), "other" to gtv("whatever"))), GtvDictMerge.KEEP_OLD), "first")
        assertEquals(
                """{"first":{"other":"whatever","second":{"stuff":"here"}}}""",
                json(builder.build()))
    }

    @Test
    fun testDictMergeStrict() {
        val builder = GtvBuilder()
        builder.update(gtv(mapOf("first" to gtv(mapOf("second" to gtv("here"))))))
        builder.update(GtvDictNode(gtv(mapOf("third" to gtv(mapOf("stuff" to gtv("there"))))), GtvDictMerge.STRICT), "first")
        assertEquals(
                """{"first":{"second":"here","third":{"stuff":"there"}}}""",
                json(builder.build()))
    }

    @Test
    fun testDictMergeStrictFail() {
        val builder = GtvBuilder()
        builder.update(gtv(mapOf("first" to gtv(mapOf("second" to gtv("stuff" to gtv("here")))))))
        assertThrows<IllegalStateException> {
            builder.update(GtvDictNode(gtv(mapOf("second" to gtv(mapOf("stuff" to gtv("there"))), "other" to gtv("whatever"))), GtvDictMerge.STRICT), "first")
        }
    }

    private fun json(gtv: Gtv): String = gson.toJson(gtv, Gtv::class.java)
}
