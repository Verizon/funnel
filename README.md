## Monitoring Funnel

A distributed monitoring system based on a lightweight streaming protocol. **This library depends upon Scala 2.10.x - you will see crazy errors if you try to use this on 2.9.x**

### Quickstart

First up you need to add the dependency for the monitoring library to your `build.scala` or your `build.sbt` file:

````
libraryDependencies += "intelmedia.ws.monitoring" %% "funnel" % "x.y.z"
````
(check for the latest release by [looking on the nexus](http://nexus-nexusloadbal-1cj6r4t1cb574-1345959472.us-east-1.elb.amazonaws.com/nexus/content/repositories/releases/intelmedia/ws/monitoring/funnel_2.10))

Current transitive dependencies this will introduce on your classpath:

- [Scalaz](https://github.com/scalaz/scalaz) 7.0.4
- [Scalaz Stream](https://github.com/scalaz/scalaz-stream) 0.1
- [Algebird Core](https://github.com/twitter/algebird) 0.3.0

With the dependency setup complete, you can now start instrumenting your code. The first thing you need to do is create a `metrics.scala` file, which should look like this:

````
package intelmedia.ws
package myproject

import monitoring.instruments._

object metrics {
  val HttpReadWidgets = timer("http/get/widgets")
  val HttpReadWidget  = timer("http/get/widgets/:id")
  // more metrics here
}
````

The goal of `metrics` is that you need to define your metrics up front, and think about the metric requirements that your application has. These should all be defined in the `metrics` object, and imported where they are needed by specific APIs. 

In the 1.0.x series of the monitoring library there are three supported metric types: counters, timers and gauges.


##### Counters

These simply increment an integer value specified by a label that you supply. Typical examples are number of sales, number of children etc.

````
package intelmedia.ws
package yourproject

import monitoring.instruments._

object metrics {
  val NumberOfCacheHits = counter("cache/hits")
}

````

Counters can then be incremented monotonically, or incremented by a given amount: 

````

import metrics._

trait SomeFun {
  
  def foobar = {
    // increment monotonically 
    NumberOfCacheHits.increment
    
    // increment by a given amount
    NumberOfCacheHits.incrementBy(10)
  }
 
}

````

##### Timers

As the name suggests, **timers** are all about the amount of time take to perform a specific operation.


````
package intelmedia.ws
package yourproject

import monitoring.instruments._

object metrics {
  val GetUserList = timer("db/get-user-list")
}

````
Timers can then record several different types of value:

````

import metrics._
import scalaz.concurrent.Task
import scala.concurrent.Future

trait SomeFun {
  
  private def getList: Future[Int] = …
  private def getListTask: Task[Int]
  
  def foobar = {
    
    // for timing strict values, just use the block syntax:
    GetUserList.time {
    	// expensive operation goes here
    }
    
    // for timing async Future[A]
    GetUserList.timeFuture(getList)
    
    // for timing scalaz.concurrent.Task[A]
    GetUserList.timeTask(getListTask)
    
    // for anything else procedural, the side-effecting API
    val stopwatch = GetUserList.start()
    val x = // operation to be timed
    stopwatch() // stops the stopwatch and records the time taken
    
  }
  
}

````

##### Gauges

Gauges represent the current state of a value. The best way to visualise this is a checking the oil on a car; the dipstick is the gauge of how much oil the vehicle currently has, and this changes over time, moving both up and down, but it can only have a single value at any particular reading. 

````
package intelmedia.ws
package yourproject

import monitoring.instruments._

object metrics {
  val OilGauge = gauge("oil", 0d) // gauges require an initial value
}

````
Unlike other metrics, gauges are strongly typed in their value, and must be given a default upon being created. To set the value later on in your code, you simply do the following:

````
import metrics._

trait SomeFun {
  def readOilLevel: Double = {
    val level = // database op
    OilLevel.set(level)
    level
  }
}
````
By default, the following types are supported as values for gauges:

* `String`
* Any `Numeric[A]` instance; the Scala std library supplies typeclasses for:
	* `Int`
	* `Double`
	* `Float`
	* `BigDecimal` 
	* `Long`

##### Traffic Light

As an extension to the concept of `Gauge`, there is also the notion of a "traffic light" which is essentially a gauge that can be in only one of three possible finite states:

* Red
* Amber
* Green

Traffic light metrics are used to derive actionable signals for operations (they can derive whatever they like too), but for example, one might put a traffic light metric on database access, or the status of a circuit breaker used to invoke a 3rd party system. Using traffic lights is super simple:

````
package intelmedia.ws
package yourproject

import monitoring.instruments._

object metrics {
  val GeoLocationCircuit = trafficLight("circuit/geo") 
}

````
And the actual usage in the circuit breaking code:

````
import metrics._

trait SomeFun {
  def doGeoLocation = {
    // your logic for the function
	  …
	// if all went well, set the breaker accordingly 
   GeoLocationCircuit.green
    
   /*
   Traffic lights can be set into red and amber states in a similar fashion:
   GeoLocationCircuit.amber
   GeoLocationCircuit.red
   */
  }
}
````


### Monitoring Server 

All the metrics you define in your application, plus some additional platform metrics supplied by the monitoring library can be exposed via a baked in administration server. In order to use this server, one simply only needs to add the following line to the `main` of their application:

````
import monitoring.{MonitoringServer,Monitoring}

object Main {
  def def main(args: Array[String]): Unit = {
    MonitoringServer.start(Monitoring.default, 5775)

    // your application code starts here 
  }
}

````
With this in your application, and assuming you are developing locally, once running you will be able to access [http://127.0.0.1:5775/](http://127.0.0.1:5775/) in your local web browser, where the index page will give you a list of available resources and descriptions of their function. 

