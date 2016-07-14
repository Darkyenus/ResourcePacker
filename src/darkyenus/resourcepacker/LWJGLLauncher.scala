package darkyenus.resourcepacker

import java.awt.Desktop

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.backends.lwjgl3.{Lwjgl3Application, Lwjgl3ApplicationConfiguration}
import com.badlogic.gdx.graphics.Color

/**
 * Private property.
 * User: Darkyen
 * Date: 22/04/15
 * Time: 21:25
 */
object LWJGLLauncher {

  /**
   * PackingOperation extends this and it allows for adding custom functions.
   */
  type Operation = (() => Unit)

  /**
   * See LWJGLLauncher.launchSeq for Seq[PackingOperation]. This is just shortcut to that.
   */
  def launch(packingOperation: Operation, waitForCompletion: Boolean = true, forceExit: Boolean = true): Unit = {
    launchSeq(Seq(packingOperation), waitForCompletion, forceExit)
  }

  /**
   * Launches all PackingOperations in libGDX context, one after another.
   */
  def launchSeq(operations: Seq[Operation], @deprecated("irrelevant now", "1.8") waitForCompletion: Boolean = true, @deprecated("irrelevant now", "1.8") forceExit: Boolean = true): Unit = {
    val config = new Lwjgl3ApplicationConfiguration
    config.setTitle("ResourcePacker")
    config.disableAudio(true)
    config.setInitialBackgroundColor(new Color(0.5f, 0.5f, 0.5f, 1f))
    config.setBackBufferConfig(1, 1, 1, 0, 0, 0, 0)
    config.setResizable(false)
    config.setWindowedMode(200, 1)

    config.setInitialVisible(false)

    Desktop.getDesktop

    try {
      new Lwjgl3Application(new ApplicationAdapter {
        override def create() {
          for (operation <- operations) {
            operation()
          }
          throw new ExitLwjglBeforeInputPoll
        }
      }, config)
    } catch {
      case _:ExitLwjglBeforeInputPoll => //Good
    }
  }

  /** Thrown after all operations are done to exit libGDX's loop before first input poll, which causes crash, because
    * we are in (basically) headless mode. */
  private final class ExitLwjglBeforeInputPoll extends RuntimeException
}
