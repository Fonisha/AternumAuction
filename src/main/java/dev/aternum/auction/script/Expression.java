package dev.aternum.auction.script;

import java.util.Locale;
import java.util.Map;

/**
 * Small, dependency-free expression evaluator for admin scripts.
 *
 * Supported syntax:
 *   numbers, %variable% placeholders, 'strings' and "strings",
 *   + - * / % ( ), comparisons &gt; &lt; &gt;= &lt;= == !=,
 *   logic &amp;&amp; || !, functions min, max, round, floor, ceil, abs,
 *   sqrt, clamp, contains, lower, upper, length, if.
 *
 * No reflection, no script engine, no file or network access is available
 * to an expression, so a broken or malicious script cannot harm the server.
 *
 * @author Fonisha
 */
public final class Expression {
    private final String input;
    private final Map<String, Object> variables;
    private final int maxSteps;
    private int position;
    private int steps;

    private Expression(String input, Map<String, Object> variables, int maxSteps) {
        this.input = input == null ? "" : input;
        this.variables = variables;
        this.maxSteps = Math.max(64, maxSteps);
    }

    /** Evaluates an expression and returns Number, String or Boolean. */
    public static Object evaluate(String expression, Map<String, Object> variables, int maxSteps) {
        Expression parser = new Expression(expression, variables, maxSteps);
        Object value = parser.parseOr();
        parser.skipWhitespace();
        if (parser.position < parser.input.length()) {
            throw new IllegalArgumentException("Unexpected character '" + parser.peek() + "' at " + parser.position);
        }
        return value;
    }

