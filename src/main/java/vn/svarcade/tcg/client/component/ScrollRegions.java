package vn.svarcade.tcg.client.component;

import java.util.*;

/** Per-panel pixel scrolling and scrollbar input in logical screen coordinates. */
public final class ScrollRegions {
    public record Region(String id,Rect box,int content,int max,int offset) {
        public Rect track(){return new Rect(box.right()-9,box.y(),9,box.h());}
        public Rect thumb(){int height=Math.min(box.h(),Math.max(24,box.h()*box.h()/Math.max(1,content)));
            int y=box.y()+(max==0?0:(box.h()-height)*offset/max);return new Rect(box.right()-8,y,7,height);}
    }
    private final Map<String,Integer> offsets=new LinkedHashMap<>();
    private final List<Region> regions=new ArrayList<>();
    private String dragging;private double grab;
    public void beginFrame(){regions.clear();}
    public void reset(){offsets.clear();dragging=null;}
    public void reset(String id){offsets.remove(id);}
    public Region register(String id,Rect box,int content){
        int max=Math.max(0,content-box.h()),value=Math.clamp(offsets.getOrDefault(id,0),0,max);
        offsets.put(id,value);while(offsets.size()>256)offsets.remove(offsets.keySet().iterator().next());
        var region=new Region(id,box,content,max,value);regions.add(region);return region;
    }
    public Optional<Region> region(String id){return regions.stream().filter(r->r.id().equals(id)).findFirst();}
    public int offset(String id){return offsets.getOrDefault(id,0);}
    public void move(String id,int pixels){region(id).ifPresent(r->offsets.put(id,Math.clamp(offset(r.id())+pixels,0,r.max())));}
    public boolean wheel(double x,double y,double delta){
        if(delta==0)return false;
        for(int i=regions.size()-1;i>=0;i--){var r=regions.get(i);if(r.box().contains(x,y)&&r.max()>0){move(r.id(),(int)Math.round(-delta*38));return true;}}
        return false;
    }
    public boolean press(double x,double y){
        for(int i=regions.size()-1;i>=0;i--){var r=regions.get(i);if(r.max()==0||!r.track().contains(x,y))continue;
            if(r.thumb().contains(x,y)){dragging=r.id();grab=y-r.thumb().y();}
            else move(r.id(),y<r.thumb().y()?-r.box().h():r.box().h());return true;}
        return false;
    }
    public boolean drag(double y){
        if(dragging==null)return false;
        region(dragging).ifPresent(r->{int travel=r.box().h()-r.thumb().h();
            offsets.put(r.id(),travel<=0?0:Math.clamp((int)Math.round((y-r.box().y()-grab)*r.max()/travel),0,r.max()));});return true;
    }
    public boolean release(){boolean active=dragging!=null;dragging=null;return active;}
}
