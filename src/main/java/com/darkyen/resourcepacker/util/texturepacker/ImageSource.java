package com.darkyen.resourcepacker.util.texturepacker;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.darkyen.resourcepacker.image.Image;
import com.esotericsoftware.minlog.Log;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 *
 */
public final class ImageSource implements Comparable<ImageSource> {

    public static final int MAX_SCALE_FACTOR = 4;
    public static final String LOG = "ImageSource";

    public final String name;
    public final int index;

    private boolean validated = false;

    private boolean ninepatch;
    private int[] splits = null;
    private int[] pads = null;
    private int stripOffX, stripOffY, stripWidth, stripHeight;
    private int baseWidth, baseHeight;

    /** Assigned bitmap overrides.
     * The distinctions allows to supply general .svg for all scales, and override some scale factors with specialized bitmap. */
    private final Image.BitmapImage[] bitmapOverrideImagesByFactor = new Image.BitmapImage[MAX_SCALE_FACTOR];
    /** Images assigned from bitmap and vector images. If the {@link #bitmapOverrideImagesByFactor} is filled, then this will hold the vector version. */
    private final Image[] imagesByFactor = new Image[MAX_SCALE_FACTOR];
    private final BufferedImage[] bitmapsByFactor = new BufferedImage[MAX_SCALE_FACTOR];
    private byte[] imageSourceHash = null;

    ImageSource(String name, int index) {
        this.name = name;
        this.index = index;
    }

    //region Public API
    public void addImage(int scaleFactor, Image image) {
        assert !validated;
        checkScaleFactor(scaleFactor);

        final int idx = scaleFactor - 1;
        bitmapsByFactor[idx] = null;

        if (image instanceof Image.BitmapImage && imagesByFactor[idx] instanceof Image.VectorImage && bitmapOverrideImagesByFactor[idx] == null) {
            bitmapOverrideImagesByFactor[idx] = (Image.BitmapImage) image;
            Log.debug(LOG, name+"@"+scaleFactor+"x is "+imagesByFactor[idx]+" with level override "+bitmapOverrideImagesByFactor[idx]);
        } else if (image instanceof Image.VectorImage && imagesByFactor[idx] instanceof Image.BitmapImage && bitmapOverrideImagesByFactor[idx] == null) {
            bitmapOverrideImagesByFactor[idx] = (Image.BitmapImage) imagesByFactor[idx];
            imagesByFactor[idx] = image;
            Log.debug(LOG, name+"@"+scaleFactor+"x is "+imagesByFactor[idx]+" with level override "+bitmapOverrideImagesByFactor[idx]);
        } else if (imagesByFactor[idx] != null) {
            Log.warn(LOG, name + "@" + scaleFactor + "x already exists at " + imagesByFactor[idx].getFile() + ", " + image.getFile() + " will be ignored");
        } else {
            imagesByFactor[idx] = image;
        }
    }

