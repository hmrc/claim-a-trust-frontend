/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models

import uk.gov.hmrc.http.{HttpReads, HttpResponse}

sealed trait EnrolmentResponse

case object EnrolmentCreated extends EnrolmentResponse

final case class UpstreamTaxEnrolmentsError(message : String) extends Exception(message) with EnrolmentResponse

object EnrolmentResponse {

  import play.api.http.Status._

  implicit lazy val httpReads : HttpReads[EnrolmentResponse] = new HttpReads[EnrolmentResponse] {
    override def read(method: String, url: String, response: HttpResponse): EnrolmentResponse = {
      response.status match {
        case NO_CONTENT => EnrolmentCreated
        case _ => throw UpstreamTaxEnrolmentsError(s"HTTP response ${response.status} ${response.body}")
      }
    }
  }

}