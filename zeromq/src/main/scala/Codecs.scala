// package oncue.svc.funnel
// package zeromq

// object Codecs {
//   import scodec.msgpack._
//   import scodec.Codec
//   import java.util.concurrent.TimeUnit

//   // implicit val stringS: Serialize[String] = Serialize.string
//   // implicit val unitsS: Codec[Units[Any]] = gen(unitSerializer)

//   // implicit def foo[A](in: Serialize[A]): Codec[A] = gen(in)


//   val key: Codec[Key[Any]] = (str ~ reportable ~ units).pxmap(
//     { case ((n,r), u) => Key(n,u)(r) },
//     k => {
//       println(">>>>>>>> " + k)
//       Option( (("test", Reportable.Stats), Units.Hours) )
//       // Option.empty[((String, Reportable[Any]), Units[Any])]
//     }
//   )

//   val reportable: Codec[Reportable[Any]] =
//     gen(reportableSerializer)

//   val units: Codec[Units[Any]] =
//     gen(unitsSerializer)

//   def reportableSerializer: Serialize[Reportable[Any]] =
//     new Serialize[Reportable[Any]]{
//       import Reportable._

//       def pack(v: Reportable[Any]): MessagePack =
//         v.description match {
//           case "Boolean" => Serialize.int.pack(1)
//           case "Double"  => Serialize.int.pack(2)
//           case "String"  => Serialize.int.pack(3)
//           case "Stats"   => Serialize.int.pack(4)
//           case e => sys.error(s"unknown reportable type: $e")
//         }

//       def unpack(v: MessagePack): Option[Reportable[Any]] =
//         Serialize.int.unpack(v).map {
//           case 1 => B
//           case 2 => D
//           case 3 => S
//           case 4 => Stats
//           case _ => sys.error("unknown int->reportable mapping")
//         }
//     }

//   def unitsSerializer: Serialize[Units[Any]] =
//     new Serialize[Units[Any]]{
//       import Units._; import Units.Base._

//       def pack(v: Units[Any]): MessagePack = v match {
//         case Bytes(Zero)                     => Serialize.int.pack(1)
//         case Bytes(Kilo)                     => Serialize.int.pack(2)
//         case Bytes(Mega)                     => Serialize.int.pack(3)
//         case Bytes(Giga)                     => Serialize.int.pack(4)
//         case Count                           => Serialize.int.pack(5)
//         case Ratio                           => Serialize.int.pack(6)
//         case TrafficLight                    => Serialize.int.pack(7)
//         case Healthy                         => Serialize.int.pack(8)
//         case Load                            => Serialize.int.pack(9)
//         case Duration(TimeUnit.MILLISECONDS) => Serialize.int.pack(10)
//         case Duration(TimeUnit.SECONDS)      => Serialize.int.pack(11)
//         case Duration(TimeUnit.MINUTES)      => Serialize.int.pack(12)
//         case Duration(TimeUnit.HOURS)        => Serialize.int.pack(13)
//         case None                            => Serialize.int.pack(14)
//         case _ => sys.error("unknown unit->int mapping")
//       }

//       def unpack(v: MessagePack): Option[Units[Any]] =
//         Serialize.int.unpack(v).map {
//           case 1  => Bytes(Zero)
//           case 2  => Bytes(Kilo)
//           case 3  => Bytes(Mega)
//           case 4  => Bytes(Giga)
//           case 5  => Count
//           case 6  => Ratio
//           case 7  => TrafficLight
//           case 8  => Healthy
//           case 9  => Load
//           case 10 => Duration(TimeUnit.MILLISECONDS)
//           case 11 => Duration(TimeUnit.SECONDS)
//           case 12 => Duration(TimeUnit.MINUTES)
//           case 13 => Duration(TimeUnit.HOURS)
//           case 14 => Units.None
//           case _ => sys.error("unknown int->unit mapping")
//         }
//     }


// }