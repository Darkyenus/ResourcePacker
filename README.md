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
Flags surrounded with `"` are considered to be part of the name, not flags.
This allows producing files (and directories) with dots in the name, e.g. `name.something.extension`.

NOTE: Flags and extension are converted to lowercase prior to processing to simplify flag matching.

For example, for "iOS Settings bundle" (a directory named `Settings.bundle`),
have directory named `Settings."bundle"`.

Directories follow similar pattern, there is just no `.extension`.

See `testresources` directory for examples.

## Default tasks ##

There is a couple of tasks that run by default in following order (order is important).
Images can have a couple of special flags, see below.

### IgnoreTask
Removes files and folders with `ignore` flag. Useful when you want to keep some (for example `.gimp`)
files in your resources directory but you don't want to export them.

**Flags (all trigger)**
* `ignore` - remove this file or directory from the virtual file tree

**Example**
```
/texture.png
/texture.ignore.gimp
```

### TransitiveFlagTask
Directories with these flags will transition that flag to children files and directories.
This can be useful if for example, you want to rasterize all vector *images* in a folder, but don't want to manually
place the `rasterize` flag or make the directory into a `pack` directory.

**Flags (all trigger)**
* `* <flag>` - Copy the flag to direct children
* `** <flag>` - Copy the flag to **all** children
* `*N <flag>` - Copy the flag to `N` levels of children (`* <flag>` == `*1 <flag>`)

**Example**
```
/Images.* rasterize/
	image1.svg
	image2.svg
	image3.svg
```

### CreateIOSIconTask
iOS requires quite a lot different icons of different sizes to be created (18 in total in some extreme cases!),
which is very tedious and boring.
This task automatically creates iOS icons from an *image*.

**Flags (all trigger)**
* `iOSIcon` - Marks the file for conversion, for universal apps
* `iPhoneIcon` - Marks the file for conversion, for iPhone apps only
* `iPadIcon` - Marks the file for conversion, for iPad apps only

Append `Small` to any flag to create small icons as well (used for spotlight & settings)
Append `Artwork` to any flag to create big artwork icon dimensions as well (for ad-hoc distribution only).
 
When files are already present (by name), they are not created, so specialized icons can be created.

For more info about iOS icons, see [relevant article in the Apple Developer Library](https://developer.apple.com/library/ios/qa/qa1686/_index.html)

### CreateFontsTask
Rasterizes true type fonts. Font must have .N. flag where N is font size in pixels.

**Flags**
* `<N>` - **trigger** - size
* `<S>-<E>` - adds all codepoints from S to E, inclusive. Missing glyphs are not added.
* `bg#RRGGBBAA` - background color, default is transparent
* `fg#RRGGBBAA` - foreground color, default is white
* `outline W RRGGBBAA [straight]` - add outline of width W and specified color, add "straight" for sharp/mitered edges

When no glyphs are specified, ASCII glyphs are added.

**Example**
```
/Arial.23.outline 2 000000.otf
```

### ConvertModelsTask
Converts obj and fbx files to g3db or different format.

**Flags**
* `to <format>` - **trigger** - To which convert. Setting it again in options (flag above) leads to undefined behavior
* `options -option1 -option2 ... -optionN`  - These options go directly to fbx-conv tool

**Supported formats**
* fbx
* g3dj
* g3db <- default

### FlattenTask
Flattens directories marked with `flatten`. Flattening is not recursive, flatten flag must be present on each
flattened folder.

### RasterizeTask
Rasterize *image* files with "rasterize" (or "r") flag. This applies the *image* flags explicitly, see *Image flags* below.

**Flags**
* `rasterize` or `r` - **trigger** - Mark this file for rasterization by this task
* `scaled` - Search parent directories for flags in form of @Nx, where N is positive integer.
Rasterize additional images with dimensions N times bigger and append @Nx to its name.

**Example**
```
/cog.r.#FF0000.png
/plane.42x?.rasterize.svg
```

### PackTask
Packs all *images* in `pack` flagged directory using libGDX's texture atlas packer and then flattens it.

If the directory contains pack.json file (name can contain flags), it will be used in packing,
as with default packing procedures.

**Flags (all trigger)**
* `pack` - Create a texture atlas from the *images* in this directory

**Example**
```
/UITextures.pack/
	button.png
	slider.9.png
	icon_cog.svg
```
produces
```
UITextures.atlas
UITextures.png
```

### DensityPackTask
More advanced version of PackTask

Can generate multiple atlas image files with different, Apple-like densities, that is, with @2x scheme.
Files with @Nx (where N is scale level) are assigned to that level.
Missing density levels are created from existing ones, either by up/downscaling or by rasterization with different dimensions.

Generates only one atlas file and (potentially) multiple image files.
Therefore, all images are packed to the same locations, only all coordinates are multiplied by N.
This allows for simple runtime density switching.
 
Scale level 1 is specified automatically, others (usually 2) can be added with @Nx flag (@2x in case of level 2).
Note that if the level is not a power of two (for example 3), `pot` setting must be set to false (default is true),
otherwise the level will be ignored and warning will be emitted.

**Flags**
* `densitypack` - **trigger** - Create a texture atlas from the *images* in this directory
* `@Nx` - Specify which density should be also generated, @1x is enabled by default

**Example**
```
/UITextures.@2x.densitypack/
	button.png
	slider.9.png
	icon_cog.svg
```
produces
```
UITextures.atlas
UITextures.png
UITextures@2x.png
```

### RemoveEmptyDirectoriesTask
Removes all directories which are empty from the output. This is triggered by the directory being empty and is automatic.

**Flags**
* `retain` - do not remove this directory, even if it is empty


## Image flags ##

When a task deals with an *image*, it can be a bitmap (png, jpg, ...) or a vector (svg) image.
These images can have multiple flags, these can be thought of as a "loading" flags, because they affect the process of
loading the image data into a internal image representation (bitmap). These flags are:

* `w<W>h<H>` - Image will be W tile-sizes wide and H tile-sizes tall (see *TileSize* setting and *Dimensions* below)
* `<W>x<H>` - Image will be W pixels wide and H pixels tall (see *Dimensions* below)
* `#RRGGBBAA` - Change the color of the background of the image - affects only images with transparency.
This flag also has multiple alternate forms: `#RGB`, `#RGBA`, `#RRGGBB`. Each letter (`R`, `G`, `B` or `A`) is
a hexadecimal digit.
* `9` - This image is a ninepatch (currently available only for bitmap images) (see *Dimensions* below)
* `scaling <algo>` - Use scaling algorithm `<algo>` available algorithms are `nearest`, `bilinear` and `bicubic`

**Dimensions**  
In image scaling flags, `<W>` or `<H>` can not only contain a positive decimal number, but also a single symbol `?`.
When present, it means that this dimension is either specified by the other scaling flag, or that this dimension should
be derived from the source image dimensions, in a way that maintains the aspect ratio.

In bitmap ninepatch images (only), these dimensions apply only to the image data - without ninepatch frame. Therefore tasks that
produce images with baked ninepatch frame, such as RasterizeTask will for flag 50x50 produce image 52x52 pixels large.

**Settings**
* TileSize - Size of tile used in w<W>h<H> flag pattern

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