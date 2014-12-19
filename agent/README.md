## Agent

The sole job of this module is to build a tiny, tiny lightweight streaming proxy agent for metrics collected from multi-tenant environments. The rationale here is that not all machines will host a single application, and in this case, where applications are deployed on random host:port combinations it is necessary to have a mechanism that provides a fixed point for the monitoring infrastructure to attach to a given host. This agent uses [UNIX domain sockets](http://en.wikipedia.org/wiki/Unix_domain_socket) for high-throughput IPC that does not touch the network card.

In addition, this agent also provides a small HTTP API for remote instrumentation; useful for processes that do not have native funnel support.

