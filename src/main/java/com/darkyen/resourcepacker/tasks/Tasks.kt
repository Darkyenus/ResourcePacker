package com.darkyen.resourcepacker.tasks

import com.darkyen.resourcepacker.tasks.densitypack.DensityPackTask

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
        ResizeTask,
        RasterizeTask,
        PreBlendTask,
        PackTask,
        DensityPackTask,
        RemoveEmptyDirectoriesTask)