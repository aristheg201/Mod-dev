package vn.svframe.svrtp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

final class SafeLanding {
    private SafeLanding() {}
    static boolean valid(ServerLevel world,ServerPlayer player,int x,int y,int z) {
        if(y<=world.getMinBuildHeight() || y+Math.ceil(player.getBbHeight())>=world.getMaxBuildHeight())return false;
        if(world.dimensionType().hasCeiling() && y>=120)return false;
        if(!Integrations.border(world,x+.5,z+.5,player.getBbWidth()/2.0+1))return false;
        // All validation stays within one loaded chunk. Never call getBlockState on an unloaded neighbour.
        if((x&15)<3 || (x&15)>12 || (z&15)<3 || (z&15)>12)return false;
        if(world.getChunkSource().getChunkNow(x>>4,z>>4)==null)return false;
        BlockPos floor=new BlockPos(x,y-1,z);
        if(!support(world,floor))return false;
        var box=new AABB(x+.5-player.getBbWidth()/2,y,z+.5-player.getBbWidth()/2,
                x+.5+player.getBbWidth()/2,y+player.getBbHeight(),z+.5+player.getBbWidth()/2);
        if(!world.noCollision(player,box))return false;
        // A 5x5 dry floor with three clear blocks rejects tiny caves, lava edges and immediate drops.
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
            if(!support(world,floor.offset(dx,0,dz)))return false;
            for(int dy=0;dy<3;dy++) {
                var pos=new BlockPos(x+dx,y+dy,z+dz);var state=world.getBlockState(pos);
                if(!state.getFluidState().isEmpty() || hazard(state) || !state.getCollisionShape(world,pos).isEmpty())return false;
            }
        }
        // The Nether needs a genuinely open space above the pocket, not the roof or a sealed 3-high tunnel.
        if(world.dimensionType().hasCeiling())for(int dy=3;dy<6;dy++) {
            var pos=new BlockPos(x,y+dy,z);var state=world.getBlockState(pos);
            if(!state.getFluidState().isEmpty() || !state.getCollisionShape(world,pos).isEmpty())return false;
        }
        return true;
    }
    private static boolean support(ServerLevel world,BlockPos pos) {
        var state=world.getBlockState(pos);
        return !hazard(state) && state.getFluidState().isEmpty()
                && state.isFaceSturdy(world,pos,net.minecraft.core.Direction.UP)
                && Block.isShapeFullBlock(state.getCollisionShape(world,pos));
    }
    private static boolean hazard(BlockState s) {
        return s.is(Blocks.BEDROCK) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(Blocks.FIRE)
                || s.is(Blocks.SOUL_FIRE) || s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE)
                || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.WITHER_ROSE)
                || s.is(Blocks.POINTED_DRIPSTONE) || s.is(Blocks.COBWEB) || s.is(Blocks.NETHER_PORTAL)
                || s.is(Blocks.END_PORTAL) || s.is(Blocks.END_GATEWAY) || s.is(Blocks.TNT);
    }
    static boolean origin(ServerLevel world,ServerPlayer p) {
        // Source validation also uses loaded blocks only; restoration is never an unchecked emergency teleport.
        var pos=p.blockPosition();
        return valid(world,p,pos.getX(),pos.getY(),pos.getZ()) && world.noCollision(p,p.getBoundingBox());
    }
}
