package com.darkyen.resourcepacker.util

import com.badlogic.gdx.utils.SnapshotArray

/**
 *
 */
class SnapshotArrayList<T>(ordered:Boolean = true, initialCapacity:Int=16, type:Class<T>? = null) : SnapshotArray<T>(ordered, initialCapacity, type), List<T> {
    override var size: Int
        get() = super.size
        set(value) {
            super.size = value
        }

    override fun contains(element: T): Boolean {
        return super.contains(element, false)
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        for (element in elements) {
            if (!super.contains(element, false)) return false
        }
        return true
    }

    override fun indexOf(element: T): Int {
        return super.indexOf(element, true)
    }

    override fun isEmpty(): Boolean {
        return super.size == 0
    }

    override fun lastIndexOf(element: T): Int {
        return super.lastIndexOf(element, false)
    }

    override fun listIterator(): ListIterator<T> {
        return listIterator(0, 0, super.size)
    }

    override fun listIterator(index: Int): ListIterator<T> {
        return listIterator(0, index, super.size)
    }

    private fun listIterator(minIndex:Int, beginIndex:Int, maxIndexExclusive:Int):ListIterator<T> {
        return SnapshotArrayListIterator(minIndex, beginIndex, maxIndexExclusive)
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<T> {
        if (fromIndex < 0 || fromIndex > toIndex || toIndex > super.size) throw IllegalArgumentException("Invalid indices, from: $fromIndex to: $toIndex size: ${super.size}")
        return SnapshotArraySubList(fromIndex, toIndex + 1 - fromIndex)
    }

    internal inner class SnapshotArraySubList(val from:Int, override val size:Int) : List<T> {

        @Suppress("NOTHING_TO_INLINE")
        private inline fun assertInRange(index:Int) {
            if (index < 0 || index >= size) throw IndexOutOfBoundsException("Index: $index, size: $size")
        }

        override fun contains(element: T): Boolean {
            val items = this@SnapshotArrayList.items
            for (i in from..(from + size - 1)) {
                if (items[i] == element) return true
            }
            return false
        }

        override fun containsAll(elements: Collection<T>): Boolean {
            for (element in elements) {
                if (!contains(element)) return false
            }
            return true
        }

        override fun get(index: Int): T {
            assertInRange(index)
            return this@SnapshotArrayList[from + index]
        }

        override fun indexOf(element: T): Int {
            val items = this@SnapshotArrayList.items
            for (i in from..(from + size - 1)) {
                if (items[i] == element) return i - from
            }
            return -1
        }

        override fun isEmpty(): Boolean {
            return size == 0
        }

        override fun lastIndexOf(element: T): Int {
            val items = this@SnapshotArrayList.items
            for (i in IntProgression.fromClosedRange(from + size - 1, from, -1)) {
                if (items[i] == element) return i - from
            }
            return -1
        }

        override fun iterator(): Iterator<T> {
            return listIterator()
        }

        override fun listIterator(): ListIterator<T> {
            return this@SnapshotArrayList.listIterator(from, from, from + size)
        }

        override fun listIterator(index: Int): ListIterator<T> {
            assertInRange(index)
            return this@SnapshotArrayList.listIterator(from, from + index, from + size)
        }

        override fun subList(fromIndex: Int, toIndex: Int): List<T> {
            assertInRange(fromIndex)
            if (toIndex < 0 || toIndex > size) throw IndexOutOfBoundsException("toIndex: $toIndex, size: $size")
            return this@SnapshotArrayList.subList(from + fromIndex, from + toIndex)
        }

    }

    internal inner class SnapshotArrayListIterator(val minIndex: Int, val maxIndexExclusive: Int, var currentIndex:Int):ListIterator<T> {

        override fun hasNext(): Boolean = currentIndex < maxIndexExclusive

        override fun hasPrevious(): Boolean = currentIndex > minIndex

        override fun next(): T {
            if (!hasNext()) throw NoSuchElementException()
            return this@SnapshotArrayList[currentIndex++]
        }

        override fun nextIndex(): Int = currentIndex

        override fun previous(): T {
            if (!hasPrevious()) throw NoSuchElementException()
            return this@SnapshotArrayList[--currentIndex]
        }

        override fun previousIndex(): Int = currentIndex - 1
    }
}