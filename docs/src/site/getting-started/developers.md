---
layout: default
title:  "Getting Started - Developers"
section: "getting-started"
---

# Getting Started: Developers

First up you need to add the dependency for the monitoring library to your `build.scala` or your `build.sbt` file:

```
libraryDependencies += "funnel" %% "http" % "x.y.z"
```

(check for the latest release by [looking on the nexus](http://nexus.svc.oncue.com/nexus/content/repositories/releases/oncue/svc/funnel/http_2.10/))

Current transitive dependencies this will introduce on your classpath:

- [Scalaz](https://github.com/scalaz/scalaz) 7.1.0
- [Scalaz Stream](https://github.com/scalaz/scalaz-stream) 0.6a
- [Algebird Core](https://github.com/twitter/algebird) 0.8.0

You will likely never touch these dependencies directly, but its important to be aware of them in case you decide yourself to use Scalaz (for example); if you have conflicting versions then you will see some very strange binary collisions at runtime.

### Defining Metrics

With the dependency setup complete, you can now start instrumenting your code. The first thing you need to do is create a `metrics.scala` file, which should look something like this:

```
package oncue.svc
package myproject

import funnel.instruments._

object metrics {
  val HttpReadWidgets = timer("http/get/widgets", 
    "time taken to read and display the list of widgets")
  val HttpReadWidget  = timer("http/get/widgets/:id", 
    "time taken to read and display a given widget")
  val FooCount        = counter("domain/foocount", 
    "the number of foos that have been seen")
  // more metrics here
}
```

The goal of this `metrics` object is to define your metrics up front, and force yourself to think about the metric requirements that your application has. All of your application metrics should be defined in the `metrics` object, and imported where they are needed by specific APIs.

> **NOTE:** When it comes to naming metrics, always bucket your metrics using `/` as a delimiter, and try to logically order things as a "tree". For example:
>
> * When implementing timers for HTTP resources, use the idiom: `http/<verb>/<resource>`. If parts of the resource are variable, then use `:yourvar`, or whatever parameter name makes most sense. The reason for structure HTTP metrics like this is that it is easily consumable by operations and that it makes it easy to quickly compare different resources for a given HTTP verb (for example)
> * As a rule of thumb, try to logically order your metrics in order of component coarseness. For example, anything starting with "db" for database operations can all be compared together in a convenient fashion, so it would make sense to bucket all database operations together, and then perhaps bucket all the *read* operations together, versus the *write* operations (see below for a more complete example)

At first blush, clearly having a single namespace for the entire world of application metrics could become unwieldy, so it is recommended to organise your metrics with nested objects, based on logical application layering. Here's a more complete example from the SU service that illustrates this pattern:

```
package oncue.svc.su

import funnel.instruments._

object metrics {

  /**
    * Instruments for everything related to the HTTP stack.
    */
  object http {
    val ReadUpdates       = timer("http/post/updates", 
      "time taken to determine if the supplied device profile needs any software updated")
    val CreateAllocations = timer("http/post/allocations", 
      "time taken to make new allocations")
    val ReadAllocations   = timer("http/get/allocations/:key", 
      "time taken to read a specified allocation")
    val DeleteAllocation  = timer("http/delete/allocations/:key", 
      "time taken to delete an allocation")
    val ReadGroups        = timer("http/get/groups", 
      "time taken to load and present all defined groups as json")
    val CreateGroup       = timer("http/post/groups", 
      "time taken to add a new group")
    val ReadGroup         = timer("http/get/groups/:key", 
      "time taken to read a group definition and display as json")
    val UpdateGroup       = timer("http/put/groups/:key", 
      "time taken to replace the definition of a group")
    val DeleteGroup       = timer("http/delete/groups/:key", 
      "time taken to delete a group with a given key")
  }

  /**
    * Instruments for the database access operations
    */
  object db {
    val ReadLatency       = timer("db/read/single/latency", 
      "time taken to read a single entry from the database")
    val ReadBatchLatency  = timer("db/read/batch/latency", 
      "time taken to read a batch of items from the database with a specified set of keys")
    val ReadListLatency   = timer("db/read/list/latency", 
      "time taken to list items from the database with a given hash+range combination")
    val ReadScanLatency   = timer("db/read/scan/latency", 
      "time taken to scan the specified database table")
    val WriteLatency      = timer("db/write/single/latency", 
      "time taken to write a single entry into the database")
    val WriteBatchLatency = timer("db/write/batch/latency", 
      "time taken to write a batch to the database")
    val DeleteLatency     = timer("db/delete/batch/latency", 
      "time taken to delete a batch from the database")
  }

  /**
   * Instruments that record latencies for talking to other services
   */
  object s2s {
    val ArtifactSyncLatency = timer("s2s/artifacts/latency", 
      "time taken to request all the artifacts from inventory service")
  }
}

```

The 1.0.x series of `funnel-core` has four supported metric types: `Counter`, `Timer`, `Gauge` and `TrafficLight`.


#### Counters

A counter simply increments an integer value specified by a label that you supply. A counter is appropriate for metrics that represent a tally, or the number of occurrences of something during a particular period. Typical examples are the number of sales, the number of errors, or messages received.

You should **not** use a counter for a magnitude or amount that represents the current value of something, such as the number of concurrent connections, the number of ongoing transactions, or the number of messages in a queue. In that case, use a _gauge_ instead (see below).

For example, to create a counter for the number of cache hits:

```
package oncue.svc
package yourproject

import funnel.instruments._

object metrics {
  val NumberOfCacheHits = counter("cache/hits")
}

```

Counters can then be incremented monotonically, or incremented by a given amount:

```
import metrics._

trait SomeFun {

  def foobar = {
    // increment monotonically
    NumberOfCacheHits.increment

    // increment by a given amount
    NumberOfCacheHits.incrementBy(10)
  }

}
```

#### Timers

As the name suggests, **timers** are all about the amount of time take to perform a specific operation.

```
package oncue.svc
package yourproject

import funnel.instruments._

object metrics {
  val GetUserList = timer("db/get-user-list")
}

```

Timers can then record several different types of value:

```
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

```

#### Gauges

A gauge represents the current state of a value. A good analogy is checking the oil on a car; the dipstick is the gauge of how much oil the engine currently has, and this changes over time, moving both up and down, but it can only have a single value at any particular reading.

Use a gauge when you want to monitor the magnitude or amount of something at a particular point in time, or the average amount of something over a period, such as the number of concurrent connections, ongoing transactions, or messages in a queue. A gauge is **not** appropriate for counting or tallying occurrences of some event, such as the number of errors, number of sales, or messages received so far. For such things, use a _counter_ instead (see above).


```
package oncue.svc
package yourproject

import funnel.Units
import funnel.instruments._

object metrics {
  // gauges require an initial value
  val OilGauge = gauge("oil", 0d, Units.Count)
}

```

Unlike other metrics, gauges are strongly typed in their value, and must be given a default upon being created. To set the value later on in your code, you simply do the following:

```
import metrics._

trait SomeFun {
  def readOilLevel: Double = {
    val level = // database op
    OilLevel.set(level)
    level
  }
}
```

By default, the following types are supported as values for gauges:

* `String`
* Any `Numeric[A]` instance; the Scala std library supplies typeclasses for:
  * `Int`
  * `Double`
  * `Float`
  * `BigDecimal`
  * `Long`

In addition to simply recording the value of the gauge, there is a specialised gauge that can also track numerical statistics like `mean`, `variance` etc. To use it, just adjust your `metrics` declaration like so:

```
package oncue.svc
package yourproject

import funnel.instruments._

object metrics {
  // gauges require an initial value
  val OilGauge = numericGauge("oil", 0d, Units.Count)
}

```


#### Traffic Light

As an extension to the concept of `Gauge`, there is also the notion of a "traffic light" which is essentially a gauge that can be in only one of three possible finite states:

* Red
* Amber
* Green

Traffic light metrics are used to derive actionable signals for operations (they can derive whatever they like too), but for example, one might put a traffic light metric on database access, or the status of a circuit breaker used to invoke a 3rd party system. Using traffic lights is super simple:

```
package oncue.svc
package yourproject

import funnel.instruments._

object metrics {
  val GeoLocationCircuit = trafficLight("circuit/geo")
}

```
And the actual usage in the circuit breaking code:

```
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
```


### Monitoring Server

All the metrics you define in your application, plus some additional platform metrics supplied by the monitoring library can be exposed via a baked in administration server. In order to use this server, one simply only needs to add the following line to the `main` of their application:

```
import funnel.http.MonitoringServer
import funnel.Monitoring

object Main {
  def def main(args: Array[String]): Unit = {
    MonitoringServer.start(Monitoring.default, 5775)

    // your application code starts here
  }
}

```

**NOTE: By application `main`, this does not have to be the actual main, but rather, the end of the world for your application (which however, would usually be the main). For Play! applications, this means the Global object.**

With this in your application, and assuming you are developing locally, once running you will be able to access [http://127.0.0.1:5775/](http://127.0.0.1:5775/) in your local web browser, where the index page will give you a list of available resources and descriptions of their function:

![image]({{ site.baseurl }}img/control-panel.png)