    public final MultiScaleTexturePacker.Rect validate(MultiScaleTexturePacker.Settings settings, int[] scales) {
        assert !validated;
        validated = true;

        final int baseWidth, baseHeight;
        final boolean ninepatch;
        {
            // Determine base size
            gotImage: {
                for (int i = 0; i < imagesByFactor.length; i++) {
                    final Image image = imagesByFactor[i];
                    if (image == null) continue;

                    this.baseWidth = baseWidth = image.getWidth() / (i+1);
                    this.baseHeight = baseHeight = image.getHeight() / (i+1);
                    this.ninepatch = ninepatch = image.getNinepatch();
                    if (ninepatch) {
                        final int[] splits = image.ninepatchSplits(baseWidth, baseHeight);
                        assert splits != null;
                        this.splits = new int[]{
                                splits[0] / (i+1),
                                splits[1] / (i+1),
                                splits[2] / (i+1),
                                splits[3] / (i+1)
                        };

                        final int[] pads = image.ninepatchPads(baseWidth, baseHeight);
                        if (pads != null) {
                            this.pads = new int[]{
                                    pads[0] / (i+1),
                                    pads[1] / (i+1),
                                    pads[2] / (i+1),
                                    pads[3] / (i+1)
                            };
                        }
                    }
                    break gotImage;
                }

                throw new Error("ImageSource has no images: "+this);
            }

            // Prepare given bitmaps and validate their sizes (TODO Validate ninepatch dimensions)
            for (int scale : scales) {
                final Image image = imagesByFactor[scale - 1];
                if (image == null) {
                    continue;
                }

                final int expectedW = baseWidth * scale;
                final int expectedH = baseHeight * scale;
                if (image.getWidth() != expectedW || image.getHeight() != expectedH) {
                    Log.warn(LOG, "Expected size of "+image+" @"+scale+"x is "+expectedW+"x"+expectedH+", but image reports "+image.getWidth()+"x"+image.getHeight()+". Image may be distorted as a result.");
                }

                final Image overrideImage = bitmapOverrideImagesByFactor[scale - 1];
                final Image rasterizationImage = overrideImage == null ? image : overrideImage;

                bitmapsByFactor[scale - 1] = ensureCorrectFormat(rasterizationImage.image(expectedW, expectedH, rasterizationImage.getBackgroundColor()));
            }

            // Derive missing bitmaps
            deriveMissingBitmaps:
            for (int scale : scales) {
                if (bitmapsByFactor[scale - 1] != null) {
                    continue;
                }

                //First look for bigger images to downscale
                for (int i = scale + 1; i <= MAX_SCALE_FACTOR; i++) {
                    final Image image = imagesByFactor[i - 1];
                    if (image != null) {
                        bitmapsByFactor[scale - 1] = ensureCorrectFormat(image.image(baseWidth*scale, baseHeight*scale, image.getBackgroundColor()));
                        continue deriveMissingBitmaps;
                    }
                }
                //Then look for smaller images to upscale, biggest first
                for (int i = scale - 1; i >= 1; i--) {
                    final Image image = imagesByFactor[i - 1];
                    if (image != null) {
                        bitmapsByFactor[scale - 1] = ensureCorrectFormat(image.image(baseWidth*scale, baseHeight*scale, image.getBackgroundColor()));
                        continue deriveMissingBitmaps;
                    }
                }
                //Then fail
                assert false : "Failed to fill scales of "+this;
            }
        }

        {
            final Rectangle essentialBounds = new Rectangle();
            final Rectangle scaleEssentialBounds = new Rectangle();
            boolean firstBound = true;

            for (int scaleFactor : scales) {
                // Do not consider derived images, they are covered by their parents
                if (imagesByFactor[scaleFactor-1] == null) continue;

                final BufferedImage image = bitmapsByFactor[scaleFactor - 1];
                stripWhitespace(settings, image, scaleEssentialBounds);
                scaleEssentialBounds.x = scaleEssentialBounds.x / scaleFactor;
                scaleEssentialBounds.y = scaleEssentialBounds.y / scaleFactor;
                scaleEssentialBounds.width = scaleEssentialBounds.width / scaleFactor;
                scaleEssentialBounds.height = scaleEssentialBounds.height / scaleFactor;

                if (firstBound) {
                    essentialBounds.set(scaleEssentialBounds);
                    firstBound = false;
                } else {
                    essentialBounds.merge(scaleEssentialBounds);
                }
            }

            stripOffX = Math.max(0, MathUtils.floorPositive(essentialBounds.x));
            stripOffY = Math.max(0, MathUtils.floorPositive(essentialBounds.y));
            stripWidth = Math.min(baseWidth, MathUtils.ceilPositive(essentialBounds.width));
            stripHeight = Math.min(baseHeight, MathUtils.ceilPositive(essentialBounds.height));
        }

        return new MultiScaleTexturePacker.Rect(this, stripWidth, stripHeight);
    }

