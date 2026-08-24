package vn.svframe.mythicmobsfabric.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

final class ParityUtil {
    static final Random RANDOM = new Random();
    private ParityUtil() {}

    static UUID entity(SkillContext c, UUID e) { return e == null ? c.caster() : e; }
    static UUID target(SkillContext c, UUID e) {
        if (e != null && !e.equals(c.caster())) return e;
        if (c.triggerEntity() != null) return c.triggerEntity();
        return c.entityTargets().isEmpty() ? c.caster() : c.entityTargets().get(0);
    }
    static Vec3 location(SkillPlatform p, SkillContext c, UUID e, Vec3 v) {
        if (v != null) return v;
        if (e != null) return p.position(e);
        return c.origin();
    }
    static String text(SkillContext c, SkillLine l, String... keys) {
        String x = text(l, keys);
        for (Map.Entry<String,String> e : c.parameters().entrySet()) x = x.replace("<"+e.getKey()+">", e.getValue()).replace("%"+e.getKey()+"%", e.getValue());
        for (Map.Entry<String,Object> e : c.variables().entrySet()) x = x.replace("<"+e.getKey()+">", String.valueOf(e.getValue())).replace("%"+e.getKey()+"%", String.valueOf(e.getValue()));
        return x;
    }
    static String text(SkillLine l, String... keys) {
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            String value = l.config().get(norm(key));
            if (value != null) return value;
        }
        return "";
    }
    static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace("_","").replace("-",""); }
    static boolean compare(double actual, String expr) {
        if (expr == null || expr.isBlank()) return actual != 0;
        String s=expr.trim();
        try {
            if (s.contains("to")) { String[] p=s.split("to",2); return actual>=Double.parseDouble(p[0].trim()) && actual<=Double.parseDouble(p[1].trim()); }
            if (s.startsWith(">=")) return actual>=Double.parseDouble(s.substring(2).trim());
            if (s.startsWith("<=")) return actual<=Double.parseDouble(s.substring(2).trim());
            if (s.startsWith(">")) return actual>Double.parseDouble(s.substring(1).trim());
            if (s.startsWith("<")) return actual<Double.parseDouble(s.substring(1).trim());
            if (s.startsWith("=")) return actual==Double.parseDouble(s.substring(1).trim());
            return actual==Double.parseDouble(s);
        } catch (NumberFormatException ignored) { return false; }
    }
    static boolean contains(String csv, String value) {
        if (csv == null || value == null) return false;
        for (String x : csv.split("[,|]")) if (x.trim().equalsIgnoreCase(value)) return true;
        return false;
    }
    static boolean eq(String a,String b) { return a != null && b != null && a.equalsIgnoreCase(b); }
    static double parseDouble(String s,double fallback) { try { return s==null?fallback:Double.parseDouble(s); } catch (NumberFormatException e) { return fallback; } }
    static double distance(SkillPlatform p, UUID a, UUID b) { return a==null||b==null?Double.POSITIVE_INFINITY:Math.sqrt(p.position(a).distanceSquared(p.position(b))); }
    static boolean timeBetween(long time,long start,long end) { long t=Math.floorMod(time,24000L); return start<=end?t>=start&&t<=end:t>=start||t<=end; }
    static Vec3 forwardVector(SkillPlatform p, UUID u) {
        if (p instanceof RotationAwareSkillPlatform r) {
            double yaw=Math.toRadians(r.yaw(u)), pitch=Math.toRadians(r.pitch(u)), cp=Math.cos(pitch);
            return new Vec3(-Math.sin(yaw)*cp,-Math.sin(pitch),Math.cos(yaw)*cp,p.world(u)).normalize();
        }
        Vec3 v=p.velocity(u);
        return v.lengthSquared()>1e-6?v.normalize():new Vec3(0,0,1,p.world(u));
    }
    static Vec3 forward(SkillPlatform p, UUID u, double distance) { return p.eyePosition(u).add(forwardVector(p,u).multiply(distance)); }
    static Collection<UUID> filterWorld(SkillPlatform p, Collection<UUID> xs, String world) {
        List<UUID> out=new ArrayList<>(); for(UUID u:xs) if(world.equals(p.world(u))) out.add(u); return out;
    }
    static Collection<UUID> ring(SkillPlatform p,UUID center,Collection<UUID> xs,SkillLine l) {
        double min=l.decimal("minradius",l.decimal("min",0,"minr")), max=l.decimal("radius",l.decimal("r",5)); Vec3 c=p.position(center); List<UUID> out=new ArrayList<>();
        for(UUID u:xs){if(u.equals(center)||!p.world(u).equals(c.world()))continue;double d=Math.sqrt(c.distanceSquared(p.position(u)));if(d>=min&&d<=max)out.add(u);}return out;
    }
    static Collection<UUID> cone(SkillPlatform p,UUID center,Collection<UUID> xs,SkillLine l) {
        double radius=l.decimal("radius",l.decimal("r",10)), angle=Math.toRadians(l.decimal("angle",l.decimal("a",90))/2); Vec3 c=p.position(center), f=forwardVector(p,center).normalize(); List<UUID> out=new ArrayList<>();
        for(UUID u:xs){if(u.equals(center)||!p.world(u).equals(c.world()))continue;Vec3 d=p.position(u).subtract(c);double len=d.length();if(len<=radius&&len>0&&(f.x()*d.x()+f.y()*d.y()+f.z()*d.z())/len>=Math.cos(angle))out.add(u);}return out;
    }
    static Collection<UUID> line(SkillPlatform p,UUID center,Collection<UUID> xs,SkillLine l) {
        double length=l.decimal("length",l.decimal("l",10)), radius=l.decimal("radius",l.decimal("r",1)); Vec3 c=p.position(center),f=forwardVector(p,center).normalize(); List<UUID> out=new ArrayList<>();
        for(UUID u:xs){if(u.equals(center)||!p.world(u).equals(c.world()))continue;Vec3 d=p.position(u).subtract(c);double along=f.x()*d.x()+f.y()*d.y()+f.z()*d.z();if(along>=0&&along<=length&&c.add(f.multiply(along)).distanceSquared(p.position(u))<=radius*radius)out.add(u);}return out;
    }
    static List<Vec3> randomLocations(Vec3 c,SkillLine l,boolean sphere) {
        int n=Math.max(1,l.integer("amount",l.integer("a",1))),tries=0;double r=l.decimal("radius",l.decimal("r",5));List<Vec3> out=new ArrayList<>();
        while(out.size()<n&&tries++<n*20){double x=(RANDOM.nextDouble()*2-1)*r,z=(RANDOM.nextDouble()*2-1)*r,y=sphere?(RANDOM.nextDouble()*2-1)*r:0;if(!sphere||x*x+y*y+z*z<=r*r)out.add(new Vec3(c.x()+x,c.y()+y,c.z()+z,c.world()));}return out;
    }
    static List<Vec3> randomRing(Vec3 c,SkillLine l) {
        int n=Math.max(1,l.integer("amount",l.integer("a",1)));double min=l.decimal("minradius",l.decimal("min",1)),max=l.decimal("radius",l.decimal("r",5));List<Vec3>out=new ArrayList<>();
        for(int i=0;i<n;i++){double a=RANDOM.nextDouble()*Math.PI*2,d=min+RANDOM.nextDouble()*Math.max(0,max-min);out.add(new Vec3(c.x()+Math.cos(a)*d,c.y(),c.z()+Math.sin(a)*d,c.world()));}return out;
    }
    static List<Vec3> ringLocations(Vec3 c,SkillLine l) {
        int n=Math.max(4,l.integer("points",l.integer("p",32)));double r=l.decimal("radius",l.decimal("r",3));List<Vec3>out=new ArrayList<>();for(int i=0;i<n;i++){double a=Math.PI*2*i/n;out.add(new Vec3(c.x()+Math.cos(a)*r,c.y(),c.z()+Math.sin(a)*r,c.world()));}return out;
    }
    static List<Vec3> sphereLocations(Vec3 c,SkillLine l) {
        int n=Math.max(8,l.integer("points",l.integer("p",64)));double r=l.decimal("radius",l.decimal("r",3));List<Vec3>out=new ArrayList<>();double phi=Math.PI*(3-Math.sqrt(5));
        for(int i=0;i<n;i++){double y=1-(i/(double)(n-1))*2,rr=Math.sqrt(Math.max(0,1-y*y)),theta=phi*i;out.add(new Vec3(c.x()+Math.cos(theta)*rr*r,c.y()+y*r,c.z()+Math.sin(theta)*rr*r,c.world()));}return out;
    }
}
