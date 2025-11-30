package aichat.color;

import aichat.model.ColorPoint;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import static org.junit.jupiter.api.Assertions.*;

class ColorSpaceConverterProperties {

    private static final double ROUND_TRIP_TOLERANCE = 2.0;
    private static final double LUMINANCE_TOLERANCE = 0.5;

    @Property
    @Label("RGB round-trip: rgbToLab(labToRgb(c)) ≈ c for all valid RGB")
    void rgbRoundTripPreservesColor(
            @ForAll @IntRange(min = 0, max = 255) int r,
            @ForAll @IntRange(min = 0, max = 255) int g,
            @ForAll @IntRange(min = 0, max = 255) int b) {
        
        ColorPoint original = new ColorPoint(r, g, b);
        ColorPoint lab = ColorSpaceConverter.rgbToLab(original);
        ColorPoint recovered = ColorSpaceConverter.labToRgb(lab);

        double distance = original.distanceTo(recovered);
        
        assertTrue(distance < ROUND_TRIP_TOLERANCE,
            "Round-trip distance " + distance + " exceeds tolerance for RGB(" + r + "," + g + "," + b + ")");
    }

    @Property
    @Label("L* increases monotonically with RGB gray levels")
    void luminanceIncreasesWithGrayLevel(
            @ForAll @IntRange(min = 0, max = 254) int gray1) {
        
        int gray2 = gray1 + 1;
        
        ColorPoint darker = new ColorPoint(gray1, gray1, gray1);
        ColorPoint brighter = new ColorPoint(gray2, gray2, gray2);
        
        ColorPoint labDarker = ColorSpaceConverter.rgbToLab(darker);
        ColorPoint labBrighter = ColorSpaceConverter.rgbToLab(brighter);
        
        assertTrue(labBrighter.c1() >= labDarker.c1(),
            "L*(" + gray2 + ") = " + labBrighter.c1() + 
            " should be >= L*(" + gray1 + ") = " + labDarker.c1());
    }

    @Property
    @Label("Neutral grays have near-zero chroma (a* ≈ 0, b* ≈ 0)")
    void neutralGraysHaveZeroChroma(
            @ForAll @IntRange(min = 0, max = 255) int gray) {
        
        ColorPoint rgb = new ColorPoint(gray, gray, gray);
        ColorPoint lab = ColorSpaceConverter.rgbToLab(rgb);
        
        assertEquals(0.0, lab.c2(), LUMINANCE_TOLERANCE,
            "Gray " + gray + " should have a* ≈ 0, got " + lab.c2());
        assertEquals(0.0, lab.c3(), LUMINANCE_TOLERANCE,
            "Gray " + gray + " should have b* ≈ 0, got " + lab.c3());
    }

    @Property
    @Label("L* is bounded approximately in [0, 100] for all valid RGB")
    void luminanceIsBounded(
            @ForAll @IntRange(min = 0, max = 255) int r,
            @ForAll @IntRange(min = 0, max = 255) int g,
            @ForAll @IntRange(min = 0, max = 255) int b) {
        
        ColorPoint rgb = new ColorPoint(r, g, b);
        ColorPoint lab = ColorSpaceConverter.rgbToLab(rgb);
        
        // Allow small tolerance for numerical precision
        assertTrue(lab.c1() >= -0.5 && lab.c1() <= 100.5,
            "L* = " + lab.c1() + " should be approximately in [0, 100] for RGB(" + r + "," + g + "," + b + ")");
    }

    @Property
    @Label("LAB to RGB always produces values in [0, 255]")
    void labToRgbOutputIsBounded(
            @ForAll @IntRange(min = 0, max = 100) int l,
            @ForAll @IntRange(min = -128, max = 128) int a,
            @ForAll @IntRange(min = -128, max = 128) int b) {
        
        ColorPoint lab = new ColorPoint(l, a, b);
        ColorPoint rgb = ColorSpaceConverter.labToRgb(lab);
        
        assertTrue(rgb.c1() >= 0 && rgb.c1() <= 255,
            "R = " + rgb.c1() + " should be in [0, 255]");
        assertTrue(rgb.c2() >= 0 && rgb.c2() <= 255,
            "G = " + rgb.c2() + " should be in [0, 255]");
        assertTrue(rgb.c3() >= 0 && rgb.c3() <= 255,
            "B = " + rgb.c3() + " should be in [0, 255]");
    }

