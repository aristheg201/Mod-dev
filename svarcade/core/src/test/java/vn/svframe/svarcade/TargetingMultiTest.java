package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.runtime.ThreadGuard;
import vn.svframe.svarcade.systems.path.Vec3;
import vn.svframe.svarcade.systems.targeting.*;
import vn.svframe.svarcade.systems.targeting.TargetingAccess.*;
import static org.junit.jupiter.api.Assertions.*;

class TargetingMultiTest {
    @Test void multiSelectUsesSameSpatialOrderingAndBound() {
        SpatialTargetIndex index = new SpatialTargetIndex(new ThreadGuard(), 4, 32, 32, 8, 512, 32, Set.of(Mode.CLOSEST, Mode.FIRST));
        UUID requester = UUID.randomUUID();
        Target a = new Target(new UUID(0,1), new Vec3(3,0,0), 10, 1, .8, Set.of("ground"));
        Target b = new Target(new UUID(0,2), new Vec3(1,0,0), 10, 1, .4, Set.of("ground"));
        Target c = new Target(new UUID(0,3), new Vec3(2,0,0), 10, 1, .9, Set.of("ground"));
        index.upsert(a); index.upsert(b); index.upsert(c);
        Query closest = new Query(new Vec3(0,0,0), 10, Mode.CLOSEST, Filter.unrestricted());
        assertEquals(List.of(b.id(), c.id()), index.selectMany(requester, closest, 2).stream().map(Target::id).toList());
        Query first = new Query(new Vec3(0,0,0), 10, Mode.FIRST, Filter.unrestricted());
        assertEquals(List.of(c.id(), a.id(), b.id()), index.selectMany(requester, first, 3).stream().map(Target::id).toList());
        assertThrows(IllegalArgumentException.class, () -> index.selectMany(requester, closest, 33));
    }
}
