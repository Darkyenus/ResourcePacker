import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.viewport.ScreenViewport


/**
 * Simple libGDX application that uses packed resources.
 * @author Darkyen
 */
fun main(args: Array<String>) {
    val config = Lwjgl3ApplicationConfiguration()
    config.setTitle("UsingPackedResourcesTest")
    config.setWindowedMode(800, 600)
    config.disableAudio(true)
    Lwjgl3Application(object : ApplicationAdapter() {

        lateinit var skin: Skin

        lateinit var stage: Stage

        override fun create() {
            Gdx.gl.glClearColor(0.5f, 0.3f, 0.3f, 1f)
            skin = Skin(Gdx.files.local("build/cache/RPTestResult/UISkin.json"))
            stage = Stage(ScreenViewport())

            val table = Table(skin)
            table.add("The COG:", "font-default", Color.WHITE).padRight(10f)
            table.add(Image(skin.getDrawable("cog"))).size(40f, 40f).row()
            table.add(TextButton("Sphinx of black quartz, judge my vow.", skin, "button")).colspan(2).row()
            table.setFillParent(true)
            stage.addActor(table)

            Gdx.input.inputProcessor = stage
        }

        override fun render() {
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            stage.viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
            stage.act()
            stage.draw()
        }

    }, config)
}