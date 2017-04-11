import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration

import java.awt.Desktop

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.graphics.Color
import com.esotericsoftware.minlog.Log

/**
 * Entry-point for the Resource Packer
 */


/**
 * PackingOperation extends this and it allows for adding custom functions.
 */
typealias ResourcePackerOperation = () -> Unit

/**
 * Launches all PackingOperations in libGDX context, one after another.
 */
fun resourcePack(vararg operations: ResourcePackerOperation) {
    val config = Lwjgl3ApplicationConfiguration()
    config.setTitle("ResourcePacker")
    config.disableAudio(true)
    config.setInitialBackgroundColor(Color(0.5f, 0.5f, 0.5f, 1f))
    config.setBackBufferConfig(1, 1, 1, 0, 0, 0, 0)
    config.setResizable(false)
    config.setWindowedMode(200, 1)

    config.setInitialVisible(false)

    Desktop.getDesktop()

    try {
        Lwjgl3Application(object: ApplicationAdapter() {
            override fun create() {
                for (operation in operations) {
                    operation()
                }
                throw ExitLwjglBeforeInputPoll()
            }
        }, config)
    } catch (normalExit:ExitLwjglBeforeInputPoll) {
        Log.info("Resource packing completed successfully")
    }
}

/** Thrown after all operations are done to exit libGDX's loop before first input poll, which causes crash, because
 * we are in (basically) headless mode. */
private class ExitLwjglBeforeInputPoll : RuntimeException()