package aichat.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ColorPalette {
    
    private final List<ColorPoint> colors;
    
    public ColorPalette(List<ColorPoint> colors) {
        this.colors = new ArrayList<>(colors);
    }
    
    public List<ColorPoint> getColors() {
        return Collections.unmodifiableList(colors);
    }
    
    public int size() {
        return colors.size();
    }
    
    public ColorPoint getColor(int index) {
        return colors.get(index);
    }
    
    public ColorPoint findClosest(ColorPoint target) {
        ColorPoint closest = null;
        double minDistance = Double.MAX_VALUE;
        
        for (ColorPoint color : colors) {
            double distance = target.distanceTo(color);
            if (distance < minDistance) {
                minDistance = distance;
                closest = color;
            }
        }
        
        return closest;
    }
    
    public int findClosestIndex(ColorPoint target) {
        int closestIndex = 0;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < colors.size(); i++) {
            double distance = target.distanceTo(colors.get(i));
            if (distance < minDistance) {
                minDistance = distance;
                closestIndex = i;
            }
        }
        
        return closestIndex;
    }
    
    public List<String> toHexStrings() {
        List<String> hexColors = new ArrayList<>(colors.size());
        for (ColorPoint color : colors) {
            hexColors.add(color.toHexString());
        }
        return hexColors;
    }
    
    public ColorPalette sortByLuminance() {
        List<ColorPoint> sorted = new ArrayList<>(colors);
        sorted.sort((a, b) -> {
            double lumA = 0.299 * a.c1() + 0.587 * a.c2() + 0.114 * a.c3();
            double lumB = 0.299 * b.c1() + 0.587 * b.c2() + 0.114 * b.c3();
            return Double.compare(lumA, lumB);
        });
        return new ColorPalette(sorted);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ColorPalette[\n");
        for (int i = 0; i < colors.size(); i++) {
            sb.append("  ").append(i).append(": ")
              .append(colors.get(i).toHexString())
              .append(" ").append(colors.get(i))
              .append("\n");
        }
        sb.append("]");
        return sb.toString();
    }
}
