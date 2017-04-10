/* ******************************************************************************
 * Based on FreeTypeFontGenerator from libGDX, under:
 *
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
 * *****************************************************************************/
package darkyenus.resourcepacker.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.StringBuilder;
import java.nio.ByteBuffer;

/**
 * FreeType based font rasterizer and packer.
 *
 * BASED ON {@link com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator} FROM libGDX PROJECT, SEE HEADER
 */
public class FreeTypePacker {

    final FreeType.Library library;
    final FreeType.Face face;
    final FileHandle fontFile;

    /** Creates a new generator from the given font file. Uses {@link FileHandle#length()} to determine the file size. If the file
     * length could not be determined (it was 0), an extra copy of the font bytes is performed. Throws a
     * {@link GdxRuntimeException} if loading did not succeed. */
    public FreeTypePacker(FileHandle fontFile) {
        this.fontFile = fontFile;
        int fileSize = (int)fontFile.length();

        library = FreeType.initFreeType();
        if (library == null) throw new GdxRuntimeException("Couldn't initialize FreeType");

        ByteBuffer buffer;
        InputStream input = fontFile.read();
        try {
            if (fileSize == 0) {
                // Copy to a byte[] to get the file size, then copy to the buffer.
                byte[] data = StreamUtils.copyStreamToByteArray(input, 1024 * 16);
                buffer = BufferUtils.newUnsafeByteBuffer(data.length);
                BufferUtils.copy(data, 0, buffer, data.length);
            } else {
                // Trust the specified file size.
                buffer = BufferUtils.newUnsafeByteBuffer(fileSize);
                StreamUtils.copyStream(input, buffer);
            }
        } catch (IOException ex) {
            throw new GdxRuntimeException(ex);
        } finally {
            StreamUtils.closeQuietly(input);
        }

        face = library.newMemoryFace(buffer, 0);
        if (face == null) throw new GdxRuntimeException("Couldn't create face for font: " + fontFile);
    }

    private static final class CharacterData implements Comparable<CharacterData> {
        public final int glyphIndex;
        public final int codePoint;
        public int advanceX, offsetX, offsetY;
        public Pixmap pixmap;
        public Rectangle packed;
        public int page = 0;

        private CharacterData(int glyphIndex, int codePoint) {
            this.glyphIndex = glyphIndex;
            this.codePoint = codePoint;
        }

        @Override
        public int compareTo(CharacterData o) {
            return o.pixmap.getHeight() - pixmap.getHeight();
        }
    }

