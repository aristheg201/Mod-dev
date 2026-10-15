package dev.svextract;

import dev.svextract.persistence.*;
import net.minecraft.nbt.*;
import java.io.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StashInventoryTest {
    private byte[] encode(NbtCompound root)throws IOException{var bytes=new ByteArrayOutputStream();NbtIo.writeCompound(root,new DataOutputStream(bytes));return bytes.toByteArray();}
    private NbtCompound read(byte[] bytes)throws IOException{return NbtIo.readCompound(new DataInputStream(new ByteArrayInputStream(bytes)),NbtSizeTracker.of(65536));}
    private NbtCompound gun(int slot){
        NbtCompound item=new NbtCompound(),components=new NbtCompound(),custom=new NbtCompound();
        item.putByte("Slot",(byte)slot);item.putString("id","pointblank:glock17");item.putInt("count",1);
        custom.putUuid("svextract_item",UUID.randomUUID());custom.putString("attachment","do-not-strip");components.put("minecraft:custom_data",custom);components.putInt("pointblank:ammo",7);item.put("components",components);return item;
    }
    @Test void depositAndWithdrawalPreserveIdentityAttachmentsAmmoAndPlayerMode()throws Exception{
        NbtCompound root=new NbtCompound();NbtList items=new NbtList();NbtCompound gun=gun(0);items.add(gun);root.put("inventory",items);root.putInt("gamemode",2);root.putInt("selected",4);
        byte[] original=encode(root),empty=InventorySnapshot.empty(65536);var deposit=StashInventory.move(original,empty,true,0,54,65536);
        assertTrue(read(deposit.inventory()).getList("inventory",10).isEmpty());assertEquals(gun,read(deposit.stash()).getList("inventory",10).getCompound(0));
        var withdrawal=StashInventory.move(deposit.inventory(),deposit.stash(),false,0,54,65536);
        assertEquals(read(original),read(withdrawal.inventory()));assertEquals(2,read(withdrawal.inventory()).getInt("gamemode"));assertArrayEquals(original,encode(root));
    }
    @Test void fullDestinationAndEmptySourceLeaveBothSnapshotsUntouched()throws Exception{
        NbtCompound root=new NbtCompound();NbtList items=new NbtList();for(int i=0;i<27;i++)items.add(gun(i));root.put("inventory",items);byte[] full=encode(root),empty=InventorySnapshot.empty(65536);
        assertThrows(IOException.class,()->StashInventory.move(full,full,true,0,27,65536));assertThrows(IOException.class,()->StashInventory.move(empty,full,true,0,27,65536));
        assertArrayEquals(full,encode(root));assertThrows(IOException.class,()->StashInventory.move(full,empty,true,150,54,65536));
    }
    @Test void pagesDoNotLeakOtherSlotsAndWithdrawalUsesMainInventoryOnly()throws Exception{
        NbtCompound root=new NbtCompound();NbtList items=new NbtList();NbtCompound item=gun(27);items.add(item);root.put("inventory",items);byte[] stash=encode(root),empty=InventorySnapshot.empty(65536);
        assertTrue(read(StashInventory.page(stash,0,54,65536)).getList("inventory",10).isEmpty());
        assertEquals(0,read(StashInventory.page(stash,1,54,65536)).getList("inventory",10).getCompound(0).getByte("Slot"));
        var move=StashInventory.move(empty,stash,false,27,54,65536);NbtCompound received=read(move.inventory()).getList("inventory",10).getCompound(0);
        assertEquals(item.getCompound("components"),received.getCompound("components"));assertEquals(0,received.getByte("Slot"));
        assertThrows(IOException.class,()->StashInventory.page(stash,0,27,65536));assertThrows(IOException.class,()->StashInventory.page(stash,2,54,65536));
    }
    @Test void corruptDuplicateSlotsAreRejectedBeforeOwnershipMoves()throws Exception{
        NbtCompound root=new NbtCompound();NbtList items=new NbtList();items.add(gun(0));items.add(gun(0));root.put("inventory",items);
        assertThrows(IOException.class,()->StashInventory.move(InventorySnapshot.empty(65536),encode(root),false,0,54,65536));
    }
}
