package io.github.aristheg201.svhub.engine;

import java.util.*;

public record BattleBoard(int columns, int rows, boolean hex, Set<Integer> blocked) {
    public BattleBoard {
        if (columns < 1 || rows < 1 || (long)columns * rows > 4096) throw new IllegalArgumentException("Invalid board dimensions");
        blocked = blocked == null ? Set.of() : Set.copyOf(blocked);
        for (int cell : blocked) if (cell < 0 || cell >= columns * rows) throw new IllegalArgumentException("Blocked cell out of bounds");
    }
    public boolean valid(int cell) { return cell >= 0 && cell < columns * rows && !blocked.contains(cell); }
    public int distance(int a, int b) {
        int ar=a/columns, br=b/columns, ac=a%columns, bc=b%columns;
        if (!hex) return Math.max(Math.abs(ar-br),Math.abs(ac-bc));
        int aq=ac-(ar-(ar&1))/2, bq=bc-(br-(br&1))/2;
        return Math.max(Math.abs(aq-bq),Math.max(Math.abs(ar-br),Math.abs(aq+ar-bq-br)));
    }
    public List<Integer> neighbors(int cell) {
        List<Integer> result=new ArrayList<>(); int r=cell/columns,c=cell%columns;
        for(int dr=-1;dr<=1;dr++) for(int dc=-1;dc<=1;dc++) {
            if(dr==0&&dc==0) continue;
            if(hex && dr!=0 && dc==((r&1)==0?1:-1)) continue;
            int nr=r+dr,nc=c+dc,next=nr*columns+nc;
            if(nr>=0&&nr<rows&&nc>=0&&nc<columns&&valid(next))result.add(next);
        }
        return result;
    }
    /** Bounded BFS resolves blocking formations deterministically; cells break ties. */
    public int nextStep(int start, int target, int range, Set<Integer> occupied) {
        if(distance(start,target)<=range)return start;
        ArrayDeque<Integer> queue=new ArrayDeque<>(); Map<Integer,Integer> first=new HashMap<>();
        queue.add(start);first.put(start,start);
        while(!queue.isEmpty()) {
            int current=queue.removeFirst();
            for(int next:neighbors(current)) {
                if(first.containsKey(next)||occupied.contains(next))continue;
                int step=current==start?next:first.get(current);first.put(next,step);
                if(distance(next,target)<=range)return step;
                queue.addLast(next);
            }
        }
        return start;
    }
}
