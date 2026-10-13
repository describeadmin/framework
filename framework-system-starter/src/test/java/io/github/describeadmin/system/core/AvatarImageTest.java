package io.github.describeadmin.system.core;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AvatarImage} 的格式识别与校验。
 *
 * <p>读取端点免登录，所以上传这一关是唯一能挡住"伪装成图片的脚本"的地方。
 */
@DisplayName("AvatarImage 头像格式校验")
class AvatarImageTest {

    @Test
    @DisplayName("PNG / JPEG 按魔数识别，扩展名与 Content-Type 由识别结果决定")
    void recognisesPngAndJpeg() throws IOException {
        assertThat(AvatarImage.inspect(encode("png", 64, 64))).isEqualTo(AvatarImage.Format.PNG);
        assertThat(AvatarImage.inspect(encode("jpg", 64, 64))).isEqualTo(AvatarImage.Format.JPEG);
        assertThat(AvatarImage.Format.JPEG.extension()).isEqualTo("jpg");
        assertThat(AvatarImage.Format.JPEG.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("WebP 只校验 RIFF....WEBP 魔数")
    void recognisesWebpByMagic() {
        byte[] webp = new byte[32];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 4);
        assertThat(AvatarImage.inspect(webp)).isEqualTo(AvatarImage.Format.WEBP);
    }

    @Test
    @DisplayName("SVG（可携带脚本）一律拒收，无论声称的类型是什么")
    void rejectsSvg() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        assertBadRequest(svg, "PNG、JPEG、WebP");
    }

    @Test
    @DisplayName("HTML 文件改个 .png 扩展名也进不来——只认魔数")
    void rejectsRenamedNonImage() {
        assertBadRequest("<html><body>hi</body></html>".getBytes(StandardCharsets.UTF_8), "PNG、JPEG、WebP");
        assertBadRequest(new byte[0], "PNG、JPEG、WebP");
    }

    @Test
    @DisplayName("魔数对、文件头被截断：按损坏文件拒收")
    void rejectsTruncatedPng() throws IOException {
        byte[] png = encode("png", 64, 64);
        assertBadRequest(Arrays.copyOf(png, 12), "已损坏");
    }

    @Test
    @DisplayName("宽或高超过上限即拒收，只读文件头、不解码像素")
    void rejectsOversizedDimensions() throws IOException {
        // 1bit 位图：5000×8 的 PNG 只有几十字节，不会把测试撑大
        BufferedImage img = new BufferedImage(AvatarImage.MAX_DIMENSION + 1, 8, BufferedImage.TYPE_BYTE_BINARY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        assertBadRequest(out.toByteArray(), "尺寸过大");
    }

    private static void assertBadRequest(byte[] bytes, String messagePart) {
        assertThatThrownBy(() -> AvatarImage.inspect(bytes))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(messagePart)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }

    private static byte[] encode(String format, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(img, format, out)).as("JDK 应自带 %s 编码器", format).isTrue();
        return out.toByteArray();
    }
}
