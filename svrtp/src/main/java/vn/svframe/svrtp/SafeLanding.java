package vn.svframe.svrtp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
        // Real terrain may slope by one block. Validate a dry 3x3 neighbourhood
        // with normal step heights instead of requiring a perfectly flat 5x5 plaza.
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            int surface=Integer.MIN_VALUE;
            for(int fy=y;fy>=y-2;fy--)if(support(world,new BlockPos(x+dx,fy,z+dz))) {surface=fy;break;}
            if(surface==Integer.MIN_VALUE)return false;
            for(int dy=1;dy<=2;dy++) {
                var pos=new BlockPos(x+dx,surface+dy,z+dz);var state=world.getBlockState(pos);
                if(!state.getFluidState().isEmpty() || hazard(state) || !state.getCollisionShape(world,pos).isEmpty())return false;
            }
        }
        // The Nether needs a genuinely open space above the pocket, not the roof or a sealed 3-high tunnel.
        if(world.dimensionType().hasCeiling())for(int dy=2;dy<6;dy++) {
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
        return restorable(world,p,p.position());
    }
    /** Validate the exact source feet and body, not the much wider random-arrival surface. */
    static boolean restorable(ServerLevel world,ServerPlayer p,Vec3 position) {
        if(!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z))return false;
        AABB body=p.getBoundingBox().move(position.subtract(p.position()));
        if(body.minY<=world.getMinBuildHeight() || body.maxY>=world.getMaxBuildHeight())return false;
        if(!Integrations.border(world,position.x,position.z,p.getBbWidth()/2.0))return false;
        // A thin probe below the feet accepts real support from slabs, stairs and carpets.
        // It also rejects hovering, fluids and dangerous floors without requiring a 5x5 plaza.
        AABB feet=new AABB(body.minX,body.minY-0.0625,body.minZ,body.maxX,body.minY,body.maxZ);
        int minX=(int)Math.floor(body.minX),maxX=(int)Math.floor(body.maxX-1.0e-7);
        int minZ=(int)Math.floor(body.minZ),maxZ=(int)Math.floor(body.maxZ-1.0e-7);
        int minY=(int)Math.floor(feet.minY),maxY=(int)Math.floor(body.maxY-1.0e-7);
        for(int cx=minX>>4;cx<=maxX>>4;cx++)for(int cz=minZ>>4;cz<=maxZ>>4;cz++)
            if(world.getChunkSource().getChunkNow(cx,cz)==null)return false;
        boolean supported=false;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=minY;y<=maxY;y++) {
            BlockPos pos=new BlockPos(x,y,z);BlockState state=world.getBlockState(pos);
            if(hazard(state) || !state.getFluidState().isEmpty())return false;
            for(AABB local:state.getCollisionShape(world,pos).toAabbs()) {
                AABB collision=local.move(x,y,z);
                if(collision.intersects(body))return false;
                if(collision.intersects(feet))supported=true;
            }
        }
        return supported && world.noCollision(p,body);
    }
}
