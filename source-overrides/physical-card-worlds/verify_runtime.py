from pathlib import Path
import struct
root=Path('run-physical-visual')
log=(root/'logs/latest.log').read_text()
markers=['CARDWORLDS_CREATIVE_FULL_INVENTORY_DROP_PASS','CARDWORLDS_LIVE_BASE_FORM_PROVIDER_IDENTITY_PASS','CARDWORLDS_PHYSICAL_CARD_ITEM_PASS','CARDWORLDS_PHYSICAL_LOOT_PASS','CARDWORLDS_BLANK_CAPTURE_PASS','CARDWORLDS_CAPTURE_IDENTITY_PASS','CARDWORLDS_CAPTURE_NO_STORAGE_DUPLICATE_PASS','CARDWORLDS_PHYSICAL_REDEEM_PASS','CARDWORLDS_PHYSICAL_REDEEM_IDEMPOTENT_PASS','CARDWORLDS_REDEEM_POP_PASS','CARDWORLDS_PHYSICAL_SECURITY_PASS','CARDWORLDS_BLANK_CAPTURE_FAILURE_PASS','CARDWORLDS_BLANK_FULL_INVENTORY_DROP_PASS','CARDWORLDS_PHYSICAL_CARD_RUNTIME_QA_PASS']
for marker in markers:assert marker in log,marker
for failure in ['CARDWORLDS_PHYSICAL_CARD_RUNTIME_QA_FAILED','Encountered an unexpected exception','NoClassDefFoundError','Mixin apply failed','Physical card front fallback']:
 assert failure not in log,failure
for image in ['physical-01-blank-held','physical-02-blank-inventory','physical-03-target-pokemon','physical-04-captured-card','physical-05-card-held','physical-06-redeem-pop','physical-07-collection']:
 path=root/'screenshots'/f'{image}.png';data=path.read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n',path
 w,h=struct.unpack('>II',data[16:24]);assert w>=1280 and h>=720 and len(data)>10000,(path,w,h,len(data))
print('CARDWORLDS_PHYSICAL_RUNTIME_EVIDENCE_VERIFIED markers='+str(len(markers))+' screenshots=7')
