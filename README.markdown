## Monitoring Funnel

A distributed monitoring system based on a lightweight streaming protocol. **This library depends upon Scala 2.10.x - you will see crazy errors if you try to use this on 2.9.x**

To get started, please read the appropriate documentation:

* For engineers that want to instrument their code, [see here](docs/quickstart-dev.markdown)
* For operations who want to aggregate metrics and monitor systems, [see here](docs/quickstart-ops.markdown)

The funnel allows systems to push, pull, mirror and aggregate metrics from disparate systems, all with a unified API which can be seamlessly streamed into a real-time dashboard:

![image](docs/img/riemann-dash.png)

