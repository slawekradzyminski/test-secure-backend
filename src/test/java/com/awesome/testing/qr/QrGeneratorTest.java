package com.awesome.testing.qr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.File;

import static com.awesome.testing.qr.QrGenerator.*;
import static org.assertj.core.api.Assertions.assertThat;

public class QrGeneratorTest {

    @TempDir
    private File tempDir;

    @Test
    public void shouldGenerateQrCode() {
        // given
        String qrCodeText = "https://www.awesome-testing.com";
        File qrCodeFile = new File(tempDir, "qr.png");

        // when
        BufferedImage qrCodeImage = generateQRCodeImage(qrCodeText);
        saveImage(qrCodeImage, qrCodeFile);
        String textReadByJava = readQRCode(qrCodeFile);

        //
        assertThat(textReadByJava).isEqualTo(qrCodeText);
    }

}
