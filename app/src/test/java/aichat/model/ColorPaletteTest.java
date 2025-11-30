package aichat.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ColorPalette Tests")
class ColorPaletteTest {

    private ColorPalette palette;

    @BeforeEach
    void setUp() {
        List<ColorPoint> colors = new ArrayList<>();
        colors.add(new ColorPoint(255, 0, 0));    // Red
        colors.add(new ColorPoint(0, 255, 0));    // Green
        colors.add(new ColorPoint(0, 0, 255));    // Blue
        palette = new ColorPalette(colors);
    }

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperationsTests {

        @Test
        @DisplayName("Should return correct size")
        void shouldReturnCorrectSize() {
            assertEquals(3, palette.size());
        }

        @Test
        @DisplayName("Should return correct color at index")
        void shouldReturnCorrectColorAtIndex() {
            ColorPoint red = palette.getColor(0);
            assertEquals(255, red.c1(), 0.1);
            assertEquals(0, red.c2(), 0.1);
            assertEquals(0, red.c3(), 0.1);
        }

        @Test
        @DisplayName("Should return unmodifiable list")
        void shouldReturnUnmodifiableList() {
            List<ColorPoint> colors = palette.getColors();
            assertThrows(UnsupportedOperationException.class, () -> 
                colors.add(new ColorPoint(0, 0, 0))
            );
        }

        @Test
        @DisplayName("Should handle empty palette")
        void shouldHandleEmptyPalette() {
            ColorPalette empty = new ColorPalette(new ArrayList<>());
            assertEquals(0, empty.size());
            assertTrue(empty.getColors().isEmpty());
        }
    }

    @Nested
    @DisplayName("Find Closest Color")
    class FindClosestTests {

        @Test
        @DisplayName("Should find exact match")
        void shouldFindExactMatch() {
            ColorPoint target = new ColorPoint(255, 0, 0);
            ColorPoint closest = palette.findClosest(target);
            
            assertEquals(255, closest.c1(), 0.1);
            assertEquals(0, closest.c2(), 0.1);
            assertEquals(0, closest.c3(), 0.1);
        }

        @Test
        @DisplayName("Should find closest when no exact match")
        void shouldFindClosestWhenNoExactMatch() {
            ColorPoint target = new ColorPoint(250, 10, 10);  // Near red
            ColorPoint closest = palette.findClosest(target);
            
            // Should be red
            assertEquals(255, closest.c1(), 0.1);
            assertEquals(0, closest.c2(), 0.1);
        }

        @Test
        @DisplayName("Should return correct index for closest")
        void shouldReturnCorrectIndex() {
            ColorPoint nearGreen = new ColorPoint(10, 240, 10);
            int index = palette.findClosestIndex(nearGreen);
            
            assertEquals(1, index);  // Green is at index 1
        }
    }

    @Nested
    @DisplayName("Hex String Conversion")
    class HexStringTests {

        @Test
        @DisplayName("Should convert to hex strings")
        void shouldConvertToHexStrings() {
            List<String> hexStrings = palette.toHexStrings();
            
            assertEquals(3, hexStrings.size());
            assertEquals("#FF0000", hexStrings.get(0));  // Red
            assertEquals("#00FF00", hexStrings.get(1));  // Green
            assertEquals("#0000FF", hexStrings.get(2));  // Blue
        }
    }

    @Nested
    @DisplayName("Sort By Luminance")
    class SortByLuminanceTests {