    public Array<FileHandle> generate (FreeTypeFontParameter parameter, FileHandle outputFolder) {
        final Array<FileHandle> resultFiles = new Array<>(FileHandle.class);

        if (!face.setPixelSizes(0, parameter.size)) throw new GdxRuntimeException("Couldn't set size for font");

        // set general font data
        FreeType.SizeMetrics fontMetrics = face.getSize().getMetrics();
        final int descender = FreeType.toInt(fontMetrics.getDescender());
        final int lineHeight = FreeType.toInt(fontMetrics.getHeight());

        final Array<CharacterData> glyphs = new Array<>();

        FreeType.Stroker stroker = null;
        if (parameter.borderWidth > 0) {
            stroker = library.createStroker();
            stroker.set((int)(parameter.borderWidth * 64f),
                    parameter.borderStraight ? FreeType.FT_STROKER_LINECAP_BUTT : FreeType.FT_STROKER_LINECAP_ROUND,
                    parameter.borderStraight ? FreeType.FT_STROKER_LINEJOIN_MITER_FIXED : FreeType.FT_STROKER_LINEJOIN_ROUND, 0);
        }

        if(parameter.codePoints == null) {
            for (int codePoint = 0; codePoint < 0x10FFFF; codePoint++) {
                createGlyph(codePoint, parameter, stroker, glyphs);
            }
        } else {
            parameter.codePoints.add(0);
            parameter.codePoints.add(' ');
            for(final IntSet.IntSetIterator it = parameter.codePoints.iterator();it.hasNext;){
                createGlyph(it.next(), parameter, stroker, glyphs);
            }
        }


        glyphs.sort();

        // Guess needed texture size
        final int textureSize;
        {
            long totalSize2 = 0;
            for (CharacterData glyph : glyphs) {
                totalSize2 += glyph.pixmap.getWidth() * glyph.pixmap.getHeight();
            }
            final double waste = 1.2;
            int totalSize = (int)Math.ceil(Math.sqrt(totalSize2 * waste));
            int size = MathUtils.nextPowerOfTwo(totalSize);
            if (size > 4096) size = 4096;
            //System.out.println("Will use size: "+size);
            textureSize = size;
        }

        final PixmapPacker packer = new PixmapPacker(textureSize, textureSize, Pixmap.Format.RGBA8888, 1, false, new PixmapPacker.SkylineStrategy());
        if (parameter.borderWidth > 0) {
            packer.setTransparentColor(parameter.borderColor);
            packer.getTransparentColor().a = 0;
        } else {
            packer.setTransparentColor(parameter.color);
            packer.getTransparentColor().a = 0;
        }

        //System.out.println("Generated "+glyphs.size+" glyphs");

        for (CharacterData glyph : glyphs) {
            if (glyph.pixmap.getHeight() != 0 && glyph.pixmap.getWidth() != 0) {
                glyph.packed = packer.pack(glyph.pixmap);
                glyph.page = packer.getPages().size - 1;
            }
        }

        final int topToBaseline = lineHeight + descender;

        final StringBuilder fnt = new StringBuilder();
        fnt.append("info face=\"").append(parameter.fontName).append("\" size=").append(parameter.size)
                .append(" bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=1 aa=1 padding=1,1,1,1 spacing=1,1 outline=0");
        fnt.append('\n');
        fnt.append("common lineHeight=").append(lineHeight).append(" base=").append(topToBaseline).append(" scaleW=")
                .append(packer.getPageWidth()).append(" scaleH=").append(packer.getPageHeight()).append(" pages=").append(packer.getPages().size)
                .append(" packed=0 alphaChnl=0 redChnl=4 greenChnl=4 blueChnl=4");
        fnt.append('\n');

        {
            int pageNo = 0;
            final boolean addPageNumbers = packer.getPages().size > 1;
            for (PixmapPacker.Page page : packer.getPages()) {
                final FileHandle file = outputFolder.child(parameter.fontName + (addPageNumbers ? "_"+pageNo:"") + ".png");
                resultFiles.add(file);
                PixmapIO.writePNG(file, page.getPixmap());
                fnt.append("page id=").append(pageNo).append(" file=\"").append(file.name()).append("\"");
                fnt.append('\n');
                pageNo++;
            }
        }

        for (CharacterData glyph : glyphs) {
            fnt.append("char");
            fnt.append(" id=").append(glyph.codePoint);
            if (glyph.packed == null) {
                fnt.append(" x=0 y=0 width=0 height=0");
            } else {
                fnt.append(" x=").append(MathUtils.round(glyph.packed.x));
                fnt.append(" y=").append(MathUtils.round(glyph.packed.y));
                fnt.append(" width=").append(MathUtils.round(glyph.packed.width));
                fnt.append(" height=").append(MathUtils.round(glyph.packed.height));
            }
            fnt.append(" xoffset=").append(glyph.offsetX);
            fnt.append(" yoffset=").append(topToBaseline - glyph.offsetY);
            fnt.append(" xadvance=").append(glyph.advanceX);
            fnt.append(" page=").append(glyph.page);
            fnt.append(" chnl=15");

            fnt.append('\n');
        }

        // Generate kerning.
        if (parameter.kerning) {
            fnt.append("kernings \n");//No count, not needed

            if(face.hasKerning()) {
                final int glyphsSize = glyphs.size;

                for (int i = 0; i < glyphsSize; i++) {
                    final CharacterData first = glyphs.get(i);

                    for (int ii = i; ii < glyphsSize; ii++) {
                        final CharacterData second = glyphs.get(ii);

                        int kerning = FreeType.toInt(face.getKerning(first.glyphIndex, second.glyphIndex, 0));
                        if (kerning != 0) {
                            fnt.append("kerning first=").append(first.codePoint).append(" second=").append(second.codePoint).append(" amount=").append(kerning).append('\n');
                        }

                        kerning = FreeType.toInt(face.getKerning(second.glyphIndex, first.glyphIndex, 0));
                        if (kerning != 0) {
                            fnt.append("kerning first=").append(second.codePoint).append(" second=").append(first.codePoint).append(" amount=").append(kerning).append('\n');
                        }
                    }
                }
            } else {
                //Try other means

                //First create mapping glyph -> char
                final IntIntMap glyphToCodePoint = new IntIntMap();
                for (CharacterData glyph : glyphs) {
                    glyphToCodePoint.put(glyph.glyphIndex, glyph.codePoint);
                }

                try {
                    Kerning kerning = new Kerning();
                    kerning.load(fontFile.read(), parameter.size);

                    final int pairCount = kerning.resultAmount.size;

                    final int[] first = kerning.resultFirst.items;
                    final int[] second = kerning.resultSecond.items;
                    final int[] amount = kerning.resultAmount.items;

                    for (int i = 0; i < pairCount; i++) {
                        final int firstChar = glyphToCodePoint.get(first[i], -1);
                        final int secondChar = glyphToCodePoint.get(second[i], -1);
                        if(firstChar == -1 || secondChar == -1) {
                            continue;
                        }
                        fnt.append("kerning first=").append(firstChar).append(" second=").append(secondChar).append(" amount=").append(amount[i]).append('\n');
                    }
                } catch (Exception e) {
                    throw new GdxRuntimeException("Failed to load kerning from advanced format", e);
                }
            }
        }

        final FileHandle fntFile = outputFolder.child(parameter.fontName + ".fnt");
        fntFile.writeString(fnt.toString(), false, "UTF-8");
        resultFiles.add(fntFile);

        if (stroker != null) stroker.dispose();
        return resultFiles;
    }

