package com.darkyen.resourcepacker

import java.io.File

/**
 * Base class for all tasks.
 * `operate` and `prepare()` methods of `Task`s will be called sequentially on each run (launch of PackingOperation).
 *
 * <br><br>
 * <b>Implementation guide:</b><br>
 * - If you don't need multiple instances, implement as scala `object`.<br>
 * - Override one of the operate() methods, based on what you want to do.<br>
 * - If you keep any state between any operate() invocations, override prepare() method and reset it there. <br>
 *
 * @author Darkyen
 */
abstract class Task {

    private lateinit var janitor: OperationJanitor

    fun initializeForOperation(janitor: OperationJanitor) {
        this.janitor = janitor
        prepare()
    }

    val Name:String = javaClass.simpleName

    /**
     * Creates a new file based on given existing file.
     * It will have the same characteristics, extension (unless specified otherwise)
     * and will not exist. It can be used as a drop-in replacement for active ResourceFile.file.
     *
     * @return non existent file, ready to be created
     */
    fun newFile(basedOn: ResourceFile, extension: String? = null): File {
        return janitor.createTempFile(Name, basedOn.name, basedOn, extension)
    }

    /**
     * Creates a new file based on given existing file.
     * It will have the same characteristics, extension (unless specified otherwise), specified name
     * and will not exist. It can be used as a drop-in replacement for active ResourceFile.file.
     *
     * @return non existent file, ready to be created
     */
    fun newFileNamed(basedOn: ResourceFile, fileName:String, extension: String? = null): File {
        return janitor.createTempFile(Name, fileName, basedOn, extension)
    }

    /**
     * Creates a new file for writing arbitrary data to.
     * Its name will start with given file name and its extension (if specified) will be `extension`.
     * Returned file will not exist.
     *
     * @param extension to give the file or null if irrelevant
     * @return non existent file, ready to be created
     */
    fun newBlankFile(fileName:String, extension:String? = null):File {
        return janitor.createTempFile(Name, fileName, extension)
    }

    /**
     * Creates a new unique (temporary) directory in which Task results can be stored
     */
    fun newFolder(): File {
        return janitor.createTempDirectory(Name)
    }


    /**
     * Called before each run. Reset your internal state here (if you keep any).
     */
    fun prepare() {}

    /** Do your work here.
     * Called once for each file remaining in virtual working filesystem, per run.
     * @return whether the operation did something or not */
    fun operate(file: ResourceFile): Boolean = false

    /** Do your work here.
     * Called once for each directory remaining in virtual working filesystem, per run.
     * @return whether the operation did something or not */
    fun operate(directory: ResourceDirectory): Boolean = false

    /** Do your work here.
     * Called once for each run.
     * @return whether the operation did something or not
     */
    fun operate(): Boolean = false

    /** Repeating tasks will run over and over until they don't success anymore on anything. */
    val repeating = false

}
