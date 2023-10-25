
import org.slf4j.Logger
import scala.collection.immutable.TreeSeqMap.OrderBy
import scala.collection.mutable
import java.net.InetAddress
import NetGraphAlgebraDefs.{Action, NodeObject}
import Services.GraphLoader
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

object Main {
  def main(args: Array[String]): Unit = {
    val defDir = new java.io.File(".").getCanonicalPath.concat("\\")
    val graphFile = GraphLoader.load("NetGameSimNetGraph.ngs", defDir)
    val graphComponents = graphFile map { lstOfNetComponents =>
      val nodes = lstOfNetComponents.collect { case node: NodeObject => node }
      val edges = lstOfNetComponents.collect { case edge: Action => edge }
      List(nodes, edges)
    }

    // Initialize Apache Spark
    // One method to start
    //val conf = new SparkConf().setAppName("HW2").setMaster("local")
    //val sc = new SparkContext(conf)

    // Another method to start
    val spark = SparkSession.builder.appName("HW2").master("local").getOrCreate()
    val sc = spark.sparkContext

    // Create Spark GraphX graph
    val nodes = graphComponents map { _.head } map(_.map(node => (node.asInstanceOf[NodeObject].id.toLong, node.asInstanceOf[NodeObject])))
    val rdd_nodes: RDD[(VertexId, NodeObject)] = sc.parallelize(nodes.toSeq.flatten)

    val edges = graphComponents map {_.last } map(_.map(edge => Edge(edge.asInstanceOf[Action].fromId.toLong, edge.asInstanceOf[Action].toId.toLong, "")))
    val rdd_edges: RDD[Edge[String]] = sc.parallelize(edges.toSeq.flatten)

    val graph = Graph(rdd_nodes, rdd_edges)

    sc.stop()
  }
}