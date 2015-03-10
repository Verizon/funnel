package funnel
package chemist

import knobs.{FileResource,ClassPathResource,Required}
import knobs.{Config => KConfig}
import java.io.File

/**
 *
 */
trait Platform {
  type Config <: PlatformConfig

  lazy val defaultKnobs =
    knobs.loadImmutable(Required(
      FileResource(new File("/usr/share/oncue/etc/chemist.cfg")) or
      ClassPathResource("oncue/chemist.cfg")) :: Nil)

  // def knobs: KConfig
  def config: Config
}
