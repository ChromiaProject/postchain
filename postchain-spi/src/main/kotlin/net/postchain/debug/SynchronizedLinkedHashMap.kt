package net.postchain.debug

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/** Wrapper for java LinkedHashMap, but as Kotlin MutableMap, that is thread safe */
open class SynchronizedLinkedHashMap<K, V> : MutableMap<K, V> {
    private val map = LinkedHashMap<K, V>()
    private val lock = ReentrantReadWriteLock()

    override fun put(key: K, value: V): V? = lock.write {
        map.put(key, value)
    }

    override fun remove(key: K): V? = lock.write {
        map.remove(key)
    }

    override fun get(key: K): V? = lock.read {
        map[key]
    }

    fun getOrPut(key: K, defaultValue: () -> V): V = lock.write {
        map.getOrPut(key, defaultValue)
    }

    override val values: MutableCollection<V>
        get() = lock.read {
            map.values.toMutableList()
        }

    override val keys: MutableSet<K>
        get() = lock.read {
            map.keys.toMutableSet()
        }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = lock.read {
            map.entries.toMutableSet()
        }

    override val size: Int
        get() = lock.read { map.size }

    override fun clear() = lock.write {
        map.clear()
    }

    override fun isEmpty(): Boolean = lock.read {
        map.isEmpty()
    }

    override fun containsKey(key: K): Boolean = lock.read {
        map.containsKey(key)
    }

    override fun containsValue(value: V): Boolean = lock.read {
        map.containsValue(value)
    }

    override fun putAll(from: Map<out K, V>) = lock.write {
        map.putAll(from)
    }
}
