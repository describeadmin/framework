package io.github.describeadmin.system.core;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

/**
 * 头像图片的格式识别与校验。
 *
 * <p><b>只认魔数，不信 {@code Content-Type} 和文件名</b>：这两样都由客户端说了算。
 * 存储用的扩展名也由这里根据魔数决定，读取端点再按扩展名给出 {@code Content-Type}，
 * 所以"声称是 PNG、实际是 HTML"这种文件根本进不来。
 *
 * <p><b>不收 SVG</b>：SVG 是能带脚本的 XML，而读取端点是免登录的，收了就是存储型 XSS。
 *
 * <p>PNG/JPEG 只读文件头里的宽高，不解码像素：拒绝超大尺寸的图（解压炸弹会拖垮每个显示头像的浏览器），
 * 又不必在服务端把一张 4096×4096 的图解码成 64MB 的位图。JDK 自带的 ImageIO 不认 WebP，
 * 所以 WebP 只校验魔数——服务端从不解码它，尺寸也就无关服务端安全。
 */
public final class AvatarImage {

    /** 宽或高的上限。头像显示尺寸最多一两百像素，这个上限只是用来拦截明显异常的文件。 */
    public static final int MAX_DIMENSION = 4096;

    private AvatarImage() {
    }

    /** 支持的格式。{@link #extension} 会写进对象键，读取端点再靠它反推 {@link #contentType}。 */
    public enum Format {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg"),
        WEBP("webp", "image/webp");

        private final String extension;
        private final String contentType;

        Format(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String extension() {
            return extension;
        }

        public String contentType() {
            return contentType;
        }

        public static Optional<Format> ofExtension(String extension) {
            for (Format f : values()) {
                if (f.extension.equals(extension)) {
                    return Optional.of(f);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * 识别并校验图片，不合格时抛 {@code BizException(BAD_REQUEST)}。
     *
     * @return 按魔数识别出的真实格式
     */
    public static Format inspect(byte[] bytes) {
        Format format = sniff(bytes).orElseThrow(() ->
                new BizException(ResultCode.BAD_REQUEST, "头像只支持 PNG、JPEG、WebP 格式"));
        if (format != Format.WEBP) {
            checkDimensions(bytes, format);
        }
        return format;
    }

    /** 按魔数识别格式，识别不出返回空。 */
    static Optional<Format> sniff(byte[] b) {
        if (b == null) {
            return Optional.empty();
        }
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(Format.PNG);
        }
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(Format.JPEG);
        }
        if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(Format.WEBP);
        }
        return Optional.empty();
    }

    private static void checkDimensions(byte[] bytes, Format format) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null
                    ? Collections.emptyIterator()
                    : ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw invalid();
                }
                if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "头像尺寸过大（" + width + "×" + height + "），宽高均不能超过 " + MAX_DIMENSION);
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BizException biz) {
                throw biz;
            }
            // 魔数对、文件头却读不出来：截断或伪造的文件
            throw invalid();
        }
    }

    private static BizException invalid() {
        return new BizException(ResultCode.BAD_REQUEST, "头像文件已损坏或不是有效的图片");
    }

    private static boolean startsWith(byte[] b, int offset, int... expected) {
        if (b.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((b[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
