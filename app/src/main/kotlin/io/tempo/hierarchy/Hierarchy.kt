package io.tempo.hierarchy

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
 * Returns a new hierarchy containing nodes whose IDs satisfy [nodeIdPredicate]
 * and whose ancestors also satisfy it. Preserves the original order and depths.
 *
 * Rejecting a node removes its entire subtree. For example, in the hierarchy
 * above, rejecting node 3 also removes node 4.
 *
 * DFS order places descendants immediately after their ancestor, at greater
 * depths. After rejecting a node, skips these descendants until reaching a node
 * at the same or a shallower depth. The predicate is not called for skipped nodes.
 *
 * Runs in O(n) time and uses O(n) additional space, where n is [Hierarchy.size],
 * assuming constant-time hierarchy access and predicate evaluation.
 */
fun Hierarchy.filter(nodeIdPredicate: (Int) -> Boolean): Hierarchy {
  val nodeIds = IntArray(size)
  val depths = IntArray(size)
  var resultSize = 0
  var rejectedDepth: Int? = null

  for (index in 0 until size) {
    val currentDepth = depth(index)
    val blockedDepth = rejectedDepth
    if (blockedDepth != null && currentDepth > blockedDepth) continue
    rejectedDepth = null

    val id = nodeId(index)
    if (nodeIdPredicate(id)) {
      nodeIds[resultSize] = id
      depths[resultSize] = currentDepth
      resultSize++
    } else {
      // Descendants cannot survive a rejected ancestor.
      rejectedDepth = currentDepth
    }
  }

  return ArrayBasedHierarchy(nodeIds.copyOf(resultSize), depths.copyOf(resultSize))
}
