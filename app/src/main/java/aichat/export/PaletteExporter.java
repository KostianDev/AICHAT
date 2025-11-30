package aichat.export;

import aichat.core.ImageHarmonyEngine.ColorModel;
import aichat.model.ColorPalette;
import aichat.model.ColorPoint;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;

public class PaletteExporter {
    
    private static final int SQUARE_SIZE = 100;
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    
    public enum ExportFormat {
        OPTIMAL,
        PNG_IMAGE
    }
    
    public static File exportPalette(ColorPalette palette, ColorModel colorModel, 
                                     ExportFormat format, File directory) throws IOException {
        if (palette == null || palette.size() == 0) {
            throw new IllegalArgumentException("Palette is empty");
        }
        
        return switch (format) {
            case OPTIMAL -> exportOptimal(palette, colorModel, directory);
            case PNG_IMAGE -> exportPng(palette, directory);
        };
    }
    
    private static File exportOptimal(ColorPalette palette, ColorModel colorModel, 
                                      File directory) throws IOException {
        return switch (colorModel) {
            case RGB -> exportGpl(palette, directory);
            case CIELAB -> exportLabCsv(palette, directory);
        };
    }
    
    private static File exportGpl(ColorPalette palette, File directory) throws IOException {
        String filename = "palette-" + generateRandomString(10) + ".gpl";
        File file = new File(directory, filename);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("GIMP Palette\n");
            writer.write("Name: AICHAT Palette\n");
            writer.write("Columns: " + Math.min(palette.size(), 16) + "\n");
            writer.write("#\n");
            
            ColorPalette sorted = palette.sortByLuminance();
            
            for (int i = 0; i < sorted.size(); i++) {
                ColorPoint color = sorted.getColor(i);
                int r = clamp((int) Math.round(color.c1()));
                int g = clamp((int) Math.round(color.c2()));
                int b = clamp((int) Math.round(color.c3()));
                
                writer.write(String.format("%3d %3d %3d\tColor %d\n", r, g, b, i + 1));
            }
        }
        
        return file;
    }
    
    private static File exportLabCsv(ColorPalette palette, File directory) throws IOException {
        String filename = "palette-" + generateRandomString(10) + ".csv";
        File file = new File(directory, filename);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("Index,L,a,b,Hex\n");
            
            ColorPalette sorted = palette.sortByLuminance();
            
            for (int i = 0; i < sorted.size(); i++) {
                ColorPoint color = sorted.getColor(i);
                String hex = color.toHexString();
                
                writer.write(String.format("%d,%.2f,%.2f,%.2f,%s\n", 
                    i + 1, color.c1(), color.c2(), color.c3(), hex));
            }
        }
        
        return file;
    }
    
    private static File exportPng(ColorPalette palette, File directory) throws IOException {
        String filename = "palette-" + generateRandomString(10) + ".png";
        File file = new File(directory, filename);
        
        ColorPalette sorted = palette.sortByLuminance();
        int n = sorted.size();
        
        int cols = calculateOptimalColumns(n);
        int rows = (n + cols - 1) / cols;
        
        int width = cols * SQUARE_SIZE;
        int height = rows * SQUARE_SIZE;
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        g.setColor(new Color(30, 30, 30));
        g.fillRect(0, 0, width, height);
        
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        Font font = new Font("Monospaced", Font.PLAIN, 11);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        
        for (int i = 0; i < n; i++) {
            ColorPoint color = sorted.getColor(i);
            
            int col = i % cols;
            int row = i / cols;
            int x = col * SQUARE_SIZE;
            int y = row * SQUARE_SIZE;
            
            int r = clamp((int) Math.round(color.c1()));
            int gr = clamp((int) Math.round(color.c2()));
            int b = clamp((int) Math.round(color.c3()));
            
            g.setColor(new Color(r, gr, b));
            g.fillRect(x + 2, y + 2, SQUARE_SIZE - 4, SQUARE_SIZE - 4);
            
            g.setColor(new Color(60, 60, 60));
            g.drawRect(x + 2, y + 2, SQUARE_SIZE - 4, SQUARE_SIZE - 4);
            
            String hex = color.toHexString();
            double luminance = 0.299 * r + 0.587 * gr + 0.114 * b;
            g.setColor(luminance > 128 ? Color.BLACK : Color.WHITE);
            
            int textWidth = fm.stringWidth(hex);
            int textX = x + (SQUARE_SIZE - textWidth) / 2;
            int textY = y + SQUARE_SIZE - 8;
            
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRoundRect(textX - 4, textY - fm.getAscent() - 2, textWidth + 8, fm.getHeight() + 4, 4, 4);
            
            g.setColor(luminance > 128 ? new Color(30, 30, 30) : new Color(240, 240, 240));
            g.drawString(hex, textX, textY);
        }
        
        g.dispose();
        ImageIO.write(image, "PNG", file);
        
        return file;
    }
    
    private static int calculateOptimalColumns(int n) {
        if (n <= 4) return n;
        if (n <= 8) return 4;
        if (n <= 16) return 4;
        if (n <= 25) return 5;
        if (n <= 36) return 6;
        if (n <= 64) return 8;
        if (n <= 100) return 10;
        if (n <= 144) return 12;
        
        return (int) Math.ceil(Math.sqrt(n));
    }
    
    private static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
    
    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
    
    public static String getOptimalFormatDescription(ColorModel colorModel) {
        return switch (colorModel) {
            case RGB -> "GIMP Palette (.gpl)";
            case CIELAB -> "CSV with L*a*b* values (.csv)";
        };
    }
    
    public static String getOptimalExtension(ColorModel colorModel) {
        return switch (colorModel) {
            case RGB -> "gpl";
            case CIELAB -> "csv";
        };
    }
}
