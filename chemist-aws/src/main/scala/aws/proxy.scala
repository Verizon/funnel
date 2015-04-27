package funnel
package aws

import com.amazonaws.{ClientConfiguration,Protocol}

object proxy {
  def configuration(
    awsProxyHost: Option[String] = None,
    awsProxyPort: Option[Int] = None,
    awsProxyProtocol: Option[String] = None
  ): ClientConfiguration = {
    awsProxyHost map { h =>
      (new ClientConfiguration)
        .withProxyHost(h)
        .withProxyPort(awsProxyPort.getOrElse(911))
        .withProtocol {
          awsProxyProtocol.filter(_ equalsIgnoreCase "https"
          ).map(z => Protocol.HTTPS).getOrElse(Protocol.HTTP)
        }
    } getOrElse (new ClientConfiguration)
  }
}
