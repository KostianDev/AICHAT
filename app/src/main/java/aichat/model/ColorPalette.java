package aichat.model;

import java.util.ArrayList;
import java.util.Arrays;
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
    
    public int[] computeMappingTo(ColorPalette target) {
        int n = this.size();
        int m = target.size();
        int size = Math.max(n, m);
        
        double[][] cost = new double[size][size];
        double maxCost = 0;
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                cost[i][j] = perceptualDistance(this.colors.get(i), target.colors.get(j));
                maxCost = Math.max(maxCost, cost[i][j]);
            }
        }
        
        double dummy = maxCost * 10;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i >= n || j >= m) {
                    cost[i][j] = dummy;
                }
            }
        }
        
        int[] assignment = hungarianAlgorithm(cost, size);
        
        int[] mapping = new int[n];
        for (int i = 0; i < n; i++) {
            mapping[i] = assignment[i] < m ? assignment[i] : 0;
        }
        
        return mapping;
    }
    
    private double perceptualDistance(ColorPoint a, ColorPoint b) {
        double dr = a.c1() - b.c1();
        double dg = a.c2() - b.c2();
        double db = a.c3() - b.c3();
        double avgR = (a.c1() + b.c1()) * 0.5;
        
        double wr = avgR < 128 ? 2.0 : 3.0;
        double wg = 4.0;
        double wb = avgR < 128 ? 3.0 : 2.0;
        
        double lumA = 0.299 * a.c1() + 0.587 * a.c2() + 0.114 * a.c3();
        double lumB = 0.299 * b.c1() + 0.587 * b.c2() + 0.114 * b.c3();
        double lumDiff = (lumA - lumB) * (lumA - lumB) * 0.5;
        
        return wr * dr * dr + wg * dg * dg + wb * db * db + lumDiff;
    }
    
    private int[] hungarianAlgorithm(double[][] cost, int n) {
        double[] u = new double[n + 1];
        double[] v = new double[n + 1];
        int[] p = new int[n + 1];
        int[] way = new int[n + 1];
        
        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[n + 1];
            boolean[] used = new boolean[n + 1];
            Arrays.fill(minv, Double.MAX_VALUE);
            
            do {
                used[j0] = true;
                int i0 = p[j0];
                double delta = Double.MAX_VALUE;
                int j1 = 0;
                
                for (int j = 1; j <= n; j++) {
                    if (!used[j]) {
                        double cur = cost[i0 - 1][j - 1] - u[i0] - v[j];
                        if (cur < minv[j]) {
                            minv[j] = cur;
                            way[j] = j0;
                        }
                        if (minv[j] < delta) {
                            delta = minv[j];
                            j1 = j;
                        }
                    }
                }
                
                for (int j = 0; j <= n; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                
                j0 = j1;
            } while (p[j0] != 0);
            
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }
        
        int[] result = new int[n];
        for (int j = 1; j <= n; j++) {
            if (p[j] > 0 && p[j] <= n) {
                result[p[j] - 1] = j - 1;
            }
        }
        return result;
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
