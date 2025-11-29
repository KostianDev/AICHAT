package aichat.model;

public record ColorPoint(double c1, double c2, double c3) {
    
    public static ColorPoint fromRGB(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return new ColorPoint(r, g, b);
    }
    
    public int toRGB() {
        int r = clamp((int) Math.round(c1), 0, 255);
        int g = clamp((int) Math.round(c2), 0, 255);
        int b = clamp((int) Math.round(c3), 0, 255);
        return (r << 16) | (g << 8) | b;
    }
    
    public double distanceTo(ColorPoint other) {
        double dc1 = this.c1 - other.c1;
        double dc2 = this.c2 - other.c2;
        double dc3 = this.c3 - other.c3;
        return Math.sqrt(dc1 * dc1 + dc2 * dc2 + dc3 * dc3);
    }
    
    public double distanceSquaredTo(ColorPoint other) {
        double dc1 = this.c1 - other.c1;
        double dc2 = this.c2 - other.c2;
        double dc3 = this.c3 - other.c3;
        return dc1 * dc1 + dc2 * dc2 + dc3 * dc3;
    }
    
    public ColorPoint add(ColorPoint other) {
        return new ColorPoint(c1 + other.c1, c2 + other.c2, c3 + other.c3);
    }
    
    public ColorPoint divide(double scalar) {
        return new ColorPoint(c1 / scalar, c2 / scalar, c3 / scalar);
    }
    
    public String toHexString() {
        return String.format("#%02X%02X%02X", 
            clamp((int) Math.round(c1), 0, 255),
            clamp((int) Math.round(c2), 0, 255),
            clamp((int) Math.round(c3), 0, 255));
    }
    
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    @Override
    public String toString() {
        return String.format("ColorPoint[%.2f, %.2f, %.2f]", c1, c2, c3);
    }
}
