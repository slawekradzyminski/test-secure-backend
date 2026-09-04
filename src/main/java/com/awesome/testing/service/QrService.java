package com.awesome.testing.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;

@Service
public class QrService {

    @SneakyThrows
    public BufferedImage generateQrCode(String text) {
        BitMatrix bitMatrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 400, 400);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }
}
