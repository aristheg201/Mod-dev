package vn.svframe.mythicmobsfabric.engine;

import java.util.Locale;
import java.util.UUID;

/** Exact legacy parameter/offset handling for equation particle mechanics. */
public final class ParticleGeometryCompat {
    private ParticleGeometryCompat() {}

    public static boolean cast(SkillPlatform platform, SkillContext ctx, SkillLine line, UUID entity, Vec3 loc) {
        String mechanic = SkillLine.normalize(line.mechanic());
        if (!mechanic.equals("particlelineequation")) return ParticleGeometry.cast(platform, ctx, line, entity, loc);
        Vec3 target = loc != null ? loc : entity != null ? platform.position(entity) : ctx.origin();
        return target != null && lineEquation(platform, ctx, line, target);
    }

    private static boolean lineEquation(SkillPlatform platform, SkillContext ctx, SkillLine skill, Vec3 target) {
        boolean fromOrigin = bool(skill.string("fromorigin", skill.string("fo", "false")), false);
        boolean useEye = bool(skill.string("useeyelocation", skill.string("uel", "false")), false);
        double startYOffset = skill.decimal("startyoffset", skill.decimal("starty", skill.decimal("sy", 0.0)));
        double targetYOffset = skill.decimal("targetyoffset", skill.decimal("targety", skill.decimal("ty", 0.0)));

        Vec3 start;
        if (fromOrigin && ctx.origin() != null) start = ctx.origin().add(new Vec3(0.0, startYOffset, 0.0));
        else if (useEye) start = platform.eyePosition(ctx.caster());
        else {
            start = platform.position(ctx.caster());
            if (start != null) start = start.add(new Vec3(0.0, startYOffset, 0.0));
        }
        if (start == null) return false;
        Vec3 end = target.add(new Vec3(0.0, targetYOffset, 0.0));

        float yaw = platform instanceof RotationAwareSkillPlatform rotation ? rotation.yaw(ctx.caster()) : 0.0f;
        float pitch = platform instanceof RotationAwareSkillPlatform rotation ? rotation.pitch(ctx.caster()) : 0.0f;
        String fixedYaw = skill.string("fixedyaw", skill.string("yaw", ""));
        String fixedPitch = skill.string("fixedpitch", skill.string("pitch", ""));
        if (!fixedYaw.isBlank()) yaw = number(fixedYaw, yaw);
        if (!fixedPitch.isBlank()) pitch = number(fixedPitch, pitch);

        double forward = skill.decimal("forwardoffset", skill.decimal("startfoffset", skill.decimal("sfo", 0.0)));
        double side = skill.decimal("sideoffset", skill.decimal("soffset", skill.decimal("sso", 0.0)));
        if (forward != 0.0) start = move(start, yaw, pitch, -forward, 0.0, 0.0);
        if (side != 0.0) start = move(start, yaw, pitch, 0.0, 0.0, side);

        double distance = Math.sqrt(end.subtract(start).lengthSquared());
        double maxDistance = skill.decimal("maxdistance", skill.decimal("md", 256.0));
        if (distance > maxDistance) distance = maxDistance;
        double distanceBetween = skill.decimal("distancebetween", skill.decimal("db", 1.0));
        if (!(distanceBetween > 0.0) || !(distance > 0.0) || !Double.isFinite(distance)) return false;

        Vec3 direction = end.subtract(start).normalize();
        int points = (int) Math.round(distance / distanceBetween);
        if (points <= 0) return false;
        double step = distance / points;

        Expression x = Expression.compile(skill.string("equationx", skill.string("eqx", "0")));
        Expression y = Expression.compile(skill.string("equationy", skill.string("eqy", "0")));
        Expression z = Expression.compile(skill.string("equationz", skill.string("eqz", "0")));
        boolean any = false;
        for (int i = 0; i < points; i++) {
            double travelled = step * i;
            double t = travelled / distance;
            Vars vars = new Vars(t, distance);
            double ox = x.eval(vars), oy = y.eval(vars), oz = z.eval(vars);
            if (!Double.isFinite(ox) || !Double.isFinite(oy) || !Double.isFinite(oz)) continue;
            Vec3 point = start.add(direction.multiply(travelled)).add(new Vec3(ox, oy, oz));
            any |= emit(platform, skill, point);
        }
        return any;
    }

    private static Vec3 move(Vec3 origin, float yaw, float pitch, double x, double y, double z) {
        // MythicUtil.move rotates (-z, y, -x) around X(pitch), then Y(-yaw).
        double vx = -z, vy = y, vz = -x;
        double pr = Math.toRadians(pitch), yr = Math.toRadians(-yaw);
        double cosP = Math.cos(pr), sinP = Math.sin(pr);
        double py = vy * cosP - vz * sinP;
        double pz = vy * sinP + vz * cosP;
        double cosY = Math.cos(yr), sinY = Math.sin(yr);
        double rx = vx * cosY + pz * sinY;
        double rz = -vx * sinY + pz * cosY;
        return origin.add(new Vec3(rx, py, rz));
    }

