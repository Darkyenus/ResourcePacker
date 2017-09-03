/* *****************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.darkyen.resourcepacker.util.texturepacker;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.*;
import com.darkyen.resourcepacker.image.Image;
import com.darkyen.resourcepacker.util.tools.texturepacker.ColorBleedEffect;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * @author Nathan Sweet
 */
public class MultiScaleTexturePacker {
    private final Settings settings;
    private final Packer packer;
    private final ObjectMap<String, IntMap<ImageSource>> imageSourcesByName = new ObjectMap<>();

    public MultiScaleTexturePacker(Settings settings) {
        this.settings = settings;

        if (settings.pot) {
            if (settings.maxWidth != MathUtils.nextPowerOfTwo(settings.maxWidth))
                throw new RuntimeException("If pot is true, maxWidth must be a power of two: " + settings.maxWidth);
            if (settings.maxHeight != MathUtils.nextPowerOfTwo(settings.maxHeight))
                throw new RuntimeException("If pot is true, maxHeight must be a power of two: " + settings.maxHeight);
        }

        packer = new MaxRectsPacker(settings);
    }


    public ImageSource addImage(String name, int index, int scaleFactor, Image image) {
        IntMap<ImageSource> indices = imageSourcesByName.get(name);
        if (indices == null) {
            imageSourcesByName.put(name, indices = new IntMap<>(8));
        }
        ImageSource source = indices.get(index);
        if (source == null) {
            indices.put(index, source = new ImageSource(name, index));
        }
        source.addImage(scaleFactor, image);
        return source;
    }

    public void pack(File outputDir, String packFileName) {
        if (packFileName.endsWith(settings.atlasExtension))
            packFileName = packFileName.substring(0, packFileName.length() - settings.atlasExtension.length());
        if (!outputDir.mkdirs() && !outputDir.isDirectory()) {
            System.err.println("Failed to create output directory " + outputDir + ", output will probably fail.");
        }

        final int[] scales = settings.scales;
        final Array<Rect> rects = new Array<>(false, imageSourcesByName.size);

        for (IntMap<ImageSource> indices : imageSourcesByName.values()) {
            for (ImageSource source : indices.values()) {
                final Rect rect = source.validate(settings, scales);
                if (settings.ignoreBlankImages && rect.pageWidth == 0 && rect.pageHeight == 0) {
                    if (!settings.silent) System.out.println("Ignoring blank input image: " + rect.source);
                    continue;
                }
                rects.add(rect);
            }
        }

        if (settings.alias) {
            int size = rects.size;
            for (int a = 0; a < size - 1; a++) {
                final Rect rectA = rects.get(a);
                for (int b = a + 1; b < size; b++) {
                    final Rect rectB = rects.get(b);
                    if (rectA.source.isIdentical(rectB.source)) {
                        rectA.aliases.add(rectB.source);
                        rects.removeIndex(b);
                        b--;
                        size--;
                    }
                }
            }
        }

        final Array<Page> pages = packer.pack(rects);

        Arrays.sort(scales);
        //Iterate in reverse so that smallest scale (usually 1, base scale) is left in "pages"
        // and we can write correct atlas file
        for (int i = scales.length - 1; i >= 0; i--) {
            final int scale = scales[i];

            if (settings.pot && !MathUtils.isPowerOfTwo(scale)) {
                System.err.println("Skipping scale " + scale + " because only power of two scales can be used with power of two textures");
                continue;
            }
            writeImages(outputDir, settings.getScaledPackFileName(packFileName, scale), pages, scale);
        }

        try {
            assert scales[0] == 1;
            writePackFile(outputDir, packFileName, pages);
        } catch (IOException ex) {
            throw new RuntimeException("Error writing pack file.", ex);
        }
    }

