package Services

import NetGraphAlgebraDefs.{Action, NetGraphComponent, NodeObject}
import com.google.common.graph.MutableValueGraph

import java.io.{FileInputStream, ObjectInputStream}
import java.util.Base64
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.{Failure, Success, Try}

// Adapted from NetGameSim net graph load
object GraphLoader {
  def load(fileName: String, dir: String = ""): Option[List[NetGraphComponent]] =
    Try(new FileInputStream(s"$dir$fileName"))
      .map(fis => (fis, new ObjectInputStream(fis)))
      .map { case (fis, ois) =>
        val ng = ois.readObject.asInstanceOf[List[NetGraphComponent]]
        ois.close()
        fis.close()
        ng
      }.toOption

//      .flatMap { lstOfNetComponents =>
//        val nodes = lstOfNetComponents.collect { case node: NodeObject => node }
//        val edges = lstOfNetComponents.collect { case edge: Action => edge }
//        List(nodes, edges)
//      }
}