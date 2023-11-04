
import ConfigReader.getConfigEntry
import Constants._
import NetGraphAlgebraDefs.{Action, NetGraphComponent, NodeObject}
import Services.{GraphLoader, SimRankAlgorithm}
import com.typesafe.config.ConfigFactory
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import org.slf4j.Logger

import java.io.{File, PrintWriter}
import java.net.InetAddress
import scala.sys.exit
import scala.util.{Failure, Random, Success, Try}

class Main

object Main {
  val logger: Logger = CreateLogger(classOf[Main])
  val ipAddr: InetAddress = InetAddress.getLocalHost
  val hostName: String = ipAddr.getHostName
  val hostAddress: String = ipAddr.getHostAddress
  private val jarOutputDirectory = getConfigEntry(Constants.globalConfig, JAR_OUTPUT_DIRECTORY, DEFAULT_JAR_OUTPUT_DIRECTORY)
  private val similarityThreshold = getConfigEntry(Constants.globalConfig, SIMILARITY_THRESHOLD, DEFAULT_SIMILARITY_THRESHOLD)
  private val originalGraphFileName = getConfigEntry(globalConfig, ORIGINAL_GRAPH_FILE_NAME, DEFAULT_ORIGINAL_GRAPH_FILE_NAME)
  private val perturbedGraphFileName = getConfigEntry(globalConfig, PERTURBED_GRAPH_FILE_NAME, DEFAULT_PERTURBED_ORIGINAL_GRAPH_FILE_NAME)
  private val maxNodeVisitMultiple = getConfigEntry(Constants.globalConfig, MAX_NODE_VISIT_MULTIPLE, DEFAULT_MAX_NODE_VISIT_MULTIPLE)
  private val numberOfAttackers = getConfigEntry(Constants.globalConfig, NUMBER_OF_ATTACKERS, DEFAULT_NUMBER_OF_ATTACKERS)
  private val outputDirectory: String = {
    val defDir = new java.io.File(".").getCanonicalPath
    logger.info(s"Default output directory: $defDir")
    val dir: String = getConfigEntry(globalConfig, OUTPUT_DIRECTORY, defDir)
    val ref = new File(dir)
    if (ref.exists() && ref.isDirectory) {
      logger.info(s"Using output directory: $dir")
      if (dir.endsWith("/")) dir else dir + "/"
    }
    else {
      logger.error(s"Output directory $dir does not exist or is not a directory, using current directory instead: $defDir")
      defDir
    }
  }
  private val outputFileName = "results"
  private val RANDOM = new Random
  private val EDGE_DIRECTION = EdgeDirection.Out
  var successful_attacks = 0
  var unsuccessful_attacks = 0

  def main(args: Array[String]): Unit = {
    logger.info(s"The Apache Spark program is running on the host $hostName with the following IP addresses:")
    logger.info(hostAddress)

    val config = ConfigFactory.load()
    logger.info("Loading config.")
    config.getConfig("HW2").entrySet().forEach(e => logger.info(s"Config entry key: ${e.getKey} value: ${e.getValue.unwrapped()}"))

    // Initialize Apache Spark
    // One method to start
    val conf = new SparkConf().setAppName("HW2").setMaster("local[10]")
    val sc = new SparkContext(conf)
//    sc.addJar(jarOutputDirectory)

    // Another method to start
    // val spark = SparkSession.builder.appName("HW2").getOrCreate()
    // val sc = spark.sparkContext

    // Load the original graph
    val originalGraphFile = GraphLoader.load(originalGraphFileName, "",sc)
    checkGraphValidity(originalGraphFile)

    val originalGraphComponents = createGraphComponents(originalGraphFile)

    // Load the perturbed graph
    val perturbedGraphFile = GraphLoader.load(perturbedGraphFileName, "",sc)
    checkGraphValidity(perturbedGraphFile)

    val perturbedGraphComponents = createGraphComponents(perturbedGraphFile)

    // Create original graph
    val nodes = originalGraphComponents map {
      _.head
    } map (_.map(node => (node.asInstanceOf[NodeObject].id.toLong, node.asInstanceOf[NodeObject])))
    val rdd_nodes: RDD[(VertexId, NodeObject)] = sc.parallelize(nodes.toSeq.flatten)

    val edges = originalGraphComponents map {
      _.last
    } map (_.map(edge => Edge(edge.asInstanceOf[Action].fromId.toLong, edge.asInstanceOf[Action].toId.toLong, "")))
    val rdd_edges: RDD[Edge[String]] = sc.parallelize(edges.toSeq.flatten)

    val graph = Graph(rdd_nodes, rdd_edges)

    // Original graph nodes
    val original_nodes: Array[(VertexId, NodeObject)] = graph.vertices.collect()

    // Create perturbed graph
    val perturbedNodes = perturbedGraphComponents map {
      _.head
    } map (_.map(node => (node.asInstanceOf[NodeObject].id.toLong, node.asInstanceOf[NodeObject])))
    val rddPerturbedNodes: RDD[(VertexId, NodeObject)] = sc.parallelize(perturbedNodes.toSeq.flatten)

    val perturbedEdges = perturbedGraphComponents map {
      _.last
    } map (_.map(edge => Edge(edge.asInstanceOf[Action].fromId.toLong, edge.asInstanceOf[Action].toId.toLong, "")))
    val rddPerturbedEdges: RDD[Edge[String]] = sc.parallelize(perturbedEdges.toSeq.flatten)

    val perturbed_graph = Graph(rddPerturbedNodes, rddPerturbedEdges)
    val perturbed_nodes = rddPerturbedNodes.collect()

    // Find vertices that contain valuable data
    val valuable_nodes = graph.vertices.filter(_._2.valuableData == true).collect()

    val neighbors = perturbed_graph.collectNeighborIds(EDGE_DIRECTION).collect().toList

    val initNodesForAttack = (1 to numberOfAttackers).map { attack =>
      val initNode = perturbed_nodes.apply(RANDOM.nextInt(perturbed_nodes.length))
      val initNodeNeighbors = neighbors.filter(_._1 == initNode._1)
      val nextId = if (initNodeNeighbors.head._2.length > 0)
        initNodeNeighbors.head._2(RANDOM.nextInt(initNodeNeighbors.head._2.length)) else initNode._1
      (initNode, nextId)
    }

    // Create new graph
    val initial_graph_perturbed_graph = perturbed_graph.mapVertices((id, _) =>
      if (initNodesForAttack.exists(_._2 == id)) {
        val initNode = initNodesForAttack.filter(_._2 == id).head
        logger.info(s"Node ID: $id has been assigned an attacker.")
        (0, 1)
      }
      else (0, 0)
    )

    logger.info(s"Max iterations is ${perturbed_nodes.length * maxNodeVisitMultiple}.")

    // Use pregel to perform random walk
    val graph_after_random_walks = initial_graph_perturbed_graph.pregel((-1, 1), perturbed_nodes.length * maxNodeVisitMultiple, EDGE_DIRECTION)(
      (id, msg, newMsg) => {
        // Only starter nodes or nodes that are
        // recipients of messages should receive messages
        // and process them
        if (msg != (0, 0) || newMsg._1.toLong == id) {
          // Perform SimRank
          val node_object = perturbed_nodes.filter(_._1 == id)
          val matches = findMatches(node_object, original_nodes)

          // Perform attack if there are matches and contained in valuable nodes
          val false_positive: Boolean = performAttack(matches, valuable_nodes, node_object)

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
          val msg = (triplet.srcAttr._1.asInstanceOf[VertexId].toInt, triplet.srcAttr._2 + 1)
          logger.info("Node ID: " + triplet.srcId + " sending message to node ID: " + triplet.dstId + " with message: " + msg.toString())
          Iterator((triplet.dstId, msg))
        } else {
          Iterator.empty
        }
      },
      // Merge strategy: if we receive multiple messages we do not care
      // as destination will be the same
      (a, b) => a
    )

    val res = graph_after_random_walks.vertices.filter(_._2._2 > 0).collect().mkString("\n")
    sc.stop()

    writeResults(successful_attacks, unsuccessful_attacks, outputDirectory.concat(outputFileName.concat(".yaml")))
  }

