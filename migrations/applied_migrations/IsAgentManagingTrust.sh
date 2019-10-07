#!/bin/bash

echo ""
echo "Applying migration IsAgentManagingTrust"

echo "Adding routes to conf/app.routes"

echo "" >> ../conf/app.routes
echo "GET        /isAgentManagingTrust                        controllers.IsAgentManagingTrustController.onPageLoad(mode: Mode = NormalMode)" >> ../conf/app.routes
echo "POST       /isAgentManagingTrust                        controllers.IsAgentManagingTrustController.onSubmit(mode: Mode = NormalMode)" >> ../conf/app.routes

echo "GET        /changeIsAgentManagingTrust                  controllers.IsAgentManagingTrustController.onPageLoad(mode: Mode = CheckMode)" >> ../conf/app.routes
echo "POST       /changeIsAgentManagingTrust                  controllers.IsAgentManagingTrustController.onSubmit(mode: Mode = CheckMode)" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "isAgentManagingTrust.title = isAgentManagingTrust" >> ../conf/messages.en
echo "isAgentManagingTrust.heading = isAgentManagingTrust" >> ../conf/messages.en
echo "isAgentManagingTrust.checkYourAnswersLabel = isAgentManagingTrust" >> ../conf/messages.en
echo "isAgentManagingTrust.error.required = Select yes if isAgentManagingTrust" >> ../conf/messages.en

echo "Adding to UserAnswersEntryGenerators"
awk '/trait UserAnswersEntryGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryIsAgentManagingTrustUserAnswersEntry: Arbitrary[(IsAgentManagingTrustPage.type, JsValue)] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        page  <- arbitrary[IsAgentManagingTrustPage.type]";\
    print "        value <- arbitrary[Boolean].map(Json.toJson(_))";\
    print "      } yield (page, value)";\
    print "    }";\
    next }1' ../test/generators/UserAnswersEntryGenerators.scala > tmp && mv tmp ../test/generators/UserAnswersEntryGenerators.scala

echo "Adding to PageGenerators"
awk '/trait PageGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryIsAgentManagingTrustPage: Arbitrary[IsAgentManagingTrustPage.type] =";\
    print "    Arbitrary(IsAgentManagingTrustPage)";\
    next }1' ../test/generators/PageGenerators.scala > tmp && mv tmp ../test/generators/PageGenerators.scala

echo "Adding to UserAnswersGenerator"
awk '/val generators/ {\
    print;\
    print "    arbitrary[(IsAgentManagingTrustPage.type, JsValue)] ::";\
    next }1' ../test/generators/UserAnswersGenerator.scala > tmp && mv tmp ../test/generators/UserAnswersGenerator.scala

echo "Adding helper method to CheckYourAnswersHelper"
awk '/class/ {\
     print;\
     print "";\
     print "  def isAgentManagingTrust: Option[AnswerRow] = userAnswers.get(IsAgentManagingTrustPage) map {";\
     print "    x =>";\
     print "      AnswerRow(";\
     print "        HtmlFormat.escape(messages(\"isAgentManagingTrust.checkYourAnswersLabel\")),";\
     print "        yesOrNo(x),";\
     print "        routes.IsAgentManagingTrustController.onPageLoad(CheckMode).url";\
     print "      )"
     print "  }";\
     next }1' ../app/utils/CheckYourAnswersHelper.scala > tmp && mv tmp ../app/utils/CheckYourAnswersHelper.scala

echo "Migration IsAgentManagingTrust completed"