    private void createGlyph(int codePoint, FreeTypeFontParameter parameter, FreeType.Stroker stroker, Array<CharacterData> glyphs) {
        final int glyphIndex = face.getCharIndex(codePoint);
        if(glyphIndex == 0 && codePoint != 0) return;
        final CharacterData data = createGlyph(codePoint, glyphIndex, parameter, stroker);
        if(data != null) {
            glyphs.add(data);
        }
    }

    /** @return null if glyph was not found. */
    CharacterData createGlyph (int codePoint, int glyphIndex, FreeTypeFontParameter parameter, FreeType.Stroker stroker) {
        if (!face.loadGlyph(glyphIndex, parameter.loadingFlags)) return null;

        FreeType.GlyphSlot slot = face.getGlyph();
        FreeType.Glyph mainGlyph = slot.getGlyph();
        try {
            mainGlyph.toBitmap(parameter.mono ? FreeType.FT_RENDER_MODE_MONO : FreeType.FT_RENDER_MODE_NORMAL);
        } catch (GdxRuntimeException e) {
            mainGlyph.dispose();
            Gdx.app.log("FreeTypeFontGenerator", "Couldn't render char: " + codePoint);
            return null;
        }
        FreeType.Bitmap mainBitmap = mainGlyph.getBitmap();
        Pixmap mainPixmap = mainBitmap.getPixmap(Pixmap.Format.RGBA8888, parameter.color, parameter.gamma);

        if (mainBitmap.getWidth() != 0 && mainBitmap.getRows() != 0) {
            int offsetX, offsetY;
            if (parameter.borderWidth > 0) {
                // execute stroker; this generates a glyph "extended" along the outline
                int top = mainGlyph.getTop(), left = mainGlyph.getLeft();
                FreeType.Glyph borderGlyph = slot.getGlyph();
                borderGlyph.strokeBorder(stroker, false);
                borderGlyph.toBitmap(parameter.mono ? FreeType.FT_RENDER_MODE_MONO : FreeType.FT_RENDER_MODE_NORMAL);
                offsetX = left - borderGlyph.getLeft();
                offsetY = -(top - borderGlyph.getTop());

                // Render border (pixmap is bigger than main).
                FreeType.Bitmap borderBitmap = borderGlyph.getBitmap();
                Pixmap borderPixmap = borderBitmap.getPixmap(Pixmap.Format.RGBA8888, parameter.borderColor, parameter.borderGamma);

                // Draw main glyph on top of border.
                for (int i = 0, n = parameter.renderCount; i < n; i++)
                    borderPixmap.drawPixmap(mainPixmap, offsetX, offsetY);

                mainPixmap.dispose();
                mainGlyph.dispose();
                mainPixmap = borderPixmap;
                mainGlyph = borderGlyph;
            }

            if (parameter.shadowOffsetX != 0 || parameter.shadowOffsetY != 0) {
                int mainW = mainPixmap.getWidth(), mainH = mainPixmap.getHeight();
                int shadowOffsetX = Math.max(parameter.shadowOffsetX, 0), shadowOffsetY = Math.max(parameter.shadowOffsetY, 0);
                int shadowW = mainW + Math.abs(parameter.shadowOffsetX), shadowH = mainH + Math.abs(parameter.shadowOffsetY);
                Pixmap shadowPixmap = new Pixmap(shadowW, shadowH, mainPixmap.getFormat());

                Color shadowColor = parameter.shadowColor;
                byte r = (byte)(shadowColor.r * 255), g = (byte)(shadowColor.g * 255), b = (byte)(shadowColor.b * 255);
                float a = shadowColor.a;

                ByteBuffer mainPixels = mainPixmap.getPixels();
                ByteBuffer shadowPixels = shadowPixmap.getPixels();
                for (int y = 0; y < mainH; y++) {
                    int shadowRow = shadowW * (y + shadowOffsetY) + shadowOffsetX;
                    for (int x = 0; x < mainW; x++) {
                        int mainPixel = (mainW * y + x) * 4;
                        byte mainA = mainPixels.get(mainPixel + 3);
                        if (mainA == 0) continue;
                        int shadowPixel = (shadowRow + x) * 4;
                        shadowPixels.put(shadowPixel, r);
                        shadowPixels.put(shadowPixel + 1, g);
                        shadowPixels.put(shadowPixel + 2, b);
                        shadowPixels.put(shadowPixel + 3, (byte)((mainA & 0xff) * a));
                    }
                }

                // Draw main glyph (with any border) on top of shadow.
                for (int i = 0, n = parameter.renderCount; i < n; i++)
                    shadowPixmap.drawPixmap(mainPixmap, Math.max(-parameter.shadowOffsetX, 0), Math.max(-parameter.shadowOffsetY, 0));
                mainPixmap.dispose();
                mainPixmap = shadowPixmap;
            } else if (parameter.borderWidth == 0) {
                // No shadow and no border, draw glyph additional times.
                for (int i = 0, n = parameter.renderCount - 1; i < n; i++)
                    mainPixmap.drawPixmap(mainPixmap, 0, 0);
            }
        }

        final CharacterData data = new CharacterData(glyphIndex, codePoint);
        data.pixmap = mainPixmap;
        data.advanceX = FreeType.toInt(slot.getAdvanceX());
        data.offsetX = mainGlyph.getLeft();
        data.offsetY = mainGlyph.getTop();

        mainGlyph.dispose();

        return data;
    }

