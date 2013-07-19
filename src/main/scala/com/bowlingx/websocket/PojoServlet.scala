package com.bowlingx.websocket

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import org.atmosphere.cpr.{Broadcaster, BroadcasterFactory, Meteor, AtmosphereResourceEventListenerAdapter}

import org.atmosphere.cpr.AtmosphereResource.TRANSPORT._
import org.scalatra._
import scala.collection.JavaConverters._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent.{Map => ConcurrentMap}
import org.scalatra.PathPattern
import org.eclipse.jetty.http.HttpStatus
import org.fusesource.scalate.util.Logging

/**
 * This is just a plain servlet
 */
trait SimpleAtmosphereServlet extends HttpServlet with Logging {


  type ActionBlock = (HttpServletRequest, HttpServletResponse) => Unit

  case class Action(pattern: PathPattern, action: ActionBlock)

  val broadcastId = "/at/chat"

  private[this] val _routes: ConcurrentMap[HttpMethod, Seq[Action]] =
    new ConcurrentHashMap[HttpMethod, Seq[Action]].asScala

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    log.info("doGet")
    handle(Get, req, resp)
  }

  override def doPost(req: HttpServletRequest, resp: HttpServletResponse) {
    log.info("doPost")

    handle(Post, req, resp)
  }

  private[this] def handle(m: HttpMethod, req: HttpServletRequest, resp: HttpServletResponse) = {
    _routes.foreach {
      case (t, seq) if t == m =>
        seq foreach {
          case (Action(pattern, action)) if pattern(req.getRequestURI).isDefined =>
            log.info("calling method: %s" format m.toString)
            action(req, resp)
          case _ => {
            log.info("always")
            resp.setStatus(HttpStatus.NOT_FOUND_404)
          }
        }
      case _ =>
    }
  }

  private[this] def addHandler(method: HttpMethod, pattern: String, action: ActionBlock) {
    val el = Action(SinatraPathPatternParser(pattern), action)
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
   * Creates a new Meteor with an ID
   * @param id  id
   * @param req request
   * @return
   */
  def createMeteor(id: String, req: HttpServletRequest): Meteor = {
    val m: Meteor = Meteor.build(req)
    val b = BroadcasterFactory.getDefault.lookup(id, true).asInstanceOf[Broadcaster]
    b.setScope(Broadcaster.SCOPE.APPLICATION)
    m.setBroadcaster(b)
    m resumeOnBroadcast (m.transport() == LONG_POLLING)
    m
  }
}

class PojoServlet extends SimpleAtmosphereServlet {

  get("/at/chat") {
    (req, resp) =>
      val m = createMeteor("/at/chat", req)
      m suspend -1
  }

  post("/at/chat") {
    (req, resp) =>
      val body = Option(req.getReader().readLine()).map(_.trim).getOrElse("nothing send...")
      BroadcasterFactory.getDefault().lookup(broadcastId, true).asInstanceOf[Broadcaster].broadcast(body)
  }

}
