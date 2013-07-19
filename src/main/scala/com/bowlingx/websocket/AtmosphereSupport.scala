package com.bowlingx.websocket

import org.scalatra.servlet.ServletBase
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.atmosphere.cpr.AtmosphereResource
import org.apache.shiro.subject.Subject
import org.atmosphere.cpr.PerRequestBroadcastFilter
import org.atmosphere.cpr.BroadcastFilter.BroadcastAction
import org.atmosphere.cpr.Meteor
import org.atmosphere.cpr.BroadcasterFactory
import org.atmosphere.cpr.Broadcaster
import org.apache.shiro.SecurityUtils
import org.fusesource.scalate.util.Logging


object AtmosphereSupport {

  /**
   * Subject session Key
   */
  val SUBJECT = "com.hellofellow.servlet.AtmosphereSupport.SUBJECT".intern
}


/**
 * A Simple Atmosphere Support trait that supports catching messages that were send through application
 */
trait AtmosphereSupport extends Logging {
  self: ServletBase with ApiFormats with JacksonJsonSupport =>

  import AtmosphereSupport._
  import AtmosphereResource.TRANSPORT._

  /**
   * Partial Function that matches Messages
   * Return Some() to allow that message to pass
   */
  type AtmosphereMatch = PartialFunction[Any, Option[Any]]


  /**
   * Will extract current user subject from a request attribute
   * @return found subject (if any)
   */
  def subject: Option[Subject] = Option(request.getAttribute(SUBJECT).asInstanceOf[Subject])

  /**
   * Checks if a subject is logged in
   * @return
   */
  def subjectIsLoggedIn:Boolean = subject.filter(s => s.isAuthenticated || s.isRemembered).isDefined

  /**
   * Creates a new Request filter for an atmosphere Result
   * @param block
   * @return
   */
  private def createFilterForBlock(block:AtmosphereMatch) = {
    new PerRequestBroadcastFilter() {
      def filter(originalMessage: Any, message: Any): BroadcastAction = {
        log.info("Filter: " + message.toString)
        new org.atmosphere.cpr.BroadcastFilter.BroadcastAction(message)
      }

      def filter(r: AtmosphereResource, originalMessage: Any, message: Any): BroadcastAction = {
        // Bind request and response to scope
        log.info("RequestFilter: " + message.toString + " original: " + originalMessage.toString)

        withRequestResponse(r.getRequest, r.getResponse) {
          block.lift.apply(originalMessage).flatMap {
            result =>
              result.map(new org.atmosphere.cpr.BroadcastFilter.BroadcastAction(_))
          } getOrElse {
            new org.atmosphere.cpr.BroadcastFilter.BroadcastAction(BroadcastAction.ACTION.ABORT, message)
          }
        }
      }
    }
  }
  /**
   * Creates a new Atmosphere Response with an specific id
   * Only valid in get, post, put...
   * @param id
   * @param block
   * @return
   */
  protected def atmosphereWithId(id: String)(block: AtmosphereMatch) = {
    contentType = formats("json")

    val m: Meteor = Meteor.build(request)
    val b = BroadcasterFactory.getDefault.lookup(id, true).asInstanceOf[Broadcaster]
    b.setScope(Broadcaster.SCOPE.APPLICATION)
    m.setBroadcaster(b)

    // Save current subject to request because it will get lost in other threads otherwise
    request.setAttribute(SUBJECT, SecurityUtils.getSubject)

    if (!b.getBroadcasterConfig.hasPerRequestFilters) {
      b.getBroadcasterConfig.addFilter(createFilterForBlock(block))
    }

    m resumeOnBroadcast (m.transport() == LONG_POLLING)
    m
  }

  /**
   * Register a atmosphere route endpoint
   * @param transformers
   * @param block
   * @return
   */
  def atmosphere(transformers: RouteTransformer*)(block: AtmosphereMatch) = {
    get(transformers: _*) {
      val m = atmosphereWithId(request.getRequestURI())(block)
      m suspend -1
      Unit
    }

  }
}