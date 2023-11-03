import Main.{findMatches, performAttack}
import NetGraphAlgebraDefs.NodeObject
import org.apache.spark.graphx.VertexId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.mockito.MockitoSugar

class MainTest extends AnyFlatSpec with Matchers with MockitoSugar {
  behavior of "Attack method"
  it should "shouldn't generate a false positive when the attacker attacks" +
    "a node that does not contain valuable data" in {
    val matches = Array[(VertexId, NodeObject)]((1L, NodeObject(1, 1, 1, 1, 1, 1, 1, 1, 0.01, valuableData = false)))
    val valuableNodes = Array[(VertexId, NodeObject)]((2L, NodeObject(2, 1, 1, 1, 1, 1, 1, 1, 0.01, valuableData = true)))
    val nodeObject = Array[(VertexId, NodeObject)]((1L, NodeObject(1, 1, 1, 1, 1, 1, 1, 1, 0.01, valuableData = false)))
    performAttack(matches, valuableNodes, nodeObject) shouldBe false
  }

  it should "shouldn't generate a false positive when the attacker attacks" +
    "a node that does contain valuable data and matches one of the valuable known nodes" in {
    val matches = Array[(VertexId, NodeObject)]((1L, NodeObject(1, 1, 1, 1, 1, 1, 1, 1, 0.01, valuableData = true)))
    val valuableNodes = Array[(VertexId, NodeObject)]((1L, NodeObject(1, 1, 1, 1, 1, 1, 1, 1, 0.01, valuableData = true)))
    val nodeObject = Array[(VertexId, NodeObject)]((1L, NodeObject(1, 1, 1, 1, 1, 1, 1, 1, 0.01, valuableData = true)))
    performAttack(matches, valuableNodes, nodeObject) shouldBe false
  }

  it should "should generate a false positive when the attacker attacks" +
    "a node that does contain valuable data but doesn't match one of the valuable known nodes" in {
    val matches = Array[(VertexId, NodeObject)]((1L, NodeObject(1, 1, 1, 1, 1, 1, 1, 1, 0.01, valuableData = true)))
    val valuableNodes = Array[(VertexId, NodeObject)]((2L, NodeObject(2, 1, 1, 1, 1, 1, 1, 1, 0.01, valuableData = true)))
    val nodeObject = Array[(VertexId, NodeObject)]((1L, NodeObject(1, 1, 1, 1, 1, 1, 1, 1, 0.01, valuableData = true)))
    performAttack(matches, valuableNodes, nodeObject) shouldBe true
  }

  it should "shouldn't generate a false positive when the attacker looks to attack but" +
    "there are no matches" in {
    val matches = Array[(VertexId, NodeObject)]()
    val valuableNodes = Array[(VertexId, NodeObject)]((2L, NodeObject(2, 1, 1, 1, 1, 1, 1, 1, 0.01, valuableData = true)))
    val nodeObject = Array[(VertexId, NodeObject)]((1L, NodeObject(1, 1, 1, 1, 1, 1, 1, 1, 0.01, valuableData = true)))
    performAttack(matches, valuableNodes, nodeObject) shouldBe false
  }

  behavior of "Find matches method"
  it should "return no matches if there is no node to be compared to" in {
    val nodeObject = Array[(VertexId, NodeObject)]()
    val originalNodes = Array[(VertexId, NodeObject)]()
    findMatches(nodeObject, originalNodes)
  }
}
