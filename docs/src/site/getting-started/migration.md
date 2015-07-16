---
layout: default
title:  "Getting Started - Migration"
section: "getting-started"
---

# Getting Started: Migration

This document outlines the various steps required to migrate from one version of funnel to another.

* [3.2 to 4.0](#32to40)
* [3.0 to 3.2](#30to32)
* [2.1 to 3.0](#21to30)
* [1.7 to 2.x](#17to20)

<a name="32to40"></a>

# 3.2 to 4.0

* Updated dependencies:
	* `core` now uses `scalaz-stream 0.7.1a`
	* `core` now uses `journal 2.1.+`
	* `core` now uses `scalaz 7.1.2`
	* `http` now uses public `argonaut 6.1` (no longer uses the oncue-internal build)
	* `chemist`, `flask` now use open source version of Knobs

* Upgrade to sbt-oncue 7.3 to make use of the compatible testing libraries. In your `plugins.sbt` ensure that you have the following: `addSbtPlugin("oncue.build" %% "sbt-oncue" % "7.3.+")`

* Ensure that your dependency versions match the following (if applicable - using older versions will result in binary collisions are runtime):
	* `"oncue.knobs" %% "core" % "3.2.+"` - does not depend on funnel, but is scalaz-compatible
	* `"oncue.commonutils" %% "dal-cassandra" % "4.0.+"` - depends on funnel directly
	* `"oncue.svc.search.client" %% "core" % "9.0.+"` - depends on funnel directly
	* `"oncue.rna.jetevents" %% "core" % "3.0.+"` - 

*Be sure that you do not have funnel 2.x on your transitive classpath, and that any common libraries you're using are suitable upgraded.*

Other things of note in this release cycle (does not affect migration):

* Added the first iteration of an experimentation API - this is highly experimental and only for use when supported by the experimentation team.

* Added key senessence to the standard monitoring instance. This means that keys that are not used over a long period (default: 36 hours) are expired from the internal monitoring state.

* Fixed Chemist lifecycle machine and added automatic cleanup / detection of orphaned monitoring targets.


<a name="30to32"></a>

# 3.0 to 3.2

* `Counter` no longer has the `decrement` or `decrementBy` functions. If you require reporting of a value that could go up or down, please instead use a `Gauge` (aka `gauge` function on the `Instruments` instance).

* `Edge` metric type is now available for reporting on graph-like structures / topologies. 

* `Counter` and `Gauge` were reimplemented in terms of mathematical `Group` construct. This came with a slight performance impact but should not be directly noticeable for mainline code. CPU usage may be higher in hot code paths that touch gauges or counters.

Other features of note in this series:

* Chemist had a massive overhaul; now has a formalised state machine to track assignment and load partitions.

* Flask now has administrative 0mq channels for reporting.

* System now has a huge integration test that checks the sanity of the system as a whole.

* Implemented a set of binary messages for metric types to replace JSON on the wire.

* Flask now has a mechanism for key expiry to keep its view of the world sanitised. 

<a name="21to30"></a>

# 2.1 to 3.0

* Package names were changed from `oncue.svc.funnel` to `funnel`.

* Added a specialised attribute that identifies which logical type of instrument this particular key is from. This will allow us to collect only specific types of instrument from different windows (i.e. collecting the traffic lights only from `now` window).


<a name="17to20"></a>

# 1.7 to 2.x

* Package names were changed from `intelmedia.ws.funnel` to `oncue.svc.funnel`.

* Added the concept of key attributes so that keys can have an accompanying set of metadata (e.g. description)

* 1.7 is NOT binary compatible with earlier versions. Please ensure you fully update your transitive classpath.