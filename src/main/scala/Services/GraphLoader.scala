package Services

import NetGraphAlgebraDefs._
import org.apache.spark.SparkContext

import java.io.{ByteArrayInputStream, IOException, ObjectInputStream}

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
  }
}