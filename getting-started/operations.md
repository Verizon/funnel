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





