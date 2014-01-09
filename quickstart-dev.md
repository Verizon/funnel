---
layout: default
title:  "Quickstart - Engineering"
---

# Quickstart: Engineering Team

First up you need to add the dependency for the monitoring library to your `build.scala` or your `build.sbt` file:

````
libraryDependencies += "intelmedia.ws.funnel" %% "http" % "x.y.z"
````
(check for the latest release by [looking on the nexus](http://nexus-nexusloadbal-1cj6r4t1cb574-1345959472.us-east-1.elb.amazonaws.com/nexus/content/repositories/releases/intelmedia/ws/funnel/http_2.10/))

Current transitive dependencies this will introduce on your classpath:

- [Scalaz](https://github.com/scalaz/scalaz) 7.0.4
- [Scalaz Stream](https://github.com/scalaz/scalaz-stream) 0.1
- [Algebird Core](https://github.com/twitter/algebird) 0.3.0

You will likely never touch these dependencies directly, but its important to be aware of them in case you decide yourself to use Scalaz (for example); if you have conflicting versions then you will see some very strange binary collisions at runtime.

### Defining Metrics

With the dependency setup complete, you can now start instrumenting your code. The first thing you need to do is create a `metrics.scala` file, which should look something like this:

````
package intelmedia.ws
package myproject

import funnel.instruments._

object metrics {
  val HttpReadWidgets = timer("http/get/widgets")
  val HttpReadWidget  = timer("http/get/widgets/:id")
  val FooCount        = counter("domain/foocount")
  // more metrics here
}
````

The goal of this `metrics` object is to define your metrics up front, and force yourself to think about the metric requirements that your application has. All of your application metrics should be defined in the `metrics` object, and imported where they are needed by specific APIs. 

> **NOTE:** When it comes to naming metrics, always bucket your metrics using `/` as a delimiter, and try to logically order things as a "tree". For example:
>
> * When implementing timers for HTTP resources, use the idiom: `http/<verb>/<resource>`. If parts of the resource are variable, then use `:yourvar`, or whatever parameter name makes most sense. The reason for structure HTTP metrics like this is that it is easily consumable by operations and that it makes it easy to quickly compare different resources for a given HTTP verb (for example)
> * As a rule of thumb, try to logically order your metrics in order of component coarseness. For example, anything starting with "db" for database operations can all be compared together in a convenient fashion, so it would make sense to bucket all database operations together, and then perhaps bucket all the *read* operations together, versus the *write* operations (see below for a more complete example)

At first blush, clearly having a single namespace for the entire world of application metrics could become unwieldy, so it is recommended to organise your metrics with nested objects, based on logical application layering. Here's a more complete example from the SU3 service that illustrates this pattern:

````
package intelmedia.ws
package su

import funnel.instruments._

object metrics {

  /**
    * Instruments for everything related to the HTTP stack.
    */
  object http {
    val ReadUpdates       = timer("http/post/updates")
    val CreateAllocations = timer("http/post/allocations")
    val ReadAllocations   = timer("http/get/allocations/:key")
    val DeleteAllocation  = timer("http/delete/:key")
    val ReadGroups        = timer("http/get/groups")
    val CreateGroup       = timer("http/post/groups")
    val ReadGroup         = timer("http/get/groups/:key")
    val UpdateGroup       = timer("http/put/groups/:key")
    val DeleteGroup       = timer("http/delete/groups/:key")
  }

  /**
    * Instruments for the database access operations
    */
  object db {
    val ReadLatency       = timer("db/read/single/latency")
    val ReadBatchLatency  = timer("db/read/batch/latency")
    val ReadListLatency   = timer("db/read/list/latency")
    val ReadScanLatency   = timer("db/read/scan/latency")
    val WriteLatency      = timer("db/write/single/latency")
    val WriteBatchLatency = timer("db/write/batch/latency")
    val DeleteLatency     = timer("db/delete/batch/latency")
  }

  /**
    * Instruments for the domain-specific operations contained within SU
    */
  object domain {
    val ResolverLatency   = timer("domain/resolver/latency")
  }
}

````

The 1.0.x series of `funnel-core` has four supported metric types: `Counter`, `Timer`, `Gauge` and `TrafficLight`.


##### Counters

These simply increment an integer value specified by a label that you supply. Typical examples are number of sales, number of children etc.

````
package intelmedia.ws
package yourproject

import funnel.instruments._

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

import funnel.instruments._

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

import funnel.instruments._

object metrics {
  // gauges require an initial value
  val OilGauge = gauge("oil", 0d, Units.Count) 
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

In addition to simply recording the value of the gauge, there is a specialised gauge that can also track numerical statistics like `mean`, `variance` etc. To use it, just adjust your `metrics` declaration like so:

````
package intelmedia.ws
package yourproject

import funnel.instruments._

object metrics {
  // gauges require an initial value
  val OilGauge = numericGauge("oil", 0d, Units.Count) 
}

````


##### Traffic Light

As an extension to the concept of `Gauge`, there is also the notion of a "traffic light" which is essentially a gauge that can be in only one of three possible finite states:

* Red
* Amber
* Green

Traffic light metrics are used to derive actionable signals for operations (they can derive whatever they like too), but for example, one might put a traffic light metric on database access, or the status of a circuit breaker used to invoke a 3rd party system. Using traffic lights is super simple:

````
package intelmedia.ws
package yourproject

import funnel.instruments._

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
import funnel.http.{MonitoringServer,Monitoring}

object Main {
  def def main(args: Array[String]): Unit = {
    MonitoringServer.start(Monitoring.default, 5775)

    // your application code starts here 
  }
}

````

**NOTE: By application `main`, this does not have to be the actual main, but rather, the end of the world for your application (which however, would usually be the main). For Play! applications, this means the Global object.**

With this in your application, and assuming you are developing locally, once running you will be able to access [http://127.0.0.1:5775/](http://127.0.0.1:5775/) in your local web browser, where the index page will give you a list of available resources and descriptions of their function. 

