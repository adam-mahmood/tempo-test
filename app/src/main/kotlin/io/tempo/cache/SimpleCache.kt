package io.tempo.cache

import java.util.concurrent.ConcurrentHashMap


//REVIEW:The unconstrained type parameters (K, V) allow nullable keys and values. A null key causes ConcurrentHashMap
// to throw an exception at runtime, while a null value wrapped in CacheEntry makes get() unable to distinguish a 
// cache hit from a missing or expired entry. Please constrain the parameters to K : Any, V : Any. 
//This prevents Kotlin callers from passing null keys or values at compile time. It also means that null
//returned by get() clearly means the item is missing or expired.
// If cached null values are required, return an explicit hit/miss result type.
class SimpleCache<K, V> {
    // REVIEW: Expired items stay in memory, and there is no limit on how many items we store.
    // Adding new keys over time can run out of memory (OutOfMemoryError). Remove old items and set a size limit.
    // Old items are hidden, but they still take up memory. Nothing here deletes them.
    // We can delete old items when someone reads or writes to the cache.
    // Or we can use a background task (Thread Pool using ExecutorService) to delete them every few seconds.
    // A background task is optional, but it also works when nobody is using the cache.
    // Cleanup should also cover items that nobody reads again.
    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    // IMPROVEMENT: Every item expires after one minute. If callers need a different lifetime,
    // they may get stale data or unnecessary misses. Consider a configurable Duration.
    // A fixed TTL is fine if one minute meets the application requirements.
    private val ttlMs = 60000 // 1 minute
    
    // REVIEW:CacheEntry has val properties, but value may reference a mutable object i.e. a List. Multiple callers receive the same reference.
    //Production impact: Concurrent mutation of a cached object can cause data races or inconsistent application state, despite the thread-safe map.
    //ConcurrentHashMap makes map operations thread-safe.It does not make the stored objects thread-safe.
    //Required fix: Define an ownership policy: use immutable values, defensive copies, or values that provide their own synchronization.
    data class CacheEntry<V>(val value: V, val timestamp: Long)
    
    fun put(key: K, value: V) {
        // REVIEW: System.currentTimeMillis() represents wall-clock time, which can change due to NTP 
        // adjustments, manual clock changes or virtual-machine time corrections. If the system 
        // clock changes, items can expire too early or too late.
        // Late expiry can serve stale data; early expiry can increase database or service calls.
        // Use System.nanoTime() here and in get(), and convert the TTL to nanoseconds too.
        cache[key] = CacheEntry(value, System.currentTimeMillis())
    }
    
    fun get(key: K): V? {
        val entry = cache[key]
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp < ttlMs) {
                return entry.value
            }
            // REVIEW: Returning null does not remove this expired item from memory.
            // When adding cleanup, remove it only if it is still the item we checked;
            // another thread may have replaced it with a new item. Deleting that new item
            // would cause unnecessary misses and extra database or service calls.
        }
        return null
    }
    
    fun size(): Int {
        // REVIEW: This counts expired items too, so monitoring can overstate usable items.
        // Other threads can change the count. Using it to check a size limit before adding
        // an item can let several threads pass the check and exceed the limit.
        return cache.size
    }
}