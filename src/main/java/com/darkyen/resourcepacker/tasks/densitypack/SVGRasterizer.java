package com.darkyen.resourcepacker.tasks.densitypack;

import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.StreamUtils;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.TranscodingHints;
import org.apache.batik.transcoder.image.ImageTranscoder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;

/**
 *
 */
public class SVGRasterizer {

    public static BufferedImage rasterize(File svgFile, int fromScale, int toScale, ObjectMap<TranscodingHints.Key, Object> hintsOverride) {
        FileInputStream inputStream = null;
        final float scale = (float) toScale / (float) fromScale;
        try {
            final SimpleTranscoder transcoder = new SimpleTranscoder(scale);
            transcoder.addTranscodingHint(ImageTranscoder.KEY_BACKGROUND_COLOR, new Color(0, 0, 0, 0));
            if (hintsOverride != null) {
                for (ObjectMap.Entry<TranscodingHints.Key, Object> entry : hintsOverride) {
                    transcoder.addTranscodingHint(entry.key, entry.value);
                }
            }

            inputStream = new FileInputStream(svgFile);
            final TranscoderInput in = new TranscoderInput(inputStream);
            transcoder.transcode(in, null);
            return transcoder.result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to rasterize file " + svgFile + " at scale " + scale, e);
        } finally {
            StreamUtils.closeQuietly(inputStream);
        }
    }

    private static final class SimpleTranscoder extends ImageTranscoder {

        private final float scale;
        public BufferedImage result = null;

        private SimpleTranscoder(float scale) {
            this.scale = scale;
        }

        @Override
        public BufferedImage createImage(int width, int height) {
            return new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        }

        @Override
        public void writeImage(BufferedImage img, TranscoderOutput output) throws TranscoderException {
            this.result = img;
        }

        @Override
        protected void setImageSize(float docWidth, float docHeight) {
            super.setImageSize(docWidth, docHeight);
            width *= scale;
            height *= scale;
        }
    }
}
