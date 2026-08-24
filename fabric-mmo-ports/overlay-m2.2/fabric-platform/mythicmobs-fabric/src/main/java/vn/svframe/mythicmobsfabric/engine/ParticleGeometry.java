package vn.svframe.mythicmobsfabric.engine;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Geometry-aware implementation for legacy MythicMobs particle mechanics. */
public final class ParticleGeometry {
    private ParticleGeometry() {}

    public static boolean cast(SkillPlatform platform, SkillContext ctx, SkillLine line, UUID entity, Vec3 loc) {
        Vec3 center = loc != null ? loc : entity != null ? platform.position(entity) : ctx.origin();
        if (center == null) return false;
        String kind = SkillLine.normalize(line.mechanic());
        return switch (kind) {
            case "particleline" -> line(platform, ctx, line, center, false);
            case "particlelinehelix" -> line(platform, ctx, line, center, true);
            case "particlering", "particleorbital" -> ring(platform, line, center);
            case "particlesphere" -> sphere(platform, line, center);
            case "particlebox" -> box(platform, line, center);
            case "particlewave" -> wave(platform, line, center);
            case "particletornado" -> tornado(platform, line, center);
            case "particleatom" -> atom(platform, line, center);
            case "particlelinering" -> lineRing(platform, ctx, line, center);
            case "particleequation" -> equation(platform, line, center);
            case "particlelineequation" -> lineEquation(platform, ctx, line, center);
            default -> emit(platform, line, center, Math.max(1, line.integer("amount", 10, "a")), true);
        };
    }

    private static boolean line(SkillPlatform platform, SkillContext ctx, SkillLine skill, Vec3 end, boolean helix) {
        Vec3 start = ctx.origin() == null ? platform.position(ctx.caster()) : ctx.origin();
        if (start == null) return false;
        int points = clamp(skill.integer("points", skill.integer("amount", 20, "a")), 2, 256);
        double radius = Math.max(0.0, skill.decimal("radius", skill.decimal("r", 0.35)));
        double turns = Math.max(0.25, skill.decimal("turns", skill.decimal("t", 2.0)));
        Vec3 direction = end.subtract(start);
        Vec3 axis = direction.normalize();
        Vec3 side = perpendicular(axis);
        Vec3 up = cross(axis, side).normalize();
        boolean any = false;
        for (int i = 0; i < points; i++) {
            double progress = (double) i / (points - 1);
            Vec3 point = start.add(direction.multiply(progress));
            if (helix && radius > 0.0) {
                double angle = Math.PI * 2.0 * turns * progress;
                point = point.add(side.multiply(Math.cos(angle) * radius)).add(up.multiply(Math.sin(angle) * radius));
            }
            any |= emit(platform, skill, point, 1, false);
        }
        return any;
    }

