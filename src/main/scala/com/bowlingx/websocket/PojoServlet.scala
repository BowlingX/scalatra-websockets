package com.bowlingx.websocket

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import org.atmosphere.cpr.{Broadcaster, BroadcasterFactory, Meteor, AtmosphereResourceEventListenerAdapter}

import org.atmosphere.cpr.AtmosphereResource.TRANSPORT._

/**
 * This is just a plain servlet
 */
class PojoServlet extends HttpServlet {

  val broadcastId = "/at/chat"

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    val m = Meteor.build(req).addListener(new AtmosphereResourceEventListenerAdapter())
    val broadcaster = BroadcasterFactory.getDefault().lookup(broadcastId, true).asInstanceOf[Broadcaster]
    m.setBroadcaster(broadcaster)
    m.resumeOnBroadcast(if (m.transport() == LONG_POLLING) true else false).suspend(-1)
  }

  override def doPost(req: HttpServletRequest, resp: HttpServletResponse) {
    val body = Option(req.getReader().readLine()).map(_.trim).getOrElse("nothing send...")
    BroadcasterFactory.getDefault().lookup(broadcastId, true).asInstanceOf[Broadcaster].broadcast(body)
  }
}
