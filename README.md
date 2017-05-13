# Resource Packer #
[![](https://jitpack.io/v/Darkyenus/ResourcePacker.svg)](https://jitpack.io/#Darkyenus/ResourcePacker)

## What does it do ##

It takes things from input directory, does some tasks on it and writes the result to output directory.

Default set of tasks is centered around game development in libGDX. However, what runs when is fully customizable.
There are no configuration files (generally). What task should run on which file/directory is determined by its name.
Each file in input directory is named as follows:
```
name.flag1.flag2.<...>.flagN.extension
```
Flags surrounded with `"` are considered to be part of the name not as a flags.
This allows producing files (and directories) in format `name.something.extension`.

NOTE: Flags and extension are converted to lowercase prior to processing to simplify flag matching.

For example, for "iOS Settings bundle" (a directory named `Settings.bundle`),
have directory named `Settings."bundle"`.

Directories follow similar pattern, there is just no `.extension`.

See `testresources` directory for examples.

## Default tasks ##

There is a couple of tasks that run by default in following order (order is important).

### IgnoreTask
Removes files and folders with `ignore` flag. Useful when you want to keep some (for example .gimp)
files in your resources directory but you don't want to export them.

### CreateIOSIconTask
iOS requires quite a lot different icons of different sizes to be created (18 in total in some extreme cases!),
which is very tedious and boring.
This task automatically creates iOS icons from svg vector image.

**Flags**:
* `iOSIcon` - Marks the file for conversion, for universal apps
* `iPhoneIcon` - Marks the file for conversion, for iPhone apps only
* `iPadIcon` - Marks the file for conversion, for iPad apps only

Append `Small` to any flag to create small icons as well (used for spotlight & settings)
Append `Artwork` to any flag to create big artwork icon dimensions as well (for ad-hoc distribution only).
NOTE: Artwork does not currently work properly, because extension (png) is left there.
 
When files are already present (by name), they are not created, so specialized icons can be created.

Sizes are from Apple Developer Library:
https://developer.apple.com/library/ios/qa/qa1686/_index.html

### CreateFontsTask
Rasterizes .ttf fonts. Font must have .N. flag where N is font size in pixels.

**Flags**:
* `<N>` - size - mandatory
* `<S>-<E>` - adds all codepoints from S to E, inclusive. Missing glyphs are not added.
* `bg#RRGGBBAA` - background color, default is transparent
* `fg#RRGGBBAA` - foreground color, default is white
* `outline <W> RRGGBBAA [straight]` - add outline of width W and specified color, add "straight" for sharp/mitered edges

When no glyphs are specified, ASCII glyphs are added.

### ConvertModelsTask

Converts obj and fbx files to g3db or different format.

**Flags**:
* `options -option1 -option2 ... -optionN`  - These options go directly to fbx-conv tool
* `to <format>` - To which convert. Setting it again in options (flag above) leads to undefined behavior

**Supported formats**:
* fbx
* g3dj
* g3db <- default

### FlattenTask

Flattens directories marked with `flatten`. Flattening is not recursive.

### ResizeTask

Resizes image by given arguments

**Flags**:
* `w<W>h<H>` - Image will be W tile-sizes wide and H tile-sizes tall
* `<W>x<H>` - Image will be W pixels wide and H pixels tall

**Settings**:
* TileSize - Size of tile used by ResizeTask's w<W>h<H> flag pattern

### RasterizeTask

Rasterize .svg files with "rasterize" (or "r") flag using default size (in file) or one specified in flags.

**Flags**:
* `rasterize` or `r` - Mark this file for rasterisation by this task
* `[W]x[H]` - W is width in pixels and H is height in pixels
* `scaled` - Search parent directories for flags in form of @Nx, where N is positive integer.
Rasterize additional images with dimensions N times bigger and append @Nx to its name.

One of them (W or H) can be left blank to infer the size by maintaining ratio.

### PreBlendTask

Renders image onto background of given color.

**Flags**:
* `#RRGGBB` - colors are in hexadecimal

**Example**:

`.#FFFFFF.` puts a white background in the image

### PackTask

Packs all images in .pack. flagged directory using libGDX's texture packer and then flattens it.
Can also preblend all packed resources if supplied with #RRGGBB flag, see **PreBlendTask**.

If the directory contains pack.json file (name can contain flags),
it will be used in packing, as with default packing procedures.

### DensityPackTask

More advanced version of PackTask

Can generate multiple atlas image files with different, Apple-like densities, that is, with @2x scheme.
Files with @Nx (where N is scale level) are assigned to that level.
Missing density levels are created from existing ones, either by up/downscaling or by rasterization with different dimensions.
Automatically rasterizes .svg files, flags from RasterizeTask can be used for specifying scale.

Generates only one atlas file and (potentially) multiple image files.
Therefore, all images are packed to the same locations, only all coordinates are multiplied by N.
This allows for simple runtime density switching.
 
Scale level 1 is specified automatically, others (usually 2) can be added with @Nx flag (@2x in case of level 2).
Note that if the level is not a power of two (for example 3), `pot` setting must be set to false (default is true),
otherwise the level will be ignored and warning will be emitted.

### RemoveEmptyDirectoriesTask

Removes all directories which are empty from the output.


## How to use ##

Right now, it is best used through JVM based build system, like SBT or Gradle.
Here are instructions on how to use it in build.sbt based project:

1. In your project's `project/plugins.sbt` add lines:
```
resolvers += "jitpack" at "https://jitpack.io"

libraryDependencies += "com.github.Darkyenus" %% "ResourcePacker" % "1.7"
```
1. In your project's build.sbt add lines:
```
import darkyenus.resourcepacker.{PackingOperation, LWJGLLauncher}

TaskKey[Unit]("packResources") := {
  LWJGLLauncher.launch(new PackingOperation(baseDirectory.value / "resources",baseDirectory.value / "assets"))
}
```
1. Now you can run `sbt packResources`. That will pack contents of folder `resources` to `assets` in project's root directory.

_Note: UI icon may appear during packing, that is normal, packer needs it for GL context._

## Tips ##
- Use `PreferSymlinks` setting during development for faster packing or to eliminate the need to repack when editing
	files that don't need packing, such as shaders.