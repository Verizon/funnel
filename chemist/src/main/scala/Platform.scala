package funnel
package chemist

import java.io.File
import knobs.{Config => KConfig}
import knobs.{FileResource,ClassPathResource,Required}

/**
 * A `Platform` is somewhere that hosts your application. Examples
 * of a platform would be Amazon Web Services, Mesos, your internal
 * datacenter etc.
 */
trait Platform {
  type Config <: PlatformConfig

  lazy val defaultKnobs =
    knobs.loadImmutable(Required(
      FileResource(new File("/usr/share/oncue/etc/chemist.cfg")) or
      ClassPathResource("oncue/chemist.defaults.cfg")) :: Nil)

  def config: Config
}