    private static boolean ring(SkillPlatform platform, SkillLine skill, Vec3 center) {
        double radius = Math.max(0.0, skill.decimal("radius", skill.decimal("r", 1.0)));
        int points = clamp(skill.integer("points", skill.integer("amount", 24, "a")), 4, 256);
        double y = skill.decimal("yoffset", skill.decimal("y", 0.0));
        boolean any = false;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            any |= emit(platform, skill, center.add(new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius)), 1, false);
        }
        return any;
    }

    private static boolean sphere(SkillPlatform platform, SkillLine skill, Vec3 center) {
        double radius = Math.max(0.0, skill.decimal("radius", skill.decimal("r", 1.0)));
        int points = clamp(skill.integer("points", skill.integer("amount", 48, "a")), 6, 384);
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        boolean any = false;
        for (int i = 0; i < points; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / points;
            double radial = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double angle = goldenAngle * i;
            any |= emit(platform, skill, center.add(new Vec3(Math.cos(angle) * radial * radius, y * radius, Math.sin(angle) * radial * radius)), 1, false);
        }
        return any;
    }

    private static boolean box(SkillPlatform platform, SkillLine skill, Vec3 center) {
        double radius = skill.decimal("radius", skill.decimal("r", 1.0));
        double rx = Math.max(0.0, skill.decimal("xradius", radius));
        double ry = Math.max(0.0, skill.decimal("yradius", radius));
        double rz = Math.max(0.0, skill.decimal("zradius", radius));
        int edgePoints = clamp(skill.integer("edgepoints", Math.max(2, skill.integer("amount", 24, "a") / 12)), 2, 32);
        boolean any = false;
        for (int axis = 0; axis < 3; axis++) {
            for (int a = -1; a <= 1; a += 2) {
                for (int b = -1; b <= 1; b += 2) {
                    for (int i = 0; i < edgePoints; i++) {
                        double t = -1.0 + 2.0 * i / (edgePoints - 1.0);
                        Vec3 offset = switch (axis) {
                            case 0 -> new Vec3(t * rx, a * ry, b * rz);
                            case 1 -> new Vec3(a * rx, t * ry, b * rz);
                            default -> new Vec3(a * rx, b * ry, t * rz);
                        };
                        any |= emit(platform, skill, center.add(offset), 1, false);
                    }
                }
            }
        }
        return any;
    }

    private static boolean wave(SkillPlatform platform, SkillLine skill, Vec3 center) {
        double radius = Math.max(0.0, skill.decimal("radius", skill.decimal("r", 3.0)));
        int rings = clamp(skill.integer("rings", 4), 1, 16);
        int points = clamp(skill.integer("points", skill.integer("amount", 48, "a")), 8, 384);
        boolean any = false;
        for (int ring = 1; ring <= rings; ring++) {
            double rr = radius * ring / rings;
            int ringPoints = Math.max(8, points * ring / rings);
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints;
                any |= emit(platform, skill, center.add(new Vec3(Math.cos(angle) * rr, 0.0, Math.sin(angle) * rr)), 1, false);
            }
        }
        return any;
    }

    private static boolean tornado(SkillPlatform platform, SkillLine skill, Vec3 center) {
        double height = Math.max(0.05, skill.decimal("height", skill.decimal("h", 3.0)));
        double radius = Math.max(0.0, skill.decimal("radius", skill.decimal("r", 1.5)));
        double turns = Math.max(0.25, skill.decimal("turns", skill.decimal("t", 3.0)));
        int points = clamp(skill.integer("points", skill.integer("amount", 64, "a")), 8, 384);
        boolean any = false;
        for (int i = 0; i < points; i++) {
            double progress = (double) i / (points - 1);
            double angle = Math.PI * 2.0 * turns * progress;
            double rr = radius * progress;
            any |= emit(platform, skill, center.add(new Vec3(Math.cos(angle) * rr, height * progress, Math.sin(angle) * rr)), 1, false);
        }
        return any;
    }

    private static boolean atom(SkillPlatform platform, SkillLine skill, Vec3 center) {
        double radius = Math.max(0.0, skill.decimal("radius", skill.decimal("r", 1.0)));
        int points = clamp(skill.integer("points", skill.integer("amount", 48, "a")), 12, 384);
        boolean any = false;
        for (int plane = 0; plane < 3; plane++) {
            int ringPoints = Math.max(4, points / 3);
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints;
                double a = Math.cos(angle) * radius;
                double b = Math.sin(angle) * radius;
                Vec3 offset = switch (plane) {
                    case 0 -> new Vec3(a, 0.0, b);
                    case 1 -> new Vec3(a, b, 0.0);
                    default -> new Vec3(0.0, a, b);
                };
                any |= emit(platform, skill, center.add(offset), 1, false);
            }
        }
        return any;
    }

    private static boolean lineRing(SkillPlatform platform, SkillContext ctx, SkillLine skill, Vec3 end) {
        Vec3 start = ctx.origin() == null ? platform.position(ctx.caster()) : ctx.origin();
        if (start == null) return false;
        int segments = clamp(skill.integer("segments", 8), 2, 32);
        double radius = Math.max(0.0, skill.decimal("radius", skill.decimal("r", 0.5)));
        int ringPoints = clamp(skill.integer("ringpoints", 12), 4, 64);
        Vec3 direction = end.subtract(start);
        Vec3 axis = direction.normalize();
        Vec3 side = perpendicular(axis);
        Vec3 up = cross(axis, side).normalize();
        boolean any = false;
        for (int segment = 0; segment < segments; segment++) {
            double progress = (double) segment / (segments - 1);
            Vec3 c = start.add(direction.multiply(progress));
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints;
                Vec3 point = c.add(side.multiply(Math.cos(angle) * radius)).add(up.multiply(Math.sin(angle) * radius));
                any |= emit(platform, skill, point, 1, false);
            }
        }
        return any;
    }

    private static boolean equation(SkillPlatform platform, SkillLine skill, Vec3 center) {
        EquationSpec spec = EquationSpec.from(skill);
        int points = clamp(skill.integer("points", skill.integer("amount", 64, "a")), 2, 512);
        double start = skill.decimal("start", skill.decimal("s", 0.0));
        double end = skill.decimal("end", skill.decimal("e", Math.PI * 2.0));
        boolean any = false;
        for (int i = 0; i < points; i++) {
            double progress = points == 1 ? 0.0 : (double) i / (points - 1);
            double t = start + (end - start) * progress;
            Variables vars = new Variables(t, progress, i, points, skill);
            Vec3 offset = new Vec3(spec.x.eval(vars), spec.y.eval(vars), spec.z.eval(vars));
            if (finite(offset)) any |= emit(platform, skill, center.add(offset), 1, false);
        }
        return any;
    }

    private static boolean lineEquation(SkillPlatform platform, SkillContext ctx, SkillLine skill, Vec3 end) {
        Vec3 start = ctx.origin() == null ? platform.position(ctx.caster()) : ctx.origin();
        if (start == null) return false;
        EquationSpec spec = EquationSpec.from(skill);
        int points = clamp(skill.integer("points", skill.integer("amount", 64, "a")), 2, 512);
        Vec3 direction = end.subtract(start);
        Vec3 axis = direction.normalize();
        Vec3 side = perpendicular(axis);
        Vec3 up = cross(axis, side).normalize();
        double parameterStart = skill.decimal("start", skill.decimal("s", 0.0));
        double parameterEnd = skill.decimal("end", skill.decimal("e", Math.PI * 2.0));
        boolean any = false;
        for (int i = 0; i < points; i++) {
            double progress = (double) i / (points - 1);
            double t = parameterStart + (parameterEnd - parameterStart) * progress;
            Variables vars = new Variables(t, progress, i, points, skill);
            double x = spec.x.eval(vars);
            double y = spec.y.eval(vars);
            double z = spec.z.eval(vars);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) continue;
            Vec3 base = start.add(direction.multiply(progress));
            Vec3 point = base.add(side.multiply(x)).add(up.multiply(y)).add(axis.multiply(z));
            any |= emit(platform, skill, point, 1, false);
        }
        return any;
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x()) && Double.isFinite(value.y()) && Double.isFinite(value.z());
    }

    private static boolean emit(SkillPlatform platform, SkillLine skill, Vec3 point, int amount, boolean spread) {
        String particle = skill.string("particle", skill.string("p", "crit"));
        double hSpread = spread ? skill.decimal("hspread", skill.decimal("spread", 0.2)) : 0.0;
        double vSpread = spread ? skill.decimal("vspread", skill.decimal("spread", 0.2)) : 0.0;
        return platform.particle(point, particle, Math.max(1, amount), hSpread, vSpread, hSpread, skill.decimal("speed", 0.01));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Vec3 perpendicular(Vec3 axis) {
        if (axis == null) return new Vec3(1, 0, 0);
        Vec3 reference = Math.abs(axis.y()) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 result = cross(axis, reference).normalize();
        return result.lengthSquared() == 0.0 ? new Vec3(1, 0, 0) : result;
    }

    private static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x());
    }

    private record EquationSpec(Expression x, Expression y, Expression z) {
        static EquationSpec from(SkillLine skill) {
            String combined = skill.string("equation", skill.string("eq", ""));
            String x = skill.string("x", "");
            String y = skill.string("y", "");
            String z = skill.string("z", "");
            if (!combined.isBlank()) {
                for (String part : combined.split("[;,]")) {
                    int equals = part.indexOf('=');
                    if (equals <= 0) continue;
                    String key = part.substring(0, equals).trim().toLowerCase(Locale.ROOT);
                    String value = part.substring(equals + 1).trim();
                    if (key.equals("x")) x = value;
                    else if (key.equals("y")) y = value;
                    else if (key.equals("z")) z = value;
                }
            }
            return new EquationSpec(
                    Expression.compile(x.isBlank() ? "0" : x),
                    Expression.compile(y.isBlank() ? "0" : y),
                    Expression.compile(z.isBlank() ? "0" : z));
        }
    }

    private record Variables(double t, double progress, int index, int points, SkillLine skill) {
        double value(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "t" -> t;
                case "p", "progress" -> progress;
                case "i", "index" -> index;
                case "n", "points" -> points;
                case "pi" -> Math.PI;
                case "e" -> Math.E;
                case "r", "radius" -> skill.decimal("radius", skill.decimal("r", 1.0));
                default -> 0.0;
            };
        }
    }

    /** Small deterministic expression engine for legacy particle equations. */
    private interface Expression {
        double eval(Variables vars);

        static Expression compile(String source) {
            return new Parser(source == null ? "0" : source).parse();
        }
    }

    private static final class Parser {
        private final String source;
        private int index;

        Parser(String source) {
            this.source = source;
        }

        Expression parse() {
            Expression expression = parseExpression();
            skipWhitespace();
            if (index != source.length()) throw error("Unexpected token");
            return expression;
        }

        private Expression parseExpression() {
            Expression left = parseTerm();
            while (true) {
                skipWhitespace();
                if (take('+')) {
                    Expression a = left, b = parseTerm();
                    left = vars -> a.eval(vars) + b.eval(vars);
                } else if (take('-')) {
                    Expression a = left, b = parseTerm();
                    left = vars -> a.eval(vars) - b.eval(vars);
                } else return left;
            }
        }

        private Expression parseTerm() {
            Expression left = parsePower();
            while (true) {
                skipWhitespace();
                if (take('*')) {
                    Expression a = left, b = parsePower();
                    left = vars -> a.eval(vars) * b.eval(vars);
                } else if (take('/')) {
                    Expression a = left, b = parsePower();
                    left = vars -> a.eval(vars) / b.eval(vars);
                } else if (take('%')) {
                    Expression a = left, b = parsePower();
                    left = vars -> a.eval(vars) % b.eval(vars);
                } else return left;
            }
        }

        private Expression parsePower() {
            Expression left = parseUnary();
            skipWhitespace();
            if (!take('^')) return left;
            Expression a = left, b = parsePower();
            return vars -> Math.pow(a.eval(vars), b.eval(vars));
        }

        private Expression parseUnary() {
            skipWhitespace();
            if (take('+')) return parseUnary();
            if (take('-')) {
                Expression value = parseUnary();
                return vars -> -value.eval(vars);
            }
            return parsePrimary();
        }

        private Expression parsePrimary() {
            skipWhitespace();
            if (take('(')) {
                Expression value = parseExpression();
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
                double number;
                try {
                    number = Double.parseDouble(source.substring(start, index));
                } catch (NumberFormatException exception) {
                    throw error("Invalid number");
                }
                return vars -> number;
            }
            String name = identifier();
            if (name.isEmpty()) throw error("Expected value");
            skipWhitespace();
            if (!take('(')) return vars -> vars.value(name);
            Expression first = parseExpression();
            skipWhitespace();
            Expression second = null;
            if (take(',')) second = parseExpression();
            expect(')');
            Expression b = second;
            return function(name, first, b);
        }

        private Expression function(String rawName, Expression a, Expression b) {
            String name = rawName.toLowerCase(Locale.ROOT);
            return switch (name) {
                case "sin" -> vars -> Math.sin(a.eval(vars));
                case "cos" -> vars -> Math.cos(a.eval(vars));
                case "tan" -> vars -> Math.tan(a.eval(vars));
                case "asin" -> vars -> Math.asin(a.eval(vars));
                case "acos" -> vars -> Math.acos(a.eval(vars));
                case "atan" -> vars -> Math.atan(a.eval(vars));
                case "abs" -> vars -> Math.abs(a.eval(vars));
                case "sqrt" -> vars -> Math.sqrt(a.eval(vars));
                case "floor" -> vars -> Math.floor(a.eval(vars));
                case "ceil" -> vars -> Math.ceil(a.eval(vars));
                case "round" -> vars -> Math.rint(a.eval(vars));
                case "exp" -> vars -> Math.exp(a.eval(vars));
                case "log", "ln" -> vars -> Math.log(a.eval(vars));
                case "min" -> requireBinary(name, a, b, Math::min);
                case "max" -> requireBinary(name, a, b, Math::max);
                case "pow" -> requireBinary(name, a, b, Math::pow);
                case "atan2" -> requireBinary(name, a, b, Math::atan2);
                default -> throw error("Unknown function " + rawName);
            };
        }

        private Expression requireBinary(String name, Expression a, Expression b, BinaryMath function) {
            if (b == null) throw error(name + " requires two arguments");
            return vars -> function.apply(a.eval(vars), b.eval(vars));
        }

        private String identifier() {
            skipWhitespace();
            int start = index;
            while (index < source.length()) {
                char c = source.charAt(index);
                if (!Character.isLetterOrDigit(c) && c != '_') break;
                index++;
            }
            return source.substring(start, index);
        }

        private void expect(char expected) {
            skipWhitespace();
            if (!take(expected)) throw error("Expected '" + expected + "'");
        }

        private boolean take(char c) {
            if (index < source.length() && source.charAt(index) == c) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
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