        @Test
        @DisplayName("Should sort colors by luminance")
        void shouldSortByLuminance() {
            List<ColorPoint> colors = new ArrayList<>();
            colors.add(new ColorPoint(255, 255, 255));  // White (brightest)
            colors.add(new ColorPoint(0, 0, 0));        // Black (darkest)
            colors.add(new ColorPoint(128, 128, 128));  // Gray (middle)
            ColorPalette unsorted = new ColorPalette(colors);
            
            ColorPalette sorted = unsorted.sortByLuminance();
            
            // Should be: black, gray, white
            ColorPoint first = sorted.getColor(0);
            ColorPoint last = sorted.getColor(2);
            
            // First should be darker than last
            double lumFirst = 0.299 * first.c1() + 0.587 * first.c2() + 0.114 * first.c3();
            double lumLast = 0.299 * last.c1() + 0.587 * last.c2() + 0.114 * last.c3();
            
            assertTrue(lumFirst < lumLast, "First color should be darker");
        }

        @Test
        @DisplayName("Sort should not modify original")
        void sortShouldNotModifyOriginal() {
            ColorPoint originalFirst = palette.getColor(0);
            
            palette.sortByLuminance();
            
            ColorPoint stillFirst = palette.getColor(0);
            assertEquals(originalFirst.c1(), stillFirst.c1(), 0.1);
        }
    }

    @Nested
    @DisplayName("Color Mapping (Hungarian Algorithm)")
    class ColorMappingTests {

        @Test
        @DisplayName("Should map identical palettes to identity")
        void shouldMapIdenticalPalettesToIdentity() {
            int[] mapping = palette.computeMappingTo(palette);
            
            assertEquals(3, mapping.length);
            // Each color should map to itself
            for (int i = 0; i < mapping.length; i++) {
                assertEquals(i, mapping[i]);
            }
        }

        @Test
        @DisplayName("Should map similar colors together")
        void shouldMapSimilarColorsTogether() {
            // Create a target palette with slightly different reds/greens/blues
            List<ColorPoint> targetColors = new ArrayList<>();
            targetColors.add(new ColorPoint(250, 5, 5));    // Near red
            targetColors.add(new ColorPoint(5, 250, 5));    // Near green  
            targetColors.add(new ColorPoint(5, 5, 250));    // Near blue
            ColorPalette target = new ColorPalette(targetColors);
            
            int[] mapping = palette.computeMappingTo(target);
            
            // Red should map to near-red, etc.
            assertEquals(0, mapping[0]);  // Red -> Near red
            assertEquals(1, mapping[1]);  // Green -> Near green
            assertEquals(2, mapping[2]);  // Blue -> Near blue
        }

        @Test
        @DisplayName("Should handle different sized palettes")
        void shouldHandleDifferentSizedPalettes() {
            List<ColorPoint> smallerColors = new ArrayList<>();
            smallerColors.add(new ColorPoint(255, 0, 0));
            smallerColors.add(new ColorPoint(0, 0, 255));
            ColorPalette smaller = new ColorPalette(smallerColors);
            
            int[] mapping = palette.computeMappingTo(smaller);
            
            assertEquals(3, mapping.length);
            // All mappings should be valid indices in target
            for (int m : mapping) {
                assertTrue(m >= 0 && m < smaller.size());
            }
        }

        @Test
        @DisplayName("Should produce bijective mapping for equal sizes")
        void shouldProduceBijectiveMapping() {
            List<ColorPoint> shuffled = new ArrayList<>();
            shuffled.add(new ColorPoint(0, 0, 255));    // Blue
            shuffled.add(new ColorPoint(255, 0, 0));    // Red
            shuffled.add(new ColorPoint(0, 255, 0));    // Green
            ColorPalette shuffledPalette = new ColorPalette(shuffled);
            
            int[] mapping = palette.computeMappingTo(shuffledPalette);
            
            // Each source should map to unique target
            boolean[] used = new boolean[3];
            for (int m : mapping) {
                assertFalse(used[m], "Mapping should be bijective");
                used[m] = true;
            }
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("Should produce readable string representation")
        void shouldProduceReadableString() {
            String str = palette.toString();
            
            assertNotNull(str);
            assertTrue(str.contains("ColorPalette"));
            assertTrue(str.contains("#FF0000") || str.contains("255"));
        }
    }
}
