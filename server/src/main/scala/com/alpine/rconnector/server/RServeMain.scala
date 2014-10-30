/*
 * This file is part of Alpine Data Labs' R Connector (henceforth " R Connector").
 * R Connector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * R Connector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with R Connector.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.alpine.rconnector.server

import akka.actor.{ Props, ActorSystem }
import akka.event.Logging
import com.typesafe.config.ConfigFactory
import java.net.{ BindException, ServerSocket }
import scala.util.{ Failure, Success, Try }

/**
 * This starts up the R server actor system.
 */
object RServeMain {

  val config = ConfigFactory.load().getConfig("rServeKernelApp")
  // this has to be lazy since we first need to check if the port is free
  private lazy val system: ActorSystem = ActorSystem.create("rServeActorSystem", config)

  def startup(): Unit = system.actorOf(Props[RServeMaster], "master")
  def shutdown(): Unit = system.shutdown()

  /**
   * Old Akka versions will hang and not exit the program if the socket is already bound.
   * Check if the socket is bound and if it is, do a System.exit(1).
   * If the socket is free, close it and let Akka do its thing.
   * @param port
   */
  private def isSocketFree(port: Int): Unit = {

    Try(new ServerSocket(port)) match {
      case Success(socket) => socket.close()
      case Failure(e: BindException) => {
        sys.error(s"Port $port is already bound")
        sys.exit(1)
      }
      case Failure(e) => {
        sys.error(e.getMessage)
        e.printStackTrace()
        sys.exit(1)
      }
    }
  }

  def main(args: Array[String]): Unit = {

    val port = config.getInt("akka.remote.netty.tcp.port")

    // Need to check if port is free due to pre-Akka 2.3 bug
    println(s"Checking if chosen port $port is free")
    isSocketFree(port)

    // If port is free, start actor system
    println("Port is free - starting actor system")
    startup()

    // If startup succeeds, add shutdown hook
    // need to exit cleanly (e.g. on Ctrl+C), so shut down actor system and free up ports
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run: Unit = {
        println("Shutting down actor system")
        shutdown()
        println("Shutdown complete")
      }
    })
  }
}