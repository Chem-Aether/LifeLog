package com.lifelog.auth.captcha;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 生成随机图形验证码及对应的 JPEG 图片。 */
public class CaptchaUtil {
    //  配置验证码图片
    private static final int WIDTH = 100;
    private static final int HEIGHT = 40;
    private static final int FONT_SIZE = 30;

    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Random random = new Random();

    /**
     * 创建随机验证码，并绘制包含干扰线的 JPEG 图片。
     * @return 验证码文本及图片字节数据
     */
    public static Captcha generateCaptcha() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 设置背景颜色
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 绘制干扰线
        for (int i = 0; i < 5; i++) {
            g.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }

        // 生成随机验证码
        StringBuilder captchaText = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            char c = CHARACTERS.charAt(random.nextInt(CHARACTERS.length()));
            captchaText.append(c);
            g.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            g.setFont(new Font("Arial", Font.BOLD, FONT_SIZE));
            g.drawString(String.valueOf(c), 20 * i + 10, 30);
        }

        g.dispose();

        // 将图片转换为字节数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "JPEG", baos);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate captcha image", e);
        }

        return new Captcha(captchaText.toString(), baos.toByteArray());
    }

    /** 封装验证码文本和对应的图片数据。 */
    @Getter
    @AllArgsConstructor
    public static class Captcha {
        private final String text;
        private final byte[] imageBytes;
    }

}
