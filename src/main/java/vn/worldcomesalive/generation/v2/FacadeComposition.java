package vn.worldcomesalive.generation.v2;

import java.util.*;

/**
 * Authored facade grammar. Building role changes frontage hierarchy, projections and work-facing detail;
 * it is not a repeated window-bay pass over a rectangular shell.
 */
public final class FacadeComposition {
    public record Element(int x,int y,int z,String material,String facing,String role){}

    public static List<Element> compose(GenerationCatalog.BuildingDef building,GenerationCatalog.FacadeDef style){
        List<Element> result=new ArrayList<>();
        int w=building.width(),d=building.depth(),mid=w/2;
        int left=mid-style.porchWidth()/2,right=left+style.porchWidth()-1;

        // Strong entrance composition.
        for(int x=left;x<=right;x++)for(int z=d;z<d+style.porchDepth();z++){
            result.add(new Element(x,0,z,"$plinth","north","ENTRY_LANDING"));
            result.add(new Element(x,style.canopyHeight(),z,"$timber_slab","north","ENTRY_CANOPY"));
        }
        for(int x:new int[]{left,right})for(int y=1;y<style.canopyHeight();y++)
            result.add(new Element(x,y,d+style.porchDepth()-1,"$log","north","PORCH_POST"));
        for(int x=left;x<=right;x++)
            result.add(new Element(x,style.canopyHeight()-1,d+style.porchDepth()-1,"$timber_fence","north","PORCH_LINTEL"));
        result.add(new Element(left,style.canopyHeight()-2,d+style.porchDepth()-2,"minecraft:lantern","north","ENTRY_LIGHT"));
        result.add(new Element(right,style.canopyHeight()-2,d+style.porchDepth()-2,"minecraft:lantern","north","ENTRY_LIGHT"));

        // Controlled facade rhythm, varied deterministically by building identity.
        int offset=Math.floorMod(building.id().hashCode(),3);
        for(int floor=0;floor<building.floors();floor++)for(int x=2+offset;x<w-2;x+=4){
            int y=2+floor*building.storey();if(floor==0&&Math.abs(x-mid)<2)continue;
            for(int z:new int[]{-1,d}){
                String facing=z<0?"north":"south";
                result.add(new Element(x,y-1,z,"$timber_slab",facing,"WINDOW_SILL"));
                if(style.shutters())for(int side:new int[]{-1,1})
                    result.add(new Element(x+side,y,z,"$timber_trapdoor",facing,"SHUTTER"));
                result.add(new Element(x,y+1,z,"$timber_slab",facing,"WINDOW_HOOD"));
            }
        }

        // Structural posts make long facades read as bays rather than one flat wall.
        for(int x=3;x<w-2;x+=5)for(int floor=0;floor<building.floors();floor++)
            for(int y=1+floor*building.storey();y<Math.min((floor+1)*building.storey(),building.floors()*building.storey());y++)
                result.add(new Element(x,y,d,"$log","south","FRONT_BAY_POST"));

        if(style.balcony()&&building.floors()>1){
            int y=building.storey();
            for(int x=mid-3;x<=mid+3;x++)for(int z=d;z<=d+1;z++)
                result.add(new Element(x,y,z,"$timber_slab","south","UPPER_GALLERY"));
            for(int x=mid-3;x<=mid+3;x++)
                result.add(new Element(x,y+1,d+1,"$timber_fence","south","GALLERY_RAIL"));
        }

        roleFrontage(building,style,result);
        return List.copyOf(result);
    }

    private static void roleFrontage(GenerationCatalog.BuildingDef b,GenerationCatalog.FacadeDef style,List<Element> out){
        int w=b.width(),d=b.depth(),mid=w/2;
        switch(b.type()){
            case "tavern" -> {
                // Broad public frontage and hanging sign; service side remains visually separate.
                for(int x=2;x<w-2;x++)out.add(new Element(x,3,d+1,"$timber_slab","south","TAVERN_AWNING"));
                for(int x:new int[]{2,w-3})for(int y=1;y<=3;y++)out.add(new Element(x,y,d+1,"$log","south","TAVERN_POST"));
                out.add(new Element(mid+4,3,d+2,"$timber_fence","south","HANGING_SIGN_ARM"));
            }
            case "forge" -> {
                for(int z=3;z<Math.min(d-2,10);z++)for(int x=-3;x<=-1;x++)
                    out.add(new Element(x,3,z,"$timber_slab","east","FORGE_WORK_CANOPY"));
                for(int y=1;y<=3;y++)out.add(new Element(-3,y,4,"$log","east","FORGE_POST"));
            }
            case "farm","barn" -> {
                // Loft articulation and deep working eaves.
                for(int x=mid-2;x<=mid+2;x++)out.add(new Element(x,Math.max(3,b.storey()-1),d,"$timber_slab","south","LOFT_HOOD"));
                for(int x=1;x<w-1;x++)out.add(new Element(x,3,-1,"$timber_slab","north","REAR_WORK_EAVE"));
            }
            case "trading_post" -> {
                for(int x=2;x<w-2;x++)out.add(new Element(x,3,d+1,"$timber_slab","south","TRADE_AWNING"));
                out.add(new Element(mid+3,2,d+2,"$timber_fence","south","TRADE_SIGN"));
            }
            case "civic" -> {
                for(int x=mid-4;x<=mid+4;x++)out.add(new Element(x,4,d+1,"$timber_slab","south","CIVIC_PORTICO"));
                for(int x:new int[]{mid-4,mid+4})for(int y=1;y<4;y++)out.add(new Element(x,y,d+1,"$log","south","CIVIC_COLUMN"));
            }
            default -> {
                if(b.id().equals("house_noble")){
                    for(int x=mid-3;x<=mid+3;x++)out.add(new Element(x,b.storey(),d+1,"$timber_slab","south","NOBLE_GALLERY"));
                    for(int x=mid-3;x<=mid+3;x++)out.add(new Element(x,b.storey()+1,d+1,"$timber_fence","south","NOBLE_RAIL"));
                }else if(style.serviceCanopy()){
                    for(int z=3;z<Math.min(d-3,10);z++)for(int x=-3;x<=-1;x++)
                        out.add(new Element(x,3,z,"$timber_slab","east","SERVICE_ROOF"));
                }
            }
        }
    }

    private FacadeComposition(){}
}
