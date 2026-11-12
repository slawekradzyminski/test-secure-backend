package com.awesome.testing.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void shouldWriteWellFormedChunksWithValidChecksums() {
        byte[] png = new QrService().generateQrPng("chunk check");

        List<String> chunkTypes = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(png, 8, png.length - 8);
        while (buffer.hasRemaining()) {
            int length = buffer.getInt();
            byte[] typeAndData = new byte[4 + length];
            buffer.get(typeAndData);
            CRC32 crc = new CRC32();
            crc.update(typeAndData);
            String type = new String(typeAndData, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
            assertThat(buffer.getInt()).as("CRC of " + type).isEqualTo((int) crc.getValue());
            chunkTypes.add(type);
        }
        assertThat(chunkTypes).containsExactly("IHDR", "IDAT", "IEND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Zażółć gęślą jaźń", "Zażółć 😀", "Ünïcödé ✓ 東京", "ó"})
    void shouldDecodeNonAsciiTextExactly(String text) throws Exception {
        assertThat(decode(new QrService().generateQrPng(text))).isEqualTo(text);
    }

    @Test
    void shouldKeepTheHeaderlessEncodingForAsciiText() {
        assertThat(QrService.hints("https://awesome-testing.com")).isEmpty();
        assertThat(QrService.hints("Zażółć")).containsEntry(EncodeHintType.CHARACTER_SET, "UTF-8");
    }

    @Test
    void shouldEncodeTheLargestAsciiTextAndRejectOneMoreByte() throws Exception {
        String largest = "a".repeat(2953);

        assertThat(decode(new QrService().generateQrPng(largest))).isEqualTo(largest);
        assertThatThrownBy(() -> new QrService().generateQrPng(largest + "a"))
                .isInstanceOf(QrTextTooLongException.class)
                .hasMessage("Text is too long for a QR code");
    }

    @Test
    void shouldCountTheCapacityInUtf8Bytes() {
        // 1500 two-byte letters are 3000 bytes: fewer characters than the ASCII limit, yet too big.
        assertThatThrownBy(() -> new QrService().generateQrPng("ż".repeat(1500)))
                .isInstanceOf(QrTextTooLongException.class);
    }

    private static String decode(byte[] png) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        return new MultiFormatReader().decode(bitmap, Map.of(DecodeHintType.PURE_BARCODE, true)).getText();
    }
}
