---
layout: default
title:  "Home"
section: "home"
---

# Introduction

A distributed monitoring system based on a lightweight streaming protocol. To get started, please read the most appropriate documentation:

* For engineers that want to instrument their code, [see here](getting-started/developers.html)
* For operations staff who want to aggregate metrics, monitor systems and familiarise themselves with the failure modes, [see here](getting-started/operations.html)

*Funnel* allows systems to push, pull, mirror and aggregate metrics from disparate systems, all with a unified API. High-level dashboards can then be created by down-stream consumers (in this case, pushing to elastic search and visualising *Funnel* data with Kibana)

![image]({{ site.baseurl }}img/elk-dash-2.png)

<a name="overview"></a>

# Design Overview

This section outlines some of the specific design choices within *Funnel* - from a single machine perspective - that make it specifically different to other systems that are avalible in the market today. Above all else, Funnel is a stream processor and all metric keys are modeled as a stream internally, and their values are updated with instruments over time. At a high-level, the design can be visulized as four discrete parts:

![design]({{ site.baseurl }}img/funnel-internals.png)

Each part of this pipeline has a distinct function:

* **Importers** are needed when integrating *Funnel* with existing systems, importers are needed to translate the third-party world into instruments *Funnel* understands. When using the native Funnel instruments in Scala, this step is entirely ommited. 

* **Instruments** represent the kinds of metric operations that are avalible with *Funnel*. Instruments create updates to metric key streams. 

* **Core** is the primary part of the *Funnel* system, and is essentially a stateful container for all the metric key streams. The `Monitoring` instance internally allows for a range of operations on the world of metric streams, such as listing, searching, creating topics etc.

* **Exporters** are the last peice of the puzzle. Exporters consume the streams in the core of *Funnel* and typically execute some kind of effectful transduction on that stream. In practice this might be transforming the streams into JSON to send to elastic search, or sending to a network socket. This section is highly extensible and simpy needs the implementor to provide a function `URI => Process[Task, Datapoint[Any]]`.

This is the view of the system from a single machine perspective, but conveniently it turns out that this design composes accross the network to create a large distributed system. Viewed at a larger scale, the system can be visualized like this:

![design]({{ site.baseurl }}img/chemist-flask-funnel.png)

The specifics of this diagram are covered in the [operatations guide]({{ site.baseurl }}services/) section. From the high-level, the pushing and pulling of data is simply a combintation of various importers and exporters.

Several design choices were made specifically with goal of giving *Funnel* the ability to scale linearly. Probably the most obvious is the pull-based model the system employed by the agent <-> flask reltationship: by pulling metrics and conducting service-local aggregation of samples, the aggregating systems never see load-spikes that might cause cascading failure: they instead see a consistent load that only increases with the number of metric keys in total that *Flask* instance is being asked to monitor. Put another way: increases in local sampling on a given host adds overhead to that host, but it doesn't affect the wider system.

<a name="time-periods"></a>

## Time Periods

As with all monitoring systems, understanding the way time periods is handled is really important (where time period represents the time in which metrics are collected, and then reset). *Funnel* supports three different time period types, as illustrated by the diagram below.

![image]({{ site.baseurl }}img/time-periods.jpg)

In this diagram, **T** represents time moving left to right. A, B and C respectively illustrate the boundaries of given time periods. The duration of the time period is very arbitrary, but the default setting is for 1 minute periods. In this frame:

* **N** - The "now" time period, representing the current set of metrics that are in flight, and explicitly does not represent a completed period. If 0.5 minutes have elapsed in the current 1 minute period, then **N** only illustrates the metrics collected in 0.5 minutes.
* **P** - The "previous" time period, representing the last "full" window that has elapsed. This can be thought of as a stream that only updates its values once every period duration.
* **S** - The "sliding" time period, representing the last period duration, irrespective of if that crossed period boundaries defined by N and P. This is a useful system to collect "what happened in the last 5 minutes"-style view.

<a name="units"></a>

## Units

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




