/**
  * Created by a on 5/25/17.
  */

package controllers

import play.api.test._
import play.api.libs.json._

class ApplicationSpec extends PlaySpecification {
  "the application controller" should {
      "return 200 for OPTIONS: /graphql" in new WithApplication() {
        val Some(result) = route(app, FakeRequest(OPTIONS, "/graphql"))

        status(result) must equalTo(OK)
        header(ACCESS_CONTROL_ALLOW_CREDENTIALS, result) must beSome("true")
        header(ACCESS_CONTROL_ALLOW_METHODS, result) must beSome("GET, POST")
        header(ACCESS_CONTROL_MAX_AGE, result) must beSome((60 * 60 * 24).toString)
        header(ACCESS_CONTROL_ALLOW_HEADERS, result) must beSome("Accept, Origin, Content-type, X-Requested-With, f_base, s_exch")
        header(ACCESS_CONTROL_ALLOW_ORIGIN, result) must beSome("*")
      }

      "return 200 for GET: /graphql" in new WithApplication() {

      }

      "return 200 and render the graphiql page for GET: /graphiql" in new WithApplication() {
        "run in a browser" in new WithBrowser(webDriver = WebDriverFactory(HTMLUNIT), app = app) {
          browser.goTo("/graphiql")

          // Check the page
          browser.$("#title").getTexts.get(0) must equalTo("GraphiQL")
        }
      }

      "return 200 for POST: /graphql with authorized ID" in new WithApplication() {
        //// id should be changed when doing test
        val query = "query User {" +
                            "viewer {" +
                                "_user3JZMRV: user(id:\"0MOeQQWRZBZdo4A5r0XGEntUaUI3\") {id, userName}, id}" +
                    "}"
        val Some(result) = route(app, FakeRequest(POST, "/graphql").withJsonBody(Json.obj(
            "query" -> query,
            "variable" -> ""
        )))

        status(result) must equalTo(OK)
      }

      "return 400 for POST: /graphql with unauthorized ID" in new WithApplication() {
        val query = "query User {" +
                              "viewer {" +
                                  "_user3JZMRV: user(id:\"unAuthorizedID\") {id, userName}, id}" +
                    "}"
        val Some(result) = route(app, FakeRequest(POST, "/graphql").withJsonBody(Json.obj(
          "query" -> query,
          "variable" -> ""
        )))

        status(result) must equalTo(BAD_REQUEST)
      }
  }
}
