#!/usr/bin/env python3
"""Rewrite legacy phone branches while retaining node IDs and saved message identities."""
from pathlib import Path
import json
here=Path(__file__).parent
root=here.parents[1]/'src/main/resources'
records={locale:json.loads((here/f'phone.{language}.json').read_text()) for locale,language in [('vi_vn','vi'),('en_us','en')]}
assert set(records['vi_vn'])==set(records['en_us'])
langs={locale:json.loads((root/f'assets/cobblemonworld/lang/{locale}.json').read_text()) for locale in records}
for path in (root/'data/cobblemonworld/contacts').glob('*.json'):
    contact=json.loads(path.read_text());nodes={n['id']:n for n in contact['messages']}
    for node in contact['messages']:
        id=contact['id']+':'+node['id']
        if id not in records['vi_vn']:continue
        assert len(node['choices'])==3
        prefix='narrative.v032.phone.'+contact['id']+'.'+node['id']
        node['text']=prefix+'.line'
        for locale in records:
            opening,choices,replies=records[locale][id]
            assert len(choices)==len(replies)==3
            langs[locale][node['text']]=opening
            for index,choice in enumerate(node['choices']):
                choice['text']=prefix+f'.choice{index}'
                reply=nodes[choice['nextNode']];reply['text']=prefix+f'.reply{index}'
                langs[locale][choice['text']]=choices[index]
                langs[locale][reply['text']]=replies[index]
    path.write_text(json.dumps(contact,ensure_ascii=False,indent=2)+'\n')
for locale,lang in langs.items():
    lang['narrative.phone.read']='Tôi đã đọc, cảm ơn.' if locale=='vi_vn' else 'I have read it. Thank you.'
    lang['narrative.phone.later']='Để tôi đọc kỹ rồi nhắn lại.' if locale=='vi_vn' else 'I will read it carefully and reply later.'
    lang['narrative.phone.read_ack']='Ừ. Có gì cần hỏi thì cứ nhắn lại nhé.' if locale=='vi_vn' else 'All right. Send me your questions if anything needs explaining.'
    lang['narrative.phone.later_ack']='Được. Cứ đọc kỹ, tôi sẽ trả lời khi cậu nhắn.' if locale=='vi_vn' else 'Take your time. I will answer when you write back.'
    (root/f'assets/cobblemonworld/lang/{locale}.json').write_text(json.dumps(lang,ensure_ascii=False,indent=2)+'\n')
print('Rewritten phone branches:',len(records['vi_vn']))
