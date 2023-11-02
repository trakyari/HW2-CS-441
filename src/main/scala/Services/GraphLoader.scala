package Services

import NetGraphAlgebraDefs.{Action, NetGraphComponent, NodeObject}
import com.google.common.graph.MutableValueGraph
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import java.io.{ByteArrayInputStream, FileInputStream, IOException, ObjectInputStream}
import java.util.Base64
import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.convert.ImplicitConversions.`seq AsJavaList`
import scala.util.{Failure, Success, Try}

// Adapted from NetGameSim net graph load
object GraphLoader {
  def load(fileName: String, dir: String = "", sc: SparkContext): Option[List[NetGraphComponent]] = {
    val binaryData = sc.binaryFiles(s"$dir$fileName")
    val netGraphComponentsList = binaryData.flatMap { case (filePath, data) =>
      // Deserialize binary data and create instances of NetGraphComponent
      try {
        val byteArray = data.toArray()
        val bis = new ByteArrayInputStream(byteArray)
        val ois = new ObjectInputStream(bis)
        val ng = ois.readObject.asInstanceOf[List[NetGraphComponent]]
        ois.close()
        Some(ng)
      } catch {
        case e: IOException =>
          println(s"Failed to deserialize data: ${e.getMessage}")
          None
      }
    }

    if (netGraphComponentsList.isEmpty()) {
      None
    } else {
      Some(netGraphComponentsList.collect().head.toList)
    }
//    Try(new FileInputStream(s"$dir$fileName"))
//      .map(fis => (fis, new ObjectInputStream(fis)))
//      .map { case (fis, ois) =>
//        println(s"Trying to open: $dir$fileName")
//        val ng = ois.readObject.asInstanceOf[List[NetGraphComponent]]
//        ois.close()
//        fis.close()
//        ng
//      }.toOption
  }
}