package com.bowlingx.websocket

import org.scalatra._
import scalate.ScalateSupport

class MainServlet extends WebsocketscalatraStack {

  get("/") {
    redirect("/hello")
  }
  
}
