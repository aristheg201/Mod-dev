package vn.svframe.svarcade.systems.board;

import java.util.UUID;
import vn.svframe.svarcade.security.IntentGate;

/** Server-authoritative two-click board controller. Platform supplies only server-derived square/facts. */
public interface BoardInputAccess {
    enum Status { SELECTED, CANCELLED, MOVED, REJECTED }
    record Result(Status status, int selected, String reason) { }
    Result click(IntentGate.Facts facts, int square, boolean cancel);
    void clear(UUID actor);
}
