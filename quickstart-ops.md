---
layout: default
title:  "Quickstart - Operations"
---

# Quickstart: Operations Team

Before reading this guide, ensure you have read the [primer document](riemann.html) that discusses the setup required for [Riemann](http://riemann.io/).

Assuming you have that done, and you're keen to get started, great!

**NOTE: Media deployments are always done to Linux machines, and operational monitoring will only be supported on linux.**

For operations staff, the funnel system has a binary called `utensil` that simply boots a local monitoring server and has the ability to funnel metrics into Riemann for operational consumption. 

### Manual Installation 

If you would like to simply download the binary and run it manually using your shell, TGZ and ZIP packages are available on the nexus. Download the [.zip](http://nexus.svc.oncue.com/nexus/content/repositories/releases/intelmedia/ws/funnel/oncue/) files and expand them into the directory of your choice. 

On unix systems, make the `./bin/utensil` shell executable (`chmod 755 ./bin/utensil`) and then execute the same file. This will boot up a local funnel instance that is ready to receive monitoring instructions. 


### System Package Installation 

***CURRENTLY NOT AVAILABLE DUE TO A PROBLEM WITH OUR REPOSITORIES***

The binary has been made available with a super simple `yum` command:

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


### Local system metrics

Utensil will also collect operating system statistics for the local machine if these two conditions are true:

* The Hyperic SIGAR library is available on the `LD_LIBRARY_PATH`.
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

