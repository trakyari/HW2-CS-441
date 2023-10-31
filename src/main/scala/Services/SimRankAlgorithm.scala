package Services

import NetGraphAlgebraDefs.NodeObject

object SimRankAlgorithm {
  // Use the stored value property of a node in order to calculate
  // a similarity rank score. The closer the score is (higher or lower),
  // the closer the score will be to 1.
  def computeSimRankWithValue(node_one: NodeObject, node_two: NodeObject): Double =
    1.0 - Math.abs(node_two.storedValue - node_one.storedValue)
}