    public static double number(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof Boolean) return ((Boolean) value) ? 1 : 0;
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    public static boolean bool(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text)) return true;
            if ("false".equalsIgnoreCase(text) || text.isEmpty()) return false;
            return number(text) != 0;
        }
        return false;
    }

    public static String text(Object value) {
        if (value == null) return "";
        if (value instanceof Boolean) return ((Boolean) value) ? "true" : "false";
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (number == Math.rint(number) && !Double.isInfinite(number)) return String.valueOf((long) number);
            return String.valueOf(number);
        }
        return String.valueOf(value);
    }

    // ---------- grammar ----------

    private Object parseOr() {
        Object left = parseAnd();
        while (match("||")) {
            Object right = parseAnd();
            left = bool(left) || bool(right);
        }
        return left;
    }

    private Object parseAnd() {
        Object left = parseComparison();
        while (match("&&")) {
            Object right = parseComparison();
            left = bool(left) && bool(right);
        }
        return left;
    }

    private Object parseComparison() {
        Object left = parseAdditive();
        while (true) {
            String operator = null;
            if (match(">=")) operator = ">=";
            else if (match("<=")) operator = "<=";
            else if (match("==")) operator = "==";
            else if (match("!=")) operator = "!=";
            else if (match(">")) operator = ">";
            else if (match("<")) operator = "<";
            if (operator == null) return left;
            Object right = parseAdditive();
            left = compare(left, operator, right);
        }
    }

    private Object parseAdditive() {
        Object left = parseMultiplicative();
        while (true) {
            if (match("+")) left = number(left) + number(parseMultiplicative());
            else if (match("-")) left = number(left) - number(parseMultiplicative());
            else return left;
        }
    }

    private Object parseMultiplicative() {
        Object left = parseUnary();
        while (true) {
            if (match("*")) left = number(left) * number(parseUnary());
            else if (match("/")) {
                double divisor = number(parseUnary());
                left = divisor == 0 ? 0 : number(left) / divisor;
            } else if (match("%")) {
                double divisor = number(parseUnary());
                left = divisor == 0 ? 0 : number(left) % divisor;
            } else return left;
        }
    }

    private Object parseUnary() {
        if (match("!")) return !bool(parseUnary());
        if (match("-")) return -number(parseUnary());
        if (match("+")) return number(parseUnary());
        return parsePrimary();
    }

    private Object parsePrimary() {
        step();
        skipWhitespace();
        if (position >= input.length()) throw new IllegalArgumentException("Unexpected end of expression");
        char character = input.charAt(position);

        if (character == '(') {
            position++;
            Object value = parseOr();
            skipWhitespace();
            if (position >= input.length() || input.charAt(position) != ')') {
                throw new IllegalArgumentException("Missing closing bracket");
            }
            position++;
            return value;
        }
        if (character == '\'' || character == '"') return readString(character);
        if (character == '%') return readVariable();

        if (Character.isDigit(character) || (character == '.' && position + 1 < input.length()
                && Character.isDigit(input.charAt(position + 1)))) {
            return readNumber();
        }
        if (Character.isLetter(character) || character == '_') {
            String name = readIdentifier();
            if ("true".equalsIgnoreCase(name)) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(name)) return Boolean.FALSE;
            skipWhitespace();
            if (position < input.length() && input.charAt(position) == '(') {
                position++;
                java.util.List<Object> arguments = new java.util.ArrayList<Object>();
                skipWhitespace();
                if (position < input.length() && input.charAt(position) != ')') {
                    while (true) {
                        arguments.add(parseOr());
                        skipWhitespace();
                        if (position < input.length() && input.charAt(position) == ',') {
                            position++;
                            continue;
                        }
                        break;
                    }
                }
                skipWhitespace();
                if (position >= input.length() || input.charAt(position) != ')') {
                    throw new IllegalArgumentException("Missing closing bracket in " + name);
                }
                position++;
                return call(name, arguments);
            }
            throw new IllegalArgumentException("Unexpected identifier '" + name + "' (use %name% for variables)");
        }
        throw new IllegalArgumentException("Unexpected character '" + character + "' at " + position);
    }

    // ---------- helpers ----------

    private boolean match(String token) {
        skipWhitespace();
        if (input.startsWith(token, position)) {
            position += token.length();
            return true;
        }
        return false;
    }

    private void skipWhitespace() {
        while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++;
    }

    private char peek() {
        return position < input.length() ? input.charAt(position) : '\0';
    }

    private void step() {
        if (++steps > maxSteps) throw new IllegalArgumentException("Expression is too complex");
    }

    private String readIdentifier() {
        int start = position;
        while (position < input.length()) {
            char character = input.charAt(position);
            if (!Character.isLetterOrDigit(character) && character != '_') break;
            position++;
        }
        return input.substring(start, position);
    }

    private String readString(char quote) {
        position++;
        StringBuilder result = new StringBuilder();
        while (position < input.length()) {
            char character = input.charAt(position++);
            if (character == '\\' && position < input.length()) {
                result.append(input.charAt(position++));
            } else if (character == quote) {
                return result.toString();
            } else {
                result.append(character);
            }
        }
        throw new IllegalArgumentException("Unclosed string literal");
    }

    private Object readVariable() {
        position++;
        int start = position;
        while (position < input.length() && input.charAt(position) != '%') position++;
        if (position >= input.length()) throw new IllegalArgumentException("Unclosed variable placeholder");
        String name = input.substring(start, position).toLowerCase(Locale.ENGLISH);
        position++;
        if (variables == null) return "";
        Object value = variables.get(name);
        return value == null ? "" : value;
    }

    private Double readNumber() {
        int start = position;
        while (position < input.length()) {
            char character = input.charAt(position);
            if (!Character.isDigit(character) && character != '.') break;
            position++;
        }
        try {
            return Double.valueOf(input.substring(start, position));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid number '" + input.substring(start, position) + "'");
        }
    }

    private Boolean compare(Object left, String operator, Object right) {
        boolean numeric = left instanceof Number || right instanceof Number
                || isNumericString(left) || isNumericString(right);
        int result;
        if (numeric) {
            double first = number(left);
            double second = number(right);
            result = Double.compare(first, second);
        } else {
            result = text(left).compareToIgnoreCase(text(right));
        }
        if ("==".equals(operator)) return result == 0;
        if ("!=".equals(operator)) return result != 0;
        if (">".equals(operator)) return result > 0;
        if ("<".equals(operator)) return result < 0;
        if (">=".equals(operator)) return result >= 0;
        return result <= 0;
    }

    private boolean isNumericString(Object value) {
        if (!(value instanceof String)) return false;
        try {
            Double.parseDouble(((String) value).trim());
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private Object call(String name, java.util.List<Object> arguments) {
        String function = name.toLowerCase(Locale.ENGLISH);
        double first = arguments.isEmpty() ? 0 : number(arguments.get(0));
        double second = arguments.size() > 1 ? number(arguments.get(1)) : 0;
        double third = arguments.size() > 2 ? number(arguments.get(2)) : 0;
        if ("min".equals(function)) return Math.min(first, second);
        if ("max".equals(function)) return Math.max(first, second);
        if ("round".equals(function)) return Math.round(first);
        if ("floor".equals(function)) return Math.floor(first);
        if ("ceil".equals(function)) return Math.ceil(first);
        if ("abs".equals(function)) return Math.abs(first);
        if ("sqrt".equals(function)) return Math.sqrt(Math.max(0, first));
        if ("clamp".equals(function)) return Math.max(second, Math.min(third, first));
        if ("len".equals(function) || "length".equals(function)) return text(first).length();
        if ("lower".equals(function)) return text(first).toLowerCase(Locale.ENGLISH);
        if ("upper".equals(function)) return text(first).toUpperCase(Locale.ENGLISH);
        if ("contains".equals(function)) return text(first).toLowerCase(Locale.ENGLISH)
                .contains(text(second).toLowerCase(Locale.ENGLISH));
        if ("startswith".equals(function)) return text(first).toLowerCase(Locale.ENGLISH)
                .startsWith(text(second).toLowerCase(Locale.ENGLISH));
        if ("if".equals(function)) {
            boolean condition = arguments.size() > 2 && bool(arguments.get(0));
            return condition ? arguments.get(1) : arguments.get(2);
        }
        throw new IllegalArgumentException("Unknown function '" + name + "'");
    }
}
