---
layout: default
title:  "Manual"
---

# Manual

This document details the specifics of the *Funnel* monitoring system.

### Time Periods

As with all monitoring systems, understanding the way time periods is handled is really important (where time period represents the time in which metrics are collected, and then reset). *Funnel* supports three different time period types, as illustrated by the diagram below.

![image]({{ site.baseurl }}img/time-periods.jpg)

In this diagram, **T** represents time moving left to right. A, B and C respectively illustrate the boundaries of given time periods. The duration of the time period is very arbitrary, but the default setting is for 5 minute periods. In this frame:

* **N** - The "now" time period, representing the current set of metrics that are in flight, and explicitly does not represent a completed period. If 2.5 minutes have elapsed in the current 5 minute period, then **N** only illustrates the metrics collected in 2.5 minutes.
* **P** - The "previous" time period, representing the last "full" window that has elapsed. This can be thought of as a stream that only updates its values once every period duration.
* **S** - The "sliding" time period, representing the last period duration, irrespective of if that crossed period boundaries defined by N and P. This is a useful system to collect "what happened in the last 5 minutes"-style view.

### JVM and Clock Metrics

The default set of instruments (`intelmedia.ws.funnel.instruments`) will automatically collect the following JVM statistics (where `:period` is one of either `now`, `sliding` or `previous`):

* `:period/jvm/memory/nonheap/init`
* `:period/jvm/memory/heap/committed`
* `:period/jvm/memory/total/committed`
* `:period/jvm/gc/ParNew`
* `:period/jvm/memory/nonheap/committed`
* `:period/jvm/gc/ConcurrentMarkSweep`
* `:period/jvm/gc/ConcurrentMarkSweep/time`
* `:period/jvm/memory/nonheap/max`
* `:period/jvm/memory/heap/usage`
* `:period/jvm/gc/ParNew/time`
* `:period/jvm/memory/heap/init`
* `:period/jvm/memory/nonheap/used`
* `:period/jvm/memory/total/max`
* `:period/jvm/memory/heap/used`
* `:period/jvm/memory/total/init`
* `:period/jvm/memory/total/used`
* `:period/jvm/memory/heap/max`
* `:period/jvm/memory/nonheap/usage`

In addition to these JVM metrics, the default instruments also provide the following:

* `:period/elapsed` - amount of time that has elapsed so far in the specified period.
* `:period/remaining` - amount of time remaining in the specified period.
* `uptime` - Amount of time that this monitoring instance has been running.

These are keys provided by the default instruments, but if you would like to create a custom `Instruments` instance, this is entirely possible. The only reason you would typically want to do this is to specify a custom boundary for time periods. **This is not generally advisable** as the operation logic expects the time period defined by the library. 

````
import intelmedia.ws.funnel._
import scala.concurrent.duration._

val I = new Instruments(2 minutes, Monitoring.default) with DefaultKeys {
  JVM.instrument(this)
  Clocks.instrument(this)
}
````

### Units

Any given metric has the notion of reporting the `Units` that the specific metric denotes. For example, simply seeing the value `18544` for, say, memory usage would not be specifically useful. Is it gigabytes, megabytes? If its the former, perhaps its running hot, if its the latter, there is plenty of headroom. In this frame, you can see how important it is to have an associated metric. At the time of writing the library supports the following `Units`:

* `Count`
* `Ratio`
* `TrafficLight`
* `Healthy`
* `Load`
* `Milliseconds`
* `Seconds`
* `Minutes`
* `Hours`
* `Megabytes`
* `None`

Generally speaking, this is not something that users ever specifically need to extend, but when using the `gauge` instrument the engineer will need to specify the units for that gauge. 

### Monitoring Server

Funnel comes with a built in system for plug-able monitoring servers - that is, a way to expose the internal streams over some kind of wire protocol. At the time of writing, there was only a single `MonitoringServer` implementation that uses `HTTP` as its wire protocol and `JSON` over `SSE` as its serialisation strategy. This is entirely flexible, and if one wanted to implement a different `MonitoringServer` it would be entirely easy to do so externally by writing a `scalaz.stream.Process[Task, A]` consumer. 

#### Bootup

The working expectation is that applications that expose metrics of their own (or indeed, consume metrics from others they wish to replicate) expose a monitoring server. This is done simply by calling `MonitoringServer.start`

````
import funnel.http.{MonitoringServer,Monitoring}

object Main {
  def def main(args: Array[String]): Unit = {
    MonitoringServer.start(Monitoring.default, 5775)

    // your application code starts here 
  }
}

````

Once the server is booted as a part of your application, you can visit the specified port on `localhost` and have access to a range of resources. 

#### Resources

* `/:period` - For example, `/now`, `/sliding` or `/previous`. Displays all of the metric keys that have values in that period. If there is nothing to display, the resulting JSON array will be empty. 

* `/:period>/:metric-prefix` - For example, `/now/uptime` or `/now/http/get/index`. The key thing to understand here is that `:metric-prefix` here really means the name of the key (or at least the first part of it), which is why its so useful to bucket keys by the common prefix: you can lookup all the keys related to a given subject. For example, `/now/http` gets you all the metric keys that start with the string `http`.

* `/stream` - obtain a stream of everything that is happening in the system right now as a HTTP SSE stream.


#### JSON

````
{
  "key": {
    "name": "now/jvm/memory/nonheap/init",
    "type": "Stats",
    "units": "Megabytes"
  },
  "value": {
    "count": 9,
    "variance": 58.38652776185048,
    "kurtosis": 4.1249999999999964,
    "mean": 21.61231644444444,
    "last": 24.313855999999998,
    "skewness": -2.474873734152916,
    "standardDeviation": 7.6411077575081015
  }
}
````






