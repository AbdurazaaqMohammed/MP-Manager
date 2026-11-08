package io.github.abdurazaaqmohammed.drawable;

import android.graphics.Path;

/**
 * Parser for the {@code android:pathData} mini-language used by
 * {@code <vector>} drawables. Supports M/L/H/V/C/S/Q/T/A/Z in both absolute and
 * relative form, including repeated coordinate groups and scientific notation.
 *
 * <p>The framework keeps its own parser behind a hidden API, so this one is
 * self contained and works on every supported API level.</p>
 */
final class VectorPath {

    private VectorPath() {
    }

    static Path parse(String data) {
        Path path = new Path();
        if (data == null || data.isEmpty()) return path;
        int[] pos = {0};
        int len = data.length();
        char command = 0;
        float cx = 0, cy = 0;       // current point
        float sx = 0, sy = 0;       // start of current sub path
        float lastCx = 0, lastCy = 0; // last cubic control point (absolute)
        float lastQx = 0, lastQy = 0; // last quadratic control point (absolute)

        while (true) {
            skipSeparators(data, pos);
            if (pos[0] >= len) break;
            char c = data.charAt(pos[0]);
            if (isCommand(c)) {
                command = c;
                pos[0]++;
            } else if (command == 0) {
                break;
            } else {
                // Implicit repetition. The SVG spec turns a repeated M into L.
                if (command == 'M') command = 'L';
                else if (command == 'm') command = 'l';
            }
            boolean relative = Character.isLowerCase(command);
            char op = Character.toUpperCase(command);
            switch (op) {
                case 'M': {
                    float x = number(data, pos);
                    float y = number(data, pos);
                    if (Float.isNaN(x) || Float.isNaN(y)) break;
                    if (relative) {
                        x += cx;
                        y += cy;
                    }
                    path.moveTo(x, y);
                    sx = cx = x;
                    sy = cy = y;
                    // Extra coordinate pairs on an M command are implicit line-tos.
                    while (true) {
                        int save = pos[0];
                        float nx = number(data, pos);
                        float ny = number(data, pos);
                        if (Float.isNaN(nx) || Float.isNaN(ny)) {
                            pos[0] = save;
                            break;
                        }
                        if (relative) {
                            nx += cx;
                            ny += cy;
                        }
                        path.lineTo(nx, ny);
                        cx = nx;
                        cy = ny;
                    }
                    break;
                }
                case 'L': {
                    while (true) {
                        int save = pos[0];
                        float x = number(data, pos);
                        float y = number(data, pos);
                        if (Float.isNaN(x) || Float.isNaN(y)) {
                            pos[0] = save;
                            break;
                        }
                        if (relative) {
                            x += cx;
                            y += cy;
                        }
                        path.lineTo(x, y);
                        cx = x;
                        cy = y;
                    }
                    break;
                }
                case 'H': {
                    while (true) {
                        int save = pos[0];
                        float x = number(data, pos);
                        if (Float.isNaN(x)) {
                            pos[0] = save;
                            break;
                        }
                        if (relative) x += cx;
                        path.lineTo(x, cy);
                        cx = x;
                    }
                    break;
                }
                case 'V': {
                    while (true) {
                        int save = pos[0];
                        float y = number(data, pos);
                        if (Float.isNaN(y)) {
                            pos[0] = save;
                            break;
                        }
                        if (relative) y += cy;
                        path.lineTo(cx, y);
                        cy = y;
                    }
                    break;
                }
                case 'C': {
                    while (true) {
                        int save = pos[0];
                        float x1 = number(data, pos), y1 = number(data, pos);
                        float x2 = number(data, pos), y2 = number(data, pos);
                        float x = number(data, pos), y = number(data, pos);
                        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(x1)
                                || Float.isNaN(y1) || Float.isNaN(x2) || Float.isNaN(y2)) {
                            pos[0] = save;
                            break;
                        }
                        if (relative) {
                            x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy;
                        }
                        path.cubicTo(x1, y1, x2, y2, x, y);
                        lastCx = x2;
                        lastCy = y2;
                        cx = x;
                        cy = y;
                    }
                    break;
                }
                case 'S': {
                    while (true) {
                        int save = pos[0];
                        float x2 = number(data, pos), y2 = number(data, pos);
                        float x = number(data, pos), y = number(data, pos);
                        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(x2) || Float.isNaN(y2)) {
                            pos[0] = save;
                            break;
                        }
                        float x1 = 2 * cx - lastCx;
                        float y1 = 2 * cy - lastCy;
                        if (relative) {
                            x2 += cx; y2 += cy; x += cx; y += cy;
                        }
                        path.cubicTo(x1, y1, x2, y2, x, y);
                        lastCx = x2;
                        lastCy = y2;
                        cx = x;
                        cy = y;
                    }
                    break;
                }
                case 'Q': {
                    while (true) {
                        int save = pos[0];
                        float x1 = number(data, pos), y1 = number(data, pos);
                        float x = number(data, pos), y = number(data, pos);
                        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(x1) || Float.isNaN(y1)) {
                            pos[0] = save;
                            break;
                        }
                        if (relative) {
                            x1 += cx; y1 += cy; x += cx; y += cy;
                        }
                        path.quadTo(x1, y1, x, y);
                        lastQx = x1;
                        lastQy = y1;
                        cx = x;
                        cy = y;
                    }
                    break;
                }
                case 'T': {
                    while (true) {
                        int save = pos[0];
                        float x = number(data, pos), y = number(data, pos);
                        if (Float.isNaN(x) || Float.isNaN(y)) {
                            pos[0] = save;
                            break;
                        }
                        float x1 = 2 * cx - lastQx;
                        float y1 = 2 * cy - lastQy;
                        if (relative) {
                            x += cx;
                            y += cy;
                        }
                        path.quadTo(x1, y1, x, y);
                        lastQx = x1;
                        lastQy = y1;
                        cx = x;
                        cy = y;
                    }
                    break;
                }
                case 'A': {
                    while (true) {
                        int save = pos[0];
                        float rx = number(data, pos), ry = number(data, pos);
                        float rotation = number(data, pos);
                        float large = number(data, pos), sweep = number(data, pos);
                        float x = number(data, pos), y = number(data, pos);
                        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(rx)
                                || Float.isNaN(ry) || Float.isNaN(rotation)
                                || Float.isNaN(large) || Float.isNaN(sweep)) {
                            pos[0] = save;
                            break;
                        }
                        if (relative) {
                            x += cx;
                            y += cy;
                        }
                        arcTo(path, cx, cy, x, y, rx, ry, rotation,
                                large != 0f, sweep != 0f);
                        cx = x;
                        cy = y;
                    }
                    break;
                }
                case 'Z': {
                    path.close();
                    cx = sx;
                    cy = sy;
                    break;
                }
                default:
                    // Unknown command: skip one number so we cannot loop forever.
                    number(data, pos);
                    break;
            }
            // S/T reflect the last cubic/quadratic control point of the same type.
            if (op != 'C' && op != 'S') {
                lastCx = cx;
                lastCy = cy;
            }
            if (op != 'Q' && op != 'T') {
                lastQx = cx;
                lastQy = cy;
            }
        }
        return path;
    }

    /**
     * Endpoint parameterization of an elliptical arc, approximated with cubic
     * Beziers (at most 90 degrees each). Standard SVG implementation.
     */
    private static void arcTo(Path path, float x0, float y0, float x1, float y1,
                              float rx, float ry, float rotationDeg,
                              boolean largeArc, boolean sweep) {
        if (x0 == x1 && y0 == y1) return;
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        if (rx == 0f || ry == 0f) {
            path.lineTo(x1, y1);
            return;
        }
        double phi = Math.toRadians(rotationDeg % 360f);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);
        double dx2 = (x0 - x1) / 2.0;
        double dy2 = (y0 - y1) / 2.0;
        double x1p = cosPhi * dx2 + sinPhi * dy2;
        double y1p = -sinPhi * dx2 + cosPhi * dy2;

        double rxs = rx * (double) rx;
        double rys = ry * (double) ry;
        double x1ps = x1p * x1p;
        double y1ps = y1p * y1p;
        double cr = x1ps / rxs + y1ps / rys;
        if (cr > 1) {
            double s = Math.sqrt(cr);
            rx = (float) (rx * s);
            ry = (float) (ry * s);
            rxs = rx * (double) rx;
            rys = ry * (double) ry;
        }
        double dq = rxs * y1ps + rys * x1ps;
        double pq = Math.max(0, (rxs * rys - dq) / Math.max(dq, 1e-12));
        double q = Math.sqrt(pq);
        if (largeArc == sweep) q = -q;
        double cxp = q * rx * y1p / ry;
        double cyp = -q * ry * x1p / rx;
        double cx = cosPhi * cxp - sinPhi * cyp + (x0 + x1) / 2.0;
        double cy = sinPhi * cxp + cosPhi * cyp + (y0 + y1) / 2.0;

        double ux = (x1p - cxp) / rx;
        double uy = (y1p - cyp) / ry;
        double vx = (-x1p - cxp) / rx;
        double vy = (-y1p - cyp) / ry;
        double theta1 = Math.atan2(uy, ux);
        double dtheta = angleBetween(ux, uy, vx, vy);
        if (!sweep && dtheta > 0) dtheta -= Math.PI * 2;
        else if (sweep && dtheta < 0) dtheta += Math.PI * 2;

        int segments = (int) Math.ceil(Math.abs(dtheta) / (Math.PI / 2));
        if (segments < 1) segments = 1;
        double delta = dtheta / segments;
        double t = 4.0 / 3.0 * Math.tan(delta / 4.0);
        for (int i = 0; i < segments; i++) {
            double a = theta1 + i * delta;
            double b = a + delta;
            double e1x = rx * Math.cos(a), e1y = ry * Math.sin(a);
            double e2x = rx * Math.cos(b), e2y = ry * Math.sin(b);
            double d1x = -rx * Math.sin(a), d1y = ry * Math.cos(a);
            double d2x = -rx * Math.sin(b), d2y = ry * Math.cos(b);
            double c1x = e1x + t * d1x, c1y = e1y + t * d1y;
            double c2x = e2x - t * d2x, c2y = e2y - t * d2y;
            path.cubicTo(
                    (float) (cosPhi * c1x - sinPhi * c1y + cx),
                    (float) (sinPhi * c1x + cosPhi * c1y + cy),
                    (float) (cosPhi * c2x - sinPhi * c2y + cx),
                    (float) (sinPhi * c2x + cosPhi * c2y + cy),
                    (float) (cosPhi * e2x - sinPhi * e2y + cx),
                    (float) (sinPhi * e2x + cosPhi * e2y + cy));
        }
    }

    private static double angleBetween(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double len = Math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy));
        double cos = len == 0 ? 1 : Math.max(-1, Math.min(1, dot / len));
        double a = Math.acos(cos);
        if (ux * vy - uy * vx < 0) a = -a;
        return a;
    }

    private static boolean isCommand(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static void skipSeparators(String s, int[] pos) {
        int n = s.length();
        while (pos[0] < n) {
            char c = s.charAt(pos[0]);
            if (c == ' ' || c == ',' || c == '\n' || c == '\r' || c == '\t') pos[0]++;
            else break;
        }
    }

    /** Reads one float, or NaN when the next token is not a number. */
    private static float number(String s, int[] pos) {
        skipSeparators(s, pos);
        int n = s.length();
        int start = pos[0];
        if (pos[0] < n && (s.charAt(pos[0]) == '+' || s.charAt(pos[0]) == '-')) pos[0]++;
        boolean dot = false;
        boolean exponent = false;
        boolean digit = false;
        while (pos[0] < n) {
            char c = s.charAt(pos[0]);
            if (c >= '0' && c <= '9') {
                digit = true;
                pos[0]++;
            } else if (c == '.' && !dot && !exponent) {
                dot = true;
                pos[0]++;
            } else if ((c == 'e' || c == 'E') && !exponent && digit) {
                exponent = true;
                pos[0]++;
                if (pos[0] < n && (s.charAt(pos[0]) == '+' || s.charAt(pos[0]) == '-')) pos[0]++;
            } else {
                break;
            }
        }
        if (start == pos[0] || !digit) return Float.NaN;
        try {
            return Float.parseFloat(s.substring(start, pos[0]));
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }
}
