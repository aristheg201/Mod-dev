package dev.svextract;

import dev.svextract.compat.CompanionPolicy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompanionPolicyTest {
    @Test void exactlyOneOwnedCompanionMayDeploy(){
        CompanionPolicy p=new CompanionPolicy();UUID owner=UUID.randomUUID(),selected=UUID.randomUUID();p.select(owner,List.of(selected));
        assertTrue(p.allows(owner,selected));assertFalse(p.allows(owner,UUID.randomUUID()));assertFalse(p.allows(UUID.randomUUID(),selected));
        assertThrows(IllegalArgumentException.class,()->p.select(owner,List.of(selected,UUID.randomUUID())));assertTrue(p.allows(owner,selected));
        p.release(owner);assertFalse(p.allows(owner,selected));
    }
    @Test void NoCompanionIsValidAndSelectionDoesNotLeak(){
        CompanionPolicy p=new CompanionPolicy();UUID owner=UUID.randomUUID();p.select(owner,List.of());assertFalse(p.allows(owner,UUID.randomUUID()));
        p.select(owner,List.of(UUID.randomUUID()));p.clear();assertFalse(p.allows(owner,UUID.randomUUID()));
    }
}
