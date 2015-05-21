---
layout: default
title:  "Getting Started - Migration"
section: "getting-started"
---

# Getting Started: Migration

This document outlines the various steps required to migrate from one version of funnel to another.

* [3.0 to 3.2](#30to32)
* [2.1 to 3.2](#21to30)
* [1.7 to 2.x](#17to20)

<a name="21to30"></a>

# 3.0 to 3.2

* `Counter` no longer has the `decrement` or `decrementBy` functions. If you require reporting of a value that could go up or down, please instead use a `Gauge` (aka `gauge` function on the `Instruments` instance).

* `Edge` metric type is now available for reporting on graph-like structures / topologies. 

* `Counter` and `Gauge` were reimplemented in terms of mathematical `Group` construct. This came with a slight performance impact but should not be directly noticeable for mainline code. CPU usage may be higher in hot code paths that touch gauges or counters.

Other features of note in this series:

* `Sigar` sample rate was changed from every 30 seconds to every 2 seconds to make reported aggregations statistically relevant. No user change required.

* Chemist had a massive overhaul; now has a formalised state machine to track assignment and load partitions.

* Flask now has administrative 0mq channels for reporting.

* System now has a huge integration test that checks the sanity of the system as a whole.

* Implemented a set of binary messages for metric types to replace JSON on the wire.

* Flask now has a mechanism for key expiry to keep its view of the world sanitised. 

<a name="21to30"></a>

# 2.1 to 3.0

* Package names were changed from `oncue.svc.funnel` to `funnel`.

<a name="17to20"></a>

# 1.7 to 2.x

* Package names were changed from `intelmedia.ws.funnel` to `oncue.svc.funnel`.

* 1.7 is NOT binary compatible with earlier versions. Please ensure you fully update your transitive classpath.