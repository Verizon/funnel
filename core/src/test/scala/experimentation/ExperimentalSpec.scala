//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
package funnel.experimentation

import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import org.scalacheck._


object ExperimentalSpec extends Properties("experimental") {
  property("always send base event even when experiment is defined") = secure {
    var sent = false
    def sendToken(in: Int) = {sent = true}

    val e = Experimental[Int,Int](_ => 4)
    e(Map("some" -> "foo"))(sendToken)
    sent
  }
  property("send base event even when experiment is not defined") = secure {
    var sent = false
    def sendToken(in: Int) = {sent = true}

    val e = Experimental[Int,Int](_ => 4)
    e(Map.empty[ExperimentID, GroupID])(sendToken)
    sent
  }
}
