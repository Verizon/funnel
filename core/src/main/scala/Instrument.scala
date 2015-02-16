package funnel

trait Instrument[K] {
  def keys: K
  def key[K2](implicit d: DefaultKey[K,K2]): K2 = d(keys)
}
