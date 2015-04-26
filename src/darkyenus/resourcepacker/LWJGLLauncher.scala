package darkyenus.resourcepacker

import com.badlogic.gdx.{Gdx, ApplicationListener}
import com.badlogic.gdx.backends.lwjgl.{LwjglApplication, LwjglApplicationConfiguration}
import com.badlogic.gdx.graphics.{GL20, Color}

/**
 * Private property.
 * User: Darkyen
 * Date: 22/04/15
 * Time: 21:25
 */
object LWJGLLauncher {

  /**
   * See LWJGLLauncher.launchSeq for Seq[PackingOperation]. This is just shortcut to that.
   */
  def launch(packingOperation: PackingOperation,waitForCompletion:Boolean = true,forceExit:Boolean = true):Unit = {
    launchSeq(Seq(packingOperation), waitForCompletion, forceExit)
  }

  /**
   * Launches all PackingOperations in libGDX context, one after another.
   * @param forceExit see LwjglApplicationConfiguraton.forceExit
   */
  def launchSeq(packingOperations: Seq[PackingOperation], waitForCompletion:Boolean = true,forceExit:Boolean = true):Unit = {
    val config = new LwjglApplicationConfiguration
    config.backgroundFPS = 1
    config.foregroundFPS = 1
    config.title = "ResourcePacker2"
    config.allowSoftwareMode = true
    LwjglApplicationConfiguration.disableAudio = true
    config.forceExit = forceExit
    config.resizable = false
    config.width = 200
    config.height = 1
    config.r = 1
    config.g = 1
    config.b = 1
    config.a = 1
    config.initialBackgroundColor = new Color(0.5f, 0.5f, 0.5f, 1f)

    val app = new LwjglApplication(new ApplicationListener {
      override def resize(width: Int, height: Int) {}

      override def dispose() {}

      override def pause() {}

      override def render() {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
      }

      override def resume() {}

      override def create() {
        for(packingOperation <- packingOperations){
          ResourcePacker.apply(packingOperation)
        }
        Gdx.app.exit()
      }
    }, config)
    if(waitForCompletion){
      try{
        val loopThreadField = app.getClass.getDeclaredField("mainLoopThread")
        loopThreadField.setAccessible(true)
        val loopThread = loopThreadField.get(app).asInstanceOf[Thread]
        loopThread.join()
      }catch {
        case e:Exception =>
          println("Waiting for main loop to stop failed. Incoming stack trace.")
          e.printStackTrace(Console.out)
      }
    }
  }

}
