import com.darkyen.resourcepacker.LocalWorkingRootProvider
import com.darkyen.resourcepacker.packResources

import com.esotericsoftware.minlog.Log
import java.io.File

/**
 *
 */
fun main(args: Array<String>) {
    Log.DEBUG()
    packResources(File("src/test/resources"), File("build/cache/RPTestResult"),
                    workingRootProvider = LocalWorkingRootProvider("build/cache/RPTestWorkingRoot"))
}
