package io.github.abdurazaaqmohammed.domain.math;

import java.text.DecimalFormat;

/**
 * Scientific expression evaluator (+, -, *, /, %, ^, brackets,
 * sin/cos/tan/sqrt/log/ln in degrees, pi, e).
 * Pure logic extracted from ToolRunnerActivity; no Android dependencies.
 */
public final class ExpressionEvaluator {

    private ExpressionEvaluator() {
    }

    public static double eval(String expr) throws Exception {
        if (expr == null || expr.trim().isEmpty()) {
            return 0;
        }
        String s = expr.replace("\u00d7", "*").replace("\u00f7", "-DIV-");
        s = s.replace("-DIV-", "/");
        s = s.replace(" ", "");
        return new ExprParser(s).parse();
    }

    public static String format(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "Error";
        }
        return new DecimalFormat("0.##########").format(v);
    }

    static final class ExprParser {
        private final String str;
        private int pos = 0;

        ExprParser(String s) {
            str = s;
        }

        double parse() throws Exception {
            double v = parseAddSub();
            if (pos < str.length()) {
                throw new Exception("Unexpected");
            }
            return v;
        }

        private double parseAddSub() throws Exception {
            double v = parseMulDiv();
            while (pos < str.length()) {
                char c = str.charAt(pos);
                if (c == '+') {
                    pos++;
                    v += parseMulDiv();
                } else if (c == '-') {
                    pos++;
                    v -= parseMulDiv();
                } else {
                    break;
                }
            }
            return v;
        }

        private double parseMulDiv() throws Exception {
            double v = parsePower();
            while (pos < str.length()) {
                char c = str.charAt(pos);
                if (c == '*') {
                    pos++;
                    v *= parsePower();
                } else if (c == '/') {
                    pos++;
                    double d = parsePower();
                    v = v / d;
                } else if (c == '%') {
                    pos++;
                    double d = parsePower();
                    v = v % d;
                } else {
                    break;
                }
            }
            return v;
        }

        private double parsePower() throws Exception {
            double v = parseUnary();
            if (pos < str.length() && str.charAt(pos) == '^') {
                pos++;
                double e = parseUnary();
                v = Math.pow(v, e);
            }
            return v;
        }

        private double parseUnary() throws Exception {
            if (pos < str.length() && str.charAt(pos) == '+') {
                pos++;
                return parseUnary();
            }
            if (pos < str.length() && str.charAt(pos) == '-') {
                pos++;
                return -parseUnary();
            }
            return parsePrimary();
        }

        private double parsePrimary() throws Exception {
            if (pos < str.length() && str.charAt(pos) == '(') {
                pos++;
                double v = parseAddSub();
                if (pos >= str.length() || str.charAt(pos) != ')') {
                    throw new Exception("Bracket");
                }
                pos++;
                return v;
            }
            if (matchWord("sin")) {
                expect('(');
                double v = parseAddSub();
                expect(')');
                return Math.sin(Math.toRadians(v));
            }
            if (matchWord("cos")) {
                expect('(');
                double v = parseAddSub();
                expect(')');
                return Math.cos(Math.toRadians(v));
            }
            if (matchWord("tan")) {
                expect('(');
                double v = parseAddSub();
                expect(')');
                return Math.tan(Math.toRadians(v));
            }
            if (matchWord("sqrt")) {
                expect('(');
                double v = parseAddSub();
                expect(')');
                return Math.sqrt(v);
            }
            if (matchWord("log")) {
                expect('(');
                double v = parseAddSub();
                expect(')');
                return Math.log10(v);
            }
            if (matchWord("ln")) {
                expect('(');
                double v = parseAddSub();
                expect(')');
                return Math.log(v);
            }
            if (matchWord("pi")) {
                return Math.PI;
            }
            if (pos < str.length() && (str.charAt(pos) == 'e' || str.charAt(pos) == 'E')) {
                pos++;
                return Math.E;
            }
            int start = pos;
            boolean dotSeen = false;
            while (pos < str.length()) {
                char c = str.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' && !dotSeen) {
                    dotSeen = true;
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) {
                throw new Exception("Number");
            }
            return Double.parseDouble(str.substring(start, pos));
        }

        private boolean matchWord(String w) {
            if (str.startsWith(w, pos)) {
                int after = pos + w.length();
                if (after < str.length() && Character.isLetter(str.charAt(after))) {
                    return false;
                }
                pos += w.length();
                return true;
            }
            return false;
        }

        private void expect(char c) throws Exception {
            if (pos >= str.length() || str.charAt(pos) != c) {
                throw new Exception("Expected");
            }
            pos++;
        }
    }
}
