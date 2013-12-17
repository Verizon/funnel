## Funnel - Manual




### Time Periods

As with all monitoring systems, understanding the way time periods is handled is really important (where time period represents the time in which metrics are collected, and then reset). *Funnel* supports three different time period types, as illustrated by the diagram below.

![image](img/time-periods.jpg)

In this diagram, **T** represents time moving left to right. A, B and C respectively illustrate the boundaries of given time periods. The duration of the time period is very arbitrary, but the default setting is for 5 minute periods. In this frame:

* **N** - The "now" time period, representing the current set of metrics that are in flight, and explicitly does not represent a completed period. If 2.5 minutes have elapsed in the current 5 minute period, then **N** only illustrates the metrics collected in 2.5 minutes.
* **P** - The "previous" time period, representing the last "full" window that has elapsed. This can be thought of as a stream that only updates its values once every period duration.
* **S** - The "sliding" time period, representing the last period duration, irrespective of if that crossed period boundaries defined by N and P. This is a useful system to collect "what happened in the last 5 minutes"-style view.

### JVM Metrics

The default monitoring instance (`intelmedia.ws.funnel.Monitoring.default`) will automatically collect the following JVM statistics:

* `now/jvm/memory/nonheap/init`
* `now/jvm/memory/heap/committed`
* `now/jvm/memory/total/committed`
* `now/jvm/gc/ParNew`
* `now/jvm/memory/nonheap/committed`
* `now/jvm/gc/ConcurrentMarkSweep`
* `now/jvm/gc/ConcurrentMarkSweep/time`
* `now/jvm/memory/nonheap/max`
* `now/jvm/memory/heap/usage`
* `now/jvm/gc/ParNew/time`
* `now/jvm/memory/heap/init`
* `now/jvm/memory/nonheap/used`
* `now/jvm/memory/total/max`
* `now/jvm/memory/heap/used`
* `now/jvm/memory/total/init`
* `now/jvm/memory/total/used`
* `now/jvm/memory/heap/max`
* `now/jvm/memory/nonheap/usage`


### Units

// TODO

### Monitoring Server

Funnel comes with a built in system for plug-able monitoring servers - that is, a way to expose the internal streams over some kind of wire protocol. At the time of writing, there was only a single `MonitoringServer` implementation that uses `HTTP` as its wire protocol and `JSON` over `SSE` as its serialisation strategy. This is entirely flexible, and if one wanted to implement a different `MonitoringServer` it would be entirely easy to do so externally by writing a `scalaz.stream.Process[Task, A]` consumer. 

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

#### Resources

* `/stream`
* `/stream/:period`
* `/:period`
* `/:period>/:metric`

#### Streams

// TODO

#### Keys

// TODO





