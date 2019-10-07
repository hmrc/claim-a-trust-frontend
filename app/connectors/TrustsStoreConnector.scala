/*
 * Copyright 2019 HM Revenue & Customs
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

package connectors

import config.FrontendAppConfig
import javax.inject.Inject
import models.TrustStoreRequest
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class TrustsStoreConnector @Inject()(http: HttpClient, config : FrontendAppConfig) {

  val url: String = config.trustsStoreUrl + "/claim"

  def claim(request: TrustStoreRequest)(implicit hc : HeaderCarrier, ec : ExecutionContext, writes: Writes[TrustStoreRequest]): Future[HttpResponse] = {

//    val requestBody: JsValue = for {
//      utr <- request.userAnswers.get(UtrPage)
//      managedByAgent <- request.userAnswers.get(IsAgentManagingTrustPage)
//    } yield {
//      Json.obj(
//        "internalId" -> request.internalId,
//        "managedByAgent" -> managedByAgent,
//        "utr" -> utr
//      )
//    }

    val response = http.POST[JsValue, HttpResponse](url, Json.toJson(request))

    response
  }


}
