package vn.worldcomesalive.generation.v2;
import static vn.worldcomesalive.generation.v2.Spatial.*;
import java.util.*;
/** Immutable sampled region. Pure planners never query or mutate the live world. */
public final class TerrainSnapshot {
    public record Sample(int height,boolean water,double forest){}
    public final int minX,minZ,size,step;
    private final Sample[] samples;
    public TerrainSnapshot(int minX,int minZ,int size,int step,List<Sample> samples){if(size<3||step<1||samples.size()!=size*size)throw new IllegalArgumentException("Invalid terrain snapshot");this.minX=minX;this.minZ=minZ;this.size=size;this.step=step;this.samples=samples.toArray(Sample[]::new);}
    public Sample at(double x,double z){int xx=Math.max(0,Math.min(size-1,(int)Math.round((x-minX)/step))),zz=Math.max(0,Math.min(size-1,(int)Math.round((z-minZ)/step)));return samples[zz*size+xx];}
    public int heightAt(double x,double z){double gx=Math.max(0,Math.min(size-1,(x-minX)/step)),gz=Math.max(0,Math.min(size-1,(z-minZ)/step));int x0=(int)gx,z0=(int)gz,x1=Math.min(size-1,x0+1),z1=Math.min(size-1,z0+1);double tx=gx-x0,tz=gz-z0;double a=samples[z0*size+x0].height()*(1-tx)+samples[z0*size+x1].height()*tx,b=samples[z1*size+x0].height()*(1-tx)+samples[z1*size+x1].height()*tx;return (int)Math.round(a*(1-tz)+b*tz);}
    public double slope(Point p){int y=at(p.x(),p.z()).height;double change=0;for(Point d:List.of(p.plus(step,0),p.plus(-step,0),p.plus(0,step),p.plus(0,-step)))change=Math.max(change,Math.abs(at(d.x(),d.z()).height-y));return change/step;}
    public double suitability(Rect r){int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;double forest=0;int n=0;for(int x=r.x();x<r.maxX();x+=step)for(int z=r.z();z<r.maxZ();z+=step){Sample s=at(x,z);if(s.water)return -1000;min=Math.min(min,s.height);max=Math.max(max,s.height);forest+=s.forest;n++;}if(max-min>12)return -1000;return 20-(max-min)*1.7-forest/Math.max(1,n)*2;}
    public int foundation(Rect r){List<Integer> heights=new ArrayList<>();for(int x=r.x();x<r.maxX();x+=step)for(int z=r.z();z<r.maxZ();z+=step)heights.add(at(x,z).height);Collections.sort(heights);return heights.get(heights.size()/2);}
    public boolean contains(Point p,int margin){return p.x()>=minX+margin&&p.z()>=minZ+margin&&p.x()<minX+(size-1)*step-margin&&p.z()<minZ+(size-1)*step-margin;}
    public static TerrainSnapshot flat(int radius,int height){int step=3,size=radius*2/step+1;return new TerrainSnapshot(-radius,-radius,size,step,Collections.nCopies(size*size,new Sample(height,false,0)));}
}
