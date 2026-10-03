package vn.svarcade.tcg.physical;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svarcade.tcg.data.Catalog;
import vn.svarcade.tcg.economy.CardStore;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalCardsTest {
    @TempDir Path temp;
    Catalog catalog()throws Exception{return Catalog.load(Path.of("missing-physical.json"));}
    PhysicalCardData data(Catalog c){return PhysicalCardData.create(c,"charizard","Normal","ADMIN_GRANT",UUID.randomUUID().toString(),"Finder");}
    @Test void componentCodecRoundTripAndImmutableAspects()throws Exception {
        var c=catalog();var d=data(c);var encoded=PhysicalCardData.CODEC.encodeStart(JsonOps.INSTANCE,d).getOrThrow();
        assertEquals(d,PhysicalCardData.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow());
        assertThrows(UnsupportedOperationException.class,()->d.capturedAspects().add("forged"));
    }
    @Test void codecRejectsMalformedAndMissingToken()throws Exception {
        var json=(JsonObject)PhysicalCardData.CODEC.encodeStart(JsonOps.INSTANCE,data(catalog())).getOrThrow();
        json.remove("physical_id");assertTrue(PhysicalCardData.CODEC.parse(JsonOps.INSTANCE,json).error().isPresent());
        json.addProperty("physical_id",new UUID(0,0).toString());assertTrue(PhysicalCardData.CODEC.parse(JsonOps.INSTANCE,json).error().isPresent());
        json.addProperty("physical_id",UUID.randomUUID().toString());json.addProperty("version",99);assertTrue(PhysicalCardData.CODEC.parse(JsonOps.INSTANCE,json).error().isPresent());
    }
    @Test void creationValidatesCatalogAndAlwaysUsesUniqueTokens()throws Exception {
        var c=catalog();Set<UUID> tokens=new HashSet<>();for(int i=0;i<1000;i++){var d=data(c);d.validate(c);assertTrue(tokens.add(d.physicalId()));}
        assertThrows(IllegalArgumentException.class,()->PhysicalCardData.create(c,"missing","Normal","ADMIN_GRANT","",""));
        assertThrows(IllegalArgumentException.class,()->PhysicalCardData.create(c,"charizard","forged","ADMIN_GRANT","",""));
    }
    @Test void redemptionCommitSurvivesRestartAndPreservesFinder()throws Exception {
        var c=catalog();var d=data(c);String serial;
        Path db=temp.resolve("cards.db");
        try(var store=new CardStore(db,c,new Random(1))){
            var minted=store.redeemPhysical("alice",d.physicalId(),d.cardId(),d.finish(),d.origin(),d.finderUuid());
            assertEquals(CardStore.RedeemStatus.MINTED,minted.status());serial=minted.serial();assertEquals(1,store.profile("alice").owned());
            var copy=store.binder("alice",0).getFirst();assertEquals(d.finderUuid(),copy.finder());assertEquals(d.origin(),copy.origin());assertEquals("Normal",copy.finish());
        }
        // Simulates DB commit with the pre-consumption stack surviving a process restart.
        try(var store=new CardStore(db,c,new Random(2))){
            var recovered=store.redeemPhysical("alice",d.physicalId(),d.cardId(),d.finish(),d.origin(),d.finderUuid());
            assertEquals(CardStore.RedeemStatus.RECOVERED,recovered.status());assertEquals(serial,recovered.serial());
            assertEquals(CardStore.RedeemStatus.ALREADY_REDEEMED,store.redeemPhysical("bob",d.physicalId(),d.cardId(),d.finish(),d.origin(),d.finderUuid()).status());
            assertEquals(1,store.profile("alice").owned());assertEquals(0,store.profile("bob").owned());
        }
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+db);var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM provenance WHERE serial='"+serial+"'")){rows.next();assertEquals(1,rows.getInt(1));}
    }
    @Test void concurrentTokenCannotMintForTwoOwners()throws Exception {
        var c=catalog();var d=data(c);
        try(var store=new CardStore(temp.resolve("race.db"),c,new Random(1));var threads=Executors.newFixedThreadPool(2)){
            var a=threads.submit(()->store.redeemPhysical("a",d.physicalId(),d.cardId(),d.finish(),d.origin(),d.finderUuid()));
            var b=threads.submit(()->store.redeemPhysical("b",d.physicalId(),d.cardId(),d.finish(),d.origin(),d.finderUuid()));
            assertEquals(1,(a.get().accepted()?1:0)+(b.get().accepted()?1:0));assertEquals(a.get().serial(),b.get().serial());
            assertEquals(1,store.profile("a").owned()+store.profile("b").owned());
        }
    }
    @Test void invalidRedemptionRollsBackTokenAndProfile()throws Exception {
        var c=catalog();var token=UUID.randomUUID();
        try(var store=new CardStore(temp.resolve("invalid.db"),c,new Random(1))){
            assertThrows(IllegalArgumentException.class,()->store.redeemPhysical("a",token,"missing","Normal","ADMIN_GRANT","finder"));
            assertThrows(IllegalArgumentException.class,()->store.redeemPhysical("a",null,"charizard","Normal","ADMIN_GRANT","finder"));
            assertFalse(store.hasProfile("a"));assertEquals(CardStore.RedeemStatus.MINTED,store.redeemPhysical("a",token,"charizard","Normal","ADMIN_GRANT","finder").status());
        }
    }
}
