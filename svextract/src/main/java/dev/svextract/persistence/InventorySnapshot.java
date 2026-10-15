package dev.svextract.persistence;

import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import java.io.*;
import java.util.List;

/** Captures Minecraft's registry-aware inventory NBT including modded data components verbatim. */
public final class InventorySnapshot {
    /** Failure destroys exposed equipment. This still produces a valid whole-inventory snapshot. */
    public static byte[] empty(int limit)throws IOException{
        NbtCompound root=new NbtCompound();root.put("inventory",new NbtList());root.putInt("selected",0);return encode(root,limit);
    }
    /** Restart recovery keeps checkpointed deployment items, discarding unextracted raid loot. */
    public static byte[] withoutRaidLoot(byte[] snapshot,int limit,java.util.UUID currentRaid)throws IOException{
        NbtCompound root;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(snapshot))){root=NbtIo.readCompound(in,NbtSizeTracker.of(limit));}
        NbtList items=root.getList("inventory",NbtElement.COMPOUND_TYPE);
        for(int i=items.size()-1;i>=0;i--){
            NbtCompound custom=items.getCompound(i).getCompound("components").getCompound("minecraft:custom_data");
            if(custom.containsUuid("svextract_raid")&&currentRaid.equals(custom.getUuid("svextract_raid")))items.remove(i);
        }
        return encode(root,limit);
    }
    /** Successful extraction converts current-raid ownership to permanent item identity. */
    public static byte[] secureRaidLoot(byte[] snapshot,int limit,java.util.UUID currentRaid)throws IOException{
        NbtCompound root;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(snapshot))){root=NbtIo.readCompound(in,NbtSizeTracker.of(limit));}
        NbtList items=root.getList("inventory",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<items.size();i++){
            NbtCompound custom=items.getCompound(i).getCompound("components").getCompound("minecraft:custom_data");
            if(custom.containsUuid("svextract_raid")&&currentRaid.equals(custom.getUuid("svextract_raid")))custom.remove("svextract_raid");
        }
        return encode(root,limit);
    }
    private static byte[] encode(NbtCompound root,int limit)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){NbtIo.writeCompound(root,out);}
        if(bytes.size()>limit)throw new IOException("Inventory snapshot exceeds budget");return bytes.toByteArray();
    }
    /** Adds authoritative loot to empty main slots without decoding/re-encoding reserved mod components.
     * The returned whole inventory is journaled before delivery; replay replaces, never adds, loot.
     */
    public static byte[] withLoot(byte[] reserved,List<NbtCompound> loot,int limit) throws IOException {
        if(reserved==null||reserved.length>limit||loot.size()>36)throw new IOException("Invalid loot settlement budget");
        NbtCompound root;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(reserved))){root=NbtIo.readCompound(in,NbtSizeTracker.of(limit));}
        if(!root.contains("inventory",NbtElement.LIST_TYPE))throw new IOException("Missing reserved inventory");
        NbtList items=root.getList("inventory",NbtElement.COMPOUND_TYPE);boolean[] occupied=new boolean[36];
        for(int i=0;i<items.size();i++){int slot=items.getCompound(i).getByte("Slot")&255;if(slot<36){if(occupied[slot])throw new IOException("Duplicate reserved main slot");occupied[slot]=true;}}
        for(NbtCompound reward:loot){
            if(reward.getString("id").isEmpty()||reward.getInt("count")<1)throw new IOException("Invalid encoded loot stack");
            int slot=0;while(slot<36&&occupied[slot])slot++;
            if(slot==36)throw new IOException("No reserved inventory space for loot; original equipment remains protected");
            occupied[slot]=true;NbtCompound item=reward.copy();item.putByte("Slot",(byte)slot);items.add(item);
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){NbtIo.writeCompound(root,out);}
        if(bytes.size()>limit)throw new IOException("Loot settlement exceeds snapshot budget");return bytes.toByteArray();
    }
    public static byte[] capture(ServerPlayerEntity player,int limit) throws IOException {
        NbtCompound root=new NbtCompound();
        root.put("inventory",player.getInventory().writeNbt(new NbtList()));
        root.putInt("selected",player.getInventory().selectedSlot);
        root.putInt("gamemode",player.interactionManager.getGameMode().getId());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){NbtIo.writeCompound(root,out);}
        if(bytes.size()>limit)throw new IOException("Inventory exceeds configured snapshot limit. Remove oversized items before deployment.");
        return bytes.toByteArray();
    }
    public static void restore(ServerPlayerEntity player,byte[] bytes,int limit) throws IOException {
        if(bytes==null || bytes.length>limit)throw new IOException("Invalid inventory snapshot size");
        NbtCompound root;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))){
            root=NbtIo.readCompound(in,NbtSizeTracker.of(limit));
        }
        if(!root.contains("inventory",NbtElement.LIST_TYPE))throw new IOException("Missing inventory list");
        player.getInventory().clear();
        player.getInventory().readNbt(root.getList("inventory",NbtElement.COMPOUND_TYPE));
        player.getInventory().selectedSlot=Math.max(0,Math.min(8,root.getInt("selected")));
        if(root.contains("gamemode",NbtElement.INT_TYPE))player.changeGameMode(net.minecraft.world.GameMode.byId(root.getInt("gamemode")));
        player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();
    }
    /** Inventory entries are addressed by Slot, not list position. Vanilla writes main slots before
     * armor/offhand even when a transactional withdrawal appended a lower empty slot. */
    private static NbtList orderedInventory(NbtElement value)throws IOException{
        if(!(value instanceof NbtList inventory)||!inventory.isEmpty()&&inventory.getHeldType()!=NbtElement.COMPOUND_TYPE)
            throw new IOException("Missing or malformed inventory list; ownership remains pending");
        var entries=new java.util.ArrayList<NbtCompound>(inventory.size());boolean[] occupied=new boolean[256];
        for(int i=0;i<inventory.size();i++){
            NbtCompound item=inventory.getCompound(i);
            if(!item.contains("Slot",NbtElement.BYTE_TYPE))throw new IOException("Inventory entry has no byte slot");
            int slot=item.getByte("Slot")&255;
            if(occupied[slot])throw new IOException("Duplicate inventory slot; ownership remains pending");
            occupied[slot]=true;entries.add(item);
        }
        entries.sort(java.util.Comparator.comparingInt(item->item.getByte("Slot")&255));
        NbtList ordered=new NbtList();entries.forEach(ordered::add);return ordered;
    }
    public static void verifyPlayerFile(byte[] compressed,byte[] expected,String dimension,int limit) throws IOException {
        NbtCompound saved=NbtIo.readCompressed(new ByteArrayInputStream(compressed),NbtSizeTracker.of(limit*4L));
        NbtElement snapshot;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(expected))){snapshot=NbtIo.readCompound(in,NbtSizeTracker.of(limit));}
        if(!(snapshot instanceof NbtCompound root) || !orderedInventory(root.get("inventory")).equals(orderedInventory(saved.get("Inventory")))
                || !dimension.equals(saved.getString("Dimension"))||root.contains("gamemode",NbtElement.INT_TYPE)&&root.getInt("gamemode")!=saved.getInt("playerGameType"))throw new IOException("Player save did not persist the expected inventory and return dimension; settlement remains pending");
    }
}
