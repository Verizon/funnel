package funnel
package messages

trait Codecs {
  import scodec._

  implicit val utf8: Codec[String] = codecs.variableSizeBytes(codecs.int32, codecs.utf8)

  implicit val uint8 = codecs.uint8

  implicit def tuple2[A:Codec,B: Codec]: Codec[(A,B)] =
    Codec[A] ~ Codec[B]

  implicit def indexedSeq[A:Codec]: Codec[IndexedSeq[A]] =
    codecs.variableSizeBytes(codecs.int32, codecs.vector(Codec[A]).xmap(a => a, _.toVector))

  implicit def map[K:Codec,V:Codec]: Codec[Map[K,V]] =
    indexedSeq[(K,V)].xmap[Map[K,V]](
      _.toMap,
      _.toIndexedSeq
    )
}

