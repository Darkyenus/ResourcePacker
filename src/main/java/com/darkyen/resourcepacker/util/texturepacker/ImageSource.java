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

    public final String name;
    public final int index;

    private boolean validated = false;

    private boolean ninepatch;
    private int[] splits = null;
    private int[] pads = null;
    private int stripOffX, stripOffY, stripWidth, stripHeight;
    private int baseWidth, baseHeight;

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

        if (imagesByFactor[scaleFactor - 1] != null) {
            System.err.println("Image for " + name + " @" + scaleFactor + "x already exists at " + imagesByFactor[scaleFactor - 1].getFile() + " (overriding with " + image.getFile() + ")");
        }
        imagesByFactor[scaleFactor - 1] = image;
        bitmapsByFactor[scaleFactor - 1] = null;
    }

    public final MultiScaleTexturePacker.Rect validate(MultiScaleTexturePacker.Settings settings, int[] scales) {
        assert !validated;

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
                    Log.warn("Image", "Expected size of "+image+" @"+scale+"x is "+expectedW+"x"+expectedH+", but image reports "+image.getWidth()+"x"+image.getHeight()+". Image may be distorted as a result.");
                }

                bitmapsByFactor[scale - 1] = ensureCorrectFormat(image.image(expectedW, expectedH, image.getBackgroundColor()));
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
                    }
                }
                //Then fail
                assert false;
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
        assert validated;

        final BufferedImage image = bitmapsByFactor[scaleFactor - 1];
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

    public boolean isNinepatch() {
        assert validated;
        return ninepatch;
    }

    public int[] getSplits() {
        assert validated;
        return splits;
    }

    public int[] getPads() {
        assert validated;
        return pads;
    }

    public int getStripOffX() {
        assert validated;
        return stripOffX;
    }

    public int getStripOffY() {
        assert validated;
        return stripOffY;
    }

    public int getStripWidth() {
        assert validated;
        return stripWidth;
    }

    public int getStripHeight() {
        assert validated;
        return stripHeight;
    }

    public final int getBaseWidth() {
        assert validated;
        return baseWidth;
    }

    public final int getBaseHeight() {
        assert validated;
        return baseHeight;
    }

    public final boolean isIdentical(ImageSource to) {
        assert validated;

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
            Log.warn("ImageSource", "Digest algorithm not available, reliant functionality won't work");
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
