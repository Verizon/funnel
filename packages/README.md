# Packaging

These set of packages are intended for deployment purposes. We primarily package services as RPMs, but also provide a "fat jar" for the Funnel Agent.

Deployment itself is conducted through the normal pipeline, however, because of the complexity associated with updating critical infrastructure, and seamless migrating from version-to-version, I have supplied a whole set of automation for this procedure, comprised of two Jenkins jobs:

1. [Discover](https://jenkins.oncue.verizon.net:8443/job/EC2-funnel-deploy-discover/build?delay=0sec)

2. [Executor](https://jenkins.oncue.verizon.net:8443/job/EC2-funnel-deploy-executor/build?delay=0sec)

Executor is a down-stream job of the Discover job, but they are listed here separately for completeness. In order to update the funnel infrastructure in any given environment, all one needs to do is populate the arguments for the discover job, and away you go. When running on AWS, the process looks something like this:

1. put alerts with the "funnel" tag in maintenance mode
2. locate the currently running and active chemist by resolving the defined service tag and the associated services.
3. tear down the existing chemist stack (does not impact the specified environment, unless and an outage in flask happens during the small upgrade window of a few minutes)
4. deploy the new flasks with the normal deploy pipeline
5. update the service tag to point to new flask deployment
6. tear down the current flasks
7. deploy the chemist, which then automatically assigns work to the new flasks upon starting up.

