package com.bowlingx.websocket

import org.atmosphere.cpr.{Broadcaster, BroadcasterFactory}

case class ChatMessage(msg:String)

/**
 *
 */
class WebsocketServlet extends WebsocketscalatraStack with AtmosphereSupport {


  atmosphere("/chat") {
    case ChatMessage(msg) => {
     Some(msg)
    }
  }

  post("/chat") {
    //BroadcasterFactory.getDefault.lookup("/at/chat", true).asInstanceOf[Broadcaster].broadcast(ChatMessage("bla"))
    ""
  }

}
