package com.bowlingx.websocket.hazelcast

import org.apache.shiro.cache.CacheManager
import org.apache.shiro.cache.Cache
import com.hazelcast.core.{IMap, EntryEvent, EntryListener}
import org.fusesource.scalate.util.Logging

class HazelcastCacheManager extends CacheManager with Logging {

  import HazelcastInstance._

  protected def createMap[K, V](name: String): IMap[K, V] = hazelcast.getMap[K, V](name)

  def getCache[K, V](name: String) = {

    new Cache[K, V] {
      lazy val map = createMap[K, V](name)

      def clear() {
        map.clear()
      }

      def get(key: K) = map.get(key)

      def keys() = map.keySet()

      def put(key: K, value: V) = {
        map.put(key, value)
      }

      def remove(key: K) = map.remove(key)

      def size() = map.size()

      def values() = map.values()
    }
  }

}

class HazelcastSessionCacheManager extends HazelcastCacheManager {

  import HazelcastInstance._

  override def createMap[K, V](name: String) = {
    val localMap = hazelcast.getMap[K, V](name)
    log.info("Adding Local Listener on %s" format name)
    localMap.addLocalEntryListener(new EntryListener[K, V] {

      def entryAdded(event: EntryEvent[K, V]) {}

      def entryEvicted(event: EntryEvent[K, V]) {
        // TODO: Delete Session from backing store if set
        // Session.delete(("_id" -> event.getKey.toString))
      }

      def entryRemoved(event: EntryEvent[K, V]) {}

      def entryUpdated(event: EntryEvent[K, V]) {}
    })
    localMap
  }

}
