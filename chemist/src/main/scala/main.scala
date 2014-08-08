package oncue.svc.funnel.chemist

object Main {
  def main(args: Array[String]): Unit = {
    val I = Server0
    val S = Server

    val exe = S.listen

    // block at the edge of the world
    I.run(exe).run

  }
}