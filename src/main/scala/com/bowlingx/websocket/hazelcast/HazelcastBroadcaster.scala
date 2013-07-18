package com.bowlingx.websocket.hazelcast

import com.hazelcast.core._
import org.atmosphere.util.AbstractBroadcasterProxy

import org.atmosphere.cpr._
import java.net.URI
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.ITopic
import com.hazelcast.core.IMap
import com.hazelcast.core.MessageListener
import com.hazelcast.core.Message
import org.fusesource.scalate.util.Logging

object HazelcastInstance {
  lazy val hazelcast = Hazelcast.newHazelcastInstance(null)
}

/**
 * Serializable Hazelcast Message
 * Wraps a Message and Global Cluster ID
 * @param msg
 * @param clusterIdent
 */
class HazelcastMessage[T](var msg: T, var clusterIdent: Long) extends Serializable

object HazelcastBroadcaster extends Logging {

  lazy val broadcastTopicIdentifier = classOf[HazelcastBroadcaster].getName
}

/**
 * Hazelcast Broadcasting for Atmosphere
 */
class HazelcastBroadcaster(id: String, config: AtmosphereConfig)
  extends AbstractBroadcasterProxy(id, URI.create("http://localhost"), config) with Logging {

  import HazelcastInstance._
  import HazelcastBroadcaster._

  private var topic: ITopic[HazelcastMessage[AnyRef]] = _
  // A Map to keep track of Messages
  lazy val map: IMap[Long, String] = hazelcast.getMap(broadcastTopicIdentifier)
  // A Map to keep track of Topics
  private var uniqueMessageId: Long = _
  private var msgListener: MessageListener[HazelcastMessage[AnyRef]] = _

  def setup() {
    // Subscribe to Topic
    topic = getTopic
    // Generate Cluster wide unique id to track Message
    uniqueMessageId = hazelcast.getIdGenerator(broadcastTopicIdentifier).newId()
    msgListener = new MessageListener[HazelcastMessage[AnyRef]] {
      def onMessage(message: Message[HazelcastMessage[AnyRef]]) {
        import scala.collection.JavaConversions._
        val msg = message.getMessageObject
        // Get connected Atmosphere Resources for this Broadcaster and remove Delivered Messages from distributed Map
        log.info(getAtmosphereResources.map(_.uuid()).toList.toString)
        getAtmosphereResources foreach {
          r =>
            r.addEventListener(new AtmosphereResourceEventListener() {

              def onPreSuspend(event: AtmosphereResourceEvent) {}

              def onThrowable(event: AtmosphereResourceEvent) {}

              def onBroadcast(event: AtmosphereResourceEvent) {
                log.info("removing id (onBroadcast): %s" format msg.clusterIdent.toString)
                map.remove(msg.clusterIdent)
                r.removeEventListener(this)
              }

              def onDisconnect(event: AtmosphereResourceEvent) {}

              def onResume(event: AtmosphereResourceEvent) {
                log.info("removing id (onResume): %s" format msg.clusterIdent.toString)

                map.remove(msg.clusterIdent)
                r.removeEventListener(this)
              }

              def onSuspend(event: AtmosphereResourceEvent) {}
            })
        }
        broadcastReceivedMessage(msg)
      }
    }
    topic.addMessageListener(msgListener)
  }

  override def setID(id: String) {
    super.setID(id)
    setup()
  }

  override def destroy() {
    this.synchronized {
      topic.removeMessageListener(msgListener)
      super.destroy()
    }
  }

  def getTopic = hazelcast.getTopic[HazelcastMessage[AnyRef]](getID)

  /**
   * Important: Call this Method with a delay (to assure messages can del
   * Call to check if message was successfully delivered
   * If not delete topic, because there are not listeners
   * @return
   */
  def didReceivedMessage = !map.containsKey(uniqueMessageId)

  def incomingBroadcast() {}

  def outgoingBroadcast(message: AnyRef) {
    // Track IDs:
    log.info("putting msg id: key: %s, value: %s" format(uniqueMessageId.toString, getID))
    map.put(uniqueMessageId, getID)
    val hcMessage = new HazelcastMessage[AnyRef](message, uniqueMessageId)
    topic.publish(hcMessage)

  }

  /**
   * Broadcast Hazelcast Message (contains original Message and a global Cluster ID)
   * @param message
   */
  def broadcastReceivedMessage(message: HazelcastMessage[AnyRef]) {
    try {
      val newMsg = message
      newMsg.msg = filter(newMsg.msg)
      val future = new HazelcastBroadcastFuture(newMsg, this)
      push(new Entry(newMsg.msg, future, message.msg))

    } catch {
      case e: Exception => log.error("failed to push message: " + message, e)
    }
  }
}

/**
 * A Hazelcast Future
 *
 * @param hsl
 * @param b
 * @tparam T
 */
class HazelcastBroadcastFuture[T]
(hsl: HazelcastMessage[T], b: HazelcastBroadcaster)
  extends BroadcasterFuture[T](hsl.msg, b.asInstanceOf[Broadcaster]) {


}


