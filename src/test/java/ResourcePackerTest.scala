import java.io.File

import com.esotericsoftware.minlog.Log
import darkyenus.resourcepacker.{PackingOperation, LocalWorkingRootProvider, LWJGLLauncher}

/**
 *
 * @author Darkyen
 */
object ResourcePackerTest extends App {
  Log.DEBUG()
  LWJGLLauncher.launch(new PackingOperation(new File("testresources"),new File("target/RPTestResult"),workingRootProvider = new LocalWorkingRootProvider(new File("target/RPTestWorkingRoot"))))
}
