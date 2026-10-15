package dev.svextract.persistence;

import net.minecraft.nbt.*;
import java.io.*;

/** Whole-stack ownership moves preserve raw registry-aware item components, including firearm state. */
public final class StashInventory {
    public record Move(byte[] inventory,byte[] stash) {}
    private static NbtCompound read(byte[] bytes,int limit)throws IOException{
        if(bytes==null||bytes.length==0||bytes.length>limit)throw new IOException("Invalid storage snapshot size");
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
            NbtCompound root=NbtIo.readCompound(in,NbtSizeTracker.of(limit));
            if(!root.contains("inventory",NbtElement.LIST_TYPE))throw new IOException("Missing storage inventory");
            return root;
        }
    }
    private static byte[] encode(NbtCompound root,int limit)throws IOException{
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){NbtIo.writeCompound(root,out);}
        if(bytes.size()>limit)throw new IOException("Storage snapshot exceeds configured byte budget");return bytes.toByteArray();
    }
    private static void validate(NbtList items,int capacity,boolean player)throws IOException{
        boolean[] occupied=new boolean[256];
        for(int i=0;i<items.size();i++){
            NbtCompound item=items.getCompound(i);int slot=item.getByte("Slot")&255;
            if(occupied[slot]||(!player&&slot>=capacity)||(player&&slot>=36&&slot!=100&&slot!=101&&slot!=102&&slot!=103&&slot!=150)
                    ||item.getString("id").isEmpty()||item.getInt("count")<1)throw new IOException("Invalid or duplicate storage slot; restore the previous capacity if it was reduced");
            occupied[slot]=true;
        }
    }
    public static Move move(byte[] inventory,byte[] stash,boolean deposit,int sourceSlot,int capacity,int limit)throws IOException{
        NbtCompound player=read(inventory,limit),storage=read(stash,limit);
        NbtList playerItems=player.getList("inventory",NbtElement.COMPOUND_TYPE),stashItems=storage.getList("inventory",NbtElement.COMPOUND_TYPE);
        validate(playerItems,36,true);validate(stashItems,capacity,false);
        if(sourceSlot<0||sourceSlot>=(deposit?36:capacity))throw new IOException("Invalid source slot");
        NbtList source=deposit?playerItems:stashItems,target=deposit?stashItems:playerItems;
        int found=-1;for(int i=0;i<source.size();i++)if((source.getCompound(i).getByte("Slot")&255)==sourceSlot){found=i;break;}
        if(found<0)throw new IOException("That slot is empty; refresh storage");
        boolean[] occupied=new boolean[deposit?capacity:36];
        for(int i=0;i<target.size();i++){int slot=target.getCompound(i).getByte("Slot")&255;if(slot<occupied.length)occupied[slot]=true;}
        int destination=0;while(destination<occupied.length&&occupied[destination])destination++;
        if(destination==occupied.length)throw new IOException(deposit?"Stash is full":"Loadout inventory is full");
        NbtCompound moved=source.getCompound(found).copy();source.remove(found);moved.putByte("Slot",(byte)destination);target.add(moved);
        return new Move(encode(player,limit),encode(storage,limit));
    }
    /** Only one page is replicated. Oversized item components fail visibly instead of creating unbounded packets. */
    public static byte[] page(byte[] stash,int page,int capacity,int limit)throws IOException{
        if(page<0||page>=capacity/27)throw new IOException("Invalid stash page");
        NbtList items=read(stash,limit).getList("inventory",NbtElement.COMPOUND_TYPE);validate(items,capacity,false);
        NbtList visible=new NbtList();for(int i=0;i<items.size();i++){
            NbtCompound item=items.getCompound(i);int slot=item.getByte("Slot")&255;
            if(slot/27==page){NbtCompound copy=item.copy();copy.putByte("Slot",(byte)(slot%27));visible.add(copy);}
        }
        NbtCompound root=new NbtCompound();root.put("inventory",visible);return encode(root,60*1024);
    }
    private StashInventory(){}
}