    private static boolean emit(SkillPlatform platform, SkillLine skill, Vec3 point) {
        String particle = skill.string("particle", skill.string("p", "crit"));
        return platform.particle(point, particle, 1, 0.0, 0.0, 0.0, skill.decimal("speed", 0.01));
    }

    private static boolean bool(String value, boolean fallback) {
        if (value == null || value.isBlank()) return fallback;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> fallback;
        };
    }

    private static float number(String value, float fallback) {
        try { return Float.parseFloat(value.trim()); } catch (RuntimeException ignored) { return fallback; }
    }

    private record Vars(double t, double distance) {
        double get(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "t" -> t;
                case "distance" -> distance;
                case "pi" -> Math.PI;
                case "e" -> Math.E;
                default -> 0.0;
            };
        }
    }

    private interface Expression {
        double eval(Vars vars);
        static Expression compile(String source) { return new Parser(source == null ? "0" : source).parse(); }
    }

    private static final class Parser {
        private final String source; private int index;
        private Parser(String source) { this.source = source; }
        private Expression parse() { Expression out = expression(); ws(); if (index != source.length()) throw error("Unexpected token"); return out; }
        private Expression expression() {
            Expression left = term();
            while (true) { ws(); if (take('+')) { Expression a=left,b=term(); left=v->a.eval(v)+b.eval(v); } else if (take('-')) { Expression a=left,b=term(); left=v->a.eval(v)-b.eval(v); } else return left; }
        }
        private Expression term() {
            Expression left = power();
            while (true) { ws(); if (take('*')) { Expression a=left,b=power(); left=v->a.eval(v)*b.eval(v); } else if (take('/')) { Expression a=left,b=power(); left=v->a.eval(v)/b.eval(v); } else if (take('%')) { Expression a=left,b=power(); left=v->a.eval(v)%b.eval(v); } else return left; }
        }
        private Expression power() { Expression left=unary(); ws(); if(!take('^')) return left; Expression right=power(); return v->Math.pow(left.eval(v),right.eval(v)); }
        private Expression unary() { ws(); if(take('+'))return unary(); if(take('-')){Expression e=unary();return v->-e.eval(v);} return primary(); }
        private Expression primary() {
            ws(); if(take('(')){Expression e=expression();expect(')');return e;}
            if(index<source.length()&&(Character.isDigit(source.charAt(index))||source.charAt(index)=='.')){int s=index++;while(index<source.length()){char c=source.charAt(index);if(!Character.isDigit(c)&&c!='.'&&c!='e'&&c!='E'&&c!='+'&&c!='-')break;if((c=='+'||c=='-')&&source.charAt(index-1)!='e'&&source.charAt(index-1)!='E')break;index++;}double n=Double.parseDouble(source.substring(s,index));return v->n;}
            String name=id(); if(name.isEmpty())throw error("Expected value"); ws(); if(!take('('))return v->v.get(name);
            Expression a=expression(); ws(); Expression b=null; if(take(','))b=expression(); expect(')'); return function(name,a,b);
        }
        private Expression function(String raw, Expression a, Expression b) {
            String n=raw.toLowerCase(Locale.ROOT); return switch(n){
                case "sin"->v->Math.sin(a.eval(v)); case "cos"->v->Math.cos(a.eval(v)); case "tan"->v->Math.tan(a.eval(v));
                case "asin"->v->Math.asin(a.eval(v)); case "acos"->v->Math.acos(a.eval(v)); case "atan"->v->Math.atan(a.eval(v));
                case "abs"->v->Math.abs(a.eval(v)); case "sqrt"->v->Math.sqrt(a.eval(v)); case "floor"->v->Math.floor(a.eval(v));
                case "ceil"->v->Math.ceil(a.eval(v)); case "round"->v->Math.rint(a.eval(v)); case "exp"->v->Math.exp(a.eval(v));
                case "log","ln"->v->Math.log(a.eval(v)); case "min"->binary(n,a,b,Math::min); case "max"->binary(n,a,b,Math::max);
                case "pow"->binary(n,a,b,Math::pow); case "atan2"->binary(n,a,b,Math::atan2); default->throw error("Unknown function "+raw);};
        }
        private Expression binary(String name, Expression a, Expression b, Math2 f){if(b==null)throw error(name+" requires two arguments");return v->f.apply(a.eval(v),b.eval(v));}
        private String id(){ws();int s=index;while(index<source.length()){char c=source.charAt(index);if(!Character.isLetterOrDigit(c)&&c!='_')break;index++;}return source.substring(s,index);}
        private void expect(char c){ws();if(!take(c))throw error("Expected '"+c+"'");}
        private boolean take(char c){if(index<source.length()&&source.charAt(index)==c){index++;return true;}return false;}
        private void ws(){while(index<source.length()&&Character.isWhitespace(source.charAt(index)))index++;}
        private IllegalArgumentException error(String m){return new IllegalArgumentException(m+" at "+index+" in '"+source+"'");}
    }

    @FunctionalInterface private interface Math2 { double apply(double a, double b); }
}
