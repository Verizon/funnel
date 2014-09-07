---
layout: default
title:  "Home"
section: "home"
---

# Introduction

A distributed monitoring system based on a lightweight streaming protocol. *This library depends upon Scala 2.10.x - you will see crazy errors if you try to use this on 2.9.x*

To get started, please read the most appropriate documentation:

* For engineers that want to instrument their code, [see here](getting-started/developers.html)
* For operations staff who want to aggregate metrics, monitor systems and familiarise themselves with the failure modes, [see here](getting-started/operations.html)

*Funnel* allows systems to push, pull, mirror and aggregate metrics from disparate systems, all with a unified API. High-level dashboards can then be created by down-stream consumers (in this case, pushing to elastic search and visualising *Funnel* data with Kiabana)

![image]({{ site.baseurl }}img/elk-dash.png)

<a name="overview"></a>

# Design Overview

This section outlines some of the specific design choices within *Funnel*, both from a single machine perspective and as the wider system architecture: 

1. [Scalability](#scalability) - discusses funnels scaling properties
1. [Time Periods](#time-periods) - how funnel handles time and local periodic aggregation.
1. [Units](#units) - overview of the types of units that funnel is aware of. 

<a name="scalability"></a>

## Scalability 

Several design choices were made specifically with the ability to scale linearly in mind. Probably the most obvious is the pull-based model the system employs by default: by pulling metrics and conducting service-local aggregation of samples, the aggregating systems never see load-spikes that might cause cascading failure: they instead see a consistent load that only increases with the number of metric keys in total that *Flask* instance is being asked to monitor.

The [operational instructions](getting-started/operations.html) contain some insight into how the system handles automatic load-sharding and rebalancing.

<a name="time-periods"></a>

## Time Periods

As with all monitoring systems, understanding the way time periods is handled is really important (where time period represents the time in which metrics are collected, and then reset). *Funnel* supports three different time period types, as illustrated by the diagram below.

![image]({{ site.baseurl }}img/time-periods.jpg)

In this diagram, **T** represents time moving left to right. A, B and C respectively illustrate the boundaries of given time periods. The duration of the time period is very arbitrary, but the default setting is for 5 minute periods. In this frame:

* **N** - The "now" time period, representing the current set of metrics that are in flight, and explicitly does not represent a completed period. If 2.5 minutes have elapsed in the current 5 minute period, then **N** only illustrates the metrics collected in 2.5 minutes.
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




