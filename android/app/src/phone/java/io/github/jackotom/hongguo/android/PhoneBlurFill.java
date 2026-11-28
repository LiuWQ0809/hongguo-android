package io.github.jackotom.hongguo.android;

import android.content.Context;
import android.opengl.GLES20;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BaseGlShaderProgram;
import androidx.media3.effect.GlEffect;

/** Preserve the whole foreground and blur a cover-scaled copy into the unused area. */
@UnstableApi
final class PhoneBlurFill implements GlEffect {
    private static final String VERTEX = """
        attribute vec4 aPosition;
        varying vec2 vUV;
        void main() {
            gl_Position = aPosition;
            vUV = aPosition.xy * 0.5 + 0.5;
        }
        """;
    private static final String FRAGMENT = """
        precision mediump float;
        uniform sampler2D uVideo;
        uniform vec2 uFit;
        uniform vec2 uCover;
        varying vec2 vUV;
        void main() {
            vec2 foreground = (vUV - 0.5) * uFit + 0.5;
            if (foreground.x >= 0.0 && foreground.x <= 1.0 &&
                foreground.y >= 0.0 && foreground.y <= 1.0) {
                gl_FragColor = texture2D(uVideo, foreground);
            } else {
                vec2 center = (vUV - 0.5) * uCover + 0.5;
                vec4 sum = vec4(0.0);
                for (int x = -2; x <= 2; x++) {
                    for (int y = -2; y <= 2; y++) {
                        vec2 at = clamp(center + vec2(float(x), float(y)) * 0.016, 0.0, 1.0);
                        sum += texture2D(uVideo, at);
                    }
                }
                gl_FragColor = vec4(sum.rgb * 0.030, 1.0);
            }
        }
        """;

    private final float aspect;
    PhoneBlurFill(float aspect) { this.aspect = aspect; }

    @Override public BaseGlShaderProgram toGlShaderProgram(Context context, boolean useHdr) throws VideoFrameProcessingException {
        try {
            GlProgram program = new GlProgram(VERTEX, FRAGMENT);
            program.setBufferAttribute("aPosition", GlUtil.getNormalizedCoordinateBounds(), 4);
            return new BaseGlShaderProgram(useHdr, 1) {
                @Override public Size configure(int width, int height) {
                    float inputAspect = (float) width / height;
                    float scale = aspect / inputAspect;
                    program.setFloatsUniform("uFit", new float[] {Math.max(1, scale), Math.max(1, 1 / scale)});
                    program.setFloatsUniform("uCover", new float[] {Math.min(1, scale), Math.min(1, 1 / scale)});
                    int edge = Math.min(1920, Math.max(width, height));
                    return aspect >= 1 ? new Size(edge, Math.max(1, Math.round(edge / aspect)))
                        : new Size(Math.max(1, Math.round(edge * aspect)), edge);
                }

                @Override public void drawFrame(int texture, long timeUs) throws VideoFrameProcessingException {
                    try {
                        program.use();
                        program.setSamplerTexIdUniform("uVideo", texture, 0);
                        program.bindAttributesAndUniforms();
                        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                        GlUtil.checkGlError();
                    } catch (GlUtil.GlException failure) { throw new VideoFrameProcessingException(failure, timeUs); }
                }

                @Override public void release() throws VideoFrameProcessingException {
                    try { super.release(); }
                    finally {
                        try { program.delete(); }
                        catch (GlUtil.GlException failure) { throw new VideoFrameProcessingException(failure); }
                    }
                }
            };
        } catch (GlUtil.GlException failure) {
            throw new VideoFrameProcessingException(failure);
        }
    }
}
