package samples.scaladsl

// #imports

import org.apache.mina.util.AvailablePortFinder
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.connectors.file.scaladsl.{ Directory, LogRotatorSink }
import org.apache.pekko.stream.connectors.ftp.{ FtpCredentials, SftpIdentity, SftpSettings }
import org.apache.pekko.stream.connectors.ftp.scaladsl.Sftp
import org.apache.pekko.stream.scaladsl.{ Compression, Flow, Keep, Source }
import org.apache.pekko.util.ByteString
import playground.SftpServerEmbedded
import playground.filesystem.FileSystemMock

import java.net.InetAddress
import java.nio.file.{ Files, Path }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
// #imports

object Main extends App {
  implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "RotateLogsToFtp")
  implicit val executionContext: ExecutionContext = actorSystem.executionContext

  def wait(duration: FiniteDuration): Unit = Thread.sleep(duration.toMillis)

  private val ftpFileSystem = new FileSystemMock().fileSystem
  private val privateKeyPassphrase = SftpServerEmbedded.clientPrivateKeyPassphrase
  private val pathToIdentityFile = SftpServerEmbedded.clientPrivateKeyFile
  private val username = "username"
  private val password = username
  private val hostname = "localhost"
  val port = AvailablePortFinder.getNextAvailable(21000)

  val home: Path = ftpFileSystem.getPath(SftpServerEmbedded.FtpRootDir).resolve("tmp")
  if (!Files.exists(home)) Files.createDirectories(home)

  SftpServerEmbedded.start(ftpFileSystem, port)

  // #sample
  val data = ('a' to 'd') // (1)
    .flatMap(letter => Seq.fill(10)(ByteString(letter.toString * 10000)))

  // (2)
  val rotator = () => {
    var last: Char = ' '
    (bs: ByteString) => {
      bs.head.toChar match {
        case char if char != last =>
          last = char
          Some(s"log-$char.z")
        case _ => None
      }
    }
  }

  // (3)
  val identity = SftpIdentity.createFileSftpIdentity(pathToIdentityFile, privateKeyPassphrase)
  val credentials = FtpCredentials.create(username, password)
  val settings = SftpSettings(InetAddress.getByName(hostname))
    .withPort(port)
    .withSftpIdentity(identity)
    .withStrictHostKeyChecking(false)
    .withCredentials(credentials)

  val sink = (path: String) =>
    Flow[ByteString]
      .via(Compression.gzip) // (4)
      .toMat(Sftp.toPath(s"tmp/$path", settings))(Keep.right)

  val completion = Source(data).runWith(LogRotatorSink.withSinkFactory(rotator, sink))
  // #sample

  completion
    .flatMap { _ =>
      Directory
        .ls(home)
        .runForeach(f => println(f))
    }
    .recover {
      case f =>
        f.printStackTrace()
    }
    .onComplete { _ =>
      actorSystem.terminate()
      actorSystem.getWhenTerminated.thenAccept(_ => SftpServerEmbedded.stopServer());
    }
}
