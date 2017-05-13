package com.darkyen.resourcepacker

/**
 * Tasks can create immutable instances of this.
 * User then can create setting tuple from it:
 * @example
 * {{{
 *  //In Task
 *  val PageSize = SettingKey<Int>("PageSize",1024,"Page size is the size of the page")
 *
 *  //In creating packing operation
 *  ... settings = Seq(PageSize := 56, SomethingElse := true, ...) ...
 * }}}
 * @author Darkyen
 */
class SettingKey<T>(val name: String, private val defaultValue: T, val help: String = "") {

    infix fun to(value: T): Setting<T> {
        return Setting(this, value)
    }

    internal var activeValue: T = defaultValue

    fun get(): T = activeValue

    fun reset() {
        activeValue = defaultValue
    }
}

/**
 * Created by calling [[SettingKey.:=()]] and fed into the PackingOperation
 *
 * @author Darkyen
 */
class Setting<T>(val key: SettingKey<T>, val value: T) {

    fun activate() {
        key.activeValue = value
    }

    fun reset() {
        key.reset()
    }
}