package com.bowlingx.websocket

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import org.atmosphere.cpr._

import org.atmosphere.cpr.AtmosphereResource.TRANSPORT._
import org.scalatra._
import scala.collection.JavaConverters._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent.{Map => ConcurrentMap}
import org.eclipse.jetty.http.HttpStatus
import org.fusesource.scalate.util.Logging
import org.atmosphere.cpr.BroadcastFilter.BroadcastAction
import org.scalatra.PathPattern

/**
 * Action Parameters
 * @param req req
 * @param resp response
 * @param routeParams route parameters
 */
case class ActionParams(req: HttpServletRequest, resp: HttpServletResponse, routeParams: MultiParams)

case class RequestParams(req:HttpServletRequest, resp:HttpServletResponse)
/**
 * This is a simple Servlet that supports Pattern matching style REST urls
 *
 */
trait SimpleAtmosphereServlet extends HttpServlet with Logging {

  type ActionBlock = ActionParams => Any

  type AtmosphereMatch = PartialFunction[(RequestParams, Any), Option[Any]]

  case class Action(pattern: String, action: ActionBlock)

  private[this] val _routes: ConcurrentMap[HttpMethod, Seq[Action]] =
    new ConcurrentHashMap[HttpMethod, Seq[Action]].asScala

  /**
   * Handle all GET Requests
   */
  override def doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    handle(Get, req, resp)
  }

  /**
   * Handle all POST Requests
   */
  override def doPost(req: HttpServletRequest, resp: HttpServletResponse) {
    handle(Post, req, resp)
  }

  /**
   * Handles Route dispatching
   * @param m HTTP Method
   * @param req request
   * @param resp response
   */
  private[this] def handle(m: HttpMethod, req: HttpServletRequest, resp: HttpServletResponse) = {
    _routes.foreach {
      case (t, actionSeq) if t == m =>
        val matchingRoutes = actionSeq map {
          case (Action(pattern, action)) =>
            val calcPattern = "%s%s" format (getServletContext.getContextPath, pattern)
            val extractedMultiParams = SinatraPathPatternParser(calcPattern)(req.getRequestURI)
            extractedMultiParams map {
              params =>
                (params, action)
            }
        }
        // If Routes are found, select first matching route that was found and execute action block
        val handler = matchingRoutes.find(_.isDefined).flatMap {
          case Some((params, action)) =>
            Some(action(ActionParams(req, resp, params)))
          case _ => None
        }
        if (handler.isEmpty) {
          resp.setStatus(HttpStatus.NOT_FOUND_404)
        } else {
          handler.get match {
            case s:Int => {
              resp.setStatus(s)
            }
            case Unit =>
            case r => {
              resp.getWriter.print(r)
            }
          }
        }
      case _ =>
    }
  }

  private[this] def addHandler(method: HttpMethod, pattern: String, action: ActionBlock) {
    val el = Action(pattern, action)
    _routes.put(method, _routes.get(method).map(s => s :+ el).getOrElse(Seq(el)))
  }

  /**
   * Adds a new GET route
   * @param pattern pattern to match
   * @param action calling action
   */
  def get(pattern: String)(action: ActionBlock) {
    addHandler(Get, pattern, action)
  }

  /**
   * Adds a new POST route
   * @param pattern pattern to match
   * @param action calling action
   * @return
   */
  def post(pattern: String)(action: ActionBlock) {
    addHandler(Post, pattern, action)
  }


  /**
   * Creates a new Request filter for an atmosphere Result
   * @param block
   * @return
   */
  private def createFilterForBlock(block: AtmosphereMatch) = {
    new PerRequestBroadcastFilter() {
      def filter(originalMessage: Any, message: Any): BroadcastAction = {
        new org.atmosphere.cpr.BroadcastFilter.BroadcastAction(message)
      }

      def filter(r: AtmosphereResource, originalMessage: Any, message: Any): BroadcastAction = {
        // Bind request and response to scope
        block.lift.apply((RequestParams(r.getRequest, r.getResponse), originalMessage)).flatMap {
          result =>
            result.map(new org.atmosphere.cpr.BroadcastFilter.BroadcastAction(_))
        } getOrElse {
          new org.atmosphere.cpr.BroadcastFilter.BroadcastAction(BroadcastAction.ACTION.ABORT, message)
        }
      }
    }
  }


  /**
   * Registers an atmosphere Request based on request url
   * @param pattern pattern to match
   * @param block atmosphere partial matching
   * @return
   */
  def atmosphere(pattern: String)(block: AtmosphereMatch) = {
    get(pattern) {
      a =>
        val m = createMeteor(a.req.getRequestURI, a, block)
        m suspend -1
        Unit
    }

  }

  /**
   * Creates a new Meteor
   * @param id
   * @param action
   * @param atmosphereResult
   * @return
   */
  def createMeteor(id: String, action: ActionParams, atmosphereResult: AtmosphereMatch): Meteor = {

    import AtmosphereResource.TRANSPORT._

    val m: Meteor = Meteor.build(action.req)
    val b = BroadcasterFactory.getDefault.lookup(id, true).asInstanceOf[Broadcaster]
    b.setScope(Broadcaster.SCOPE.APPLICATION)
    m.setBroadcaster(b)

    if (!b.getBroadcasterConfig.hasPerRequestFilters) {
      b.getBroadcasterConfig.addFilter(createFilterForBlock(atmosphereResult))
    }

    m resumeOnBroadcast (m.transport() == LONG_POLLING)
    m
  }
}

class PojoServlet extends SimpleAtmosphereServlet {

  atmosphere("/at/chat") {
    case m => Some(m._2)
  }

  get("/at/test/:param") {
    action =>
      log.info(action.routeParams.get("param").map(_.head).getOrElse("test"))
      <html>
        <body>
          <p>Dies ist ein Test</p>
        </body>
      </html>
  }

  post("/at/chat") {
    action =>
      val body = Option(action.req.getReader().readLine()).map(_.trim).getOrElse("nothing send...")
      BroadcasterFactory.getDefault().lookup("/at/chat", true).asInstanceOf[Broadcaster].broadcast(body)
      Unit
  }


}
