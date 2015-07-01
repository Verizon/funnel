package funnel
package chemist

// import scalaz.concurrent.Task

// // goal here is to have some kind of external coordinator
// // that can be used for knowing which deployment of any
// // given system (flask or chemist) is the "right" one.
// //
// // Derivitive implementations could supply a coordinator
// // to a Classifier, such that the classifier can then know
// // how to conduct its classification.
// trait Coordinator[A]{
//   def coordinate: Task[A]
//   // def listActive: Task[Seq[A]]
//   // def isActive(a: A): Task[Boolean]
// }

// object Coordinator {
//   private def noopCoordinator[A](
//     f: Repository => Task[IndexedSeq[A]]
//   )(r: Repository
//   ): Coordinator[A] =
//     new Coordinator[A] {
//       // def listActive: Task[IndexedSeq[A]] = f(r)
//       // def isActive(a: A): Task[Boolean] = Task.now(true)
//     }

//   // val DefaultFlaskCoordinator: Coordinator[Flask] =
//   //   noopCoordinator[Flask](_.flasks)

//   // val DefaultChemistCoordinator: Coordinator[Location] =
//   //   noopCoordinator
// }
