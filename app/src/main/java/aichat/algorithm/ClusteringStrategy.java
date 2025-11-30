package aichat.algorithm;

import aichat.model.ColorPoint;
import java.util.List;

public interface ClusteringStrategy {
    List<ColorPoint> cluster(List<ColorPoint> points, int k);
    String getName();
}
