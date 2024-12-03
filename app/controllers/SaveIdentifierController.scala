/*
 * Copyright 2023 HM Revenue & Customs
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

package controllers

import cats.data.EitherT
import controllers.actions.Actions
import errors.{InvalidIdentifier, TrustErrors}
import handlers.ErrorHandler
import models.requests.OptionalDataRequest
import models.{NormalMode, UserAnswers}
import pages.IdentifierPage
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.SessionRepository
import services.{RelationshipEstablishment, RelationshipFound, RelationshipNotFound}
import uk.gov.hmrc.http.SessionKeys.sessionId
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.TrustEnvelope.TrustEnvelope
import utils.{IdentifierRegex, Session, TrustEnvelope}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SaveIdentifierController @Inject()(
                                          val controllerComponents: MessagesControllerComponents,
                                          sessionRepository: SessionRepository,
                                          errorHandler: ErrorHandler,
                                          relationship: RelationshipEstablishment,
                                          actions: Actions
                                        )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with Logging {

  private val className = getClass.getSimpleName

  def save(identifier: String): Action[AnyContent] = actions.authWithOptionalData.async {
    implicit request =>
      val result = for {
        _ <- TrustEnvelope(getIdentifier(identifier))
        result <- checkIfAlreadyHaveIvRelationship(identifier)
      } yield result
      result.value.flatMap {
        case Right(call) => Future.successful(call)
        case Left(InvalidIdentifier) => Future.successful(Redirect(routes.FallbackFailureController.onPageLoad))
        case Left(_) => logger.warn(s"[$className][save][Session ID: ${Session.id(hc)}] " +
          s"Could not save identifier")
          errorHandler.internalServerErrorTemplate.map(res => InternalServerError(res))
      }
  }

  def getIdentifier(identifier: String)(implicit request: OptionalDataRequest[AnyContent]): Either[TrustErrors, String] = identifier match {
    case IdentifierRegex.UtrRegex(utr) => Right(utr)
    case IdentifierRegex.UrnRegex(urn) => Right(urn)
    case _ =>
      logger.error(s"[$className][getIdentifier][Session ID: ${Session.id(hc)}] " +
        s"Identifier provided is not a valid URN or UTR")
      Left(InvalidIdentifier)
  }

  private def checkIfAlreadyHaveIvRelationship(identifier: String)(implicit request: OptionalDataRequest[AnyContent]): TrustEnvelope[Result] = EitherT {
    relationship.check(request.internalId, identifier).value flatMap {
      case Right(RelationshipFound) =>
        logger.info(s"[$className][checkIfAlreadyHaveIvRelationship][Session ID: ${Session.id(hc)}] " +
          s"relationship is already established in IV for $identifier sending user to successfully claimed")

        Future.successful(Right(Redirect(routes.IvSuccessController.onPageLoad)))
      case Right(RelationshipNotFound) =>
        saveAndContinue(identifier).value
      case Left(error) => Future.successful(Left(error))
    }
  }

  private def saveAndContinue(identifier: String)(implicit request: OptionalDataRequest[AnyContent]): TrustEnvelope[Result] = EitherT {
    val result = for {
      updatedAnswers <- TrustEnvelope(extractUserAnswers(request.userAnswers, identifier, request.internalId))
      _ <- sessionRepository.set(updatedAnswers)
    } yield {
      logger.info(s"[$className][saveAndContinue][Session ID: ${Session.id(hc(request))}]" +
        s" user has started the claim a trust journey for $identifier")
      Redirect(routes.IsAgentManagingTrustController.onPageLoad(NormalMode))
    }
    result.value.map {
      case Right(call) => Right(call)
      case Left(error) => logger.warn(s"[$className][saveAndContinue][Session ID: $sessionId] Error while storing user answers")
        Left(error)
    }
  }

  private def extractUserAnswers(optUserAnswers: Option[UserAnswers], identifier: String, internalId: String): Either[TrustErrors, UserAnswers] = {
    optUserAnswers match {
      case Some(userAnswers) =>
        userAnswers.set(IdentifierPage, identifier)
      case _ =>
        UserAnswers(internalId).set(IdentifierPage, identifier)
    }
  }
}
