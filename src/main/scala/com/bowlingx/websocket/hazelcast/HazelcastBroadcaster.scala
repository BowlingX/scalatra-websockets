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
import java.security.MessageDigest
import java.math.BigInteger

object HazelcastInstance {
  lazy val hazelcast = Hazelcast.newHazelcastInstance(null)
}

/**
 * Serializable Hazelcast Message
 * Wraps a Message and Global Cluster ID
 * @param msg
 * @param clusterIdent
 */
class HazelcastMessage[T](var msg: T, var clusterIdent: String) extends Serializable

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
  lazy val map: IMap[String, String] = hazelcast.getMap(broadcastTopicIdentifier)
  // A Map to keep track of Topics
  private var uniqueBroadcasterId: Long = _
  private var msgListener: MessageListener[HazelcastMessage[AnyRef]] = _
  private var broadcastListener:BroadcasterListener = _

  def setup() {
    // Subscribe to Topic
    topic = getTopic
    // Generate Cluster wide unique id to track Message
    uniqueBroadcasterId = hazelcast.getIdGenerator(broadcastTopicIdentifier).newId()
    msgListener = new MessageListener[HazelcastMessage[AnyRef]] {
      def onMessage(message: Message[HazelcastMessage[AnyRef]]) {
        // Broadcast message to all atmosphere resources
        broadcastReceivedMessage(message.getMessageObject.msg)
      }
    }
    topic.addMessageListener(msgListener)

    broadcastListener = new BroadcasterListener {
      lazy val resourceListener = new AtmosphereResourceEventListener() {

        def onPreSuspend(event:AtmosphereResourceEvent) {}

        def onThrowable(event: AtmosphereResourceEvent) {}

        def onBroadcast(event: AtmosphereResourceEvent) {
          map.remove(calcMessageHash(event.getMessage))
        }

        def onDisconnect(event: AtmosphereResourceEvent) {}

        def onResume(event: AtmosphereResourceEvent) {
          map.remove(calcMessageHash(event.getMessage))
        }

        def onSuspend(event: AtmosphereResourceEvent) {}
      }

      def onPreDestroy(p1: Broadcaster) {}

      def onAddAtmosphereResource(p1: Broadcaster, p2: AtmosphereResource) {
        p2.addEventListener(resourceListener)
      }

      def onPostCreate(p1: Broadcaster) {}

      def onComplete(p1: Broadcaster) {}

      def onRemoveAtmosphereResource(p1: Broadcaster, p2: AtmosphereResource) {
        p2.removeEventListener(resourceListener)
      }
    }

    addBroadcasterListener(broadcastListener)
  }

  override def setID(id: String) {
    super.setID(id)
    setup()
  }

  override def destroy() {
    this.synchronized {
      removeBroadcasterListener(broadcastListener)
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
  def didReceivedMessage(message:AnyRef) = !map.containsKey(calcMessageHash(message))

  def incomingBroadcast() {}

  private def calcMessageHash(message:AnyRef) : String = {
    val v = "%s@%s" format (uniqueBroadcasterId.toString, message.toString) getBytes "UTF-8"
    val dig = MessageDigest.getInstance("MD5")
    dig.update(v, 0, v.length)
    new BigInteger(1, dig.digest()).toString(16)
  }

  def outgoingBroadcast(message: AnyRef) {
    // Track IDs:
    map.put(calcMessageHash(message), getID)
    val hcMessage = new HazelcastMessage[AnyRef](message, calcMessageHash(message))
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
      push(new Entry(newMsg.msg,future, message.msg));

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
