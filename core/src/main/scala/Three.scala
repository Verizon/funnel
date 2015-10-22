package funnel

case class Three[+A](one: A, two: A, three: A) {
  def map[B](f: A => B): Three[B] =
    Three(f(one), f(two), f(three))
  def zip[B](bs: Three[B]): Three[(A,B)] =
    Three((one, bs.one), (two, bs.two), (three, bs.three))
  def zipWith[B,C](bs: Three[B])(f: (A, B) => C): Three[C] =
    zip(bs).map(f.tupled)
  def toList: List[A] = List(one, two, three)
}
