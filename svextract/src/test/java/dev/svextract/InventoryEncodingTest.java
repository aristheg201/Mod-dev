package dev.svextract;

import dev.svextract.persistence.InventorySnapshot;
import net.minecraft.nbt.*;
import java.io.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryEncodingTest {
    private byte[] encode(NbtCompound root) throws IOException {var bytes=new ByteArrayOutputStream();NbtIo.writeCompound(root,new DataOutputStream(bytes));return bytes.toByteArray();}
    @Test void crashRecoveryRemovesUnextractedLootAndPreservesGunComponentsAndGameMode() throws Exception{
        NbtCompound root=new NbtCompound();NbtList inventory=new NbtList();NbtCompound gun=new NbtCompound();
        gun.putByte("Slot",(byte)0);gun.putString("id","pointblank:glock17");gun.putInt("count",1);
        NbtCompound components=new NbtCompound();components.putInt("pointblank:ammo",2);gun.put("components",components);inventory.add(gun);
        NbtCompound loot=gun.copy(),custom=new NbtCompound(),lootComponents=components.copy();
        java.util.UUID raid=java.util.UUID.randomUUID();custom.putUuid("svextract_raid",raid);lootComponents.put("minecraft:custom_data",custom);loot.put("components",lootComponents);loot.putByte("Slot",(byte)1);inventory.add(loot);
        root.put("inventory",inventory);root.putInt("gamemode",0);
        byte[] original=encode(root),recovered=InventorySnapshot.withoutRaidLoot(original,65536,raid);
        NbtCompound read=NbtIo.readCompound(new DataInputStream(new ByteArrayInputStream(recovered)),NbtSizeTracker.of(65536));
        assertEquals(1,read.getList("inventory",NbtElement.COMPOUND_TYPE).size());assertEquals(gun,read.getList("inventory",NbtElement.COMPOUND_TYPE).getCompound(0));
        assertEquals(0,read.getInt("gamemode"));assertArrayEquals(original,encode(root));
    }
    @Test void aLaterCrashCannotDeleteLootOwnedFromAnEarlierExtraction() throws Exception{
        java.util.UUID previous=java.util.UUID.randomUUID(),current=java.util.UUID.randomUUID(),identity=java.util.UUID.randomUUID();
        NbtCompound root=new NbtCompound(),owned=new NbtCompound(),components=new NbtCompound(),data=new NbtCompound();NbtList items=new NbtList();
        data.putUuid("svextract_raid",previous);data.putUuid("svextract_item",identity);data.putString("attachment","preserve");
        components.put("minecraft:custom_data",data);components.putInt("pointblank:ammo",2);owned.put("components",components);owned.putString("id","pointblank:glock17");owned.putByte("Slot",(byte)0);items.add(owned);root.put("inventory",items);
        byte[] oldCheckpoint=encode(root);
        assertArrayEquals(oldCheckpoint,InventorySnapshot.withoutRaidLoot(oldCheckpoint,65536,current));
        byte[] secured=InventorySnapshot.secureRaidLoot(oldCheckpoint,65536,previous);
        NbtCompound read=NbtIo.readCompound(new DataInputStream(new ByteArrayInputStream(secured)),NbtSizeTracker.of(65536));
        NbtCompound saved=read.getList("inventory",NbtElement.COMPOUND_TYPE).getCompound(0).getCompound("components");
        assertFalse(saved.getCompound("minecraft:custom_data").contains("svextract_raid"));assertEquals(identity,saved.getCompound("minecraft:custom_data").getUuid("svextract_item"));assertEquals("preserve",saved.getCompound("minecraft:custom_data").getString("attachment"));assertEquals(2,saved.getInt("pointblank:ammo"));
        assertArrayEquals(secured,InventorySnapshot.withoutRaidLoot(secured,65536,current));assertArrayEquals(oldCheckpoint,encode(root));
    }
    @Test void lootPreservesReservedComponentsAndUsesOnlyEmptyMainSlots() throws Exception {
        NbtCompound root=new NbtCompound();NbtList inventory=new NbtList();NbtCompound gun=new NbtCompound();
        gun.putByte("Slot",(byte)0);gun.putString("id","pointblank:mp5");gun.putInt("count",1);
        NbtCompound components=new NbtCompound();components.putString("custom-magazine-data","keep-original");gun.put("components",components);inventory.add(gun);
        root.put("inventory",inventory);root.putInt("selected",0);byte[] original=encode(root);
        NbtCompound loot=new NbtCompound();loot.putString("id","pointblank:ammo9mm");loot.putInt("count",8);loot.putByte("Slot",(byte)0);
        byte[] settlement=InventorySnapshot.withLoot(original,List.of(loot),65536);
        NbtCompound result=NbtIo.readCompound(new DataInputStream(new ByteArrayInputStream(settlement)),NbtSizeTracker.of(65536));
        assertEquals(gun,result.getList("inventory",NbtElement.COMPOUND_TYPE).getCompound(0));
        assertEquals(1,result.getList("inventory",NbtElement.COMPOUND_TYPE).getCompound(1).getByte("Slot"));
        assertArrayEquals(original,encode(root));assertEquals(0,loot.getByte("Slot"));
        assertArrayEquals(settlement,InventorySnapshot.withLoot(original,List.of(loot),65536));
    }
    @Test void fullInventoryRejectsLootWithoutAlteringReservation() throws Exception {
        NbtCompound root=new NbtCompound();NbtList inventory=new NbtList();
        for(int slot=0;slot<36;slot++){NbtCompound item=new NbtCompound();item.putByte("Slot",(byte)slot);item.putString("id","minecraft:stone");item.putInt("count",64);inventory.add(item);}
        root.put("inventory",inventory);byte[] original=encode(root);NbtCompound loot=new NbtCompound();loot.putString("id","minecraft:copper_ingot");loot.putInt("count",2);
        assertThrows(IOException.class,()->InventorySnapshot.withLoot(original,List.of(loot),65536));assertArrayEquals(original,encode(root));
    }
    @Test void snapshotAndPlayerSaveUseNamedCompoundEncoding() throws Exception{
        NbtCompound root=new NbtCompound();NbtList items=new NbtList();NbtCompound item=new NbtCompound();
        item.putByte("Slot",(byte)0);item.putString("id","pointblank:ak47");item.putInt("count",1);
        NbtCompound components=new NbtCompound();NbtCompound custom=new NbtCompound();custom.putString("identity","do-not-strip-me");
        components.put("minecraft:custom_data",custom);item.put("components",components);items.add(item);root.put("inventory",items);
        ByteArrayOutputStream snapshot=new ByteArrayOutputStream();NbtIo.writeCompound(root,new DataOutputStream(snapshot));
        NbtCompound saved=new NbtCompound();saved.put("Inventory",items.copy());saved.putString("Dimension","svextract:haven9");
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();NbtIo.writeCompressed(saved,compressed);
        assertDoesNotThrow(()->InventorySnapshot.verifyPlayerFile(compressed.toByteArray(),snapshot.toByteArray(),"svextract:haven9",65536));
        saved.putString("Dimension","svextract:raid");compressed.reset();NbtIo.writeCompressed(saved,compressed);
        assertThrows(IOException.class,()->InventorySnapshot.verifyPlayerFile(compressed.toByteArray(),snapshot.toByteArray(),"svextract:haven9",65536));
    }
    @Test void vanillaSlotOrderingDoesNotHoldValidWithdrawalsOrArmoredLootPending()throws Exception{
        NbtCompound root=new NbtCompound(),saved=new NbtCompound();NbtList intent=new NbtList(),actual=new NbtList();
        NbtCompound armor=new NbtCompound(),gun=new NbtCompound(),components=new NbtCompound();
        armor.putByte("Slot",(byte)100);armor.putString("id","minecraft:iron_chestplate");armor.putInt("count",1);
        gun.putByte("Slot",(byte)0);gun.putString("id","pointblank:glock17");gun.putInt("count",1);components.putInt("pointblank:ammo",7);gun.put("components",components);
        intent.add(armor);intent.add(gun);actual.add(gun.copy());actual.add(armor.copy());root.put("inventory",intent);
        saved.put("Inventory",actual);saved.putString("Dimension","svextract:haven9");var bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(saved,bytes);
        assertDoesNotThrow(()->InventorySnapshot.verifyPlayerFile(bytes.toByteArray(),encode(root),"svextract:haven9",65536));
        actual.getCompound(0).getCompound("components").putInt("pointblank:ammo",8);bytes.reset();NbtIo.writeCompressed(saved,bytes);
        assertThrows(IOException.class,()->InventorySnapshot.verifyPlayerFile(bytes.toByteArray(),encode(root),"svextract:haven9",65536));
    }
    @Test void duplicateSlotSnapshotsCannotPassSaveVerification()throws Exception{
        NbtCompound root=new NbtCompound(),saved=new NbtCompound(),item=new NbtCompound();NbtList intent=new NbtList(),actual=new NbtList();
        item.putByte("Slot",(byte)0);item.putString("id","minecraft:diamond");item.putInt("count",1);intent.add(item);intent.add(item.copy());actual.add(item.copy());root.put("inventory",intent);saved.put("Inventory",actual);saved.putString("Dimension","svextract:haven9");var bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(saved,bytes);
        assertThrows(IOException.class,()->InventorySnapshot.verifyPlayerFile(bytes.toByteArray(),encode(root),"svextract:haven9",65536));
    }
    @Test void emptyInventoryStillHasAValidList() throws Exception{
        NbtCompound root=new NbtCompound();root.put("inventory",new NbtList());ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        NbtIo.writeCompound(root,new DataOutputStream(bytes));
        NbtCompound read=NbtIo.readCompound(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),NbtSizeTracker.of(65536));
        assertTrue(read.contains("inventory",NbtElement.LIST_TYPE));assertEquals(0,read.getList("inventory",NbtElement.COMPOUND_TYPE).size());
    }
}
