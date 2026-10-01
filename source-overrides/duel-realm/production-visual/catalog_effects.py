from pathlib import Path

p=Path('src/main/java/vn/svarcade/tcg/data/Catalog.java')
s=p.read_text()
if 'EffectSpec spec' not in s:
    s=s.replace('List<String> phases, boolean oncePerTurn) {}','''List<String> phases, boolean oncePerTurn, EffectSpec spec) {
        public Effect(String operation,int amount,int speed,int lifeCost,String target,List<String> phases,boolean oncePerTurn) {
            this(operation,amount,speed,lifeCost,target,phases,oncePerTurn,null);
        }
    }''')
    s=s.replace('c.validate(); return c;', 'c.validate(); c=EffectContent.apply(c); c.validate(); return c;')
    s=s.replace('Set.of("damage", "draw",', 'Set.of("composite", "damage", "draw",')
    s=s.replace('            if(c.triggers!=null)', '            if(c.effect!=null&&c.effect.spec()!=null)c.effect.spec().validate();\n            if(c.triggers!=null)')
p.write_text(s)
