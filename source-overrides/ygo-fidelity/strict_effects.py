import copy, hashlib, itertools, json, re
from pathlib import Path

ROOT=Path.cwd()
DATA=ROOT/'src/main/resources/data/svarcade_tcg'
POKE=DATA/'pokemon_effects.json'
SIG=DATA/'signature_presentations.json'

OPS=[
 'ADD_COUNTER','MODIFY_ATK','MODIFY_DEF','HEAL_LP','LOOK_TOP_DECK','REORDER_DECK','REMOVE_STATUS',
 'PREVENT_DAMAGE','PREVENT_TARGET','PIERCE','CHANGE_POSITION','DRAW','SEARCH'
]
TRIGGERS=['ON_ATTACK_DECLARE','ON_POSITION_CHANGE','ON_DAMAGE','ON_DESTROYED','ON_TURN_START','ON_CHAIN','ON_SUMMON','ON_CHAIN_RESOLVE']
SHAPES=['RING','ARC','SPIRAL','HELIX','BEAM','TRAIL','IMPACT_CONE','SHOCKWAVE','ORB','VORTEX','COLUMN','AURA','GLYPH','CHAIN_LINE']
SEMANTICS=['CHARGE','CAST_STATUS','BUFF','DEBUFF','SHIELD','HEAL','SEARCH','DRAW','PROJECTILE','IMPACT','POSITION_CHANGE','CHAIN_LINK','CHAIN_NEGATE','REVIVE']

def h(key):
    return hashlib.sha256(key.encode()).digest()

def safe_name(key):
    raw=key.replace('|',' · ').replace('__form__',' · ').replace('fakemon__','').replace('__',' · ').replace('_',' ')
    return ' '.join(w[:1].upper()+w[1:] for w in raw.split())

