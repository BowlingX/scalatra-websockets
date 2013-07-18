package com.bowlingx.websocket

import org.atmosphere.cpr.{Broadcaster, BroadcasterFactory}
import org.fusesource.scalate.util.Logging
import org.scalatra.servlet.ServletBase
import org.scalatra.{ScalatraServlet, ApiFormats}
import org.scalatra.json.JacksonJsonSupport

case class ChatMessage(msg:String)

/**
 *
 */
class WebsocketServlet extends ScalatraServlet with ApiFormats with JacksonJsonSupport with AtmosphereSupport with Logging {
  protected implicit val jsonFormats = org.json4s.DefaultFormats


  atmosphere("/chat") {
    case ChatMessage(msg) => {
      log.info("got message %s" format msg)
     Some(msg)
    }
  }

  post("/chat") {
    // just read the message that was pushed und create a new chatMessage
    BroadcasterFactory.getDefault.lookup("/at/chat", true).asInstanceOf[Broadcaster]
      .broadcast(ChatMessage(request.body))
    ""
  }

}
