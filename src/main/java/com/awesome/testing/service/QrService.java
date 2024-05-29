package com.awesome.testing.service;

import com.awesome.testing.qr.QrGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;

@Service
@RequiredArgsConstructor
public class QrService {

    public BufferedImage generateQrCode(String text) {
        return QrGenerator.generateQRCodeImage(text);
    }
}
