package intelmedia.ws.commons.monitoring

import com.twitter.algebird.{Group, Moments, MomentsGroup}

/** Monoidally track count/mean/variance/skewness/kurtosis. */
class Stats(moments: Moments) {
  private[monitoring] def get = moments
  def count = moments.count
  def mean = moments.mean
  def variance = moments.variance
  def skewness = moments.skewness
  def kurtosis = moments.kurtosis

  def ++(d: Double): Stats =
    new Stats(Moments.group.plus(moments, Moments(d)))

  def ++(s: Stats): Stats =
    new Stats(Moments.group.plus(moments, s.get))

  def ++(ds: Seq[Double]): Stats =
    this ++ Stats.reduce(ds)
}

object Stats {
  def apply(d: Double): Stats =
    new Stats(Moments(d))

  def reduce(s: Seq[Double]): Stats =
    new Stats(s.view.map(d => Moments(d)).reduce(Moments.group.plus))

  implicit def statsGroup: Group[Stats] = new Group[Stats] {
    def zero = new Stats(MomentsGroup.zero)
    override def negate(s: Stats) = new Stats(MomentsGroup.negate(s.get))
    def plus(s1: Stats, s2: Stats): Stats = s1 ++ s2
  }
}
