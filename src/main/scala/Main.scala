
import org.slf4j.Logger

import scala.collection.immutable.TreeSeqMap.OrderBy
import scala.collection.mutable
import java.net.InetAddress
import NetGraphAlgebraDefs.{Action, NodeObject}
import Services.{GraphLoader, SimRankAlgorithm}
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

import scala.util.Random

object Main {
  private val SIMILARITY_THRESHOLD = 0.999
  val MAX_NODE_VISIT_MULTIPLE = 2
  val ITERATIONS_OF_ATTACKS = 100
  private val RANDOM = new Random
  private val EDGE_DIRECTION = EdgeDirection.Out

  def main(args: Array[String]): Unit = {
    // Get the current directory
    val defDir = new java.io.File(".").getCanonicalPath.concat("\\")

    // Load the original graph
    val originalGraphFile = GraphLoader.load("NetGameSimNetGraph.ngs", defDir)
    val originalGraphComponents = originalGraphFile map { lstOfNetComponents =>
      val nodes = lstOfNetComponents.collect { case node: NodeObject => node }
      val edges = lstOfNetComponents.collect { case edge: Action => edge }
      List(nodes, edges)
    }

    // Load the perturbed graph
    val perturbedGraphFile = GraphLoader.load("NetGameSimNetGraph.ngs.perturbed", defDir)
    val perturbedGraphComponents = perturbedGraphFile map { lstOfNetComponents =>
      val nodes = lstOfNetComponents.collect { case node: NodeObject => node }
      val edges = lstOfNetComponents.collect { case edge: Action => edge }
      List(nodes, edges)
    }

    // Initialize Apache Spark
    // One method to start
    //val conf = new SparkConf().setAppName("HW2").setMaster("local")
    //val sc = new SparkContext(conf)

    // Another method to start
    val spark = SparkSession.builder.appName("HW2").master("local[4]").getOrCreate()
    val sc = spark.sparkContext

    // Create original graph
    val nodes = originalGraphComponents map { _.head } map(_.map(node => (node.asInstanceOf[NodeObject].id.toLong, node.asInstanceOf[NodeObject])))
    val rdd_nodes: RDD[(VertexId, NodeObject)] = sc.parallelize(nodes.toSeq.flatten)

    val edges = originalGraphComponents map {_.last } map(_.map(edge => Edge(edge.asInstanceOf[Action].fromId.toLong, edge.asInstanceOf[Action].toId.toLong, "")))
    val rdd_edges: RDD[Edge[String]] = sc.parallelize(edges.toSeq.flatten)

    val graph = Graph(rdd_nodes, rdd_edges)

    // Original graph nodes
    val original_nodes = graph.vertices.collect()

    // Create perturbed graph
    val perturbedNodes = perturbedGraphComponents map {
      _.head
    } map (_.map(node => (node.asInstanceOf[NodeObject].id.toLong, node.asInstanceOf[NodeObject])))
    val rddPerturbedNodes: RDD[(VertexId, NodeObject)] = sc.parallelize(perturbedNodes.toSeq.flatten)

    val perturbedEdges = perturbedGraphComponents map {
      _.last
    } map (_.map(edge => Edge(edge.asInstanceOf[Action].fromId.toLong, edge.asInstanceOf[Action].toId.toLong, "")))
    val rddPerturbedEdges : RDD[Edge[String]] = sc.parallelize(perturbedEdges.toSeq.flatten)

    val perturbed_graph = Graph(rddPerturbedNodes, rddPerturbedEdges)
    val perturbed_nodes = rddPerturbedNodes.collect()

    // Find vertices that contain valuable data
    val valuable_nodes = graph.vertices.filter(_._2.valuableData == true).collect()

    val sourceId: VertexId = 78
    val neighbors = perturbed_graph.collectNeighborIds(EDGE_DIRECTION).collect().toList
    val sourceIdNeighbors = neighbors.filter(_._1 == sourceId)
    val nextId = if (sourceIdNeighbors.head._2.length > 0)
      sourceIdNeighbors.head._2(RANDOM.nextInt(sourceIdNeighbors.head._2.length)) else sourceId

    // Create new graph
    val initial_graph_perturbed_graph = perturbed_graph.mapVertices((id, _) => if (id == sourceId) {
      (nextId, 1)
    } else (0,0))

    // Use pregel to perform random walk
    val sp = initial_graph_perturbed_graph.pregel((nextId, 1), 100, EDGE_DIRECTION) (
      (id, msg, newMsg) => {
        if (id == newMsg._1) {
          // Perform SimRank
          val node_object = perturbed_nodes.filter(_._1 == id).head._2
          val matches = original_nodes.filter(node => SimRankAlgorithm.computeSimRankWithValue(node._2, node_object) > SIMILARITY_THRESHOLD)
          // Perform attack if there are matches and contained in valuable nodes
          if (matches.length > 0) {
            val node_of_interest = matches.apply(RANDOM.nextInt(matches.length))
          }
          val idNeighbors = neighbors.filter(_._1 == id)
          // Check if there are reachable nodes
          if (idNeighbors.head._2.length > 0) {
            val nextId = idNeighbors.head._2(RANDOM.nextInt(idNeighbors.head._2.length))
            (nextId, newMsg._2)
          } else {
            msg
          }
        }
        else msg
      },
      triplet => {
        if (triplet.srcAttr._2 != 0 && triplet.srcAttr._1 == triplet.dstId) {
          val msg = (triplet.srcAttr._1.asInstanceOf[VertexId], triplet.srcAttr._2+1)
          Iterator((triplet.dstId, msg))
        } else {
          Iterator.empty
        }
      },
      // Merge strategy: if we receive multiple messages we do not care
      // as destination will be the same
      (a, b) => a
    )

//    println(sp.collectNeighborIds(EdgeDirection.Out).lookup(sourceId).toArray.mkString("\n"))
//    println("#")
    val res = sp.vertices.filter(_._2._2 > 0).collect().mkString("\n")
    sc.stop()
    println(res)
  }
}