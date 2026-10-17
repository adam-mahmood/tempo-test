# Tempo Technical Submission

A Kotlin/JVM project covering two tasks: filtering an array-backed hierarchy and reviewing a cache intended for a highly concurrent application.

## Problem definition

The original task descriptions and starter code are reproduced below for context. The implementation approach follows in the Approach section.

### Hierarchy

````kotlin
import kotlin.test.*

// The task:
// 1. Read and understand the Hierarchy data structure described in this file.
// 2. Implement filter() function.
// 3. Implement more test cases.
//
// The task should take 30-90 minutes.
//
// When assessing the submission, we will pay attention to:
// - correctness, efficiency, and clarity of the code;
// - the test cases.

/**
 * A `Hierarchy` stores an arbitrary _forest_ (an ordered collection of ordered trees)
 * as an array of node IDs in the order of DFS traversal, combined with a parallel array of node depths.
 *
 * Parent-child relationships are identified by the position in the array and the associated depth.
 * Each tree root has depth 0, its children have depth 1 and follow it in the array, their children have depth 2 and follow them, etc.
 *
 * Example:
 * ```
 * nodeIds: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
 * depths:  0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2
 * ```
 *
 * the forest can be visualized as follows:
 * ```
 * 1
 * - 2
 * - - 3
 * - - - 4
 * - 5
 * 6
 * - 7
 * 8
 * - 9
 * - 10
 * - - 11
 *```
 * 1 is a parent of 2 and 5, 2 is a parent of 3, etc. Note that depth is equal to the number of hyphens for each node.
 *
 * Invariants on the depths array:
 *  * Depth of the first element is 0.
 *  * If the depth of a node is `D`, the depth of the next node in the array can be:
 *      * `D + 1` if the next node is a child of this node;
 *      * `D` if the next node is a sibling of this node;
 *      * `d < D` - in this case the next node is not related to this node.
 */
interface Hierarchy {
  /** The number of nodes in the hierarchy. */
  val size: Int

  /**
   * Returns the unique ID of the node identified by the hierarchy index. The depth for this node will be `depth(index)`.
   * @param index must be non-negative and less than [size]
   * */
  fun nodeId(index: Int): Int

  /**
   * Returns the depth of the node identified by the hierarchy index. The unique ID for this node will be `nodeId(index)`.
   * @param index must be non-negative and less than [size]
   * */
  fun depth(index: Int): Int

  fun formatString(): String {
    return (0 until size).joinToString(
      separator = ", ",
      prefix = "[",
      postfix = "]"
    ) { i -> "${nodeId(i)}:${depth(i)}" }
  }
}

/**
 * A node is present in the filtered hierarchy iff its node ID passes the predicate and all of its ancestors pass it as well.
 */
fun Hierarchy.filter(nodeIdPredicate: (Int) -> Boolean): Hierarchy {
  // todo implement
  return ArrayBasedHierarchy(IntArray(0), IntArray(0))
}

class ArrayBasedHierarchy(
  private val myNodeIds: IntArray,
  private val myDepths: IntArray,
) : Hierarchy {
  override val size: Int = myDepths.size

  override fun nodeId(index: Int): Int = myNodeIds[index]

  override fun depth(index: Int): Int = myDepths[index]
}

class FilterTest {
  @Test
  fun testFilter() {
    val unfiltered: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
      intArrayOf(0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2))
    val filteredActual: Hierarchy = unfiltered.filter { nodeId -> nodeId % 3 != 0 }
    val filteredExpected: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 5, 8, 10, 11),
      intArrayOf(0, 1, 1, 0, 1, 2))
    assertEquals(filteredExpected.formatString(), filteredActual.formatString())
  }
}
````

### Code review

You are reviewing the following code submitted as part of a task to implement an item cache in a highly concurrent application. The anticipated load includes: thousands of reads per second, hundreds of writes per second, tens of concurrent threads.
Your objective is to identify and explain the issues in the implementation that must be addressed before deploying the code to production. Please provide a clear explanation of each issue and its potential impact on production behaviour.

