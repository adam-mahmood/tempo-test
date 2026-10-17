package io.tempo.hierarchy

import kotlin.test.Test
import kotlin.test.assertEquals

class FilterTest {
  private val forest = ArrayBasedHierarchy(
    intArrayOf(1, 2, 3, 4, 5, 6),
    intArrayOf(0, 1, 2, 1, 0, 1))

  @Test
  fun emptyHierarchyDoesNotCallPredicate() {
    val result = ArrayBasedHierarchy(intArrayOf(), intArrayOf()).filter {
      error("Empty input must not call the predicate")
    }
    assertEquals(0, result.size)
  }

  @Test
  fun acceptingAllPreservesOrderAndDepths() {
    assertEquals(forest.formatString(), forest.filter { true }.formatString())
  }

  @Test
  fun rejectingAllRemovesEveryTree() {
    assertEquals(0, forest.filter { false }.size)
  }

  @Test
  fun rejectedRootDoesNotRemoveNextTree() {
    assertEquals("[5:0, 6:1]", forest.filter { it != 1 }.formatString())
  }

  @Test
  fun rejectedBranchDoesNotRemoveSibling() {
    assertEquals("[1:0, 4:1, 5:0, 6:1]", forest.filter { it != 2 }.formatString())
  }

  @Test
  fun rejectingLeafPreservesAncestorsAndInput() {
    val original = forest.formatString()
    assertEquals("[1:0, 2:1, 4:1, 5:0, 6:1]", forest.filter { it != 3 }.formatString())
    assertEquals(original, forest.formatString())
  }

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
