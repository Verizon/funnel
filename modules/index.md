---
layout: default
title:  "Modules"
section: "modules"
---

# {{ page.title }}

*Funnel* comes with a range of modules that allows composistion of the system in a variety of ways. Some modules are straight exporters, others provide both importing and exporting functionality.

<a name="agent"></a>

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


<a name="agent"></a>

## Riemann

Primarily for alerting, *Funnel* datapoints are batched up and sent to Riemann so that alerts can be configured (and saving the need for us to integrate with many 3rd party vendors). Please see the [Riemann documentation](http://riemann.io) for information on how to setup the alerting system.

Riemann is a great choice for small teams using *Funnel*, but in larger organisations the lack of self-service alert definition is usually a deal breaker. In our field testing, we were easily able to overwhelm Reimann, even when it was running on a powerful box - hence, this is not recomended for large distributed systems.

<a name="zeromq"></a>

## ZeroMQ

sdfsdfsdfsdf
