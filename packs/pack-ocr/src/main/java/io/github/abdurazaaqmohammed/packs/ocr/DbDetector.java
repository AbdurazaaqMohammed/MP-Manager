package io.github.abdurazaaqmohammed.packs.ocr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * DB probability-map postprocessing. Components and rotated rectangles are
 * computed in Java, avoiding a second large native runtime (OpenCV).
 * PaddleOCR/RapidOCR's thresholds and rectangle unclip rule are retained.
 */
final class DbDetector {
    record Point(float x, float y) {}
    record Quad(Point topLeft, Point topRight, Point bottomRight, Point bottomLeft, float confidence) {
        float width() { return distance(topLeft, topRight); }
        float height() { return distance(topLeft, bottomLeft); }
        private static float distance(Point a, Point b) {
            return (float) Math.hypot(a.x - b.x, a.y - b.y);
        }
    }

    static List<Quad> boxes(float[] map, int width, int height, int imageWidth, int imageHeight,
                            BooleanSupplier cancelled) {
        if (width <= 0 || height <= 0 || (long) width * height != map.length) {
            throw new IllegalArgumentException("Invalid detection map");
        }
        byte[] mask = new byte[map.length];
        // 2 x 2 dilation, as in the mobile PaddleOCR pipeline.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (map[y * width + x] <= .3f) continue;
                mask[y * width + x] = 1;
                if (x + 1 < width) mask[y * width + x + 1] = 1;
                if (y + 1 < height) {
                    mask[(y + 1) * width + x] = 1;
                    if (x + 1 < width) mask[(y + 1) * width + x + 1] = 1;
                }
            }
        }
        int[] queue = new int[map.length];
        List<Quad> boxes = new ArrayList<>();
        int candidates = 0;
        for (int seed = 0; seed < mask.length && candidates < 1000; seed++) {
            if (mask[seed] != 1) continue;
            if (cancelled.getAsBoolean()) throw new CancellationException();
            candidates++;
            int head = 0, tail = 1;
            queue[0] = seed;
            mask[seed] = 2;
            List<Point> boundary = new ArrayList<>();
            int minX = width, minY = height, maxX = 0, maxY = 0;
            while (head < tail) {
                if ((head & 4095) == 0 && cancelled.getAsBoolean()) throw new CancellationException();
                int index = queue[head++], x = index % width, y = index / width;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                boolean edge = false;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) { edge = true; continue; }
                        int neighbor = ny * width + nx;
                        if (mask[neighbor] == 0) edge = true;
                        if (mask[neighbor] == 1) {
                            mask[neighbor] = 2;
                            queue[tail++] = neighbor;
                        }
                    }
                }
                if (edge) boundary.add(new Point(x, y));
            }
            if (maxX - minX < 3 || maxY - minY < 3 || tail < 9) continue;
            List<Point> hull = convexHull(boundary);
            if (hull.size() < 3) continue;
            Quad rectangle = minimumRectangle(hull);
            float rectWidth = rectangle.width(), rectHeight = rectangle.height();
            if (Math.min(rectWidth, rectHeight) < 3) continue;
            float score = score(map, width, height, rectangle, minX, minY, maxX, maxY);
            if (score < .5f) continue;
            float padding = rectWidth * rectHeight * 1.6f / (2 * (rectWidth + rectHeight));
            Quad expanded = expand(rectangle, padding, score);
            if (Math.min(expanded.width(), expanded.height()) < 5) continue;
            boxes.add(scale(expanded, imageWidth / (float) width, imageHeight / (float) height,
                    imageWidth, imageHeight));
        }
        return boxes;
    }

    private static List<Point> convexHull(List<Point> points) {
        points.sort(Comparator.comparingDouble(Point::x).thenComparingDouble(Point::y));
        List<Point> hull = new ArrayList<>();
        for (Point point : points) {
            while (hull.size() >= 2 && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), point) <= 0) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        int lower = hull.size();
        for (int index = points.size() - 2; index >= 0; index--) {
            Point point = points.get(index);
            while (hull.size() > lower && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), point) <= 0) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        if (!hull.isEmpty()) hull.remove(hull.size() - 1);
        return hull;
    }

    private static float cross(Point a, Point b, Point c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private static Quad minimumRectangle(List<Point> hull) {
        float bestArea = Float.MAX_VALUE;
        Quad best = null;
        for (int index = 0; index < hull.size(); index++) {
            Point a = hull.get(index), b = hull.get((index + 1) % hull.size());
            double angle = Math.atan2(b.y - a.y, b.x - a.x);
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            float left = Float.MAX_VALUE, top = Float.MAX_VALUE;
            float right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
            for (Point point : hull) {
                float x = point.x * cos + point.y * sin, y = -point.x * sin + point.y * cos;
                left = Math.min(left, x); right = Math.max(right, x);
                top = Math.min(top, y); bottom = Math.max(bottom, y);
            }
            float area = (right - left) * (bottom - top);
            if (area < bestArea) {
                bestArea = area;
                best = order(new Point[]{rotate(left, top, cos, sin), rotate(right, top, cos, sin),
                        rotate(right, bottom, cos, sin), rotate(left, bottom, cos, sin)});
            }
        }
        return best;
    }

    private static Point rotate(float x, float y, float cos, float sin) {
        return new Point(x * cos - y * sin, x * sin + y * cos);
    }

    private static Quad order(Point[] points) {
        Arrays.sort(points, Comparator.comparingDouble(Point::x));
        Point tl = points[0].y <= points[1].y ? points[0] : points[1];
        Point bl = points[0].y > points[1].y ? points[0] : points[1];
        Point tr = points[2].y <= points[3].y ? points[2] : points[3];
        Point br = points[2].y > points[3].y ? points[2] : points[3];
        return new Quad(tl, tr, br, bl, 0);
    }

    private static float score(float[] map, int width, int height, Quad q, int minX, int minY, int maxX, int maxY) {
        float sum = 0;
        int count = 0;
        for (int y = minY; y <= maxY && y < height; y++) {
            for (int x = minX; x <= maxX && x < width; x++) {
                Point point = new Point(x, y);
                if (cross(q.topLeft, q.topRight, point) >= -.01f
                        && cross(q.topRight, q.bottomRight, point) >= -.01f
                        && cross(q.bottomRight, q.bottomLeft, point) >= -.01f
                        && cross(q.bottomLeft, q.topLeft, point) >= -.01f) {
                    sum += map[y * width + x];
                    count++;
                }
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    private static Quad expand(Quad q, float padding, float score) {
        float ux = (q.topRight.x - q.topLeft.x) / q.width();
        float uy = (q.topRight.y - q.topLeft.y) / q.width();
        float vx = (q.bottomLeft.x - q.topLeft.x) / q.height();
        float vy = (q.bottomLeft.y - q.topLeft.y) / q.height();
        return new Quad(move(q.topLeft, -ux - vx, -uy - vy, padding),
                move(q.topRight, ux - vx, uy - vy, padding),
                move(q.bottomRight, ux + vx, uy + vy, padding),
                move(q.bottomLeft, -ux + vx, -uy + vy, padding), score);
    }

    private static Point move(Point p, float x, float y, float padding) {
        return new Point(p.x + x * padding, p.y + y * padding);
    }

    private static Quad scale(Quad q, float sx, float sy, int width, int height) {
        return new Quad(scale(q.topLeft, sx, sy, width, height), scale(q.topRight, sx, sy, width, height),
                scale(q.bottomRight, sx, sy, width, height), scale(q.bottomLeft, sx, sy, width, height), q.confidence);
    }

    private static Point scale(Point p, float sx, float sy, int width, int height) {
        return new Point(Math.max(0, Math.min(width - 1, p.x * sx)),
                Math.max(0, Math.min(height - 1, p.y * sy)));
    }
}
