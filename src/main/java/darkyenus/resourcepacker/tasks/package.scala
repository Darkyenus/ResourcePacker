package darkyenus.resourcepacker

import darkyenus.resourcepacker.tasks.densitypack.DensityPackTask

/**
 * @author Darkyen
 */
package object tasks {

  /**
   * Collection of all tasks from this package in logical order.
   */
  val DefaultTasks: Seq[Task] = Seq(IgnoreTask, CreateIOSIconTask, CreateAppleStringsTask, CreateFontsTask, PyxelTilesTask, ConvertModelsTask, FlattenTask, ResizeTask, RasterizeTask, PreBlendTask, PackTask, DensityPackTask, RemoveEmptyDirectoriesTask)
}
