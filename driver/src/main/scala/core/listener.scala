package reactivemongo.core

import scala.util.{ Failure, Success, Try }

import reactivemongo.api.MongoConnectionOptions
import reactivemongo.core.nodeset.NodeSetInfo

/** Listener definition for the connection events. */
trait ConnectionListener {
  /** Logger available for the listener implementation. */
  lazy val logger: org.slf4j.Logger = ConnectionListener.logger.underlying

  /**
   * The connection pool is initialized.
   *
   * @param options the connection options
   * @param supervisor the name of the pool supervisor (for logging)
   * @param connection the name of the connection pool
   */
  def poolCreated(options: MongoConnectionOptions, supervisor: String, connection: String): Unit

  /**
   * The node set of the connection pool has been updated.
   * This is fired asynchronously.
   *
   * @param previous the previous node set
   * @param updated the new/updated node set
   */
  def nodeSetUpdated(previous: NodeSetInfo, updated: NodeSetInfo): Unit

  /**
   * The connection is being shut down.
   *
   * @param supervisor the name of the pool supervisor (for logging)
   * @param connection the name of the connection pool
   */
  def poolShutdown(supervisor: String, connection: String): Unit
}

object ConnectionListener {
  import java.net.URL

  val staticListenerBinderPath =
    "reactivemongo/core/StaticListenerBinder.class";

  private[core] val logger =
    reactivemongo.util.LazyLogger("reactivemongo.core.ConnectionListener")

  /** Optionally creates a listener according the available binding. */
  def apply(): Option[ConnectionListener] = {
    val binderPathSet = scala.collection.mutable.LinkedHashSet[URL]()

    try {
      val reactiveMongoLoader = classOf[ConnectionListener].getClassLoader
      val paths: java.util.Enumeration[URL] =
        if (reactiveMongoLoader == null) {
          ClassLoader.getSystemResources(staticListenerBinderPath)
        } else {
          reactiveMongoLoader.getResources(staticListenerBinderPath)
        }

      while (paths.hasMoreElements()) binderPathSet.add(paths.nextElement())
    } catch {
      case ioe: java.io.IOException =>
        logger.warn("Error getting resources from path", ioe);
    }

    binderPathSet.headOption.flatMap { first =>
      if (!binderPathSet.tail.isEmpty) {
        logger.warn(s"Class path contains multiple StaticListenerBinder: $first, ${binderPathSet.tail mkString ", "}")
      }

      val `try` = Try(new StaticListenerBinder().connectionListener())

      `try`.failed.foreach { reason =>
        logger.warn("Fails to create connection listener; Fallbacks to the default one", reason)
      }

      `try`.toOption
    }
  }
}

final class StaticListenerBinder {
  /**
   * Returns a new listener instance;
   * At most one will be used per driver.
   */
  def connectionListener(): ConnectionListener = ???
}