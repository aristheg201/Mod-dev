package vn.svframe.svarcade.systems.shop;

import java.util.*;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.*;

/** Match-local shop grants. Effects are typed IDs interpreted by registered composing systems. */
public interface ShopAccess {
    SessionServices.Key<ShopAccess> ACCESS = new SessionServices.Key<>(Id.of("svarcade:shop"), ShopAccess.class);
    record Item(Id id, long cost, int stock, int perActor, long durationTicks, Id effect, Map<String, Object> payload) {
        public Item { payload = Map.copyOf(payload); }
    }
    record Grant(long id, UUID actor, Id item, Id effect, Map<String, Object> payload, long remainingTicks) {
        public Grant { payload = Map.copyOf(payload); }
    }
    Map<Id, Item> items();
    List<Grant> grants(UUID actor);
    int sold(Id item);
    StateChange preparePurchase(UUID actor, Id item);
    long revision();
}
