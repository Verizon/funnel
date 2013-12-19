---
layout: default
title:  "Home"
---

# Introduction

A distributed monitoring system based on a lightweight streaming protocol. *This library depends upon Scala 2.10.x - you will see crazy errors if you try to use this on 2.9.x*

To get started, please read the appropriate documentation:

* For engineers that want to instrument their code, [see here](quickstart-dev.html)
* For operations who want to aggregate metrics and monitor systems, [see here](quickstart-ops.html)
* For general information about how the funnel system works - including some internals - [see here](manual.html)

The funnel allows systems to push, pull, mirror and aggregate metrics from disparate systems, all with a unified API which can be seamlessly streamed into a real-time dashboard:

![image]({{ site.baseurl }}/img/riemann-dash.png)

