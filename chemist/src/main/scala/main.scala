package funnel
package chemist

object Main {
  def main(args: Array[String]): Unit = {
    // alias the `Server` API and what constitutes a chemst server
    val S = Server
    // provide an interpreter for that server
    val I = Server0

    new Chemist(I, 9000).start()
    // this should probally be called to release
    // the underlying resources.
    // dispatch.Http.shutdown()
  }
}