def gf13_code(message):
    a=message%13; b=(message//13)%13; c=(message//169)%13
    return tuple((a+b*x+c*x*x)%13 for x in range(1,13))

def allocate_codes(keys):
    used=set(); out={}
    for key in sorted(keys,key=lambda k:h(k+'|strict-gameplay-v5')):
        start=int.from_bytes(h(key+'|strict-gameplay-v5')[:8],'big')%2197
        for probe in range(2197):
            n=(start+probe*5)%2197
            if n not in used:
                used.add(n); out[key]=gf13_code(n); break
        else: raise RuntimeError('GF(13) identity space exhausted')
    return out

def op_for(digit,key,slot):
    counter='sig_'+hashlib.sha1((key+'|'+str(slot)).encode()).hexdigest()[:10]
    target_self='SELF'
    if digit==0:return {'type':'ADD_COUNTER','amount':1,'target':target_self,'duration':None,'flags':{'counter':counter,'max':'4'}}
    if digit==1:return {'type':'MODIFY_ATK','amount':100,'target':target_self,'duration':'TURN_END','flags':{}}
    if digit==2:return {'type':'MODIFY_DEF','amount':100,'target':target_self,'duration':'TURN_END','flags':{}}
    if digit==3:return {'type':'HEAL_LP','amount':100,'target':target_self,'duration':None,'flags':{'recipient':'ALLY'}}
    if digit==4:return {'type':'LOOK_TOP_DECK','amount':1,'target':'TOP_DECK','duration':None,'flags':{}}
    if digit==5:return {'type':'REORDER_DECK','amount':2,'target':'TOP_DECK','duration':None,'flags':{'order':'REVERSE'}}
    if digit==6:return {'type':'REMOVE_STATUS','amount':0,'target':target_self,'duration':None,'flags':{'status':'ALL'}}
    if digit==7:return {'type':'PREVENT_DAMAGE','amount':0,'target':target_self,'duration':'TURN_END','flags':{}}
    if digit==8:return {'type':'PREVENT_TARGET','amount':0,'target':target_self,'duration':'TURN_END','flags':{}}
    if digit==9:return {'type':'PIERCE','amount':0,'target':target_self,'duration':'TURN_END','flags':{}}
    if digit==10:return {'type':'CHANGE_POSITION','amount':0,'target':target_self,'duration':None,'flags':{'position':'DEFENSE'}}
    if digit==11:return {'type':'DRAW','amount':1,'target':'DECK','duration':None,'flags':{}}
    return {'type':'SEARCH','amount':1,'target':'DECK','duration':None,'flags':{'reveal':'true'},
            'filter':{'category':'pokemon','tags':['family:'+key.split('|')[0]]}}

def strict_gameplay(data):
    defs=data['definitions']; codes=allocate_codes(defs)
    for key,d in defs.items():
        spec=d['effect']['spec']
        spec['stages']=[s for s in (spec.get('stages') or []) if not str(s.get('id','')).startswith('identity_facet_v')]
        code=codes[key]
        for facet,part in enumerate((code[:6],code[6:])):
            ops=[op_for(v,key,facet*6+i) for i,v in enumerate(part)]
            stage={
              'id':'identity_facet_v5'+('a' if facet==0 else 'b'),
              'effect':{'operation':'composite','amount':0,'speed':2,'lifeCost':0,'target':'none',
                'phases':['DRAW','STANDBY','MAIN1','BATTLE','MAIN2','END'],'oncePerTurn':False,
                'spec':{'triggers':[TRIGGERS[(code[facet]+facet)%len(TRIGGERS)]],
                  'conditions':[{'type':'SOURCE_ZONE','target':'SELF','value':'FIELD','amount':0,'children':[]}],
                  'costs':[],'targets':None,'operations':ops,'oncePerDuel':True,'optional':False,
                  'stages':[],'limitScope':'CARD_NAME','resolutionConditions':[]}},
              'sourceZones':['FIELD'],'listenAny':False}
            spec['stages'].append(stage)
        d['effect']['spec']=spec
        original=d.get('name') or 'Signature'
        label=safe_name(key)
        if not original.lower().startswith(label.lower()+':'):
            d['name']=label+': '+original
        d['signature']={'distinctnessVersion':5,'identityCode':'-'.join(map(str,code))}
    layer=data.setdefault('signatureLayer',{})
    layer.update({'version':5,'strictGameplay':'GF(13) [12,3,10]: every authored Pokemon differs in at least 10 of 12 functional signature-operation slots; two 6-operation once-per-duel facets; names are identity-specific'})
    return data,codes

def collect_presentation_keys(pokemon):
    keys=set(pokemon['definitions'])
    for name in ('catalog.json','deep_effects.json','special_aspect_cards.json'):
        p=DATA/name
        if not p.exists():continue
        raw=json.loads(p.read_text())
        def walk(v):
            if isinstance(v,dict):
                if isinstance(v.get('id'),str) and ('effect' in v or v.get('category')):keys.add(v['id'])
                for k,x in v.items():
                    if k=='cards' and isinstance(x,dict):keys.update(x.keys())
                    walk(x)
            elif isinstance(v,list):
                for x in v:walk(x)
        walk(raw)
    return sorted(keys)

def presentation_word(key,nonce):
    b=h(key+'|strict-presentation-v5|'+str(nonce))
    return tuple((SEMANTICS[b[i]%len(SEMANTICS)],SHAPES[b[6+i]%len(SHAPES)]) for i in range(6))

def allocate_presentations(keys):
    chosen={}; used_triples=set(); used_position_pairs=set()
    for key in keys:
        for nonce in range(250000):
            word=presentation_word(key,nonce)
            if len(set(word))<6:continue
            triples=list(itertools.combinations(sorted(set(word)),3))
            if any(t in used_triples for t in triples):continue
            pp=[]; bad=False
            for i in range(6):
                for j in range(i+1,6):
                    token=((i,word[i]),(j,word[j]))
                    if token in used_position_pairs:bad=True;break
                    pp.append(token)
                if bad:break
            if bad:continue
            chosen[key]=word;used_triples.update(triples);used_position_pairs.update(pp);break
        else:raise RuntimeError('presentation identity space exhausted: '+key)
    return chosen

def strict_presentations(pokemon):
    keys=collect_presentation_keys(pokemon); words=allocate_presentations(keys)
    profiles=json.loads((DATA/'duel_vfx_profiles.json').read_text())
    profile_names=sorted(profiles)
    reg={}
    for key in keys:
        b=h(key+'|presentation-meta-v5'); profile=profile_names[b[0]%len(profile_names)]
        word=words[key]; times=[.10,.25,.40,.56,.72,.87]
        reg[key]={'mode':['STATUS','PROJECTILE','AOE','BEAM'][b[1]%4],'profile':profile,'duration':1450+(b[2]%8)*100,
          'animation':{'semantic':'CAST_STATUS','windup':.08,'release':.24,'impact':.58,'recovery':.94},
          'particle':None,'fallback':'minecraft:end_rod','shape':word[0][1],'sound':'minecraft:block.amethyst_block.chime',
          'stages':[{'semantic':sem,'shape':shape,'profile':profile,'at':times[i]} for i,(sem,shape) in enumerate(word)]}
    for key,d in pokemon['definitions'].items():
        d['effect']['spec']['vfx']=copy.deepcopy(reg[key])
    pokemon['signatureLayer']['presentationDefinitions']=len(reg)
    pokemon['signatureLayer']['strictPresentation']='Six semantic+shape stages; any pair shares at most one exact stage position and at most two exact semantic+shape tokens.'
    SIG.write_text(json.dumps(reg,ensure_ascii=False,indent=2,sort_keys=True)+'\n')
    return reg

def features(defn):
    out=set()
    def scan_ops(xs,prefix):
        for i,o in enumerate(xs or []):
            t=o.get('type','');out.add(prefix+str(i)+':'+t);out.add('type:'+t)
            out.add(prefix+str(i)+':target:'+str(o.get('target')))
            for k,v in sorted((o.get('flags') or {}).items()):
                if k not in ('counter','memory'):out.add(prefix+str(i)+':flag:'+k+'='+str(v))
            scan_ops(o.get('children'),prefix+str(i)+'c.')
            scan_ops(o.get('otherwise'),prefix+str(i)+'o.')
    spec=defn['effect']['spec']
    for t in spec.get('triggers') or []:out.add('primary-trigger:'+t)
    scan_ops(spec.get('operations'), 'p.')
    for i,s in enumerate(spec.get('stages') or []):
        es=s['effect']['spec']
        for t in es.get('triggers') or []:out.add('s'+str(i)+':trigger:'+t)
        scan_ops(es.get('operations'),'s'+str(i)+'.')
    return out

def wording_shape(defn):
    spec=defn['effect']['spec']; parts=[]
    parts += ['TRIGGER_'+x for x in spec.get('triggers') or []]
    def scan(xs):
        for o in xs or []:
            parts.extend([o.get('type',''),str(o.get('target',''))])
            parts.extend(sorted(k for k in (o.get('flags') or {}) if k not in ('counter','memory')))
            scan(o.get('children'));scan(o.get('otherwise'))
    scan(spec.get('operations'))
    for s in spec.get('stages') or []:
        parts.extend(['STAGE']+(s['effect']['spec'].get('triggers') or []));scan(s['effect']['spec'].get('operations'))
    return ' '.join(parts)

def validate(data,reg,codes):
    defs=data['definitions']; keys=sorted(defs)
    names={}; wording={}; worst_game=(0,'','')
    for key in keys:
        normalized=re.sub(r'[^a-z0-9]+',' ',defs[key]['name'].lower()).strip()
        if normalized in names:raise AssertionError('duplicate wording/title: '+key+' / '+names[normalized])
        names[normalized]=key
        shape=wording_shape(defs[key])
        if shape in wording:raise AssertionError('duplicate effect wording structure: '+key+' / '+wording[shape])
        wording[shape]=key
    for i,a in enumerate(keys):
        for b in keys[i+1:]:
            hd=sum(x!=y for x,y in zip(codes[a],codes[b]))
            if hd<10:raise AssertionError('signature Hamming distance <10: '+a+' / '+b)
            fa,fb=features(defs[a]),features(defs[b]); j=len(fa&fb)/max(1,len(fa|fb))
            if j>worst_game[0]:worst_game=(j,a,b)
    if worst_game[0]>=.70:raise AssertionError('near-duplicate gameplay >=0.70: '+repr(worst_game))
    pitems=[(k,tuple((s['semantic'],s['shape']) for s in v['stages'])) for k,v in reg.items()]
    for i,(a,wa) in enumerate(pitems):
        for b,wb in pitems[i+1:]:
            if sum(x==y for x,y in zip(wa,wb))>1 or len(set(wa)&set(wb))>2:
                raise AssertionError('near-duplicate choreography: '+a+' / '+b)
    print('CARDWORLDS_STRICT_IDENTITY_OK definitions=%d presentation=%d minGameplayHamming=10/12 worstGameplay=%.4f wordingStructureDuplicates=0'%
          (len(defs),len(reg),worst_game[0]))

data=json.loads(POKE.read_text())
data,codes=strict_gameplay(data)
reg=strict_presentations(data)
validate(data,reg,codes)
POKE.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
