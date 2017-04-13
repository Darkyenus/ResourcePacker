/*******************************************************************************
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

package com.darkyen.resourcepacker.tasks.densitypack;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.*;
import com.darkyen.resourcepacker.util.tools.texturepacker.ColorBleedEffect;
import com.google.common.io.Files;
import org.apache.batik.transcoder.TranscodingHints;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * @author Nathan Sweet
 */
public class TexturePacker {
    private final Settings settings;
    private final Packer packer;
    private final ObjectMap<String, IntMap<ImageSource>> imageSourcesByName = new ObjectMap<String, IntMap<ImageSource>>();

    public TexturePacker(Settings settings) {
        this.settings = settings;

        if (settings.pot) {
            if (settings.maxWidth != MathUtils.nextPowerOfTwo(settings.maxWidth))
                throw new RuntimeException("If pot is true, maxWidth must be a power of two: " + settings.maxWidth);
            if (settings.maxHeight != MathUtils.nextPowerOfTwo(settings.maxHeight))
                throw new RuntimeException("If pot is true, maxHeight must be a power of two: " + settings.maxHeight);
        }

        packer = new MaxRectsPacker(settings);
    }


    public ImageSource addImage(File file, String name, int index, int scaleFactor, boolean ninepatch) {
        IntMap<ImageSource> indices = imageSourcesByName.get(name);
        if (indices == null) {
            imageSourcesByName.put(name, indices = new IntMap<ImageSource>(8));
        }
        ImageSource source = indices.get(index);
        if (source == null) {
            indices.put(index, source = new ImageSource(name, index));
        }
        source.addImage(file, scaleFactor, ninepatch);
        return source;
    }

