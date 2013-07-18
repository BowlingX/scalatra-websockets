import com.bowlingx.websocket._
import com.bowlingx.websocket.hazelcast.HazelcastInstance
import org.scalatra._
import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {

    context.mount(new MainServlet, "/*")
  }

  override def destroy(context:ServletContext) {
    HazelcastInstance.hazelcast.getLifecycleService().shutdown()
  }

}
