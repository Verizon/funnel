---
layout: default
title:  "Modules"
section: "modules"
---

# {{ page.title }}

*Funnel* comes with a range of modules that allows composistion of the system in a variety of ways. Some modules are straight exporters, others provide both importing and exporting functionality.

<a name="elastic"></a>

## Elastic Search

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

<a name="riemann"></a>

## Riemann

Primarily for alerting, *Funnel* datapoints are batched up and sent to Riemann so that alerts can be configured (and saving the need for us to integrate with many 3rd party vendors). Please see the [Riemann documentation](http://riemann.io) for information on how to setup the alerting system.

Riemann is a great choice for small teams using *Funnel*, but in larger organisations the lack of self-service alert definition is usually a deal breaker. In our field testing, we were easily able to overwhelm Reimann, even when it was running on a powerful box - hence, this is not recomended for large distributed systems.

<a name="zeromq"></a>

## ZeroMQ

The [ZeroMQ](http://zeromq.org/) module provides both the ability to publish metrics from a `Monitoring` instance out to a socket (with an arbitrary transport and mode), and the ability to consume metrics from a socket and import them into the specified `Monitoring` instance. 

#### Instalation

The ZeroMQ module comes in two flavours:

1. `zeromq` which uses the *native* `libzmq` C++ library and allows use of UNIX domain sockets, PGM etc. This is the reference implementation of ZMTP.

1. `zeromq-java` which uses the pure-Java implementation of the same library API. This module is meant purely for compatiblity with Windows systems, as it cannot leverage domain sockets or PGM. Do not use this module unless you are sure of what you're doing. 

With this frame, in order to run locally you'll need to install the `libzmq` and the `jzmq` native binaries. You can do that by running the following command:

```
TODO
```
That's all there is to it!

#### Exporting

In order to publish your metrics using ZeroMQ, ensure that you have the following in your `build.sbt`:

```
libraryDependencies += "oncue.svc.funnel" %% "zeromq" % "x.x.x"
```

This will allow you to setup the publishing of metrics. Technically, this publishing happens on a background daemon thread, so its typically best to put this line at the very edge of your work (typically the application main):

```
package foo

object Foo {
  def main(args: Array[String]): Unit = {
    oncue.svc.funnel.zeromq.Publish.toUnixSocket()
  }
}

```

**NOTE**: If you do not have the native library installed you will see an error at runtime on your local machine. For development purposes this can be ignored - the binaries are instaleld by default on the foundation images used during deployment.

If you would like more control over when the `Publish` process is started and stopped, then you can simply pass the function an async `Signal[Boolean]`:

```
package foo

import scalaz.stream.async.signalOf
import scalaz.stream.async.mutable.Signal
import scalaz.concurrent.Task
import oncue.svc.funnel.zeromq.Publish

object Foo {

  def stop(signal: Signal[Boolean]): Task[Unit] = {
    for {
      _ <- signal.set(false)
      _ <- signal.close
    } yield ()
  }

  def main(args: Array[String]): Unit = {
  	 val alive = signalOf(true)
    Publish.toUnixSocket(signal = alive)
    
    // sometime later when you want to stop
    stop(alive)
  }
}

```

#### Importing

Similarly, importing metrics is trival. Please note this documentation is intended for completeness only and is not a typical user-level API unless one is implementing a *Flask*-like process. Given that `Monitoring.mirrorAll` simply requires a `DatapointParser` type, the ZeroMQ module supplies one like this:

```
package foo

import scalaz.stream.async.signalOf
import oncue.svc.funnel.zeromq.Publish
import oncue.svc.funnel.DatapointParser

object Foo {

  def main(args: Array[String]): Unit = {
  	 val alive = signalOf(true)
    val dp: DatapointParser = Mirror.from(alive) _ 
  }
}
```

#### Endpoints

When using the ZeroMQ module primitives to do anything with sockets, you will need to specify and `Endpoint`. The module provides a range of useful combinators to make creating the type of socket you want simple. Here's an example:

```
package foo

import scalaz.stream.async.signalOf
import oncue.svc.funnel.zeromq.{Endpoint,sockets}, sockets._
import java.net.URI

object Foo {

  def main(args: Array[String]): Unit = {
  	 val uri = new URI("ipc:///tmp/foo.socket")
    val E1 = Endpoint(publish &&& bind, uri)
    val E2 = Endpoint(push &&& bind, uri)
    val E3 = Endpoint(subscribe &&& (connect ~ topics.all))
  }
}
```

Essentially the ZeroMQ socket types are modled as functions and you can specify if you want to be the side of the connection that `bind`'s or `connect`'s. In the case of PUB-SUB you also have the option to specify a distriminating predicate for topic subscription.