```kotlin
import java.util.concurrent.ConcurrentHashMap

class SimpleCache<K, V> {
    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    private val ttlMs = 60000 // 1 minute
    
    data class CacheEntry<V>(val value: V, val timestamp: Long)
    
    fun put(key: K, value: V) {
        cache[key] = CacheEntry(value, System.currentTimeMillis())
    }
    
    fun get(key: K): V? {
        val entry = cache[key]
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp < ttlMs) {
                return entry.value
            }
        }
        return null
    }
    
    fun size(): Int {
        return cache.size
    }
}
```

## Project structure

- `app/src/main/kotlin/io/tempo/hierarchy/` — hierarchy model and filtering implementation.
- `app/src/main/kotlin/io/tempo/cache/SimpleCache.kt` — original cache implementation reviewed below.
- `app/src/test/kotlin/io/tempo/hierarchy/FilterTest.kt` — hierarchy filtering tests.

## Approach

### Hierarchy filtering

The hierarchy represents an ordered forest using parallel node ID and depth arrays in depth-first traversal order. A node is retained only when its ID and every ancestor's ID pass the predicate.

The implementation scans the input once. When a node is rejected, it records that node's depth and skips descendants until a node at the same or a shallower depth is reached. This avoids recursion and does not call the predicate for descendants that cannot be retained. Accepted nodes are copied into new arrays, preserving their original order and depths without modifying the input.

Time complexity is **O(n)** and additional space is **O(n)**, assuming constant-time hierarchy access and predicate evaluation. Tests cover empty input, accepting or rejecting all nodes, rejected roots and branches, sibling preservation, leaf removal, and input preservation.

### Concurrent cache review

The review scenario assumes thousands of reads per second, hundreds of writes per second, and tens of concurrent threads. The original `SimpleCache` is retained as a baseline. Its main limitations are:

- Expired entries remain stored indefinitely, allowing memory growth and making `size()` include expired entries.
- Storage has no capacity limit, so even unexpired entries can exhaust available memory.
- Expiration uses wall-clock time, which can move forwards or backwards and change the effective TTL.
- A concurrent map protects map operations, but does not make mutable cached values safe to share.
- There is no invalidation API, hit/miss monitoring, or coordination of concurrent backend loads on a miss.

## Assumptions and limitations

- Hierarchy input is valid: the arrays have equal lengths, IDs are unique, depths are non-negative, the first node has depth zero, and depth increases by at most one between adjacent nodes. The constructor does not validate these invariants.
- Hierarchy arrays are not mutated during filtering. The array-backed model retains its input arrays by reference.
- Filtering removes entire rejected subtrees; it does not promote children or recalculate depths. Predicates should not rely on being called for skipped descendants.
- The cache review assumes concurrent access and expiration measured from the last write. A thread-safe map alone does not guarantee safe sharing of mutable values.

## Running locally

### Prerequisites

- **JDK 25** for the configured Java toolchain. Set `JAVA_HOME` to your JDK installation if needed.
- Internet access on the first build to download Gradle and dependencies. A toolchain resolver is configured to download the required JDK when needed.
- Use the included Gradle wrapper; a separate Gradle installation is unnecessary.

Run the following commands from the repository root. On Windows, replace `./gradlew` with `.\gradlew.bat`.

```sh
# Compile and run the full test suite
./gradlew build

# Run all tests without assembling distributions
./gradlew :app:test

# Run the hierarchy tests only
./gradlew :app:test --tests 'io.tempo.hierarchy.FilterTest'

# Run the sample application
./gradlew :app:run
```

The application entry point prints `Hello World!`, which you can ignore; hierarchy filtering is exercised through the targeted test command above.

After running tests, open `app/build/reports/tests/test/index.html` for the HTML report. No environment files, credentials, database, or external service setup is required.
