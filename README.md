# Resource Packer #

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

For example, for "iOS Settings bundle" (a directory named `Settings.bundle`),
have directory named `Settings."bundle"`.

Directories follow similar pattern, there is just no `.extension`.

See testresources directory for examples.

## Default tasks ##

There is a couple of tasks that run by default in following order (order is important).

### CreateFontsTask
Rasterizes .ttf fonts. Font must have .N. flag where N is font size in pixels.

**Flags**:
* `p[t|b|l|r]<N>` - Adds padding around every glyph (Top, Bottom, Left, Right or if no letter then everywhere)
* `<N>` - size - mandatory
* `<S>-<E>` - adds all codepoints from S to E, inclusive
* `from <F>` - adds all codepoints from file F, file must be only letters and in same directory, no extension either. UTF-8 encoding is assumed
* `bg#RRGGBBAA` - background color, default is transparent
* `fg#RRGGBBAA` - foreground color, default is white
* `native` - use native rendering
* `ascii` - add all ascii glyphs (codepoints 32 through 255)
* `nehe` - add all nehe glyphs (codepoints 32 through 128)

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

Flattens directories marked with `flatten`.

### ResizeTask

Resizes image by given arguments

**Flags**:
* `w<W>h<H>` - Image will be W tile-sizes wide and H tile-sizes tall
* `<W>x<H>` - Image will be W pixels wide and H pixels tall

**Settings**:
* TileSize - Size of tile used by ResizeTask's w<W>h<H> flag pattern

### RasterizeTask

Raterizes .svg files using default size (in file) or one specified in flags.

**Flags**:
* `[W]x[H]` - W is width in pixels and H is height in pixels

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

### RemoveEmptyDirectoriesTask

Does what it says on the tin.


## How to use ##

Right now, it is best used through JVM based build system, like SBT or Gradle.
Here are instructions on how to use it in build.sbt based project:

1. In your project's `project/plugins.sbt` add lines:
```
resolvers += "jitpack" at "https://jitpack.io"

libraryDependencies += "com.github.Darkyenus" % "ResourcePacker" % "1.2"
```
1. In your project's build.sbt add lines:
```
import darkyenus.resourcepacker.{PackingOperation, LWJGLLauncher}

TaskKey[Unit]("packResources") := {
  LWJGLLauncher.launch(new PackingOperation(baseDirectory.value / "resources",baseDirectory.value / "assets"))
}
```
1. Now you can run `sbt packResources`. That will pack contents of folder `resources` to `assets` in project's root directory.

_Note: A tiny window will be displayed during packing, that is normal, packer needs it for GL context, it will hopefully be gone in future releases._

_Another note: Jitpack builds it using Scala 2.10 - default for sbt build scripts - and does not support cross compiling. If you use different Scala version for your sbt, you will probably have to cross compile it yourself._
