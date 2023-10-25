package Services

import NetGraphAlgebraDefs.{Action, NodeObject}
import com.google.common.graph.MutableValueGraph

case class NetGraph(sm: MutableValueGraph[NodeObject, Action], initState: NodeObject)