    private void writeImages(File outputDir, String scaledPackFileName, Array<Page> pages, int scaleFactor) {
        File packFileNoExt = new File(outputDir, scaledPackFileName);
        File packDir = packFileNoExt.getParentFile();
        String imageName = packFileNoExt.getName();

        int fileIndex = 0;
        for (Page page : pages) {
            int width = page.width, height = page.height;
            int paddingX = settings.paddingX;
            int paddingY = settings.paddingY;
            if (settings.duplicatePadding) {
                paddingX /= 2;
                paddingY /= 2;
            }
            width -= settings.paddingX;
            height -= settings.paddingY;
            if (settings.edgePadding) {
                page.x = paddingX;
                page.y = paddingY;
                width += paddingX * 2;
                height += paddingY * 2;
            }

            width *= scaleFactor;
            height *= scaleFactor;

            if (settings.pot) {
                width = MathUtils.nextPowerOfTwo(width);
                height = MathUtils.nextPowerOfTwo(height);
            }
            width = Math.max(settings.minWidth, width);
            height = Math.max(settings.minHeight, height);
            page.imageWidth = width;
            page.imageHeight = height;

            assert !(page.imageWidth < page.width || page.imageHeight < page.height);

            File outputFile;
            while (true) {
                outputFile = new File(packDir, imageName + (fileIndex++ == 0 ? "" : fileIndex) + "." + settings.outputFormat);
                if (!outputFile.exists()) break;
            }
            new FileHandle(outputFile).parent().mkdirs();
            page.imageName = outputFile.getName();

            BufferedImage canvas = new BufferedImage(width, height, getBufferedImageType(settings.format));
            Graphics2D g = (Graphics2D) canvas.getGraphics();

            if (!settings.silent)
                System.out.println("Writing " + canvas.getWidth() + "x" + canvas.getHeight() + ": " + outputFile);

            for (Rect rect : page.outputRects) {
                BufferedImage image = rect.source.createTrimmedImage(scaleFactor);
                int iw = image.getWidth();
                int ih = image.getHeight();
                int rectX = page.x + rect.pageX, rectY = page.y + page.height - rect.pageY - rect.pageHeight;
                rectX *= scaleFactor;
                rectY *= scaleFactor;

                if (settings.duplicatePadding) {
                    int amountX = settings.paddingX / 2;
                    int amountY = settings.paddingY / 2;
                    if (rect.rotated) {
                        // Copy corner pixels to fill corners of the padding.
                        for (int i = 1; i <= amountX; i++) {
                            for (int j = 1; j <= amountY; j++) {
                                plot(canvas, rectX - j, rectY + iw - 1 + i, image.getRGB(0, 0));
                                plot(canvas, rectX + ih - 1 + j, rectY + iw - 1 + i, image.getRGB(0, ih - 1));
                                plot(canvas, rectX - j, rectY - i, image.getRGB(iw - 1, 0));
                                plot(canvas, rectX + ih - 1 + j, rectY - i, image.getRGB(iw - 1, ih - 1));
                            }
                        }
                        // Copy edge pixels into padding.
                        for (int i = 1; i <= amountY; i++) {
                            for (int j = 0; j < iw; j++) {
                                plot(canvas, rectX - i, rectY + iw - 1 - j, image.getRGB(j, 0));
                                plot(canvas, rectX + ih - 1 + i, rectY + iw - 1 - j, image.getRGB(j, ih - 1));
                            }
                        }
                        for (int i = 1; i <= amountX; i++) {
                            for (int j = 0; j < ih; j++) {
                                plot(canvas, rectX + j, rectY - i, image.getRGB(iw - 1, j));
                                plot(canvas, rectX + j, rectY + iw - 1 + i, image.getRGB(0, j));
                            }
                        }
                    } else {
                        // Copy corner pixels to fill corners of the padding.
                        for (int i = 1; i <= amountX; i++) {
                            for (int j = 1; j <= amountY; j++) {
                                plot(canvas, rectX - i, rectY - j, image.getRGB(0, 0));
                                plot(canvas, rectX - i, rectY + ih - 1 + j, image.getRGB(0, ih - 1));
                                plot(canvas, rectX + iw - 1 + i, rectY - j, image.getRGB(iw - 1, 0));
                                plot(canvas, rectX + iw - 1 + i, rectY + ih - 1 + j, image.getRGB(iw - 1, ih - 1));
                            }
                        }
                        // Copy edge pixels into padding.
                        for (int i = 1; i <= amountY; i++) {
                            copy(image, 0, 0, iw, 1, canvas, rectX, rectY - i, rect.rotated);
                            copy(image, 0, ih - 1, iw, 1, canvas, rectX, rectY + ih - 1 + i, rect.rotated);
                        }
                        for (int i = 1; i <= amountX; i++) {
                            copy(image, 0, 0, 1, ih, canvas, rectX - i, rectY, rect.rotated);
                            copy(image, iw - 1, 0, 1, ih, canvas, rectX + iw - 1 + i, rectY, rect.rotated);
                        }
                    }
                }
                copy(image, 0, 0, iw, ih, canvas, rectX, rectY, rect.rotated);
                if (settings.debug) {
                    g.setColor(Color.magenta);
                    g.drawRect(rectX, rectY, (rect.pageWidth - settings.paddingX - 1) * scaleFactor, (rect.pageHeight - settings.paddingY - 1) * scaleFactor);
                }
            }

            if (settings.bleed && !settings.premultiplyAlpha && !(settings.outputFormat.equalsIgnoreCase("jpg") || settings.outputFormat.equalsIgnoreCase("jpeg"))) {
                canvas = new ColorBleedEffect().processImage(canvas, 2);
                g = (Graphics2D) canvas.getGraphics();
            }

            if (settings.debug) {
                g.setColor(Color.magenta);
                g.drawRect(0, 0, width - 1, height - 1);
            }

            ImageOutputStream ios = null;
            try {
                if (settings.outputFormat.equalsIgnoreCase("jpg") || settings.outputFormat.equalsIgnoreCase("jpeg")) {
                    BufferedImage newImage = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
                    newImage.getGraphics().drawImage(canvas, 0, 0, null);
                    canvas = newImage;

                    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
                    ImageWriter writer = writers.next();
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(settings.jpegQuality);
                    ios = ImageIO.createImageOutputStream(outputFile);
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(canvas, null, null), param);
                } else {
                    if (settings.premultiplyAlpha) canvas.getColorModel().coerceData(canvas.getRaster(), true);
                    ImageIO.write(canvas, "png", outputFile);
                }
            } catch (IOException ex) {
                throw new RuntimeException("Error writing file: " + outputFile, ex);
            } finally {
                if (ios != null) {
                    try {
                        ios.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    static private void plot(BufferedImage dst, int x, int y, int argb) {
        if (0 <= x && x < dst.getWidth() && 0 <= y && y < dst.getHeight()) dst.setRGB(x, y, argb);
    }

    static private void copy(BufferedImage src, int x, int y, int w, int h, BufferedImage dst, int dx, int dy, boolean rotated) {
        if (rotated) {
            for (int i = 0; i < w; i++)
                for (int j = 0; j < h; j++)
                    plot(dst, dx + j, dy + w - i - 1, src.getRGB(x + i, y + j));
        } else {
            for (int i = 0; i < w; i++)
                for (int j = 0; j < h; j++)
                    plot(dst, dx + i, dy + j, src.getRGB(x + i, y + j));
        }
    }

    private void writePackFile(File outputDir, String scaledPackFileName, Array<Page> pages) throws IOException {
        File packFile = new File(outputDir, scaledPackFileName + settings.atlasExtension);
        File packDir = packFile.getParentFile();
        if (!packDir.mkdirs() && !packDir.isDirectory()) {
            System.err.println("Failed to create pack file directory " + packDir + ", output will probably fail.");
        }

        if (packFile.exists()) {
            // Make sure there aren't duplicate names.
            TextureAtlasData textureAtlasData = new TextureAtlasData(new FileHandle(packFile), new FileHandle(packFile), false);
            for (Page page : pages) {
                for (Rect rect : page.outputRects) {
                    String rectName = rect.source.name;
                    for (Region region : textureAtlasData.getRegions()) {
                        if (region.name.equals(rectName)) {
                            throw new GdxRuntimeException("A region with the name \"" + rectName + "\" has already been packed: "
                                    + rect.source.name);
                        }
                    }
                }
            }
        }

        FileWriter writer = new FileWriter(packFile, true);
        for (Page page : pages) {
            writer.write("\n" + page.imageName + "\n");
            writer.write("size: " + page.imageWidth + "," + page.imageHeight + "\n");
            writer.write("format: " + settings.format + "\n");
            writer.write("filter: " + settings.filterMin + "," + settings.filterMag + "\n");
            writer.write("repeat: " + getRepeatValue() + "\n");

            page.outputRects.sort();
            for (Rect rect : page.outputRects) {
                writeRect(writer, page, rect, rect.source);
                //noinspection unchecked
                Array<ImageSource> aliases = new Array(rect.aliases.size);
                for (ImageSource alias : rect.aliases) {
                    aliases.add(alias);
                }
                aliases.sort();
                for (ImageSource alias : aliases) {
                    writeRect(writer, page, rect, alias);
                }
            }
        }
        writer.close();
    }

    private void writeRect(FileWriter writer, Page page, Rect rect, ImageSource source) throws IOException {
        writer.write(source.name + "\n");
        writer.write("  rotate: " + rect.rotated + "\n");
        writer.write("  xy: " + (page.x + rect.pageX) + ", " + (page.y + page.height - rect.pageHeight - rect.pageY) + "\n");

        writer.write("  size: " + rect.source.getStripWidth() + ", " + rect.source.getStripHeight() + "\n");
        final int[] splits = source.getSplits();
        if (splits != null) {
            writer.write("  split: " //
                    + splits[0] + ", " + splits[1] + ", " + splits[2] + ", " + splits[3] + "\n");
        }
        final int[] pads = source.getPads();
        if (pads != null) {
            if (splits == null) writer.write("  split: 0, 0, 0, 0\n");
            writer.write("  pad: " + pads[0] + ", " + pads[1] + ", " + pads[2] + ", " + pads[3] + "\n");
        }

        final int originalWidth = source.getBaseWidth(), originalHeight = source.getBaseHeight();
        writer.write("  orig: " + originalWidth + ", " + originalHeight + "\n");
        writer.write("  offset: " + source.getStripOffX() + ", " + (originalHeight - rect.source.getStripHeight() - source.getStripOffY()) + "\n");
        writer.write("  index: " + source.index + "\n");
    }

    private String getRepeatValue() {
        if (settings.wrapX == TextureWrap.Repeat && settings.wrapY == TextureWrap.Repeat) return "xy";
        if (settings.wrapX == TextureWrap.Repeat && settings.wrapY == TextureWrap.ClampToEdge) return "x";
        if (settings.wrapX == TextureWrap.ClampToEdge && settings.wrapY == TextureWrap.Repeat) return "y";
        return "none";
    }

    private int getBufferedImageType(Format format) {
        switch (format) {
            case RGBA8888:
            case RGBA4444:
                return BufferedImage.TYPE_INT_ARGB;
            case RGB565:
            case RGB888:
                return BufferedImage.TYPE_INT_RGB;
            case Alpha:
                return BufferedImage.TYPE_BYTE_GRAY;
            default:
                throw new RuntimeException("Unsupported format: " + format);
        }
    }

    /**
     * @author Nathan Sweet
     */
    static public class Page {
        public String imageName;
        public Array<Rect> outputRects, remainingRects;
        public float occupancy;
        public int x, y, width, height, imageWidth, imageHeight;
    }

    /**
     * @author Nathan Sweet
     */
    static public class Rect implements Comparable<Rect> {

        public ImageSource source;
        public final ObjectSet<ImageSource> aliases = new ObjectSet<>();

        //Packing
        public int pageX, pageY, pageWidth, pageHeight; // Portion of page taken by this region, including padding.
        public boolean rotated;
        int score1, score2;

        Rect(ImageSource source, int pageWidth, int pageHeight) {
            this.source = source;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
        }

        Rect() {
        }

        Rect(Rect rect) {
            pageX = rect.pageX;
            pageY = rect.pageY;
            pageWidth = rect.pageWidth;
            pageHeight = rect.pageHeight;
        }

        public boolean canRotate() {
            return !source.isNinepatch();
        }

        void set(Rect rect) {
            source = rect.source;
            pageX = rect.pageX;
            pageY = rect.pageY;
            pageWidth = rect.pageWidth;
            pageHeight = rect.pageHeight;
            rotated = rect.rotated;
            aliases.clear();
            aliases.addAll(rect.aliases);
            score1 = rect.score1;
            score2 = rect.score2;
        }

        public int compareTo(Rect o) {
            return source.compareTo(o.source);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            Rect other = (Rect) obj;
            if (source.name == null) {
                if (other.source.name != null) return false;
            } else if (!source.name.equals(other.source.name)) return false;
            return true;
        }

        @Override
        public String toString() {
            return source.name + "[" + pageX + "," + pageY + " " + pageWidth + "x" + pageHeight + "]";
        }
    }

    /**
     * @author Nathan Sweet
     */
    static public class Settings {
        public boolean pot = true;
        public int paddingX = 2, paddingY = 2;
        public boolean edgePadding = true;
        public boolean duplicatePadding = false;
        public boolean rotation;
        public int minWidth = 16, minHeight = 16;
        public int maxWidth = 1024, maxHeight = 1024;
        public boolean square = false;
        public boolean stripWhitespaceX, stripWhitespaceY;
        public int alphaThreshold;
        public TextureFilter filterMin = TextureFilter.Nearest, filterMag = TextureFilter.Nearest;
        public TextureWrap wrapX = TextureWrap.ClampToEdge, wrapY = TextureWrap.ClampToEdge;
        public Format format = Format.RGBA8888;
        public boolean alias = true;
        public String outputFormat = "png";
        public float jpegQuality = 0.9f;
        public boolean ignoreBlankImages = true;
        public boolean fast;
        public boolean debug;
        public boolean silent;
        public boolean combineSubdirectories;
        public boolean flattenPaths;
        public boolean premultiplyAlpha;
        public boolean useIndexes = true;
        public boolean bleed = true;
        public boolean limitMemory = true;
        public int[] scales = {1};
        public String atlasExtension = ".atlas";

        public Settings() {
        }

        @SuppressWarnings("unused")
        public Settings(Settings settings) {
            fast = settings.fast;
            rotation = settings.rotation;
            pot = settings.pot;
            minWidth = settings.minWidth;
            minHeight = settings.minHeight;
            maxWidth = settings.maxWidth;
            maxHeight = settings.maxHeight;
            paddingX = settings.paddingX;
            paddingY = settings.paddingY;
            edgePadding = settings.edgePadding;
            duplicatePadding = settings.duplicatePadding;
            alphaThreshold = settings.alphaThreshold;
            ignoreBlankImages = settings.ignoreBlankImages;
            stripWhitespaceX = settings.stripWhitespaceX;
            stripWhitespaceY = settings.stripWhitespaceY;
            alias = settings.alias;
            format = settings.format;
            jpegQuality = settings.jpegQuality;
            outputFormat = settings.outputFormat;
            filterMin = settings.filterMin;
            filterMag = settings.filterMag;
            wrapX = settings.wrapX;
            wrapY = settings.wrapY;
            debug = settings.debug;
            silent = settings.silent;
            combineSubdirectories = settings.combineSubdirectories;
            flattenPaths = settings.flattenPaths;
            premultiplyAlpha = settings.premultiplyAlpha;
            square = settings.square;
            useIndexes = settings.useIndexes;
            bleed = settings.bleed;
            limitMemory = settings.limitMemory;
            scales = settings.scales;
            atlasExtension = settings.atlasExtension;
        }

        public String getScaledPackFileName(String packFileName, int scale) {
            if (scale == 1) {
                return packFileName;
            } else {
                return packFileName + "@" + scale + "x";
            }
        }
    }

    public interface Packer {
        Array<Page> pack(Array<Rect> inputRects);
    }

}
