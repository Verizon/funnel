package funnel.chemist.aws

case class MessageParseException(override val getMessage: String) extends RuntimeException
