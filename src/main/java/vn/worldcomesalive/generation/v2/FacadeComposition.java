package vn.worldcomesalive.generation.v2;

import java.util.*;

/** Authored architectural grammar: entrance, window bays, plinth, upper frontage and service wing. */
public final class FacadeComposition {
    public record Element(int x,int y,int z,String material,String facing,String role){}
    public static List<Element> compose(GenerationCatalog.BuildingDef building,GenerationCatalog.FacadeDef style){
        List<Element> result=new ArrayList<>();int w=building.width(),d=building.depth(),mid=w/2;
        int left=mid-style.porchWidth()/2,right=left+style.porchWidth()-1;
        for(int x=left;x<=right;x++)for(int z=d;z<d+style.porchDepth();z++){
            result.add(new Element(x,0,z,"$plinth","north","ENTRY_LANDING"));
            result.add(new Element(x,style.canopyHeight(),z,"$timber_slab","north","ENTRY_CANOPY"));
        }
        for(int x:new int[]{left,right})for(int y=1;y<style.canopyHeight();y++)result.add(new Element(x,y,d+style.porchDepth()-1,"$log","north","PORCH_POST"));
        for(int x=left;x<=right;x++)result.add(new Element(x,style.canopyHeight()-1,d+style.porchDepth()-1,"$timber_fence","north","PORCH_LINTEL"));
        result.add(new Element(left,style.canopyHeight()-2,d+style.porchDepth()-2,"minecraft:lantern","north","ENTRY_LIGHT"));
        result.add(new Element(right,style.canopyHeight()-2,d+style.porchDepth()-2,"minecraft:lantern","north","ENTRY_LIGHT"));
        // Window assemblies repeat a curated bay, while the entrance remains the dominant focal point.
        for(int floor=0;floor<building.floors();floor++)for(int x=2;x<w-2;x+=4){
            int y=2+floor*building.storey();if(floor==0&&Math.abs(x-mid)<2)continue;
            for(int z:new int[]{-1,d}){
                String facing=z<0?"north":"south";
                result.add(new Element(x,y-1,z,"$timber_slab",facing,"WINDOW_SILL"));
                if(style.shutters())for(int side:new int[]{-1,1})result.add(new Element(x+side,y,z,"$timber_trapdoor",facing,"SHUTTER"));
                result.add(new Element(x,y+1,z,"$timber_slab",facing,"WINDOW_HOOD"));
            }
        }
        for(int z=2;z<d-2;z+=4)for(int x:new int[]{-1,w}){
            String facing=x<0?"west":"east";
            for(int floor=0;floor<building.floors();floor++)result.add(new Element(x,1+floor*building.storey(),z,"$timber_slab",facing,"SIDE_SILL"));
        }
        if(style.balcony()&&building.floors()>1){
            int y=building.storey();for(int x=mid-2;x<=mid+2;x++)for(int z=d;z<=d+1;z++)result.add(new Element(x,y,z,"$timber_slab","south","UPPER_GALLERY"));
            for(int x=mid-2;x<=mid+2;x++)result.add(new Element(x,y+1,d+1,"$timber_fence","south","GALLERY_RAIL"));
        }
        if(style.serviceCanopy())for(int z=3;z<Math.min(d-3,10);z++){
            for(int x=-3;x<=-1;x++)result.add(new Element(x,3,z,"$timber_slab","east","SERVICE_ROOF"));
            if(z==3||z==Math.min(d-3,10)-1)for(int y=1;y<3;y++)result.add(new Element(-3,y,z,"$log","north","SERVICE_POST"));
        }
        return List.copyOf(result);
    }
    private FacadeComposition(){}
}
