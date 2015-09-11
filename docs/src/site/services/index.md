---
layout: default
title:  "Services"
section: "services"
---

# {{ page.title }}

Whilst *Funnel* is mainly a library, in order to compose the core of the system across the network it is necessary to support a variety of different service components. Each of these services outlined below uses the *Funnel* core library and itself produces metrics (the monitoring services are monitored using *Funnel* too!).

<a name="agent"></a>

# Agent

*Funnel* agent is primarily designed to run on any given host and provides several pieces of functionality:

* A stable network interface for flask to later consume a metrics from via ZeroMQ.
* Simplistic HTTP API that on-machine scripts / processes can send metrics too in order to integrate with *Funnel* (e.g. a native C++ daemon).
* [StatsD](https://github.com/etsy/statsd/) bridge interface so that existing StatsD tools can integrate with *Funnel*.
* [Nginx](http://nginx.org/) statistic importer to pull utilisation data from the specific nginx stub_status module.

The agent process makes heavy use of the <a href="{{ site.baseurl }}modules/#zeromq">ZeroMQ</a> module in order to provide communication locally, and remotely. Specifically applications running on that host may connect to the appropriate local domain socket to submit their metrics, and in turn, the agent will consume from that domain socket and transparently emit those metrics on a TCP socket bound to the host adaptor. This provides a convenient mechanism for the application to report metrics irrespective of if this particular host is mulit-tennant (e.g. using containers and a resource manager) or not.

<a name="agent-config"></a>

#### Configuration

The agent does not have a huge number of configuration options, but what is avalible can be controlled via a [Knobs](https://github.oncue.verizon.net/pages/IntelMedia/knobs/) configuration file. The agent can be controlled simply by commenting out sections of the configuration file. For example commenting out `agent.http` will disable the HTTP server when the agent boots. The default looks like this:

```
agent {

  enable-system-metrics = true

  enable-jvm-metrics = true

  #############################################
  #                   Proxy                   #
  #############################################

  zeromq {
    # local file path of the domain socket incoming
    # metrics will arrive on.
    socket = "/tmp/funnel.socket"

    proxy {
      # network address to bind to from which the flask
      # will later connect. Must be accessible from the LAN
      host = "0.0.0.0"
      port = 7390
    }
  }

  #############################################
  #                 Importers                 #
  #############################################

  # recomended to keep network host to 127.0.0.1 as
  # each node should only ever be publishing metrics
  # to its loopback network address.

  # http {
  #   host = "127.0.0.1"
  #   port = 8080
  # }

  # statsd {
  #   port   = 8125
  #   prefix = "oncue"
  # }

  # nginx {
  #   url = "http://127.0.0.1:8080/nginx_status"
  #   poll-frequency = 15 seconds
  # }

  # currently only supports non-authenticated, non-ssl connections
  # designed to be use locally within a docker container or host.
  # processes running jmx typically need the following set:
  #
  # -Dcom.sun.management.jmxremote
  # -Dcom.sun.management.jmxremote.authenticate=false
  # -Dcom.sun.management.jmxremote.ssl=false
  # -Djava.rmi.server.hostname=?????
  # -Dcom.sun.management.jmxremote.port=????
  # jmx {
  #   # Name this jmx resource so that a "cluster" field can be specified
  #   # at query time which references the metrics imported via this source.
  #   # Exmaple would be: "dev-accounts-db-west-1a"
  #   name = "example"
  #
  #   uri = "service:jmx:rmi:///jndi/rmi://127.0.0.1:7199/jmxrmi"
  #
  #   poll-frequency = 28 seconds
  #
  #   # Examples of jmx queries are:
  #   # *:type=Foo,name=Bar to match names in any domain whose exact set of keys is type=Foo,name=Bar.
  #   # d:type=Foo,name=Bar,* to match names in the domain d that have the keys type=Foo,name=Bar plus zero or more other keys.
  #   # *:type=Foo,name=Bar,* to match names in any domain that has the keys type=Foo,name=Bar plus zero or more other keys.
  #   # d:type=F?o,name=Bar will match e.g. d:type=Foo,name=Bar and d:type=Fro,name=Bar.
  #   # d:type=F*o,name=Bar will match e.g. d:type=Fo,name=Bar and d:type=Frodo,name=Bar.
  #   # d:type=Foo,name="B*" will match e.g. d:type=Foo,name="Bling". Wildcards are recognized even inside quotes, and like other special characters can be escaped with \.
  #   queries = [ "org.apache.cassandra.db:*" ]
  #
  #   # Use glob syntax to specify patterns of attributes that you do not wish to
  #   # collect from the JMX endpoint. An example are:
  #   # "*HistogramMicros"
  #   # "*Histogram"
  #   # etc
  #   exclude-attribute-patterns = [ "*HistogramMicros", "*Histogram" ]
  # }

}
```
<a name="agent-http"></a>

#### HTTP

As for the HTTP API, it supports reporting of various types of metrics and the agent HTTP process is always bound to `127.0.0.1:$port`, where `$port` is the `agent.http.port` configuration value. The structure of the JSON payload is defined below, and the body must be `POST`ed to `/metrics` resource on the specified host:port combination:

```
{
  "cluster": "example",
  "metrics": [
    {
      "name": "ntp/whatever",
      "kind": "gauge-double",
      "value": "0.1234"
    }
    {
      "name": "ntp/whatever",
      "kind": "counter",
      "value": "1"
    },
    ...
  ]
}
```

The structure is very simple, but lets take a moment to just walk through the various options for completeness:

* `cluster`: This represents the logical application stack this set of metrics is coming from. For example, if the caller was reporting on NTP drift for the host, it would be reasonable to report the cluster as the nodes hostname. However, if the application was something more specific that just didn't have native *Funnel* bindings then it would be best to report the `cluster` field as a value that would unique identify the application, revision and deployment of said application. For example, `accounts-4.3.67-WdFtgkj`, or `encoder-v1-primary`. The value itself is arbitrary, but it will be used in downstream aggregation processes to figure out which metrics have the same "origin".

* `metrics`: An array of all the metric values you wish to report. The structure contains three fields with a variety of possible combinations. These are outlined below.

* `metrics.[0].name`: Name of the metric you wish to report. Names are an opaque handle used to refer to the metric values, but typically these should form a logical pattern which groups them into meaningfull catagories. For example, it might be useful to look at all HTTP GET calls in a website, so we might prefix all keys with the name `http/get` resulting in key names like `http/get/latency/index` or `http/get/latency/account`.

* `metrics.[0].kind` and `metrics.[0].value`: The kind of metric you wish to submit. Valid options are:

<table>
  <thead>
    <td>Kind</td>
    <td>Type</td>
    <td>Required</td>
    <td>Description</td>
  </thead>
  <tr>
    <td><code>counter</code></td>
    <td><code>Int</code></td>
    <td>No</td>
    <td>Atomic counter that can be incremented over time. If no value is specified, the counter increments by one, if an `Int` value is specified then the counter is incremented by the specified number.</td>
  </tr>
  <tr>
    <td><code>timer</code></td>
    <td><code>String</code></td>
    <td>Yes</td>
    <td>Specify the time a particular operation took. It's important to note that the HTTP does not keep any state to actually conduct timing, this must be done by the caller and reported via the HTTP API. The value supplied is a simple string that is parsed out to a `scala.concurrent.Duration`, and can be constructed with strings such as "20 milliseconds", "5 minutes", "3 days" etc.</td>
  </tr>
  <tr>
    <td><code>gauge-double</code></td>
    <td><code>Double</code></td>
    <td>Yes</td>
    <td>A double value that you wish to report and track its change over time.</td>
  </tr>
  <tr>
    <td><code>gauge-string</code></td>
    <td><code>String</code></td>
    <td>Yes</td>
    <td>A string value that you wish to report and track its change over time. Non-numeric gauges are typically less useful and their use case is infrequent.</td>
  </tr>
</table>

That's about it for the HTTP API. Whilst it should be able to tollerate a good beating performance wise, it will likley not be as performant as the native Scala bindings, so keep that in mind because of the extra layers of indirection.

<a name="agent-statsd"></a>

#### StatsD

The StatsD interface provided by the agent uses UDP and supports `c`,`ms`,`g` and `m` metric types. The StatsD interface is entirely optioanl.

<a name="agent-nginx"></a>

#### Nginx

The Nginx importer periodically fetches to the specified Nginx URL and extracts the opertaional data into the agent's *Funnel*. The following metrics are exposed if this feature is enabled:

* `nginx/connections`,
* `nginx/reading`
* `nginx/writing`
* `nginx/waiting`
* `nginx/lifetime/accepts`
* `nginx/lifetime/handled`
* `nginx/lifetime/requests`

The `nginx/lifetime/*` keys are essentially gauges that detail the state of nginx *since it was first booted*. These values will not reset over time, and this is how they are extracted from Nginx, so seeing these numbers grow over time is entirely normal. The remainder of the metrics behave exactly how normal gauges work: their values reflect current usage and are rolled up into the *Funnel* windowing.

<a name="agent-jmx"></a>

#### JMX

The JMX module is designed to periodically import metrics from a specificed JMX RMI location. The agent does not support authenticated JMX endpoints at this time and assumes that it is operating on the same machine as the JMX service you wish to connect too (technically it could do remote monitoring too, but its less flexible in that regard by design as the intentino is not to deploy a central service that reaches out to remote JMX endpoints - hence only supporting a single RMI endpoint).

The primary thing to understand about the JMX module is that you need to specify the MBean queries you are actually interested in. Examples of MBean queries (taken from the `ObjectName` JavaDoc in the JDK):

* `*:type=Foo,name=Bar` to match names in any domain whose exact set of keys is `type=Foo,name=Bar`.
* `d:type=Foo,name=Bar,*` to match names in the domain d that have the keys type=Foo,name=Bar plus zero or more other keys.
* `*:type=Foo,name=Bar,*` to match names in any domain that has the keys type=Foo,name=Bar plus zero or more other keys.
* `d:type=F?o,name=Bar` will match e.g. `d:type=Foo,name=Bar` and `d:type=Fro,name=Bar`.
* `d:type=F*o,name=Bar` will match e.g. `d:type=Fo,name=Bar` and `d:type=Frodo,name=Bar`.
* `d:type=Foo,name="B*"` will match e.g. `d:type=Foo,name="Bling"`. Wildcards are recognized even inside quotes, and like other special characters can be escaped with `\`.

Whilst this is reasoanbly expressive, `ObjectName` queries do not really support negation, and in some JMX endpoints which publish huge numbers of metric vectors it is often desirable to run a set of filters to remove unwanted keys. This is supported by the `exclude-attribute-patterns` configuration paramater in the `agent.cfg` (detailed above). The paramter uses [glob syntax](http://en.wikipedia.org/wiki/Glob_%28programming%29) to select metrics names the user wishes to *not* import.


<a name="flask"></a>

# Flask

*Flask* takes the role of a collector. It reaches out to nodes that have the *Funnel Agent* running and consumes their metrics which it turn were gathered from the application(s) running on that host.

Whilst *Flask* is a fairly small process, this section includes several sub-sections:

* [Configuration](#flask-configuration): details about configuring *Flask*.
* [Error Reporting](#flask-error-reporting): how does *Flask* report on errors
* [Stream Consumers](#flask-stream-consumers): how does *Flask* push data to 3rd party systems for visualisation and analysis.
* [System Metrics](#flask-system-metrics): enabling / disabling *Flask*'s built-in *Funnel*.

*Flask*'s sole job is to "mirror" the metrics from application hosts by taking mirroring instructions from an external orchestrating process (i.e. *Flask* only ever mirrors nodes it is instructed to mirror - it never figures this out itself). This is achieved with a simple HTTP `POST` to `http://<host>:<port>/mirror` on the *Flask* host. For example:

````
[
  {
    "cluster": "accounts-2.1-us-east",
    "urls": [
      "http://accounts-01.us-east.verizon.com:5777/stream/previous",
      "http://accounts-01.us-east.verizon.com:5777/stream/now?type=\"String\""
    ]
  }
]
````

This simple structure represents the "cluster" of URLs that one wishes to monitor. The expectation is that a given logical cluster (for example, accounts-2.1-us-east) there will be an associated set of URLs that are exposing metrics. This clustering is entirely arbitrary, and you can construct whatever cluster/URL combinations you want, with the one restriction that a given stream can only be connected to a single cluster at any given time (on the basis that connecting over and over to the same machine is a needless drain on resources). However, it is fine to send the same URL multiple times. In the event that there is an existing connection to that URL, the existing connection will be used instead of creating a new one (in other words, monitoring instructions are idempotent for cluster -> url combinations).

A URL can be any Funnel URL that serves a stream of metrics. It's a good idea to be as specific as possible here. Don't request the entire stream if you don't need it. As an example, the above cluster specification asks only for metrics from the `previous` window, as well as metrics from the `now` window that have the type `"String"` (which will generally not exist in the `previous` window).

<a name="flask-configuration"></a>

### Configuration

Flask takes all of its configuration information via [Knobs](https://github.oncue.verizon.net/pages/IntelMedia/knobs); primarily by file. The default configuration file looks like this:

```
flask {
  name = "flask"
  sns-error-topic = "flask-notifications"

  collect-local-metrics = true
  local-metric-frequency = 30

  network {
    host = "localhost"
    port = 6777
  }

# elastic-search {
#   url = "http://localhost:9200"
#   index-name = "eee"
#   type-name = "fff"
#
#   # Optional Parameters:
#   partition-date-format = "yyyy.ww"
#   connection-timeout-in-ms = 5000
# }

# riemann {
#   host = "localhost"
#   port = 5555
#   ttl-in-minutes = 5
# }

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

The final component living under the *Funnel* umbrella is *Chemist*. Given that each and every *Flask* does not know about any of its peers - it only understands the work it has been assigned - there has to be a way to automatically identify and assign new work as machines in a operational region startup, fail and gracefully shutdown. This is especially true when a given *Flask* instance fails unexpectedly, as its work will need to be re-assigned to other available *Flask* instances so that operational visibility is not compromised.

*Chemist* can be run on a variety of platforms. Currently there are two fully supported paths:

* `chemist-aws`: for dynamically growing and shrinking monitoring workloads utilising Amazon Web Services.
* `chemist-static`: for staticly provisioned on-premis hardware that seldom changes.
* `chemist-mesos`: **currently not implemented**, but initial work has been done on implementing a native mesos scheduler that is capable of conducting all the normal chemist behaviour, along with launching new Flask instances, as-and-when the system needs it.

Chemist has a rudimentary but useful API for conducting several management tasks. At the time of writing the following APIs were available:

* `GET /status`: simple status check for load-balancers to use to determine active nodes.

* `GET /shards`: display a list of all *Flask* shards, their network and version information.

* `GET /shards/:id`: display a information pertaining to a specific *Flask* shard ID, their network and version information.

* `POST /shards/:id/exclude`: migrate all the monitoring targets currently assigned to this flask shard to the other shards in the cluster. This is an extremely useful utility for conducting rolling upgrades to the flask cluster.

* `POST /shards/:id/include`: the opposite of the `exclude` resource, this re-registers the shard and allows chemist to assign it work.

* `GET /errors`: a bounded stack of all the errors the current set of *Flask* instances have seen. This is intended to be informative for administrators only; its just an FYI about the things the system stopped monitoring (for whatever reason).

* `GET /distribution`: the current split of available work over all the current shards. Essentially detailing which clusters are assigned to which shards.

* `GET /lifecycle/history`: the history of events as seen by chemist. This typically represents events like `StateChange` of a particular node, or `NewFlask` in the event new sharding capacity came online.

* `GET /lifecycle/states`: a bounded stack of the most recent state changes nodes in the system went through (see the below figure detailing the state workflow diagram of chemist internals).

* `GET /platform/history`

* `POST /bootstrap`: force chemist to conduct a full phase of discovery and request all current shard load from all flasks and assign any delta that is not found to be currently monitored. 

The chemist state machine can be visualised with this figure:

![image]({{ site.baseurl }}img/chemist-states.png)

### Chemist AWS

For deployment to AWS, *Chemist* leverages the fact that auto-scalling groups can notify an SNS topic upon different lifecycle events happening which are associated to that group. For example, if a machine is terminated a notification is pushed to SNS detailing exactly what happened and to the specific instance(s). An SQS topic is then connected to this SNS topic, and *Chemist* consumes the incoming messages. SQS acts as a persistent queue, so if the chemist node fails, and a new chemist is brought back up from the auto-scalling group, it simply picks up where its predecessor left off.

### Chemist Static

For deployment to on-premis, or fixed-capacity infrastructure, *Funnel* provides "chemist-static" which can orchestrate your *Flask* instances by way of a simple static file-based configuration. The configuration options are the same as the regular *Chemist*, with a few key additions:

```
chemist {
  ...

  targets {
    instance1 {
      cluster-name = "one"
      uris = [ "http://alpha:1234" ]
    }
    instance2 {
      cluster-name = "two"
      uris = [ "http://beta:5678" ]
    }
    instance3 {
      cluster-name = "three"
      uris = [ "http://delta:9012" ]
    }
  }

  flasks {
    flask1 {
      location {
        host = "ay"
	     port = 1111
        protocol = "http"
      }
      telemetry {
        host = "bee"
        port = 2222
        protocol = "tcp"
      }
    }
  }
}

```

As you can see, the `targets` section denotes the *Funnel* endpoints *Chemist* should try to monitor, whilst the `flasks` section denotes those nodes which should do the monitoring. The primary differentiator between the `chemist-static` module from other chemist implementations is that the configuration file will be dynamically reloaded if changes are made at runtime. The reason for this is that in fixed infrastructure, machines are typically mutated in-place using IT automation tools such as [Ansible](http://www.ansible.com/) or [Chef](https://www.chef.io/).

  
