
import org.slf4j.Logger

import scala.collection.immutable.TreeSeqMap.OrderBy
import scala.collection.mutable
import java.net.InetAddress
import NetGraphAlgebraDefs.{Action, NetGraphComponent, NodeObject}
import Services.{GraphLoader, SimRankAlgorithm}
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

import scala.sys.exit
import scala.util.Random

object Main {
  private val SIMILARITY_THRESHOLD = 0.999
  val MAX_NODE_VISIT_MULTIPLE = 2
  val ITERATIONS_OF_ATTACKS = 100
  private val RANDOM = new Random
  private val EDGE_DIRECTION = EdgeDirection.Out
  var successful_attacks = 0
  var unsuccessful_attacks = 0
  val defaultDir = "C:\\Users\\tarij\\Documents\\UIC\\CS441\\HW2\\"

  // Looks for matches amongst the original nodes in the graph. We look in the original
  // graph since an attacker can't know the modified network topology, only the original.
  def findMatches(node_object: Array[(VertexId, NodeObject)], original_nodes: Array[(VertexId, NodeObject)]):Array[(VertexId, NodeObject)] =
   if (node_object.length > 0)
     original_nodes.filter(node => SimRankAlgorithm.computeSimRankWithValue(node._2, node_object.head._2) > SIMILARITY_THRESHOLD)
   else {
      Array[(VertexId, NodeObject)]()
   }

  // Creates graph components based on the graph file provided by NetGameSim.
  // This has been adapted from NetGameSim as well.
  def createGraphComponents(graph: Option[List[NetGraphAlgebraDefs.NetGraphComponent]]): Option[List[List[NetGraphAlgebraDefs.NetGraphComponent with Product]]] =
    graph map { lstOfNetComponents =>
      val nodes = lstOfNetComponents.collect { case node: NodeObject => node }
      val edges = lstOfNetComponents.collect { case edge: Action => edge }
      List(nodes, edges)
    }

  def main(args: Array[String]): Unit = {
    // Initialize Apache Spark
    // One method to start
    val conf = new SparkConf().setAppName("HW2")
    val sc = new SparkContext(conf)

    // Another method to start
//    val spark = SparkSession.builder.appName("HW2").getOrCreate()
//    val sc = spark.sparkContext

    // Get the current directory
    val defDir = defaultDir
    // Use when running the tool local
    val dir = new java.io.File(".").getCanonicalPath.concat("\\")

    println("Current dir: " + dir)

    // Load the original graph
    val originalGraphFile = GraphLoader.load("NetGameSimNetGraph.ngs", defDir, sc)
    originalGraphFile match {
      case Some(graph) => println("Graph original file not empty, continuing.")
      case None =>
        println("Graph original file empty, exiting...")
        exit(1)
    }

    val originalGraphComponents = createGraphComponents(originalGraphFile)

    // Load the perturbed graph
    val perturbedGraphFile = GraphLoader.load("NetGameSimNetGraph.ngs.perturbed", defDir, sc)

    val perturbedGraphComponents = createGraphComponents(perturbedGraphFile)

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

    val neighbors = perturbed_graph.collectNeighborIds(EDGE_DIRECTION).collect().toList

    val initNodesForAttack = (1 to ITERATIONS_OF_ATTACKS).map{attack =>
      val initNode = perturbed_nodes.apply(RANDOM.nextInt(perturbed_nodes.length))
      val initNodeNeighbors = neighbors.filter(_._1 == initNode._1)
      val nextId = if (initNodeNeighbors.head._2.length > 0)
      initNodeNeighbors.head._2(RANDOM.nextInt(initNodeNeighbors.head._2.length)) else initNode._1
    (initNode, nextId)}

    // Create new graph
    val initial_graph_perturbed_graph = perturbed_graph.mapVertices((id, _) =>
      if (initNodesForAttack.exists(_._2 == id)) {
        val initNode = initNodesForAttack.filter(_._2 == id).head
        (0, 1)
      }
      else (0, 0)
    )

    // Use pregel to perform random walk
    val sp = initial_graph_perturbed_graph.pregel((-1, 1), perturbed_nodes.length * MAX_NODE_VISIT_MULTIPLE, EDGE_DIRECTION) (
      (id, msg, newMsg) => {
        // Only starter nodes or nodes that are
        // recipients of messages should receive messages
        // and process them
        if (msg != (0,0) || newMsg._1.toLong == id) {
          // Perform SimRank
          val node_object = perturbed_nodes.filter(_._1 == id)
          val matches = findMatches(node_object, original_nodes)

          // Perform attack if there are matches and contained in valuable nodes
          val false_positive: Boolean = if (matches.length > 0) {
            val node_of_interest = matches.apply(RANDOM.nextInt(matches.length))
            val is_valuable = valuable_nodes.contains(node_of_interest)
            if (is_valuable && node_object.head._2.valuableData) {
              successful_attacks += 1
              false
            } else if (is_valuable) {
              // Attack is a bust
              unsuccessful_attacks += 1
              true
            } else false
          } else false

          // Determine next vertex to visit using neighbors
          val idNeighbors = neighbors.filter(_._1 == id)
          // Check if there are reachable nodes
          if (idNeighbors.head._2.length > 0 && !false_positive) {
            val nextId = idNeighbors.head._2(RANDOM.nextInt(idNeighbors.head._2.length))
            (nextId.toInt, newMsg._2)
          } else {
            msg
          }
        }
        else msg
      },
      triplet => {
        if (triplet.srcAttr._2 != 0 && triplet.srcAttr._1 == triplet.dstId) {
          val msg = (triplet.srcAttr._1.asInstanceOf[VertexId].toInt, triplet.srcAttr._2+1)
          println("Node ID: " + triplet.srcId + " sending message to node ID: " + triplet.dstId + " with message: " + msg.toString())
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
    println("Successful attacks: " + successful_attacks)
    println("Unsuccessful attacks: " + unsuccessful_attacks)
  }
}