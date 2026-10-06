package vn.worldcomesalive.generation.v2;

/**
 * High-level architectural massing policy. The materializer consumes this profile to keep
 * building silhouettes role-specific instead of applying one full-span Minecraft roof to every box.
 */
public final class MassingComposition {
    public record Profile(int maxRoofRise,int frontGableHalfWidth,boolean rearLeanTo,boolean sideLeanTo,boolean civicTower,int chimneyCount){}

    public static Profile profile(GenerationCatalog.BuildingDef building){
        return switch(building.type()){
            case "tavern" -> new Profile(5,4,true,true,false,2);
            case "civic" -> new Profile(6,4,false,false,true,2);
            case "farm" -> new Profile(5,3,true,true,false,1);
            case "barn" -> new Profile(6,0,true,true,false,0);
            case "forge" -> new Profile(4,2,false,true,false,2);
            case "trading_post" -> new Profile(4,3,true,true,false,1);
            case "market" -> new Profile(4,0,false,true,false,0);
            case "guardhouse" -> new Profile(4,2,false,false,false,1);
            case "bakery" -> new Profile(4,3,true,false,false,2);
            default -> building.id().equals("house_noble")
                ?new Profile(5,3,true,false,false,2)
                :building.id().equals("house_guild")
                    ?new Profile(5,3,false,true,false,1)
                    :building.id().equals("house_crofter")
                        ?new Profile(5,0,true,true,false,1)
                        :new Profile(4,building.id().equals("house_hearth")?2:0,false,false,false,1);
        };
    }

    public static int compressedRise(int raw,int span,int maxRise){
        int half=Math.max(1,(span+1)/2);
        if(raw<=0)return 0;
        return Math.max(1,(int)Math.ceil(raw*(maxRise/(double)half)));
    }

    private MassingComposition(){}
}
