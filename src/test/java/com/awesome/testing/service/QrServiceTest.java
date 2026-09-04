package com.awesome.testing.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class QrServiceTest {

    @Test
    void shouldGenerateQrCode() throws Exception {
        String qrCodeText = "https://www.awesome-testing.com";

        BufferedImage qrCodeImage = new QrService().generateQrCode(qrCodeText);

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(qrCodeImage)));
        String textReadByJava = new MultiFormatReader().decode(bitmap).getText();
        assertThat(textReadByJava).isEqualTo(qrCodeText);
    }

}
