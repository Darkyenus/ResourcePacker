import com.darkyen.resourcepacker.LocalWorkingRootProvider
import com.darkyen.resourcepacker.PackingOperation

import com.esotericsoftware.minlog.Log
/**
 *
 */
fun main(args: Array<String>) {
    Log.DEBUG()
    resourcePack(
            PackingOperation("src/test/resources", "target/RPTestResult",
                    workingRootProvider = LocalWorkingRootProvider("target/RPTestWorkingRoot")))
}
