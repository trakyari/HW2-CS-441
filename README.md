# HW2-CS441
Arijus Trakymas

atraky2@uic.edu

## Prerequisites

- Spark 3.5.0
- JDK 11, https://adoptium.net/

## Run the project
### Create the jar file
```shell
sbt clean compile assembly
```

### Submit the job to Spark
```shell
~/spark-3.5.0-bin-hadoop3/bin/spark-submit --master local[4] target/scala-2.13/HW2.jar 
```

## Design
### Preprocessing
For this assignment, we are interested in nodes (servers/services)
that contain valuable information. In order to have a baseline of
what nodes contain valuable information we must process the original
graph by performing a walk in the graph and storing information
about nodes that contain valuable information.