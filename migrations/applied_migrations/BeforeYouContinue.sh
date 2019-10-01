#!/bin/bash

echo ""
echo "Applying migration BeforeYouContinue"

echo "Adding routes to conf/app.routes"
echo "" >> ../conf/app.routes
echo "GET        /beforeYouContinue                       controllers.BeforeYouContinueController.onPageLoad()" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "beforeYouContinue.title = beforeYouContinue" >> ../conf/messages.en
echo "beforeYouContinue.heading = beforeYouContinue" >> ../conf/messages.en

echo "Migration BeforeYouContinue completed"