    @Property
    @Label("DeltaE is always non-negative")
    void deltaEIsNonNegative(
            @ForAll @IntRange(min = 0, max = 100) int l1,
            @ForAll @IntRange(min = -50, max = 50) int a1,
            @ForAll @IntRange(min = -50, max = 50) int b1,
            @ForAll @IntRange(min = 0, max = 100) int l2,
            @ForAll @IntRange(min = -50, max = 50) int a2,
            @ForAll @IntRange(min = -50, max = 50) int b2) {
        
        ColorPoint lab1 = new ColorPoint(l1, a1, b1);
        ColorPoint lab2 = new ColorPoint(l2, a2, b2);
        
        double deltaE = ColorSpaceConverter.deltaE2000(lab1, lab2);
        
        assertTrue(deltaE >= 0, "DeltaE should be non-negative, got " + deltaE);
    }

    @Property
    @Label("DeltaE is symmetric: deltaE(a,b) = deltaE(b,a)")
    void deltaEIsSymmetric(
            @ForAll @IntRange(min = 0, max = 100) int l1,
            @ForAll @IntRange(min = -50, max = 50) int a1,
            @ForAll @IntRange(min = -50, max = 50) int b1,
            @ForAll @IntRange(min = 0, max = 100) int l2,
            @ForAll @IntRange(min = -50, max = 50) int a2,
            @ForAll @IntRange(min = -50, max = 50) int b2) {
        
        ColorPoint lab1 = new ColorPoint(l1, a1, b1);
        ColorPoint lab2 = new ColorPoint(l2, a2, b2);
        
        double deltaE12 = ColorSpaceConverter.deltaE2000(lab1, lab2);
        double deltaE21 = ColorSpaceConverter.deltaE2000(lab2, lab1);
        
        assertEquals(deltaE12, deltaE21, 0.0001,
            "DeltaE should be symmetric: " + deltaE12 + " vs " + deltaE21);
    }

    @Property
    @Label("DeltaE with self is zero")
    void deltaEWithSelfIsZero(
            @ForAll @IntRange(min = 0, max = 100) int l,
            @ForAll @IntRange(min = -50, max = 50) int a,
            @ForAll @IntRange(min = -50, max = 50) int b) {
        
        ColorPoint lab = new ColorPoint(l, a, b);
        
        double deltaE = ColorSpaceConverter.deltaE2000(lab, lab);
        
        assertEquals(0.0, deltaE, 0.0001, "DeltaE with self should be 0");
    }

    @Property(tries = 50)
    @Label("Brighter RGB colors have higher L* values")
    void brighterColorsHaveHigherLuminance(
            @ForAll @IntRange(min = 0, max = 255) int r,
            @ForAll @IntRange(min = 0, max = 255) int g,
            @ForAll @IntRange(min = 0, max = 255) int b) {
        
        // Create a slightly brighter version
        int dr = Math.min(255, r + 10);
        int dg = Math.min(255, g + 10);
        int db = Math.min(255, b + 10);
        
        ColorPoint original = new ColorPoint(r, g, b);
        ColorPoint brighter = new ColorPoint(dr, dg, db);
        
        // Skip if colors are identical
        if (r == dr && g == dg && b == db) return;
        
        ColorPoint labOriginal = ColorSpaceConverter.rgbToLab(original);
        ColorPoint labBrighter = ColorSpaceConverter.rgbToLab(brighter);
        
        assertTrue(labBrighter.c1() >= labOriginal.c1() - 0.1,
            "Brighter color should have higher or equal L*");
    }
}
