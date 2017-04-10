import com.badlogic.gdx.graphics.{Color, GL20}
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{ApplicationAdapter, Gdx}
import com.badlogic.gdx.backends.lwjgl3.{Lwjgl3Application, Lwjgl3ApplicationConfiguration}
import com.badlogic.gdx.scenes.scene2d.ui.{Image, Skin, Table, TextButton}

/**
 * Simple libGDX application that uses packed resources.
 * @author Darkyen
 */
object UsingPackedResourcesTest extends App {
  val config = new Lwjgl3ApplicationConfiguration
  config.setTitle("UsingPackedResourcesTest")
  config.setWindowedMode(800, 600)
  config.disableAudio(true)
  new Lwjgl3Application(new ApplicationAdapter {

    lazy val skin = new Skin(Gdx.files.local("target/RPTestResult/UISkin.json"))

    lazy val stage = new Stage(new ScreenViewport())

    override def create(): Unit = {
      Gdx.gl.glClearColor(0.5f,0.3f,0.3f,1f)
      val table = new Table(skin)
      table.add("The COG:","font-default",Color.WHITE).padRight(10f)
      table.add(new Image(skin.getDrawable("cog"))).size(40f,40f).row()
      table.add(new TextButton("Sphinx of black quartz, judge my vow.", skin, "button")).colspan(2).row()
      table.setFillParent(true)
      stage.addActor(table)

      Gdx.input.setInputProcessor(stage)
    }

    override def render(): Unit = {
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
      stage.getViewport.update(Gdx.graphics.getWidth,Gdx.graphics.getHeight,true)
      stage.act()
      stage.draw()
    }

  },config)
}
