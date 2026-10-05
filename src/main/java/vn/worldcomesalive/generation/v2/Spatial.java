package vn.worldcomesalive.generation.v2;
/** Engine coordinates are independent of Minecraft blocks and entity APIs. */
public final class Spatial {
    public record Point(double x,double z){public double distance(Point b){return Math.hypot(x-b.x,z-b.z);}public Point plus(double dx,double dz){return new Point(x+dx,z+dz);}public Point lerp(Point b,double t){return new Point(x+(b.x-x)*t,z+(b.z-z)*t);}}
    public record Rect(int x,int z,int width,int depth){public int maxX(){return x+width;}public int maxZ(){return z+depth;}public Point center(){return new Point(x+width*.5,z+depth*.5);}public int area(){return width*depth;}public boolean contains(double px,double pz){return px>=x&&px<maxX()&&pz>=z&&pz<maxZ();}public boolean overlaps(Rect r){return x<r.maxX()&&maxX()>r.x&&z<r.maxZ()&&maxZ()>r.z;}public Rect expand(int n){return new Rect(x-n,z-n,width+2*n,depth+2*n);}public Rect inset(int n){return expand(-n);}}
    public static Rect centered(Point p,int w,int d){return new Rect((int)Math.round(p.x-w*.5),(int)Math.round(p.z-d*.5),w,d);}
    public static Point rotate(Point p,int rotation){return switch(Math.floorMod(rotation,4)){case 1->new Point(-p.z,p.x);case 2->new Point(-p.x,-p.z);case 3->new Point(p.z,-p.x);default->p;};}
    private Spatial(){}
}
