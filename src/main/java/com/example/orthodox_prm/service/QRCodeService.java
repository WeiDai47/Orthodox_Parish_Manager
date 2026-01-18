package com.example.orthodox_prm.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

@Service
public class QRCodeService {

    /**
     * Generate a QR code from the given URL and return it as a Base64-encoded PNG
     * @param url The URL to encode in the QR code
     * @param width The width of the QR code image
     * @param height The height of the QR code image
     * @return Base64-encoded PNG image string with data URI prefix
     * @throws WriterException if QR code generation fails
     */
    public String generateQRCodeBase64(String url, int width, int height) throws WriterException {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, width, height);
            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            // Convert BufferedImage to Base64-encoded PNG
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "PNG", outputStream);
            byte[] imageBytes = outputStream.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // Return with data URI prefix for direct use in HTML <img> tags
            return "data:image/png;base64," + base64Image;
        } catch (Exception e) {
            throw new WriterException("Failed to generate QR code: " + e.getMessage());
        }
    }

    /**
     * Generate a QR code with default size (300x300)
     */
    public String generateQRCodeBase64(String url) throws WriterException {
        return generateQRCodeBase64(url, 300, 300);
    }
}
