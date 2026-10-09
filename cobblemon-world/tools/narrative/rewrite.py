#!/usr/bin/env python3
"""Compile reviewed dialogue records; stable stage IDs and historical keys stay intact."""
from pathlib import Path
import json
ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources'
HERE = Path(__file__).parent
world_path = ROOT/'data/cobblemonworld/narrative/world.json'
w = json.loads(world_path.read_text())
langs = {code: json.loads((ROOT/f'assets/cobblemonworld/lang/{code}.json').read_text()) for code in ('vi_vn','en_us')}

def rows(name, fields):
    records={}
    for line in (HERE/name).read_text().splitlines():
        if not line or line.startswith('#'): continue
        values=line.split('|')
        assert len(values)==fields, (name,values[0],len(values))
        assert values[0] not in records, (name,values[0])
        records[values[0]]=values[1:]
    return records

main=rows('main.vi.tsv',6);main_ack=rows('main.ack.vi.tsv',2);main_en=rows('main.en.tsv',7)
assert set(main)==set(main_en)
side=rows('side.choices.vi.tsv',5);side_open=rows('side.openings.vi.tsv',2)
outcomes=rows('outcomes.vi.tsv',5)
stages={s['scene']:s for s in w['campaign']}
stages.update({s['scene']:s for c in w['chains'] for s in c['stages']})
assert set(side)==set(side_open)
reviewed=set()
legacy_en=json.loads((HERE/'legacy.en.json').read_text())

def key(scene, name, vi, en):
    result='narrative.v032.'+scene+'.'+name
    langs['vi_vn'][result]=vi;langs['en_us'][result]=en
    return result

def choice(scene, id, vi, en, next='', action='', personality=''):
    return {'id':id,'text':key(scene,'choice.'+id,vi,en),'reply':'','next':next,'personality':personality,'action':action}

def node(scene, id, speaker, vi, en, choices):
    return {'id':id,'speaker':speaker,'text':key(scene,'node.'+id,vi,en),'choices':choices}

for scene in w['scenes']:
    sid=scene['id'];old={n['id']:n for n in scene['nodes']}
    speaker=old[scene['start']]['speaker']
    old_start=legacy_en[sid]['start']
    old_facts=legacy_en[sid]['facts']
    if sid.startswith('outcome.'):
        _,actor,result=sid.split('.');win,loss,thanks,retry=outcomes[actor]
        opening=win if result=='win' else loss
        scene['nodes']=[
            node(sid,'start',speaker,opening,old_start,[
                choice(sid,'thanks','Cảm ơn vì trận đấu.','Thank you for the battle.','thanks','', 'direct'),
                choice(sid,'retry','Tôi sẽ hồi phục cho đội rồi quay lại.','I will heal my party before returning.','retry','', 'careful')]),
            node(sid,'thanks',speaker,thanks,'Thank you. The result of this battle will be kept.',[
                choice(sid,'finish','Tôi hiểu. Hẹn gặp lại.','Understood. See you later.','', 'close')]),
            node(sid,'retry',speaker,retry,'Heal your party first. You can return when you are ready.',[
                choice(sid,'finish','Được, tôi đi chăm đội trước.','All right. I will take care of my party first.','', 'close')])]
    else:
        if sid in main:
            opening,question,answer,accept,refusal=main[sid];ack=main_ack[sid][0]
        else:
            question,answer,accept,ack=side[sid];opening=side_open[sid][0]
            refusal='Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.'
            if speaker=='professor_hale':refusal='Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.'
            elif speaker=='dr_orin':refusal='Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.'
            elif speaker=='mara_voss':refusal='Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.'
            elif speaker=='school_wolf_master':refusal='Được. Chuẩn bị xong rồi quay lại, ta chưa tính việc này là hoàn thành.'
        stage=stages.get(sid);kind=stage['type'] if stage else 'talk'
        if sid in main_en:
            old_start,en_question,old_facts,en_accept_main,en_refusal,en_ack=main_en[sid]
        else:
            en_question='Could you explain that part in more detail?';en_refusal='All right. You can return when you are ready.';en_ack='Understood. '+old_facts
        action='battle' if kind=='battle' else 'finish'
        if stage is None or kind in {'travel','capture','collect'}:action='close'
        # Document inspections are observations, not a mysteriously omniscient service NPC.
        if kind in {'inspect','capture'}:speaker='narrator'
        en_accept={'battle':'I am ready. Let us begin the battle.', 'deliver':'Here are the supplies you requested.',
                   'heal':'Please check my party.', 'buy':'I would like to browse the shop.',
                   'claim':'I accept this Pokémon as a companion.', 'inspect':'I will keep these details for the next step.'}.get(kind,'I understand. I will follow up on this.')
        if sid in main_en:en_accept=en_accept_main
        scene['nodes']=[
            node(sid,'start',speaker,opening,old_start,[
                choice(sid,'accept',accept,en_accept,'accepted','', 'direct'),
                choice(sid,'ask',question,en_question,'answer','', 'careful'),
                choice(sid,'decline','Để lúc khác nhé, tôi chưa muốn tiếp tục.','Not yet. I would like to leave this for later.','declined','', 'cautious')]),
            node(sid,'answer',speaker,answer,old_facts,[
                choice(sid,'confirm',accept,en_accept,'accepted'),
                choice(sid,'later','Tôi hiểu rồi. Để tôi suy nghĩ thêm.','I understand. I need some time to think.','declined')]),
            node(sid,'accepted',speaker,ack,en_ack,[
                choice(sid,'finish', 'Bắt đầu trận đấu.' if kind=='battle' else 'Được, tiếp tục nhé.', 'Begin the battle.' if kind=='battle' else 'All right. Continue.', '', action)]),
            node(sid,'declined',speaker,refusal,en_refusal,[
                choice(sid,'leave','Hẹn gặp lại.','See you later.','', 'close')])]
        if sid=='shady_business.1':
            scene['nodes'][0]['choices'].insert(2,choice(sid,'try_box','Tôi muốn trả 10 BeastCoin để xem hộp.','I want to pay 10 BeastCoin to inspect the box.','paid','scam','curious'))
            scene['nodes'].append(node(sid,'paid',speaker,'Được, cậu đã trả tiền. Đây là hộp. Nếu không đúng lời quảng cáo, cậu có thể đối chiếu hóa đơn với Mai.','You paid for the box. Keep the receipt and compare it with Mai’s if the contents do not match the description.',[
                choice(sid,'investigate','Tôi sẽ hỏi Mai và giữ hóa đơn này.','I will ask Mai and keep this receipt.','accepted')]))
    scene['start']='start';reviewed.add(sid)
assert reviewed=={s['id'] for s in w['scenes']}
world_path.write_text(json.dumps(w,ensure_ascii=False,indent=2)+'\n')
for code,data in langs.items():
    (ROOT/f'assets/cobblemonworld/lang/{code}.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
print(f'Compiled {len(reviewed)} scenes: {len(main)} main/service, {len(side)} side, {len(reviewed)-len(main)-len(side)} outcomes.')
