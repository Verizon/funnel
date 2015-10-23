package funnel

case class Triple[+A](one: A, two: A, three: A) {
  def map[B](f: A => B): Triple[B] =
    Triple(f(one), f(two), f(three))
  def zip[B](bs: Triple[B]): Triple[(A,B)] =
    Triple((one, bs.one), (two, bs.two), (three, bs.three))
  def zipWith[B,C](bs: Triple[B])(f: (A, B) => C): Triple[C] =
    zip(bs).map(f.tupled)
  def toList: List[A] = List(one, two, three)
}
