package com.darkyen.resourcepacker.tasks

/**
 * Collection of all tasks from this package in logical order.
 */
val DefaultTasks = listOf(
        IgnoreTask,
        TransitiveFlagTask,
        CreateIOSIconTask,
        CreateAppleStringsTask,
        CreateFontsTask,
        PyxelTilesTask,
        ConvertModelsTask,
        FlattenTask,
        RasterizeTask,
        PackTask,
        RemoveEmptyDirectoriesTask)