    public final BufferedImage createTrimmedImage(int scaleFactor) {
        assert validated : "Not validated yet";

        final BufferedImage image = bitmapsByFactor[scaleFactor - 1];
        assert image != null;
        if (stripOffX == 0 && stripOffY == 0 && stripWidth == baseWidth && stripHeight == baseHeight) {
            return image;
        }

        if (stripWidth == 0 || stripHeight == 0) {
            Log.warn(LOG, "Image "+this+" is stripped whole, returning single pixel.");
            return new BufferedImage(image.getColorModel(),
                    image.getRaster().createWritableChild(
                            stripOffX * scaleFactor,
                            stripOffY * scaleFactor,
                            scaleFactor,
                            scaleFactor, 0, 0, null),
                    image.getColorModel().isAlphaPremultiplied(),
                    null);
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

    public boolean isNinepatch() {
        assert validated : "Not validated yet";
        return ninepatch;
    }

    public int[] getSplits() {
        assert validated : "Not validated yet";
        return splits;
    }

    public int[] getPads() {
        assert validated : "Not validated yet";
        return pads;
    }

    public int getStripOffX() {
        assert validated : "Not validated yet";
        return stripOffX;
    }

    public int getStripOffY() {
        assert validated : "Not validated yet";
        return stripOffY;
    }

    public int getStripWidth() {
        assert validated : "Not validated yet";
        return stripWidth;
    }

    public int getStripHeight() {
        assert validated : "Not validated yet";
        return stripHeight;
    }

    public final int getBaseWidth() {
        assert validated : "Not validated yet";
        return baseWidth;
    }

    public final int getBaseHeight() {
        assert validated : "Not validated yet";
        return baseHeight;
    }

    public final boolean isIdentical(ImageSource to) {
        assert validated : "Not validated yet";

        final byte[] myHash = getHash();
        final byte[] theirHash = to.getHash();
        return myHash != null && theirHash != null && Arrays.equals(myHash, theirHash);

    }
    //endregion

    private byte[] getHash() {
        if (imageSourceHash != null) {
            return imageSourceHash;
        }

        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            Log.warn(LOG, "Digest algorithm not available, reliant functionality won't work");
            return null;
        }

        for (BufferedImage image : bitmapsByFactor) {
            if (image == null) {
                digest.update((byte)0);
                continue;
            }

            // Ensure image is the correct format.
            int width = image.getWidth();
            int height = image.getHeight();

            WritableRaster raster = image.getRaster();
            int[] pixel = new int[4];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    raster.getPixel(x, y, pixel);
                    for (int i : pixel) {
                        hash(digest, i);
                    }
                }
            }

            hash(digest, width);
            hash(digest, height);
        }

        return imageSourceHash = digest.digest();
    }

    private BufferedImage ensureCorrectFormat(BufferedImage image) {
        if (image.getType() != BufferedImage.TYPE_4BYTE_ABGR) {
            BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
            newImage.getGraphics().drawImage(image, 0, 0, null);
            return newImage;
        } else return image;
    }

    /**
     * Strips whitespace and returns the rect, or null if the image should be ignored.
     */
    private void stripWhitespace(MultiScaleTexturePacker.Settings settings, BufferedImage source, Rectangle result) {
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

    static private void hash(MessageDigest digest, int value) {
        digest.update((byte) (value >> 24));
        digest.update((byte) (value >> 16));
        digest.update((byte) (value >> 8));
        digest.update((byte) value);
    }

    //region Interface
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
    //endregion

    //region Util
    protected final void checkScaleFactor(int scaleFactor) {
        if (!(scaleFactor >= 1 && scaleFactor <= MAX_SCALE_FACTOR)) {
            throw new IllegalArgumentException("Invalid scale factor for " + name + ": " + scaleFactor);
        }
    }
    //endregion
}
