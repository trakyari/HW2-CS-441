# HW2-CS441
Arijus Trakymas

atraky2@uic.edu

## Prerequisites

- Spark 3.4.1 (running Scala 2.13.10)
- JDK 11, https://adoptium.net/

## Run the project

### Make sure Spark is running (if running locally)
Start Spark on your machine if running locally.

The instructions are for Windows users who have to start
services manually.

In Spark home directory `bin` folder:
```shell
.\spark-class org.apache.spark.deploy.master.Master
```

Spark is very picky about IPs. If this fails, go to the terminal where
you started master and grab the `spark://host:port` that is provided.
```shell
.\spark-class org.apache.spark.deploy.worker.Worker spark://localhost:7077
```

### Create the jar file
```shell
sbt clean compile assembly
```

### Run locally (Apache Spark compiled against Scala 2.13)
2.13.10 gives no issues.
```shell
~/spark-3.5.0-bin-hadoop3-scala2.13/bin/spark-submit.cmd --master local[4] --class Main --jars target/scala-2.13/HW2.jar --driver-class-path target/scala-2.13/HW2.jar -Doriginal_graph_file_name="example.ngs" target/scala-2.13/HW2.jar 
```

### Run locally (Apache Spark compiled against Scala 2.12)
2.12 gives issues with serial UUID for Scala list.

Amazon EMR 6.14.0 supports Scala 2.12.15 with Apache Spark 3.4.1

```shell
~/spark-3.4.1-bin-hadoop3/bin/spark-submit.cmd --master local[4] --class Main --jars target/scala-2.12/HW2.jar --driver-class-path target/scala-2.12/HW2.jar target/scala-2.12/HW2.jar 
```

### Run via the command line (submit to Spark cluster)
```shell
~/spark-3.5.0-bin-hadoop3-scala2.13/bin/spark-submit.cmd --master spark://192.168.50.64:7077 --executor-memory 20G --class Main --jars target/scala-2.13/HW2.jar --driver-class-path target/scala-2.13/HW2.jar target/scala-2.13/HW2.jar 
```

or

### Run via Intellij
Load the project in Intellij IDEA and click on start.

## Run tests

```shell
sbt clean compile test
```

## Design
### Preprocessing
For this assignment, we are interested in nodes (servers/services)
that contain valuable information. In order to have a baseline of
what nodes contain valuable information we must process the original
graph by performing a walk in the graph and storing information
about nodes that contain valuable information.

Nodes are deemed valuable if their `valuableData` property is equal to `true`.

### System
For this project we are simulating "man in the middle" (MitM) attacks on computer
networks. In our case the computer network is represented in the form of a graph.
We construct our graph using the output from `NetGameSim` and constructing a `GraphX`
graph using the vertices and edges from `NetGameSim`.

The vertices used in our graph for GraphX uses the node ID as the VertexID and the
node object itself as the only property of the vertex.

The edges used in our graph consist of the from node ID and to node ID both from
the node objects.

The attack is defined as a random walk in the graph in which the attacker uses a
stochastic process to determine what to do on each step of the walk. The possible
options of the outcome of the process is to either to 
- finish the walk
- move to a neighboring node

If the attacker decides to end the walk, then nothing else happens.

If the attacker looks at the neighbors of the current node and sees that
there are available neighbors to walk to, they will pick one of the neighbors
at random and walk there provided are iterations still left.

Upon arriving to the node, the attacker will then perform a SimRank on the node
by comparing it to all nodes in the original network (graph). After performing
the similarity analysis, the attacker will then pick at random one of the nodes
that was equal to or above the similarity threshold. The SimRank algorithm has been
reused from HW1.

If the attacker finds themselves stuck, the walk will end there. Additionally,
if the attacker attempted to perform an attack on the current node and was unsuccessful
the walk will end there as well.

This attack is performed on the `perturbed` graph since it represents the actual
network topology including honeypots and modifications to existing machines.

The termination criteria for the walk is the number of nodes visited is equivalent
to the number of nodes in the graph multiplied by a constant.

The number of iterations or in other words the number of attacks performed is also
set but can be configured to adjust the number of attacks simulated.

## Configuration

- similarity threshold: the threshold for which nodes to have match in order to be considered a valid match
    * if this is too low there will be too many matches
- number of attackers: number of starting nodes from random walkers start walking