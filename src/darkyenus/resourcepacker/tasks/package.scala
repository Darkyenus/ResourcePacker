package darkyenus.resourcepacker

/**
 * @author Darkyen
 */
package object tasks {

  /**
   * Collection of all tasks from this package in logical order.
   */
  val DefaultTasks: Seq[Task] = Seq(IgnoreTask, CreateFontsTask, PyxelTilesTask, ConvertModelsTask, FlattenTask, ResizeTask, RasterizeTask, PreBlendTask, PackTask, RemoveEmptyDirectoriesTask)
}
