package darkyenus.resourcepacker

/**
 * Tasks can create immutable instances of this.
 * User then can create setting tuple from it:
 * @example
 * {{{
 *  //In Task
 *  val PageSize = new SettingKey[Int]("PageSize",1024,"Page size is the size of the page")
 *
 *  //In creating packing operation
 *  ... settings = Seq(PageSize := 56, SomethingElse := true, ...) ...
 * }}}
 * @author Darkyen
 */
final class SettingKey[T](val name: String, defaultValue: T, val help: String = "") {
  def :=(content: T): Setting[T] = new Setting(this, content)

  var activeValue: T = defaultValue

  def get(): T = activeValue

  def reset(): Unit = {
    activeValue = defaultValue
  }
}

/**
 * Created by calling [[SettingKey.:=()]] and fed into the PackingOperation
 *
 * @author Darkyen
 */
final class Setting[T](val key: SettingKey[T], val value: T) {
  def activate(): Unit = {
    key.activeValue = value
  }

  def reset(): Unit = {
    key.reset()
  }
}