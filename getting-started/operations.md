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

Given the different functions of each component, the sections below reviews them each in turn. Before getting to the details, it's worth noting some really important operational facts about the *Funnel* family of systems.

## Security

The security model of funnel currently **relies on network level security**; the application does not attempt to security its communication over the wire using any kind of TLS. With this in mind, never ever host a service on the public internet where the *Funnel* network port (typically `5775`) is open. Doing this would leave the system open for tampering and expose sensitive internals to attackers.

In this frame, the recommended security is to restrict access to datacenter internals, VPNs and specific VLANs. We may add application-layer security at a later date, but given that the system is designed to run fast and light, using operational network security is a fair trade off for the time being.

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

### Configuration

Flask takes all of its configuration information via [Knobs](https://github.svc.oncue.com/pages/intelmedia/knobs); primarily by file. The default configuration file looks like this:

```
flask {
  name = "flask"
  sns-error-topic = "flask-notifications"

  collect-local-metrics = true
  local-metric-frequency = 30

  network {
    host = "localhost"
    port = 5777
  }

  riemann {
    host = "localhost"
    port = 5555
    ttl-in-minutes  = 5
  }
}

aws {
  region = "$(AWS_DEFAULT_REGION)"
  access-key = "$(AWS_ACCESS_KEY)"
  secret-key = "$(AWS_SECRET_KEY)"
}

```

Let's consider these options one by one:

* `flask.name`: the name the running JVM process will report.

* `flask.sns-error-topic`: topic that flask will publish errors too (see the section [Dynamic Error Reporting](#flask-error-reporting) for more information)

* `flask.collect-local-metrics` and `flask.local-metric-frequency`: should flask use its own funnel (see the section [System Metrics](#flask-system-metrics))

* `flask.network.host` and `flask.network.port`: the network host and port that flask process will bind too when it starts.

* `flask.riemann.*`: configuration parameters that specify how flask will connect with the Riemann alerting system. See [Stream Consumers](#flask-stream-consumers) for more information)


<a name="flask-error-reporting"></a>

### Dynamic Error Reporting

Sometimes, things go wrong and *Flask* will be unable to reach a *Funnel* host that it was previously talking too. When this happens, *Flask* will exponentially back-off and try to reconnect over a period of time, but if all attempts fail *Flask* will raise an alert and push a notification onto Amazon SNS (which in turn pushes to all subscribers).

The SNS topic specified with the configuration parameter `flask.sns-error-topic` does not have to exist at the time *Flask* boots up; if its missing then it will automatically be created (assuming the AWS privileges are sufficient to allow for that).

<a name="flask-stream-consumers"></a>

### Stream Consumers


<a name="flask-system-metrics"></a>

### System Metrics

As mentioned above, the *Flask* is also a service so it too can produce metrics and be monitored. To enable funnel on the Flask instance, two conditions need to be true:

* The Hyperic SIGAR library is available on the `LD_LIBRARY_PATH`.
* The `flask.cfg` configuration file contains the `flask.collect-local-metrics = true` setting.

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


