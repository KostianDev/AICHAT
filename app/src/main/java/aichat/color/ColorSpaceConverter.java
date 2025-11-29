package aichat.color;

import aichat.model.ColorPoint;
import aichat.native_.NativeAccelerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for converting between RGB and CIELAB color spaces.
 * CIELAB is perceptually uniform, meaning Euclidean distance in CIELAB space
 * approximates human perception of color difference.
 * 
 * Supports native acceleration via Panama FFI for batch conversions.
 */
public final class ColorSpaceConverter {
    
    // D65 standard illuminant reference values
    private static final double REF_X = 95.047;
    private static final double REF_Y = 100.000;
    private static final double REF_Z = 108.883;
    
    // Threshold for LAB conversion
    private static final double EPSILON = 0.008856;
    private static final double KAPPA = 903.3;
    private static final double DELTA = 6.0 / 29.0;
    
    private static final NativeAccelerator nativeAccelerator = NativeAccelerator.getInstance();
    
    private ColorSpaceConverter() {
        // Utility class - no instantiation
    }
    
    /**
     * Converts a batch of RGB colors to CIELAB.
     * Uses native acceleration when available.
     */
    public static List<ColorPoint> rgbToLabBatch(List<ColorPoint> rgbColors) {
        if (rgbColors == null || rgbColors.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Try native batch conversion
        if (nativeAccelerator.isAvailable()) {
            List<ColorPoint> nativeResult = nativeAccelerator.rgbToLabBatch(rgbColors);
            if (nativeResult != null) {
                return nativeResult;
            }
        }
        
        // Fallback to Java
        List<ColorPoint> result = new ArrayList<>(rgbColors.size());
        for (ColorPoint rgb : rgbColors) {
            result.add(rgbToLab(rgb));
        }
        return result;
    }
    
    /**
     * Converts a batch of CIELAB colors to RGB.
     * Uses native acceleration when available.
     */
    public static List<ColorPoint> labToRgbBatch(List<ColorPoint> labColors) {
        if (labColors == null || labColors.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Try native batch conversion
        if (nativeAccelerator.isAvailable()) {
            List<ColorPoint> nativeResult = nativeAccelerator.labToRgbBatch(labColors);
            if (nativeResult != null) {
                return nativeResult;
            }
        }
        
        // Fallback to Java
        List<ColorPoint> result = new ArrayList<>(labColors.size());
        for (ColorPoint lab : labColors) {
            result.add(labToRgb(lab));
        }
        return result;
    }

    /**
     * Converts an RGB color to CIELAB color space.
     */
    public static ColorPoint rgbToLab(ColorPoint rgb) {
        double[] xyz = rgbToXyz(rgb.c1(), rgb.c2(), rgb.c3());
        return xyzToLab(xyz[0], xyz[1], xyz[2]);
    }
    
    /**
     * Converts a CIELAB color to RGB color space.
     */
    public static ColorPoint labToRgb(ColorPoint lab) {
        double[] xyz = labToXyz(lab.c1(), lab.c2(), lab.c3());
        return xyzToRgb(xyz[0], xyz[1], xyz[2]);
    }
    
    private static double[] rgbToXyz(double r, double g, double b) {
        r = r / 255.0;
        g = g / 255.0;
        b = b / 255.0;
        
        r = (r > 0.04045) ? Math.pow((r + 0.055) / 1.055, 2.4) : r / 12.92;
        g = (g > 0.04045) ? Math.pow((g + 0.055) / 1.055, 2.4) : g / 12.92;
        b = (b > 0.04045) ? Math.pow((b + 0.055) / 1.055, 2.4) : b / 12.92;
        
        r *= 100;
        g *= 100;
        b *= 100;
        
        double x = r * 0.4124564 + g * 0.3575761 + b * 0.1804375;
        double y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750;
        double z = r * 0.0193339 + g * 0.1191920 + b * 0.9503041;
        
        return new double[]{x, y, z};
    }
    
    private static ColorPoint xyzToLab(double x, double y, double z) {
        x = x / REF_X;
        y = y / REF_Y;
        z = z / REF_Z;
        
        x = labF(x);
        y = labF(y);
        z = labF(z);
        
        double l = 116 * y - 16;
        double a = 500 * (x - y);
        double bVal = 200 * (y - z);
        
        return new ColorPoint(l, a, bVal);
    }
    
    private static double labF(double t) {
        if (t > EPSILON) {
            return Math.cbrt(t);
        } else {
            return (KAPPA * t + 16) / 116;
        }
    }
    
    private static double[] labToXyz(double l, double a, double b) {
        double fy = (l + 16) / 116;
        double fx = a / 500 + fy;
        double fz = fy - b / 200;
        
        double x = labFInverse(fx) * REF_X;
        double y = labFInverse(fy) * REF_Y;
        double z = labFInverse(fz) * REF_Z;
        
        return new double[]{x, y, z};
    }
    
    private static double labFInverse(double t) {
        if (t > DELTA) {
            return t * t * t;
        } else {
            return 3 * DELTA * DELTA * (t - 4.0 / 29.0);
        }
    }
    
    private static ColorPoint xyzToRgb(double x, double y, double z) {
        x = x / 100;
        y = y / 100;
        z = z / 100;
        
        double r = x * 3.2404542 + y * -1.5371385 + z * -0.4985314;
        double g = x * -0.9692660 + y * 1.8760108 + z * 0.0415560;
        double b = x * 0.0556434 + y * -0.2040259 + z * 1.0572252;
        
        r = (r > 0.0031308) ? 1.055 * Math.pow(r, 1 / 2.4) - 0.055 : 12.92 * r;
        g = (g > 0.0031308) ? 1.055 * Math.pow(g, 1 / 2.4) - 0.055 : 12.92 * g;
        b = (b > 0.0031308) ? 1.055 * Math.pow(b, 1 / 2.4) - 0.055 : 12.92 * b;
        
        r = clamp(r * 255, 0, 255);
        g = clamp(g * 255, 0, 255);
        b = clamp(b * 255, 0, 255);
        
        return new ColorPoint(r, g, b);
    }
    
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Calculates the CIEDE2000 color difference between two LAB colors.
     * This is a more accurate perceptual color difference metric.
     */
    public static double deltaE2000(ColorPoint lab1, ColorPoint lab2) {
        double l1 = lab1.c1(), a1 = lab1.c2(), b1 = lab1.c3();
        double l2 = lab2.c1(), a2 = lab2.c2(), b2 = lab2.c3();
        
        double c1 = Math.sqrt(a1 * a1 + b1 * b1);
        double c2 = Math.sqrt(a2 * a2 + b2 * b2);
        double cAvg = (c1 + c2) / 2;
        
        double cAvg7 = Math.pow(cAvg, 7);
        double g = 0.5 * (1 - Math.sqrt(cAvg7 / (cAvg7 + Math.pow(25, 7))));
        
        double a1Prime = a1 * (1 + g);
        double a2Prime = a2 * (1 + g);
        
        double c1Prime = Math.sqrt(a1Prime * a1Prime + b1 * b1);
        double c2Prime = Math.sqrt(a2Prime * a2Prime + b2 * b2);
        
        double h1Prime = Math.atan2(b1, a1Prime);
        double h2Prime = Math.atan2(b2, a2Prime);
        
        if (h1Prime < 0) h1Prime += 2 * Math.PI;
        if (h2Prime < 0) h2Prime += 2 * Math.PI;
        
        double deltaL = l2 - l1;
        double deltaC = c2Prime - c1Prime;
        
        double deltah;
        if (c1Prime * c2Prime == 0) {
            deltah = 0;
        } else if (Math.abs(h2Prime - h1Prime) <= Math.PI) {
            deltah = h2Prime - h1Prime;
        } else if (h2Prime - h1Prime > Math.PI) {
            deltah = h2Prime - h1Prime - 2 * Math.PI;
        } else {
            deltah = h2Prime - h1Prime + 2 * Math.PI;
        }
        
        double deltaH = 2 * Math.sqrt(c1Prime * c2Prime) * Math.sin(deltah / 2);
        
        double lAvg = (l1 + l2) / 2;
        double cPrimeAvg = (c1Prime + c2Prime) / 2;
        
        double hPrimeAvg;
        if (c1Prime * c2Prime == 0) {
            hPrimeAvg = h1Prime + h2Prime;
        } else if (Math.abs(h1Prime - h2Prime) <= Math.PI) {
            hPrimeAvg = (h1Prime + h2Prime) / 2;
        } else if (h1Prime + h2Prime < 2 * Math.PI) {
            hPrimeAvg = (h1Prime + h2Prime + 2 * Math.PI) / 2;
        } else {
            hPrimeAvg = (h1Prime + h2Prime - 2 * Math.PI) / 2;
        }
        
        double t = 1 - 0.17 * Math.cos(hPrimeAvg - Math.toRadians(30))
                     + 0.24 * Math.cos(2 * hPrimeAvg)
                     + 0.32 * Math.cos(3 * hPrimeAvg + Math.toRadians(6))
                     - 0.20 * Math.cos(4 * hPrimeAvg - Math.toRadians(63));
        
        double sl = 1 + (0.015 * Math.pow(lAvg - 50, 2)) / Math.sqrt(20 + Math.pow(lAvg - 50, 2));
        double sc = 1 + 0.045 * cPrimeAvg;
        double sh = 1 + 0.015 * cPrimeAvg * t;
        
        double cPrimeAvg7 = Math.pow(cPrimeAvg, 7);
        double rc = 2 * Math.sqrt(cPrimeAvg7 / (cPrimeAvg7 + Math.pow(25, 7)));
        double deltaTheta = Math.toRadians(30) * Math.exp(-Math.pow((hPrimeAvg - Math.toRadians(275)) / Math.toRadians(25), 2));
        double rt = -Math.sin(2 * deltaTheta) * rc;
        
        return Math.sqrt(
            Math.pow(deltaL / sl, 2) +
            Math.pow(deltaC / sc, 2) +
            Math.pow(deltaH / sh, 2) +
            rt * (deltaC / sc) * (deltaH / sh)
        );
    }
}
