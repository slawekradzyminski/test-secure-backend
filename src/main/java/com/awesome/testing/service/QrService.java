package com.awesome.testing.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * Renders QR codes as 400x400 PNG images. The PNG is written directly from ZXing's bit matrix
 * (1-bit greyscale), so no AWT or ImageIO is involved at runtime, which also keeps the
 * GraalVM native image free of the AWT native libraries.
 */
@Service
public class QrService {

    static final int SIZE = 400;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    public byte[] generateQrPng(String text) {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, SIZE, SIZE, hints(text));
        } catch (WriterException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Data too big")) {
                throw new QrTextTooLongException(ex);
            }
            throw new IllegalStateException("Unable to create QR code", ex);
        }
        try {
            return toPng(matrix);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create QR code", ex);
        }
    }

    /**
     * ZXing encodes byte-mode text as ISO-8859-1 unless told otherwise, which
     * turns Polish letters and emoji into '?'. Non-ASCII text is encoded as
     * UTF-8 with an ECI header, so scanners decode exactly the submitted text;
     * ASCII text keeps the header-less encoding (and the same image) as before.
     */
    static Map<EncodeHintType, Object> hints(String text) {
        if (StandardCharsets.US_ASCII.newEncoder().canEncode(text)) {
            return Map.of();
        }
        return Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
    }

    static byte[] toPng(BitMatrix matrix) throws IOException {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(PNG_SIGNATURE);

        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream headerData = new DataOutputStream(header);
        headerData.writeInt(width);
        headerData.writeInt(height);
        headerData.writeByte(1); // bit depth
        headerData.writeByte(0); // colour type: greyscale
        headerData.writeByte(0); // compression: deflate
        headerData.writeByte(0); // filter method
        headerData.writeByte(0); // no interlace
        writeChunk(png, "IHDR", header.toByteArray());

        int rowBytes = (width + 7) / 8;
        ByteArrayOutputStream pixels = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(pixels)) {
            byte[] row = new byte[rowBytes + 1]; // leading filter byte 0 (none)
            for (int y = 0; y < height; y++) {
                java.util.Arrays.fill(row, (byte) 0);
                for (int x = 0; x < width; x++) {
                    if (!matrix.get(x, y)) { // white pixel = 1, dark module = 0
                        row[1 + x / 8] |= (byte) (0x80 >>> (x % 8));
                    }
                }
                deflater.write(row);
            }
        }
        writeChunk(png, "IDAT", pixels.toByteArray());
        writeChunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream png, String type, byte[] data) throws IOException {
        DataOutputStream out = new DataOutputStream(png);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(data.length);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeInt((int) crc.getValue());
    }
}
