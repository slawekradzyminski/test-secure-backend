package com.awesome.testing.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class QrServiceTest {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    @Test
    void shouldGenerateDecodableQrPng() throws Exception {
        String qrCodeText = "https://www.awesome-testing.com";

        byte[] png = new QrService().generateQrPng(qrCodeText);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        assertThat(new MultiFormatReader().decode(bitmap).getText()).isEqualTo(qrCodeText);
    }

    @Test
    void shouldWriteA400By400PngHeader() {
        byte[] png = new QrService().generateQrPng("header check");

        assertThat(Arrays.copyOf(png, 8)).isEqualTo(PNG_SIGNATURE);
        assertThat(ByteBuffer.wrap(png, 16, 8).getInt()).isEqualTo(400);
        assertThat(ByteBuffer.wrap(png, 20, 4).getInt()).isEqualTo(400);
    }
}
