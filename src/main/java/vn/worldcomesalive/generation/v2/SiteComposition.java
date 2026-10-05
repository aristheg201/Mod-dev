package vn.worldcomesalive.generation.v2;

import java.util.*;

/**
 * Engine-side property composition. This deliberately knows nothing about Minecraft blocks:
 * it describes how a building occupies and uses the rest of its lot before the adapter
 * materializes paths, fences, service courts and landscape.
 */
public final class SiteComposition {
    public record Element(int x,int z,String kind,String facing,String layer){}

    public static List<Element> compose(GenerationCatalog.BuildingDef building,GenerationCatalog.FacadeDef facade){
        List<Element> out=new ArrayList<>();
        int w=building.width(),d=building.depth(),margin=building.lotMargin(),mid=w/2;
        if(margin<2)return List.of();

        // Every property gets a readable approach that continues beyond the porch to the lot gate/street.
        int pathStart=d+Math.max(1,facade.porchDepth());
        for(int z=pathStart;z<=d+margin;z++)for(int x=mid-1;x<=mid+1;x++)
            out.add(new Element(x,z,"PATH","north","circulation"));

        String type=building.type();
        boolean domestic=Set.of("cottage","farm","barn").contains(type);
        boolean working=Set.of("forge","bakery","trading_post","tavern","market").contains(type);

        if(domestic){
            int rear=-margin,left=-margin,right=w+margin-1,front=d+margin-1;
            // A complete property boundary communicates ownership, but leaves a three-block front gate.
            for(int x=left;x<=right;x++){
                out.add(new Element(x,rear,"FENCE","north","boundary"));
                if(Math.abs(x-mid)>1)out.add(new Element(x,front,"FENCE","north","boundary"));
            }
            for(int z=rear+1;z<front;z++){
                out.add(new Element(left,z,"FENCE","east","boundary"));
                out.add(new Element(right,z,"FENCE","west","boundary"));
            }
            out.add(new Element(mid,front,"GATE","north","boundary"));
            if(type.equals("farm")||type.equals("barn"))out.add(new Element(left,Math.max(1,d/2),"GATE","east","boundary"));

            // Domestic planting softens the frontage instead of leaving purposeless grass.
            if(type.equals("cottage")){
                for(int x=left+2;x<mid-2;x+=2)out.add(new Element(x,front-2,"HEDGE","north","landscape"));
                for(int x=mid+3;x<=right-2;x+=2)out.add(new Element(x,front-2,"HEDGE","north","landscape"));
            }
        }

        if(type.equals("farm")||type.equals("barn")){
            // Rear work court, hay and loading apron establish a real agricultural property identity.
            for(int x=1;x<w-1;x++)for(int z=-margin+1;z<=-2;z++)
                out.add(new Element(x,z,"WORK_DIRT","north","work"));
            for(int x=2;x<=4;x++)out.add(new Element(x,-margin+2,"HAY","north","work"));
            for(int x=w-5;x<=w-3;x++)out.add(new Element(x,-margin+2,"HAY","north","work"));
        }else if(type.equals("forge")){
            // Forge yards are hard-wearing and visually distinct from residential lawns.
            for(int x=-margin+1;x<=-1;x++)for(int z=2;z<d-2;z++)
                out.add(new Element(x,z,"WORK_STONE","east","work"));
        }else if(type.equals("tavern")||type.equals("trading_post")){
            // Side/rear delivery court; the public frontage remains open and readable.
            for(int x=-margin+1;x<=-1;x++)for(int z=2;z<d-1;z++)
                out.add(new Element(x,z,"WORK_DIRT","east","service"));
            for(int z=2;z<d-1;z+=3)out.add(new Element(-margin,z,"FENCE","east","boundary"));
        }else if(type.equals("civic")){
            // Civic buildings get a deliberate forecourt instead of a bare lawn.
            for(int x=mid-4;x<=mid+4;x++)for(int z=d+facade.porchDepth();z<=d+margin;z++)
                out.add(new Element(x,z,"FORECOURT","north","public"));
        }else if(working){
            for(int x=mid-3;x<=mid+3;x++)for(int z=d+facade.porchDepth();z<=d+margin;z++)
                out.add(new Element(x,z,"WORK_DIRT","north","service"));
        }

        return List.copyOf(out);
    }

    private SiteComposition(){}
}
