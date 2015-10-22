package funnel
package chemist

import oncue.svc.funnel.BuildInfo

object Banner {
  // http://patorjk.com/software/taag/#p=display&h=3&f=Big%20Money-se&t=Chemist
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
  | ${Chemist.version}
  | Built at ${BuildInfo.buildDate} with SBT ${BuildInfo.sbtVersion}
  | """.stripMargin

  val text = wording+suffix
}