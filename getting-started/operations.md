---
layout: default
title:  "Getting Started - Operations"
section: "getting-started"
---

# Getting Started: Operations

The *Funnel* monitoring system is an umbrella for several components:

* [Funnel]({{ site.baseurl }}/services/#funnel): runs on each and every service and provides an instrumentation API for engineers to publish metrics about their application.

* [Flask]({{ site.baseurl }}/services/#flask): collects metrics coming out of funnels and stores them in local memory, partitioned by a given "bucket" (more on this later).

* [Chemist]({{ site.baseurl }}/services/#chemist): organising what funnels are pouring which metrics into which flasks. In essence this component is a job manager. Chemist can automatically detect failures in flasks and repartition the load amongst the remaining flasks in the operational cluster.

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

Flask is - perhaps confusingly - also a funnel, as it too is a service. This section includes several sub-sections which are of interest:

* [Configuraiton](#flask-configuration): details about configuring *Flask*.
* [Error Reporting](#flask-error-reporting): how does *Flask* report on errors
* [Stream Consumers](#flask-stream-consumers): how does *Flask* push data to 3rd party systems for visualisation and analysis.
* [System Metrics](#flask-system-metrics): enabling / disabling *Flask*'s built-in *Funnel*.

*Flask*'s sole job is to mirror the *Funnel* hosts by taking mirroring instructions. This is achieved with a simple HTTP `POST` to `http://<host>:<port>/mirror` on the *Flask* host. For example:

````
[
  {
    "bucket": "accounts-2.1-us-east",
    "urls": [
      "http://accounts-01.us-east.verizon.com:5777/stream/previous",
      "http://accounts-01.us-east.verizon.com:5777/stream/now?type=\"String\""
    ]
  }
]
````

This simple structure represents the "buckets" of URLs that one wishes to monitor. The expectation is that a given logical bucket (for example, accounts-2.1-us-east) there will be an associated set of URLs that are exposing metrics. This bucketing is entirely arbitrary, and you can construct whatever bucket/URL combinations you want, with the one restriction that a given stream can only be connected to a single bucket at any given time (on the basis that connecting over and over to the same machine is a needless drain on resources). However, it is fine to send the same URL multiple times. In the event that there is an existing connection to that URL, the existing connection will be used instead of creating a new one (in other words, monitoring instructions are idempotent for bucket -> url combinations).

A URL can be any Funnel URL that serves a stream of metrics. It's a good idea to be as specific as possible here. Don't request the entire stream if you don't need it. As an example, the above bucket specification asks only for metrics from the `previous` window, as well as metrics from the `now` window that have the type `"String"` (which will generally not exist in the `previous` window).

<a name="flask-configuration"></a>

### Configuration

Flask takes all of its configuration information via [Knobs](https://github.oncue.verizon.net/pages/intelmedia/knobs); primarily by file. The default configuration file looks like this:

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

* `flask.sns-error-topic`: topic that flask will publish errors to (see the section [Dynamic Error Reporting](#flask-error-reporting) for more information)

* `flask.collect-local-metrics` and `flask.local-metric-frequency`: should flask use its own funnel (see the section [System Metrics](#flask-system-metrics))

* `flask.network.host` and `flask.network.port`: the network host and port that flask process will bind to when it starts.

* `flask.riemann.*`: configuration parameters that specify how flask will connect with the Riemann alerting system. See [Stream Consumers](#flask-stream-consumers) for more information)


<a name="flask-error-reporting"></a>

### Dynamic Error Reporting

Sometimes, things go wrong and *Flask* will be unable to reach a *Funnel* host that it was previously talking to. When this happens, *Flask* will exponentially back-off and try to reconnect over a period of time, but if all attempts fail *Flask* will raise an alert and push a notification onto Amazon SNS (which in turn pushes to all subscribers).

The SNS topic specified with the configuration parameter `flask.sns-error-topic` does not have to exist at the time *Flask* boots up; if its missing then it will automatically be created (assuming the AWS privileges are sufficient to allow for that).

<a name="flask-stream-consumers"></a>

### Stream Consumers

*Flask* is designed to pull metrics from *Funnel* hosts and then emit those windows to a set of output locations. These output locations are for the most part arbitrary - all that's needed is to write a `Sink` that then serialises the stream from *Flask* to something that can be understood by the system you wish to send the datapoints to. 

At the time of writing, there were two stream consumers implemented:

#### Riemann

Primarily for alerting. Events are batched up and sent to Riemann so that alerts can be configured (and saving the need for us to integrate with many 3rd party vendors). Please see the [Riemann documentation](http://riemann.io) for information on how to setup the alerting system.

#### ElasticSearch

Primarily for visualisation, this consumer takes the stream and aggregates into one document *per-funnel-host* per time window, and then sends those documents to Elastic Search in a bulk `PUT` operation. The format of the message looks like this:

```
{
  "@timestamp": "2014-08-22T17:37:54.201855",
  "cluster": "imqa-maestro-1-0-279-F6Euts",  #This allows for a Kibana search, cluster: x
  "host": "ec2-107-22-118-178.compute-1.amazonaws.com",
  "previous" {
    "jvm": {
      "memory": {
        "heap": {
          "committed": {
            "last": 250.99763712000001,
            "mean": 250.99763712000001,
            "standard_deviation": 0.0
          },
          "usage": {
            "last": 0.042628084023299997,
            "mean": 0.042445506024100001,
            "standard_deviation": 0.00018257799924300001
          }
        }
      }
    }
  }
}
```

Using this document format enables Elastic Search to reason about both aggregate time queries, and top-n queries - the two primary use cases for monitoring visualisation.


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

The final component living under the *Funnel* umbrella is *Chemist*. Given that each and every *Flask* does not know about any of its peers - it only understands the work it has been assigned - there has to be a way to automatically identity and assign new work as machines in a operational region startup, fail and gracefully shutdown. This is especially true when a given *Flask* instance fails unexpectedly, as its work will need to be re-assigned to other available *Flask* instances so that operational visibility does not get compromised.

For deployment to AWS, *Chemist* leverages the fact that auto-scalling groups can notify an SNS topic upon different lifecycle events happening which are associated to that group. For example, if a machine is terminated a notification is pushed to SNS detailing exactly what happened and to the specific instance(s). This is incredibly useful from a monitoring perspective, as it means that the *Chemist* lifecycle is the following:

1. *Chemist* boots up and reads the list of machines from the AWS API and figures out which subset of all the given machines can be monitored. 
1. Figure out of all of those machines, which look like *Flask* instances based upon the deployment metadata.
1. Next, partition all non-flask instances to the discovered *Flask* instances that are running. 
1. Consume an SQS queue that is in turn subscribed to the aforementioned SNS topic that ASGs push their lifecycle events to. In this way, *Chemist* always gets notified of new machines that come online within a given region. 

Because of its managerial role within the system, Chemist has a rudimentary but useful API for conducting several management tasks. At the time of writing the following APIs were available:

* `GET /shards`: display a list of all *Flask* shards and display the work that is current assigned to the given shard.

* `POST /mirror`: using the same bucketing format as *Flask* instance, manually add some machines to be monitored.