    public void dispose(){
        face.dispose();
        library.dispose();
    }

    public static class FreeTypeFontParameter {
        /** Font name, used for output */
        public String fontName;
        /** The size in pixels */
        public int size = 16;
        /** Code-points to include, by default null which means to add all present. */
        public IntSet codePoints = null;
        /** If true, font smoothing is disabled. */
        public boolean mono;
        /** Loading flags used for {@link com.badlogic.gdx.graphics.g2d.freetype.FreeType.Face#loadGlyph(int, int)}. */
        public int loadingFlags = FreeType.FT_LOAD_DEFAULT;
        /** Foreground color (required for non-black borders) */
        public Color color = Color.WHITE;
        /** Glyph gamma. Values > 1 reduce antialiasing. */
        public float gamma = 1.8f;
        /** Number of times to render the glyph. Useful with a shadow or border, so it doesn't show through the glyph. */
        public int renderCount = 2;
        /** Border width in pixels, 0 to disable */
        public float borderWidth = 0;
        /** Border color; only used if borderWidth > 0 */
        public Color borderColor = Color.BLACK;
        /** true for straight (mitered), false for rounded borders */
        public boolean borderStraight = false;
        /** Values < 1 increase the border size. */
        public float borderGamma = 1.8f;
        /** Offset of text shadow on X axis in pixels, 0 to disable */
        public int shadowOffsetX = 0;
        /** Offset of text shadow on Y axis in pixels, 0 to disable */
        public int shadowOffsetY = 0;
        /** Shadow color; only used if shadowOffset > 0 */
        public Color shadowColor = new Color(0, 0, 0, 0.75f);
        /** Whether the font should include kerning */
        public boolean kerning = true;
    }

    public static File[] pack(File fontFile, File outputDirectory, FreeTypeFontParameter parameter) {
        final FreeTypePacker packer = new FreeTypePacker(new FileHandle(fontFile));
        final Array<FileHandle> generatedFiles = packer.generate(parameter, new FileHandle(outputDirectory));
        packer.dispose();
        File[] result = new File[generatedFiles.size];
        for (int i = 0; i < generatedFiles.size; i++) {
            result[i] = generatedFiles.get(i).file();
        }
        return result;
    }
}
