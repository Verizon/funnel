package funnel
package chemist

import oncue.svc.funnel.BuildInfo

object Banner {
  private val wording = """
  |  ______  __                             __            __
  | /      \|  \                           |  \          |  \
  ||  $$$$$$| $$____   ______  ______ ____  \$$ _______ _| $$_
  || $$   \$| $$    \ /      \|      \    \|  \/       |   $$ \
  || $$     | $$$$$$$|  $$$$$$| $$$$$$\$$$$| $|  $$$$$$$\$$$$$$
  || $$   __| $$  | $| $$    $| $$ | $$ | $| $$\$$    \  | $$ __
  || $$__/  | $$  | $| $$$$$$$| $$ | $$ | $| $$_\$$$$$$\ | $$|  \
  | \$$    $| $$  | $$\$$     | $$ | $$ | $| $|       $$  \$$  $$
  |  \$$$$$$ \$$   \$$ \$$$$$$$\$$  \$$  \$$\$$\$$$$$$$    \$$$$
  |
  |""".stripMargin

  private val suffix = s"""
  | Version ${BuildInfo.version} (${BuildInfo.gitRevision})
  | Built at ${BuildInfo.buildDate} with SBT ${BuildInfo.sbtVersion}
  | """.stripMargin

  val text = wording+suffix
}