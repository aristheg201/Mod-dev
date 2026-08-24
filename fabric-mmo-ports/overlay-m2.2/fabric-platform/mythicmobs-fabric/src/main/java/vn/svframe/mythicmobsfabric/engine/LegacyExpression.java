package vn.svframe.mythicmobsfabric.engine;

import java.util.Locale;

/**
 * Native evaluator for the exp4j surface used by MythicMobs 5.6.2 equations.
 * Custom Mythic operators use precedence 499, immediately below addition.
 */
final class LegacyExpression {
    @FunctionalInterface
    interface Variables {
        double value(String name);
    }

    private final Node root;

    private LegacyExpression(Node root) {
        this.root = root;
    }

    static LegacyExpression compile(String source) {
        return new LegacyExpression(new Parser(source == null ? "0" : source).parse());
    }

    double eval(Variables variables) {
        return root.eval(variables);
    }

    @FunctionalInterface
    private interface Node {
        double eval(Variables variables);
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) {
            this.source = source;
        }

        private Node parse() {
            Node result = comparison();
            whitespace();
            if (index != source.length()) throw error("Unexpected token");
            return result;
        }

        private Node comparison() {
            Node left = additive();
            while (true) {
                whitespace();
                String operator = comparisonOperator();
                if (operator == null) return left;
                Node a = left;
                Node b = additive();
                left = switch (operator) {
                    case "<" -> vars -> a.eval(vars) < b.eval(vars) ? 1.0 : 0.0;
                    case "<=" -> vars -> a.eval(vars) <= b.eval(vars) ? 1.0 : 0.0;
                    case ">" -> vars -> a.eval(vars) > b.eval(vars) ? 1.0 : 0.0;
                    case ">=" -> vars -> a.eval(vars) >= b.eval(vars) ? 1.0 : 0.0;
                    case "==" -> vars -> a.eval(vars) == b.eval(vars) ? 1.0 : 0.0;
                    default -> throw new IllegalStateException(operator);
                };
            }
        }

        private String comparisonOperator() {
            if (take("<=")) return "<=";
            if (take(">=")) return ">=";
            if (take("==")) return "==";
            if (take("<")) return "<";
            if (take(">")) return ">";
            return null;
        }

        private Node additive() {
            Node left = term();
            while (true) {
                whitespace();
                if (take('+')) {
                    Node a = left, b = term();
                    left = vars -> a.eval(vars) + b.eval(vars);
                } else if (take('-')) {
                    Node a = left, b = term();
                    left = vars -> a.eval(vars) - b.eval(vars);
                } else return left;
            }
        }

        private Node term() {
            Node left = power();
            while (true) {
                whitespace();
                if (take('*')) {
                    Node a = left, b = power();
                    left = vars -> a.eval(vars) * b.eval(vars);
                } else if (take('/')) {
                    Node a = left, b = power();
                    left = vars -> a.eval(vars) / b.eval(vars);
                } else if (take('%')) {
                    Node a = left, b = power();
                    left = vars -> a.eval(vars) % b.eval(vars);
                } else return left;
            }
        }

        private Node power() {
            Node left = unary();
            whitespace();
            if (!take('^')) return left;
            Node right = power();
            return vars -> Math.pow(left.eval(vars), right.eval(vars));
        }

        private Node unary() {
            whitespace();
            if (take('+')) return unary();
            if (take('-')) {
                Node value = unary();
                return vars -> -value.eval(vars);
            }
            return primary();
        }

        private Node primary() {
            whitespace();
            if (take('(')) {
                Node value = comparison();
                expect(')');
                return value;
            }
            if (index < source.length() && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '.')) {
                int start = index++;
                while (index < source.length()) {
                    char c = source.charAt(index);
                    if (!Character.isDigit(c) && c != '.' && c != 'e' && c != 'E' && c != '+' && c != '-') break;
                    if ((c == '+' || c == '-') && source.charAt(index - 1) != 'e' && source.charAt(index - 1) != 'E') break;
                    index++;
                }
                final double number;
                try {
                    number = Double.parseDouble(source.substring(start, index));
                } catch (NumberFormatException exception) {
                    throw error("Invalid number");
                }
                return vars -> number;
            }

