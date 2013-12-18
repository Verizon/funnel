---
layout: default
title:  "Quickstart - Operations"
---

## Quickstart: Operations Team

Before reading this guide, ensure you have read the [primer document](http://github-media.sc.intel.com/IntelMedia/monitoring/tree/master/docs/riemann.markdown) that discusses the setup required for [Riemann](http://riemann.io/).

Assuming you have that done, and you're keen to get started, great!

**NOTE: Media deployments are always done to Linux machines, and operational monitoring will only be supported on linux.**

### Installing Funnel Utensil 

For operations staff, the funnel system has a binary called `utensil` that simply boots a local monitoring server and has the ability to funnel metrics into Riemann for operational consumption. This binary has been made available with a super simple `yum` command:

````
sudo yum install funnel-utensil
````

As this is a totally internal system, this has not been published to a public yum repository, so if your box has never used the internal yum repo, then you'll need to add it to your system by adding the below to `/etc/yum.repos.d/IntelMedia.repo`:

````
[intel_media_yum]
name=Intel Media YUM Group
baseurl=http://USERNAME:PASSWORD@nexus-nexusloadbal-1cj6r4t1cb574-1345959472.us-east-1.elb.amazonaws.com/nexus/content/groups/intel_media_yum
enabled=1
protect=0
gpgcheck=0
metadata_expire=30s
autorefresh=1
type=rpm-md

````

With the binary installed, you can simply start the service using the regular `init.d` commands:

````
// TODO: Binary does not currently have init.d bash scripts and runs in the foreground. Swap this to use init.d
````


### Request metrics

From an operational perspective the only thing that one should really care about is how to instruct the aggregating funnel utensil which nodes it should be monitoring. This is achieved with a simple HTTP `POST` to `http://<host>:<port>/mirror`

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
This simple structure represents the "buckets" of nodes that one wishes to monitor. The expectation is that a given logical bucket (for example, accounts-2.1-us-east) there will be an associated set of nodes that are exposing metrics. This bucketing is entirely arbitrary, and you can construct whatever bucket/URL combinations you want, with the one restriction that a given stream can only be connected to a single bucket at any given time (on the basis that connecting over and over to the same machine is a needless drain on resources). However, it is fine to send the same URL multiple times. In the event that there is an existing connection to that node, the existing connection will be used instead of creating a new one.


### Monitoring Server: Mirroring

TODO



