#!/usr/bin/env python3
"""Preserve a supplied exact-species blacklist while removing repeated MoLang OR chains."""
import argparse, json, re, zipfile
from pathlib import Path

def build(source, target):
    rule_path='data/cobblemon/spawn_rules/svf_fakemon_only_overworld.json'
    with zipfile.ZipFile(source) as original:
        rule=json.loads(original.read(rule_path))
        names=[]
        for component in rule['components']:
            assert component['type']=='filter' and component['allow']=='false'
            assert component['spawnablePositionSelector']=="v.spawnable_position.world.is_of('minecraft:overworld')"
            expression=component['spawnDetailSelector']
            terms=re.findall(r"v\.spawn_detail\.pokemon\.species == '([a-z0-9_-]+)'",expression)
            assert terms and expression==' || '.join("v.spawn_detail.pokemon.species == '"+name+"'" for name in terms)
            names.extend(terms)
        assert len(names)==len(set(names))==1025
        membership='|'+'|'.join(names)+'|'
        rule['components']=[{
            'type':'filter',
            'spawnablePositionSelector':"v.spawnable_position.world.is_of('minecraft:overworld')",
            # Omitted detail selector uses the native constant AllSpawnDetailSelector.
            # Position is checked before evaluating this one exact delimiter lookup.
            'allow':"!q.is_included('"+membership+"', '|' + v.spawn_detail.pokemon.species + '|')"
        }]
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as result:
            for info in original.infolist():
                data=original.read(info.filename)
                if info.filename==rule_path:data=(json.dumps(rule,ensure_ascii=False,indent=2)+'\n').encode()
                result.writestr(info,data)
    print(json.dumps({'species':len(names),'oldFilters':65,'newFilters':1,'output':str(target)}))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('source',type=Path);parser.add_argument('target',type=Path)
    args=parser.parse_args();build(args.source,args.target)
