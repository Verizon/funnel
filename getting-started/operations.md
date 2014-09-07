---
layout: default
title:  "Getting Started - Operations"
section: "getting-started"
---

# Getting Started: Operations

The *Funnel* monitoring system is an umbrella for several components:

* [Funnel](#funnel): runs on each and every service and provides an instrumentation API for engineers to publish metrics about their application.

* [Flask](#flask): collects metrics coming out of funnels and stores them in local memory, partitioned by a given "bucket" (more on this later).

* [Chemist](#chemist): organising what funnels are pouring which metrics into which flasks. In essence this component is a job manager. Chemist can automatically detect failures in flasks and repartition the load amongst the remaining flasks in the operational cluster.

At a high-level, the system can be visualised like this:

![image]({{ site.baseurl }}img/chemist-flask-funnel.png)

Given the different functions of each component, this document now reviews them each in turn.

<a name="funnel"></a>

# Funnel

From an operational perspective, the typical use case is not to talk directly to Funnel. Instead, the goal is to simply instruct flasks to monitor the correct URLs being published from the built-in control server.

With that being said, its worth noting that each and every funnel server allow you to get an aggregate count of its metric keys, which can be operationally useful for debugging etc. This can be accessed by visiting `http://<location>:<port>/audit`. The response looks like:

```
{
	// add json response here
}
```

For more information about the accessible URLs on the control server, please see the [detailed information section]({{ site.baseurl }}details/#control-server).

<a name="flask"></a>

# Flask

Flask is - perhaps confusingly - also a funnel, as it too is a service. However, its sole job is to mirror the service funnels by taking mirroring instructions. This is achieved with a simple HTTP `POST` to `http://<host>:<port>/mirror` on the flask host.

````
[
  {
    "bucket": "accounts-2.1-us-east",
    "urls": [
      "http://<host>:<port>/stream"
    ]
  }
]
````

This simple structure represents the "buckets" of nodes that one wishes to monitor. The expectation is that a given logical bucket (for example, accounts-2.1-us-east) there will be an associated set of nodes that are exposing metrics. This bucketing is entirely arbitrary, and you can construct whatever bucket/URL combinations you want, with the one restriction that a given stream can only be connected to a single bucket at any given time (on the basis that connecting over and over to the same machine is a needless drain on resources). However, it is fine to send the same URL multiple times. In the event that there is an existing connection to that node, the existing connection will be used instead of creating a new one (in other words, monitoring instructions are idempotent for bucket -> url combinations).

### Local system metrics

As mentioned above, the *Flask* is also a service so it too can produce metrics and be monitored. To enable funnel on the Flask instance, two conditions need to be true:

* The Hyperic SIGAR library is available on the `LD_LIBRARY_PATH` (note this happens by default when the Flask instances are deployed using the bake pipeline).

* The Utensil configuration file contains the `localHostMonitorFrequencySeconds` setting.

If your machine is a recently baked instance, it should already contain Hyperic SIGAR on the `LD_LIBRARY_PATH`.Otherwise download the library here: [http://sourceforge.net/projects/sigar/files/](http://sourceforge.net/projects/sigar/files/).

Unzip the file and drop the appropriate `.so` file into your `LD_LIBRARY_PATH`.

SIGAR gathers a large set of metrics for the local machine:

* Aggregate CPU metrics.
* Per-CPU metrics for individual CPUs.
* TCP/IP statistics.
* Filesystem statistics where `:fs` is an individual filesystem mount point. The mount point name is URL-encoded.
* Memory statistics.
* Load averages for 5, 10, and 15 minute intervals.
* Operating system uptime.

<a name="chemist"></a>

# Chemist

Whatever normcore pour-over Portland, slow-carb paleo quinoa gentrify Helvetica small batch. Cosby sweater McSweeney's Echo Park fingerstache tofu. 90's scenester bespoke, farm-to-table you probably haven't heard of them forage 8-bit normcore Vice High Life quinoa kitsch freegan Tonx single-origin coffee. Readymade wayfarers beard pop-up Neutra banh mi ennui mlkshk. Pinterest fap hoodie seitan. Sartorial paleo kitsch dreamcatcher, food truck viral hella blog. Typewriter Tonx fashion axe biodiesel dreamcatcher.


