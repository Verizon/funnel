package funnel
package chemist
package static

import org.scalatest._
import java.io.File
import knobs.{FileResource,ClassPathResource,Required}

class StaticTest extends FlatSpec with Matchers {
  it should "load Instances from chemist.cfg" in {
    val instances = (for {
      cfg   <- (knobs.load(Required(
        ClassPathResource("oncue/chemist.cfg")) :: Nil))
      sub   <- cfg.base.at("chemist.instances")
      ins   <- Config.readInstances(sub)
    } yield ins).run
    instances.exists {
      case Instance("instance1", Location(Some("alpha"), _, 1234, _, _), _, _) => true
      case _          => false
    } &&
    instances.exists {
      case Instance("instance2", Location(Some("beta"), _, 5678, _, _), _, _) => true
      case _          => false
    } &&
    instances.exists {
      case Instance("instance3", Location(Some("delta"), _, 9012, _, _), _, _) => true
      case _          => false
    }
  }
}