  // Looks for matches amongst the original nodes in the graph. We look in the original
  // graph since an attacker can't know the modified network topology, only the original.
  def findMatches(node_object: Array[(VertexId, NodeObject)], original_nodes: Array[(VertexId, NodeObject)]): Array[(VertexId, NodeObject)] =
    if (node_object.length > 0)
      original_nodes.filter(node => SimRankAlgorithm.computeSimRankWithValue(node._2, node_object.head._2) > similarityThreshold)
    else {
      Array[(VertexId, NodeObject)]()
    }

  // Creates graph components based on the graph file provided by NetGameSim.
  // This has been adapted from NetGameSim as well.
  def createGraphComponents(graph: Option[List[NetGraphComponent]]): Option[List[List[NetGraphComponent with Product]]] =
    graph map { lstOfNetComponents =>
      val nodes = lstOfNetComponents.collect { case node: NodeObject => node }
      val edges = lstOfNetComponents.collect { case edge: Action => edge }
      List(nodes, edges)
    }

  def checkGraphValidity(graph: Option[List[NetGraphComponent]]): Unit =
    graph match {
      case Some(graph) => logger.info(s"Graph file not empty, continuing.")
      case None =>
        logger.error(s"Graph file empty, exiting...")
        exit(1)
    }

  // Perform an attack if the criteria is deemed valid
  // If the node/computer contains valuable data and is one of the valuable
  // nodes from the original graph then the attack is a success.
  // Otherwise, if the node/computer contains valuable data but does not match one
  // of the nodes/computers in the valuable nodes then the attack is a failure
  def performAttack(matches: Array[(VertexId, NodeObject)], valuable_nodes: Array[(VertexId, NodeObject)], node_object: Array[(VertexId, NodeObject)]): Boolean =
    if (matches.length > 0) {
      val node_of_interest = matches.apply(RANDOM.nextInt(matches.length))
      val isInValuableNodes = valuable_nodes.contains(node_of_interest)
      if (node_object.head._2.valuableData && isInValuableNodes) {
        successful_attacks += 1
        false
      }
      else if (node_object.head._2.valuableData) {
        // Attack is a bust
        unsuccessful_attacks += 1
        true
      }
      else
        false
    }
    else
      false

  // Adapted from NetGameSim
  def writeResults(successes: Int, failures: Int, fileName: String): Unit = {
    logger.info("Successful attacks: " + successes)
    logger.info("Unsuccessful attacks: " + failures)
    val successfulAttacks = (List("Successful attacks: ") ::: List(successes.toString) ::: List("\n"))
    val unsuccessfulAttacks = (List("Unsuccessful attacks: ") ::: List(failures.toString) ::: List("\n"))

    Try(new PrintWriter(new File(fileName))) match {
      case Success(fh) =>
        try fh.write(successfulAttacks.mkString.concat(unsuccessfulAttacks.mkString))
        finally fh.close()
        Right(())
      case Failure(e) => Left(e.getMessage)
    }
  }
}