    public void pack(File outputDir, String packFileName) {
        if (packFileName.endsWith(settings.atlasExtension))
            packFileName = packFileName.substring(0, packFileName.length() - settings.atlasExtension.length());
        if (!outputDir.mkdirs() && !outputDir.isDirectory()) {
            System.err.println("Failed to create output directory " + outputDir + ", output will probably fail.");
        }

        final int[] scales = settings.scales;
        final Array<Rect> rects = new Array<Rect>(false, imageSourcesByName.size * scales.length);

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

        writer.write("  size: " + rect.source.stripWidth + ", " + rect.source.stripHeight + "\n");
        if (source.splits != null) {
            writer.write("  split: " //
                    + source.splits[0] + ", " + source.splits[1] + ", " + source.splits[2] + ", " + source.splits[3] + "\n");
        }
        if (source.pads != null) {
            if (source.splits == null) writer.write("  split: 0, 0, 0, 0\n");
            writer.write("  pad: " + source.pads[0] + ", " + source.pads[1] + ", " + source.pads[2] + ", " + source.pads[3] + "\n");
        }

        final int originalWidth = source.getBaseWidth(), originalHeight = source.getBaseHeight();
        writer.write("  orig: " + originalWidth + ", " + originalHeight + "\n");
        writer.write("  offset: " + source.stripOffX + ", " + (originalHeight - rect.source.stripHeight - source.stripOffY) + "\n");
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
        public final ObjectSet<ImageSource> aliases = new ObjectSet<ImageSource>();

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
            return !source.ninepatch;
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

    public static final class ImageSource implements Comparable<ImageSource> {

        public static final int MAX_SCALE_FACTOR = 3;

        public final String name;
        public final int index;
        public boolean ninepatch;
        public int[] splits;
        public int[] pads;
        private int stripOffX, stripOffY, stripWidth, stripHeight;
        private int baseWidth, baseHeight;
        private final BufferedImage[] bitmapsByFactor = new BufferedImage[MAX_SCALE_FACTOR];

        public final ObjectMap<TranscodingHints.Key, Object> rasterizationHints = new ObjectMap<TranscodingHints.Key, Object>();
        private final File[] filesByFactor = new File[MAX_SCALE_FACTOR];
        private final boolean[] ninepatchByFactor = new boolean[MAX_SCALE_FACTOR];

        protected final void checkScaleFactor(int scaleFactor) {
            if (!(scaleFactor >= 1 && scaleFactor <= MAX_SCALE_FACTOR)) {
                throw new IllegalArgumentException("Invalid scale factor for " + name + ": " + scaleFactor);
            }
        }

        protected ImageSource(String name, int index) {
            this.name = name;
            this.index = index;
        }

        public void addImage(File image, int scaleFactor, boolean ninepatch) {
            checkScaleFactor(scaleFactor);
            if (filesByFactor[scaleFactor - 1] != null) {
                System.err.println("Image for " + name + " @" + scaleFactor + "x already exists at " + filesByFactor[scaleFactor - 1] + " (overriding with " + image + ")");
            }
            filesByFactor[scaleFactor - 1] = image;
            ninepatchByFactor[scaleFactor - 1] = ninepatch;
        }

        private BufferedImage loadImage(File file, int fileLevel, int targetLevel, boolean ninepatch) {
            final String[] bitmapExtensions = ImageIO.getReaderFileSuffixes();
            final String extension = Files.getFileExtension(file.getName());

            for (String bitmapExtension : bitmapExtensions) {
                if (extension.equalsIgnoreCase(bitmapExtension)) {
                    return loadBitmapImage(file, fileLevel, targetLevel, ninepatch);
                }
            }

            if ("svg".equalsIgnoreCase(extension)) {
                return loadVectorImage(file, fileLevel, targetLevel);
            }

            throw new IllegalArgumentException("Can't load image: unrecognized extension \"" + extension + "\"");
        }

        private BufferedImage loadBitmapImage(File file, int fileLevel, int targetLevel, boolean ninepatch) {
            BufferedImage image;
            try {
                image = ImageIO.read(file);
            } catch (IOException ex) {
                throw new RuntimeException("Error reading image: " + file, ex);
            }
            if (image == null) throw new RuntimeException("Unable to read image: " + file);

            image = validateImage(image);

            if (ninepatch) {
                image = processNinepatch(image, fileLevel);
            }
            return scaleImage(image, fileLevel, targetLevel);
        }

        private BufferedImage loadVectorImage(File file, int sourceLevel, int targetLevel) {
            return validateImage(SVGRasterizer.rasterize(file, sourceLevel, targetLevel, rasterizationHints));
        }

        public final int getBaseWidth() {
            return baseWidth;
        }

        public final int getBaseHeight() {
            return baseHeight;
        }

        protected final BufferedImage getImage(int scaleFactor, boolean create) {
            checkScaleFactor(scaleFactor);
            final BufferedImage existing = bitmapsByFactor[scaleFactor - 1];
            if (existing != null) {
                return existing;
            } else if (create) {
                final BufferedImage result = createImage(scaleFactor);
                if (result == null) {
                    throw new IllegalStateException("Failed to create image for " + name + " @ " + scaleFactor);
                }
                return bitmapsByFactor[scaleFactor - 1] = result;
            } else {
                return null;
            }
        }

        protected final BufferedImage createTrimmedImage(int scaleFactor) {
            final BufferedImage image = getImage(scaleFactor, true);
            assert image != null;
            if (stripOffX == 0 && stripOffY == 0 && stripWidth == baseWidth && stripHeight == baseHeight) {
                return image;
            }

            return new BufferedImage(
                    image.getColorModel(),
                    image.getRaster().createWritableChild(
                            stripOffX * scaleFactor,
                            stripOffY * scaleFactor,
                            stripWidth * scaleFactor,
                            stripHeight * scaleFactor, 0, 0, null),
                    image.getColorModel().isAlphaPremultiplied(),
                    null);
        }

        private BufferedImage createImage(int scaleFactor) {
            if (filesByFactor[scaleFactor - 1] != null) {
                return loadImage(filesByFactor[scaleFactor - 1], scaleFactor, scaleFactor, ninepatchByFactor[scaleFactor - 1]);
            } else {
                //First look for bigger images to downscale
                for (int i = scaleFactor + 1; i <= MAX_SCALE_FACTOR; i++) {
                    if (filesByFactor[i - 1] != null) {
                        return loadImage(filesByFactor[i - 1], i, scaleFactor, ninepatchByFactor[i - 1]);
                    }
                }
                //Then look for smaller images to upscale, biggest first
                for (int i = scaleFactor - 1; i >= 1; i--) {
                    if (filesByFactor[i - 1] != null) {
                        return loadImage(filesByFactor[i - 1], i, scaleFactor, ninepatchByFactor[i - 1]);
                    }
                }
                //Then fail
                throw new IllegalStateException("Missing any images for " + name);
            }
        }

        public final Rect validate(Settings settings, int[] scales) {
            final Rectangle essentialBounds = new Rectangle();
            final Rectangle scaleEssentialBounds = new Rectangle();
            boolean firstBound = true;

            for (int scaleFactor : scales) {
                final BufferedImage image = getImage(scaleFactor, true);
                stripWhitespace(settings, image, scaleEssentialBounds);
                final float scale = 1f / scaleFactor;
                scaleEssentialBounds.x = scaleEssentialBounds.x * scale;
                scaleEssentialBounds.y = scaleEssentialBounds.y * scale;
                scaleEssentialBounds.width = scaleEssentialBounds.width * scale;
                scaleEssentialBounds.height = scaleEssentialBounds.height * scale;

                if (firstBound) {
                    essentialBounds.set(scaleEssentialBounds);
                    firstBound = false;
                } else {
                    essentialBounds.merge(scaleEssentialBounds);
                }
            }

            final BufferedImage base = getImage(1, true);
            assert base != null;
            baseWidth = base.getWidth();
            baseHeight = base.getHeight();

            stripOffX = Math.max(0, MathUtils.floorPositive(essentialBounds.x));
            stripOffY = Math.max(0, MathUtils.floorPositive(essentialBounds.y));
            stripWidth = Math.min(base.getWidth(), MathUtils.ceilPositive(essentialBounds.width));
            stripHeight = Math.min(base.getHeight(), MathUtils.ceilPositive(essentialBounds.height));

            ninepatch = splits != null || pads != null;

            return new Rect(this, stripWidth, stripHeight);
        }

        protected final BufferedImage validateImage(BufferedImage image) {
            if (image.getType() != BufferedImage.TYPE_4BYTE_ABGR) {
                BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
                newImage.getGraphics().drawImage(image, 0, 0, null);
                return newImage;
            } else return image;
        }

        protected final BufferedImage scaleImage(BufferedImage image, int fromFactor, int toFactor) {
            if (fromFactor == toFactor) {
                return image;
            }
            final double scale = (double) toFactor / (double) fromFactor;
            final int width = (int) Math.round(image.getWidth() * scale);
            final int height = (int) Math.round(image.getHeight() * scale);
            BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
            if (scale < 1) {
                newImage.getGraphics().drawImage(image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null);
            } else {
                Graphics2D g = (Graphics2D) newImage.getGraphics();
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(image, 0, 0, width, height, null);
            }
            return newImage;
        }

        protected final BufferedImage processNinepatch(BufferedImage image, int scaleFactor) {
            // Strip ".9" from file name, read ninepatch split pixels, and strip ninepatch split pixels.
            splits = getSplits(image, name, scaleFactor);
            pads = getPads(image, name, splits, scaleFactor);
            // Strip split pixels.
            final int width = image.getWidth() - 2, height = image.getHeight() - 2;
            BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
            newImage.getGraphics().drawImage(image, 0, 0, width, height, 1, 1, width + 1, height + 1, null);
            return newImage;
        }

        /**
         * Returns the splits, or null if the image had no splits or the splits were only a single region. Splits are an int[4] that
         * has left, right, top, bottom.
         */
        private int[] getSplits(BufferedImage image, String name, int scaleFactor) {
            WritableRaster raster = image.getRaster();

            int startX = getSplitPoint(raster, name, 1, 0, true, true);
            int endX = getSplitPoint(raster, name, startX, 0, false, true);
            int startY = getSplitPoint(raster, name, 0, 1, true, false);
            int endY = getSplitPoint(raster, name, 0, startY, false, false);

            // Ensure pixels after the end are not invalid.
            getSplitPoint(raster, name, endX + 1, 0, true, true);
            getSplitPoint(raster, name, 0, endY + 1, true, false);

            // No splits, or all splits.
            if (startX == 0 && endX == 0 && startY == 0 && endY == 0) return null;

            // Subtraction here is because the coordinates were computed before the 1px border was stripped.
            if (startX != 0) {
                startX--;
                endX = raster.getWidth() - 2 - (endX - 1);
            } else {
                // If no start point was ever found, we assume full stretch.
                endX = raster.getWidth() - 2;
            }
            if (startY != 0) {
                startY--;
                endY = raster.getHeight() - 2 - (endY - 1);
            } else {
                // If no start point was ever found, we assume full stretch.
                endY = raster.getHeight() - 2;
            }

            if (scaleFactor != 1) {
                final double scale = 1.0 / scaleFactor;
                startX = (int) Math.round(startX * scale);
                endX = (int) Math.round(endX * scale);
                startY = (int) Math.round(startY * scale);
                endY = (int) Math.round(endY * scale);
            }

            return new int[]{startX, endX, startY, endY};
        }

        /**
         * Returns the pads, or null if the image had no pads or the pads match the splits. Pads are an int[4] that has left, right,
         * top, bottom.
         */
        @SuppressWarnings("ConstantConditions")
        private int[] getPads(BufferedImage image, String name, int[] splits, int scaleFactor) {
            WritableRaster raster = image.getRaster();

            int bottom = raster.getHeight() - 1;
            int right = raster.getWidth() - 1;

            int startX = getSplitPoint(raster, name, 1, bottom, true, true);
            int startY = getSplitPoint(raster, name, right, 1, true, false);

            // No need to hunt for the end if a start was never found.
            int endX = 0;
            int endY = 0;
            if (startX != 0) endX = getSplitPoint(raster, name, startX + 1, bottom, false, true);
            if (startY != 0) endY = getSplitPoint(raster, name, right, startY + 1, false, false);

            // Ensure pixels after the end are not invalid.
            getSplitPoint(raster, name, endX + 1, bottom, true, true);
            getSplitPoint(raster, name, right, endY + 1, true, false);

            // No pads.
            if (startX == 0 && endX == 0 && startY == 0 && endY == 0) {
                return null;
            }

            // -2 here is because the coordinates were computed before the 1px border was stripped.
            if (startX == 0 && endX == 0) {
                startX = -1;
                endX = -1;
            } else {
                if (startX > 0) {
                    startX--;
                    endX = raster.getWidth() - 2 - (endX - 1);
                } else {
                    // If no start point was ever found, we assume full stretch.
                    endX = raster.getWidth() - 2;
                }
            }
            if (startY == 0 && endY == 0) {
                startY = -1;
                endY = -1;
            } else {
                if (startY > 0) {
                    startY--;
                    endY = raster.getHeight() - 2 - (endY - 1);
                } else {
                    // If no start point was ever found, we assume full stretch.
                    endY = raster.getHeight() - 2;
                }
            }

            if (scaleFactor != 1) {
                final double scale = 1.0 / scaleFactor;
                startX = (int) Math.round(startX * scale);
                endX = (int) Math.round(endX * scale);
                startY = (int) Math.round(startY * scale);
                endY = (int) Math.round(endY * scale);
            }

            int[] pads = new int[]{startX, endX, startY, endY};

            if (splits != null && Arrays.equals(pads, splits)) {
                return null;
            }

            return pads;
        }

        /**
         * Hunts for the start or end of a sequence of split pixels. Begins searching at (startX, startY) then follows along the x or y
         * axis (depending on value of xAxis) for the first non-transparent pixel if startPoint is true, or the first transparent pixel
         * if startPoint is false. Returns 0 if none found, as 0 is considered an invalid split point being in the outer border which
         * will be stripped.
         */
        static private int getSplitPoint(WritableRaster raster, String name, int startX, int startY, boolean startPoint, boolean xAxis) {
            int[] rgba = new int[4];

            int next = xAxis ? startX : startY;
            int end = xAxis ? raster.getWidth() : raster.getHeight();
            int breakA = startPoint ? 255 : 0;

            int x = startX;
            int y = startY;
            while (next != end) {
                if (xAxis)
                    x = next;
                else
                    y = next;

                raster.getPixel(x, y, rgba);
                if (rgba[3] == breakA) return next;

                if (!startPoint && (rgba[0] != 0 || rgba[1] != 0 || rgba[2] != 0 || rgba[3] != 255))
                    splitError(x, y, rgba, name);

                next++;
            }

            return 0;
        }


        static private String splitError(int x, int y, int[] rgba, String name) {
            throw new RuntimeException("Invalid " + name + " ninepatch split pixel at " + x + ", " + y + ", rgba: " + rgba[0] + ", "
                    + rgba[1] + ", " + rgba[2] + ", " + rgba[3]);
        }

        /**
         * Strips whitespace and returns the rect, or null if the image should be ignored.
         */
        private void stripWhitespace(Settings settings, BufferedImage source, Rectangle result) {
            WritableRaster alphaRaster = source.getAlphaRaster();
            if (alphaRaster == null || (!settings.stripWhitespaceX && !settings.stripWhitespaceY)) {
                result.set(0, 0, source.getWidth(), source.getHeight());
                return;
            }
            final byte[] a = new byte[1];
            int top = 0;
            int bottom = source.getHeight();
            if (settings.stripWhitespaceX) {
                outer:
                for (int y = 0; y < source.getHeight(); y++) {
                    for (int x = 0; x < source.getWidth(); x++) {
                        alphaRaster.getDataElements(x, y, a);
                        int alpha = a[0];
                        if (alpha < 0) alpha += 256;
                        if (alpha > settings.alphaThreshold) break outer;
                    }
                    top++;
                }
                outer:
                for (int y = source.getHeight(); --y >= top; ) {
                    for (int x = 0; x < source.getWidth(); x++) {
                        alphaRaster.getDataElements(x, y, a);
                        int alpha = a[0];
                        if (alpha < 0) alpha += 256;
                        if (alpha > settings.alphaThreshold) break outer;
                    }
                    bottom--;
                }
            }
            int left = 0;
            int right = source.getWidth();
            if (settings.stripWhitespaceY) {
                outer:
                for (int x = 0; x < source.getWidth(); x++) {
                    for (int y = top; y < bottom; y++) {
                        alphaRaster.getDataElements(x, y, a);
                        int alpha = a[0];
                        if (alpha < 0) alpha += 256;
                        if (alpha > settings.alphaThreshold) break outer;
                    }
                    left++;
                }
                outer:
                for (int x = source.getWidth(); --x >= left; ) {
                    for (int y = top; y < bottom; y++) {
                        alphaRaster.getDataElements(x, y, a);
                        int alpha = a[0];
                        if (alpha < 0) alpha += 256;
                        if (alpha > settings.alphaThreshold) break outer;
                    }
                    right--;
                }
            }
            int newWidth = right - left;
            int newHeight = bottom - top;
            if (newWidth <= 0 || newHeight <= 0) {
                result.set(0, 0, 0, 0);
                return;
            }
            result.set(left, top, newWidth, newHeight);
        }

        public final boolean isIdentical(ImageSource to) {
            //TODO Implement
            return this.equals(to);
        }

        static private String hash(BufferedImage image) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA1");

                // Ensure image is the correct format.
                int width = image.getWidth();
                int height = image.getHeight();
                if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
                    BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    newImage.getGraphics().drawImage(image, 0, 0, null);
                    image = newImage;
                }

                WritableRaster raster = image.getRaster();
                int[] pixels = new int[width];
                for (int y = 0; y < height; y++) {
                    raster.getDataElements(0, y, width, 1, pixels);
                    for (int x = 0; x < width; x++)
                        hash(digest, pixels[x]);
                }

                hash(digest, width);
                hash(digest, height);

                return new BigInteger(1, digest.digest()).toString(16);
            } catch (NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex);
            }
        }

        static private void hash(MessageDigest digest, int value) {
            digest.update((byte) (value >> 24));
            digest.update((byte) (value >> 16));
            digest.update((byte) (value >> 8));
            digest.update((byte) value);
        }

        private String toStringCache = null;

        @Override
        public String toString() {
            if (toStringCache == null) {
                if (index == -1) {
                    return toStringCache = name;
                } else {
                    return toStringCache = name + "_" + index;
                }
            } else return toStringCache;
        }

        @Override
        public int compareTo(ImageSource imageSource) {
            final int nameComparison = name.compareTo(imageSource.name);
            if (nameComparison == 0) {
                return index - imageSource.index;
            }
            return nameComparison;
        }
    }
}
