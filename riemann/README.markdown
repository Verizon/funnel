## Riemann Funnel

On the receiving side, to make the funnel system work for operations it is required to have a consumer of the stream that allows for definition of operational logic and associated actions, based on said domain logic. For example, if certain systems fall into an "unhealthy" state, then perhaps the operations manager should receive a text message, or maybe the engineer responsible should receive an email. These are trivial examples, but "action" in this context is essentially any executable function - basically, do something useful based on operations defined domain logic.

So as not to reinvent the wheel, we have adopted the [Riemann](http://riemann.io/) stream processing server, which we will host at the edges of our platform network. This module represents a stream consumer that translates metrics from the funnel system to the protocol used by Riemann. Architecturally, you can visualise this setup like this:

![Workflow](../docs/img/funnel-riemann.png)

### Getting Started

In order to get started with the Riemann integration, please follow the [quick start instructions](http://riemann.io/quickstart.html) for installing and boot up Riemann locally.

With Riemann now running, in another shell window please install the following ruby gems (required to run Riemann dashboard locally):

````
sudo gem install riemann-dash
````

Once installed, you can start the dashboard with the following command:

````
riemann-dash
````
**NOTE:** If you want to be able save your dashboard layouts for later viewing after a server restart of browser reload, then make sure the location you installed the gem has write permission. Alternertavitly, if its your local dev box - and you want to party like you just don't care - use the finger of god: 

````
sudo riemann-dash
````
**DO NOT DO THIS IN PRODUCTION.**

With all the Riemann components running, all that's needed now is to actually funnel some data into it. This can be achieved by using the command line funnel client.





