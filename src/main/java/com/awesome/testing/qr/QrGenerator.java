package com.awesome.testing.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Component
public class QrGenerator {

    @SneakyThrows
    public static BufferedImage generateQRCodeImage(String barcodeText) {
        QRCodeWriter barcodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix =
                barcodeWriter.encode(barcodeText, BarcodeFormat.QR_CODE, 400, 400);

        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }

    @SneakyThrows
    public static void saveImage(BufferedImage bufferedImage, File qrCodeFile) {
        ImageIO.write(bufferedImage, "png", qrCodeFile);
    }

    @SneakyThrows
    public static String readQRCode(File file) {
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(ImageIO.read(file))));
        return new MultiFormatReader().decode(binaryBitmap).getText();
    }

}