            String name = identifier();
            if (name.isEmpty()) throw error("Expected value");
            whitespace();
            if (!take('(')) return vars -> constantOrVariable(vars, name);

            Node first = comparison();
            whitespace();
            Node second = null;
            if (take(',')) second = comparison();
            expect(')');
            return function(name, first, second);
        }

        private static double constantOrVariable(Variables vars, String rawName) {
            return switch (rawName.toLowerCase(Locale.ROOT)) {
                case "pi", "π" -> Math.PI;
                case "e" -> Math.E;
                default -> vars.value(rawName);
            };
        }

        private Node function(String rawName, Node a, Node b) {
            String name = rawName.toLowerCase(Locale.ROOT);
            return switch (name) {
                case "sin" -> vars -> Math.sin(a.eval(vars));
                case "cos" -> vars -> Math.cos(a.eval(vars));
                case "tan" -> vars -> Math.tan(a.eval(vars));
                case "cot" -> vars -> 1.0 / Math.tan(a.eval(vars));
                case "asin" -> vars -> Math.asin(a.eval(vars));
                case "acos" -> vars -> Math.acos(a.eval(vars));
                case "atan" -> vars -> Math.atan(a.eval(vars));
                case "sinh" -> vars -> Math.sinh(a.eval(vars));
                case "cosh" -> vars -> Math.cosh(a.eval(vars));
                case "tanh" -> vars -> Math.tanh(a.eval(vars));
                case "abs" -> vars -> Math.abs(a.eval(vars));
                case "sqrt" -> vars -> Math.sqrt(a.eval(vars));
                case "cbrt" -> vars -> Math.cbrt(a.eval(vars));
                case "floor" -> vars -> Math.floor(a.eval(vars));
                case "ceil" -> vars -> Math.ceil(a.eval(vars));
                case "round" -> vars -> Math.rint(a.eval(vars));
                case "signum", "sign" -> vars -> Math.signum(a.eval(vars));
                case "exp" -> vars -> Math.exp(a.eval(vars));
                case "expm1" -> vars -> Math.expm1(a.eval(vars));
                case "log", "ln" -> vars -> Math.log(a.eval(vars));
                case "log10" -> vars -> Math.log10(a.eval(vars));
                case "log2" -> vars -> Math.log(a.eval(vars)) / Math.log(2.0);
                case "log1p" -> vars -> Math.log1p(a.eval(vars));
                case "min" -> binary(name, a, b, Math::min);
                case "max" -> binary(name, a, b, Math::max);
                case "pow" -> binary(name, a, b, Math::pow);
                case "atan2" -> binary(name, a, b, Math::atan2);
                case "random" -> binary(name, a, b, (min, max) -> min + Math.random() * (max - min));
                default -> throw error("Unknown function " + rawName);
            };
        }

        private Node binary(String name, Node a, Node b, BinaryMath function) {
            if (b == null) throw error(name + " requires two arguments");
            return vars -> function.apply(a.eval(vars), b.eval(vars));
        }

        private String identifier() {
            whitespace();
            int start = index;
            while (index < source.length()) {
                char c = source.charAt(index);
                if (!Character.isLetterOrDigit(c) && c != '_' && c != 'π') break;
                index++;
            }
            return source.substring(start, index);
        }

        private void expect(char expected) {
            whitespace();
            if (!take(expected)) throw error("Expected '" + expected + "'");
        }

        private boolean take(char value) {
            if (index < source.length() && source.charAt(index) == value) {
                index++;
                return true;
            }
            return false;
        }

        private boolean take(String value) {
            if (source.startsWith(value, index)) {
                index += value.length();
                return true;
            }
            return false;
        }

        private void whitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + index + " in equation '" + source + "'");
        }
    }

    @FunctionalInterface
    private interface BinaryMath {
        double apply(double a, double b);
    }
}
