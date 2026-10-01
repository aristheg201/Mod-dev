import json,re,hashlib
from pathlib import Path
here=Path(__file__).resolve().parent
en={'key.categories.cardworlds':'Card Worlds','key.cardworlds.open':'Open Card Worlds'}
vi={'key.categories.cardworlds':'Card Worlds','key.cardworlds.open':'Mở Card Worlds'}
aliases={}
def entry(key,english,vietnamese,alias=True):
 en[key]=english;vi[key]=vietnamese
 if alias:aliases[english]=key
pairs=r'''Home|Trang chủ
Collection|Bộ sưu tập
Deck Builder|Xây dựng Bộ bài
Decks|Bộ bài
Play|Đấu bài
Market|Chợ
World|Thế giới
Packs|Gói bài
Turn |Lượt 
Draw Phase|Giai đoạn Rút bài
Standby Phase|Giai đoạn Chuẩn bị
Main Phase 1|Giai đoạn Chính 1
Battle Phase|Giai đoạn Chiến đấu
Main Phase 2|Giai đoạn Chính 2
End Phase|Giai đoạn Kết thúc
Deck|Bộ bài
Main Deck|Bộ bài Chính
Extra Deck|Bộ bài Phụ
Graveyard|Nghĩa địa
Banished|Loại bỏ
Hand|Bài trên tay
Field|Sân đấu
Summon|Triệu hồi
Normal Summon|Triệu hồi Thường
Special Summon|Triệu hồi Đặc biệt
Tribute|Hiến tế
Set Monster|Úp Quái thú
Set Spell/Trap|Úp Phép/Bẫy
Activate|Kích hoạt
Attack|Tấn công
Attack Position|Thế Công
Defense Position|Thế Thủ
Face-down|Úp
Switch to Attack|Chuyển sang Thế Công
Switch to Defense|Chuyển sang Thế Thủ
Next Phase|Giai đoạn tiếp theo
Pass|Nhường quyền
Surrender|Đầu hàng
Reset View|Đặt lại góc nhìn
YOUR RESPONSE|QUYỀN PHẢN HỒI CỦA BẠN
OPPONENT'S RESPONSE|QUYỀN PHẢN HỒI CỦA ĐỐI THỦ
Choose Target|Chọn Mục tiêu
Choose Tribute|Chọn vật Hiến tế
Choose Material|Chọn Nguyên liệu
Spectator|Khán giả
Read Only|Chỉ xem
Chain|Chuỗi
Chain Link|Mắt xích
Negated|Vô hiệu hóa
Resolved|Đã giải quyết
Victory|Chiến thắng
Defeat|Thất bại
Duel Complete|Trận đấu hoàn tất
Draw|Rút bài
Discard|Bỏ bài
Target|Mục tiêu
Once per turn|Một lần mỗi lượt
Destroy|Phá hủy
Banish|Loại bỏ
All|Tất cả
Owned|Đã sở hữu
Missing|Chưa sở hữu
Favorites|Yêu thích
Favorite|Yêu thích
Unfavorite|Bỏ yêu thích
Search cards…|Tìm bài…
Search collection…|Tìm trong bộ sưu tập…
Search listings…|Tìm tin rao…
Rarity|Độ hiếm
Set|Bộ phát hành
Previous|Trước
Next|Tiếp
Close|Đóng
Collector|Nhà sưu tập
Starter|Khởi đầu
Load|Tải
Clear|Xóa
Save Deck|Lưu Bộ bài
New Deck|Bộ bài mới
New|Mới
Deck name|Tên Bộ bài
Change Deck|Đổi Bộ bài
Add to Extra|Thêm vào Bộ bài Phụ
Add to Main|Thêm vào Bộ bài Chính
Added to deck draft.|Đã thêm vào Bộ bài nháp.
Deck is full.|Bộ bài đã đầy.
Deck is not legal.|Bộ bài không hợp lệ.
No available copies.|Không còn bản bài có thể dùng.
A card is no longer available|Một lá bài không còn khả dụng
: copy limit|: vượt giới hạn số bản
No cards match these filters.|Không có bài phù hợp với bộ lọc.
CARD DETAILS|CHI TIẾT LÁ BÀI
Select a card to view its rules, copies and source.|Chọn một lá bài để xem luật, số bản và nguồn nhận.
Set: |Bộ phát hành: 
 · Print rarity: | · Độ hiếm bản in: 
Source: |Nguồn nhận: 
Finish: |Hoàn thiện: 
Serial: |Số sê-ri: 
Found: |Đã tìm thấy: 
View details|Xem chi tiết
Hide details|Ẩn chi tiết
List on Market|Rao trên Chợ
Could not save favorites.|Không thể lưu danh sách yêu thích.
Play Ranked|Đấu Xếp hạng
Practice Duel|Đấu luyện tập
Choose a duel mode|Chọn chế độ đấu bài
Casual|Tự do
Ranked|Xếp hạng
Casual · Ranked · Practice|Tự do · Xếp hạng · Luyện tập
Casual does not affect ranked rating.|Đấu Tự do không ảnh hưởng điểm Xếp hạng.
Ranked uses server format and result tracking.|Xếp hạng áp dụng luật máy chủ và ghi nhận kết quả.
Human vs Human only • bots never fill this mode|Chỉ người đấu với người • không dùng máy thay thế
No other duelists online.|Không có đấu thủ khác đang trực tuyến.
ONLINE DUELISTS|ĐẤU THỦ TRỰC TUYẾN
Accept|Chấp nhận
Decline|Từ chối
Accept challenge from |Chấp nhận lời thách đấu từ 
Challenge  |Thách đấu  
EASY|DỄ
NORMAL|THƯỜNG
HARD|KHÓ
RANKED|XẾP HẠNG
CASUAL|TỰ DO
EVENT|SỰ KIỆN
UNLIMITED|KHÔNG GIỚI HẠN
Forgiving AI. Legal moves are mostly randomized.|Máy dễ. Thường chọn ngẫu nhiên các nước đi hợp lệ.
Balanced AI. Prioritizes sensible summons, effects and attacks.|Máy cân bằng. Ưu tiên Triệu hồi, hiệu ứng và tấn công hợp lý.
Competitive AI. Scores trades, removal, chains and lethal pressure.|Máy mạnh. Đánh giá đổi bài, loại bỏ, Chuỗi và khả năng kết liễu.
Easy and Normal are practice modes.|Dễ và Thường là chế độ luyện tập.
Only HARD victories can pay configured server currency.|Chỉ thắng ở mức KHÓ mới có thể nhận tiền máy chủ đã cấu hình.
PvE • same Duel Engine • no hidden-info cheating|Đấu máy • cùng luật đấu • không đọc thông tin ẩn
NO CURRENCY|KHÔNG NHẬN TIỀN
ECONOMY REWARD|PHẦN THƯỞNG KINH TẾ
Buy|Mua
List Card|Rao bài
Select a card to sell|Chọn lá bài để bán
Select an owned card in Collection, then list it here.|Chọn bài đã sở hữu trong Bộ sưu tập, rồi rao tại đây.
Buy · Sell · Trade|Mua · Bán · Trao đổi
Direct Trade|Trao đổi trực tiếp
DIRECT TRADE|TRAO ĐỔI TRỰC TIẾP
Coins to include|Số tiền kèm theo
Price in HunterCoin|Giá bằng HunterCoin
Sale fee 5% · minimum 1 coin|Phí bán 5% · tối thiểu 1 đồng
Your listing|Tin rao của bạn
Your card: |Bài của bạn: 
Request from |Yêu cầu từ 
Send Offer|Gửi đề nghị
View Trade|Xem trao đổi
Cancel|Hủy
Exchange|Trao đổi
No listings|Không có tin rao
Open packs · View rates|Mở gói bài · Xem tỉ lệ
Open ×1   ·   |Mở ×1   ·   
Continue|Tiếp tục
Pity |Bảo hiểm 
NEXT CARD RATES|TỈ LỆ LÁ BÀI TIẾP THEO
Next high rarity: featured|Bài hiếm cao tiếp theo: nổi bật
Featured guarantee available|Đã có bảo hiểm bài nổi bật
Scroll to view all rates|Cuộn để xem mọi tỉ lệ
 cards per pack| lá bài mỗi gói
 unique cards| loại bài khác nhau
 coins| đồng
 collected| đã sưu tập
 owned| đã sở hữu
 catalog · | trong danh mục · 
 physical · | bản vật lý · 
 generated deck templates| mẫu Bộ bài được tạo
 card(s) missing from your collection.| lá bài chưa có trong Bộ sưu tập.
 cards| lá bài
Templates|Mẫu Bộ bài
Template: |Mẫu: 
Template · |Mẫu · 
Template loaded: |Đã tải mẫu: 
Template preview loaded · |Đã tải bản xem trước mẫu · 
Click a card to add an available copy|Bấm vào lá bài để thêm một bản có sẵn
LEGAL|HỢP LỆ
Legal for |Hợp lệ cho 
CARD WORLDS|CARD WORLDS
KANTO CROSSROADS|GIAO LỘ KANTO
FEATURED SET|BỘ BÀI NỔI BẬT
ACTIVE DECK|BỘ BÀI ĐANG DÙNG
DECK BUILDER|XÂY DỰNG BỘ BÀI
DUEL VS BOT|ĐẤU VỚI MÁY
PVP DUEL|ĐẤU VỚI NGƯỜI
PLAY|ĐẤU BÀI
PACKS|GÓI BÀI
WORLD|THẾ GIỚI
RARITY|ĐỘ HIẾM
PRICE|GIÁ
UNDERGROUND|CHỢ NGẦM
Underground|Chợ ngầm
NPCs · Discoveries|Nhân vật · Khám phá
NPC Battles|Đấu với nhân vật
Archaeology|Khảo cổ học
Archaeology discovery|Khám phá khảo cổ
Return to World|Trở lại Thế giới
View Activity|Xem hoạt động
View Packs|Xem Gói bài
Claim Starter Collection|Nhận Bộ sưu tập Khởi đầu
Build & save decks|Xây dựng và lưu Bộ bài
SPECTATOR|KHÁN GIẢ
READ ONLY|CHỈ XEM
SPECTATOR • READ ONLY|KHÁN GIẢ • CHỈ XEM
SPECTATOR • hidden information protected|KHÁN GIẢ • thông tin ẩn được bảo vệ
Hidden hands / Extra Deck stay private.|Bài trên tay và Bộ bài Phụ vẫn được giữ kín.
RMB drag • Wheel zoom|Kéo chuột phải • Lăn chuột để phóng
RMB drag: free look  |  Wheel: zoom|Kéo chuột phải: đổi góc nhìn  |  Lăn chuột: phóng
CHOOSE A 3D TARGET|CHỌN MỤC TIÊU TRÊN SÂN
CHOOSE EVOLUTION MATERIAL|CHỌN NGUYÊN LIỆU TIẾN HÓA
CHOOSE SUMMON MATERIAL|CHỌN NGUYÊN LIỆU TRIỆU HỒI
CHOOSE |CHỌN 
ADVANCED SUMMON • CHOOSE |TRIỆU HỒI NÂNG CAO • CHỌN 
EVOLVE OR CHOOSE |TIẾN HÓA HOẶC CHỌN 
SET FACE-DOWN • CHOOSE |ÚP • CHỌN 
 TRIBUTE| HIẾN TẾ
 TRIBUTES| HIẾN TẾ
MATERIAL • |NGUYÊN LIỆU • 
SET POKEMON|POKÉMON ÚP
SET CARD|LÁ BÀI ÚP
  [DEF]|  [THỦ]
  [SET]|  [ÚP]
DECK|BỘ BÀI
EXTRA|BỘ BÀI PHỤ
DISCARD|NGHĨA ĐỊA
BANISHED|LOẠI BỎ
HAND|BÀI TRÊN TAY
SUPPORT|PHÉP / BẪY
STADIUM|PHÉP KHU VỰC
ATTACK|THẾ CÔNG
DEFENSE|THẾ THỦ
FACE_DOWN_DEFENSE|THẾ THỦ ÚP
DRAW|RÚT BÀI
STANDBY|CHUẨN BỊ
MAIN1|CHÍNH 1
BATTLE|CHIẾN ĐẤU
MAIN2|CHÍNH 2
END|KẾT THÚC
CHAIN|CHUỖI
VICTORY|CHIẾN THẮNG
DUEL COMPLETE|TRẬN ĐẤU HOÀN TẤT
No effect.|Không có hiệu ứng.
Trainer|Huấn luyện viên
Item|Vật phẩm
Technique|Phép
Reaction|Bẫy
Stadium|Phép Khu vực
Common|Phổ thông
Rare|Hiếm
Super Rare|Siêu hiếm
Ultra Rare|Cực hiếm
Secret|Bí mật
No Tribute|Không cần Hiến tế
Extra Evolution|Tiến hóa từ Bộ bài Phụ
FREE|KHÔNG HIẾN TẾ
No cards match these filters.|Không có bài phù hợp với bộ lọc.
Main Deck: |Bộ bài Chính: 
Extra Deck: up to |Bộ bài Phụ: tối đa 
Wait for your response window.|Hãy chờ quyền phản hồi của bạn.
The board changed. Try again.|Sân đấu đã thay đổi. Hãy thử lại.
This duel has ended.|Trận đấu đã kết thúc.
You are not a duelist.|Bạn không phải đấu thủ.
Play cards during your Main Phase.|Hãy chơi bài trong Giai đoạn Chính của bạn.
Choose one of your cards.|Chọn một lá bài của bạn.
Your field is full.|Sân đấu của bạn đã đầy.
Your support row is full.|Hàng Phép/Bẫy của bạn đã đầy.
Choose a target.|Chọn một Mục tiêu.
Choose an opposing Pokemon.|Chọn một Pokémon của đối thủ.
Choose one of your Pokemon.|Chọn một Pokémon của bạn.
Choose the latest chain link.|Chọn Mắt xích mới nhất.
Choose a Pokemon in your discard pile.|Chọn một Pokémon trong Nghĩa địa của bạn.
You have used your Normal Summon this turn.|Bạn đã dùng lượt Triệu hồi Thường này.
This card has no activated effect.|Lá bài này không có hiệu ứng Kích hoạt.
This effect cannot be used from here.|Không thể dùng hiệu ứng từ khu vực này.
This effect cannot be used in this phase.|Không thể dùng hiệu ứng trong giai đoạn này.
This effect cannot respond to that chain.|Hiệu ứng này không thể phản hồi Chuỗi đó.
Choose a fast response.|Chọn một phản hồi nhanh.
You already used this effect this turn.|Bạn đã dùng hiệu ứng này trong lượt này.
Not enough Trainer Life to pay the cost.|Không đủ Sinh lực để trả chi phí.
This Trap must be Set before activation.|Phải Úp Bẫy này trước khi Kích hoạt.
A Set Trap/Quick-Play card cannot be activated this turn.|Bẫy/Phép Nhanh vừa Úp không thể Kích hoạt trong lượt này.
Play this Pokemon before using its ability.|Triệu hồi Pokémon này trước khi dùng khả năng.
You cannot attack now.|Bạn không thể tấn công lúc này.
That Pokemon cannot attack again.|Pokémon này không thể tấn công thêm.
Only an Attack Position Pokemon can attack.|Chỉ Pokémon ở Thế Công mới có thể tấn công.
An opposing Pokemon blocks a direct attack.|Pokémon của đối thủ ngăn tấn công trực tiếp.
Finish the response window first.|Hoàn tất lượt phản hồi trước.
This summon stage is locked.|Giai đoạn Triệu hồi này đang bị khóa.
A Pokemon that attacked cannot change position this turn.|Pokémon đã tấn công không thể đổi Thế trong lượt này.
A Pokemon cannot manually change position on the turn it was Summoned or Set.|Pokémon không thể tự đổi Thế trong lượt vừa được Triệu hồi hoặc Úp.
This Pokemon already changed position this turn.|Pokémon này đã đổi Thế trong lượt này.
Choose different Tribute Pokemon.|Chọn các Pokémon Hiến tế khác nhau.
Tributes must be your Pokemon on the field.|Vật Hiến tế phải là Pokémon của bạn trên sân.
This Pokemon does not require a Tribute.|Pokémon này không cần Hiến tế.
Choose a Pokemon in your hand.|Chọn một Pokémon trên tay.
Choose one of your Pokemon on the field.|Chọn một Pokémon của bạn trên sân.
That card cannot be played here.|Không thể chơi lá bài đó tại đây.
Only a card in your hand can be Set.|Chỉ có thể Úp bài trên tay.
Use Activate, or Set this Spell/Trap first.|Dùng Kích hoạt, hoặc Úp Phép/Bẫy này trước.
This Extra Deck form has no evolution material.|Dạng trong Bộ bài Phụ này không có Nguyên liệu tiến hóa.
Choose the required evolution material.|Chọn Nguyên liệu tiến hóa yêu cầu.
Choose the required evolution.|Chọn dạng tiến hóa yêu cầu.
Main Deck Pokemon must be summoned from the hand.|Pokémon thuộc Bộ bài Chính phải được Triệu hồi từ tay.
The duel begins.|Trận đấu bắt đầu.
A duelist conceded.|Một đấu thủ đã đầu hàng.
A duelist could not draw.|Một đấu thủ không thể Rút bài.
 is Normal Summoned.| được Triệu hồi Thường.
 is Tribute Summoned.| được Triệu hồi Hiến tế.
 is Special Summoned.| được Triệu hồi Đặc biệt.
 declares an attack.| tuyên bố tấn công.
 resolves.| giải quyết hiệu ứng.
 was negated.| bị Vô hiệu hóa.
 lost its target.| mất Mục tiêu.
 changes to | chuyển sang 
 Position.|.
 evolves from | tiến hóa từ 
 evolves from the Extra Deck.| tiến hóa từ Bộ bài Phụ.
 is flipped face-up in Defense Position.| được lật ngửa ở Thế Thủ.
 triggers.| phát động hiệu ứng.
A Pokemon is Set face-down in Defense Position.|Một Pokémon được Úp ở Thế Thủ.
A Pokemon is Tribute Set face-down.|Một Pokémon được Úp bằng Hiến tế.
The field changed. Choose a new attack.|Sân đấu đã thay đổi. Hãy chọn đòn tấn công mới.
The target left the field. Choose a new attack.|Mục tiêu đã rời sân. Hãy chọn đòn tấn công mới.
Deck saved.|Đã lưu Bộ bài.
Not enough coins.|Không đủ tiền.
Invalid request.|Yêu cầu không hợp lệ.
Invalid format|Luật đấu không hợp lệ
Extra Deck is too large.|Bộ bài Phụ quá lớn.
 belongs in the Extra Deck.| phải nằm trong Bộ bài Phụ.
 belongs in the Main Deck.| phải nằm trong Bộ bài Chính.
: copy limit exceeded.|: vượt giới hạn số bản.
 cards.| lá bài.
Main Deck requires |Bộ bài Chính cần 
Level |Cấp 
 requires | cần 
 Tribute.| vật Hiến tế.
 Tributes.| vật Hiến tế.
LP |SL 
30 cards · Evolution · Reactions|30 lá bài · Tiến hóa · Bẫy
Buy · Sell · Trade|Mua · Bán · Trao đổi
a collector|một nhà sưu tập
select in Collection|chọn trong Bộ sưu tập
Catalog |Danh mục 
Collector · |Nhà sưu tập · 
Ancient Mew\nDesert · Overworld\nOne discovery per collector.|Ancient Mew\nSa mạc · Thế giới chính\nMỗi nhà sưu tập chỉ khám phá một lần.
Midnight Collector\nNether · 23:00–02:00\n3 Gastly + discovery reputation.|Nhà sưu tập Nửa đêm\nNether · 23:00–02:00\n3 Gastly + danh tiếng khám phá.
Pewter Challenger\nWin Onix and 100 HunterCoin.\nUp to 5 rewards per day.|Đấu thủ Pewter\nNhận Onix và 100 HunterCoin khi thắng.\nTối đa 5 phần thưởng mỗi ngày.
Inspect chiseled sandstone in an Overworld desert. Find an Ancient Mew archaeology edition.|Khảo sát sa thạch chạm khắc tại sa mạc Thế giới chính để tìm bản khảo cổ Ancient Mew.
'''
for line in pairs.splitlines():
 if '|' not in line:continue
 english,vietnamese=line.split('|',1)
 english=english.replace('\\n','\n');vietnamese=vietnamese.replace('\\n','\n')
 key='cardworlds.ui.'+re.sub('[^a-z0-9]+','_',english.lower()).strip('_')
 if key in en and en[key]!=english:key+='_'+hashlib.sha1(english.encode()).hexdigest()[:5]
 entry(key,english,vietnamese)
entry('cardworlds.ui.chain_link_number','LINK %s','MẮT XÍCH %s',False)
entry('cardworlds.ui.negated','NEGATED','VÔ HIỆU HÓA',False)
for key,english,vietnamese in [('trigger','Trigger','Phát động'),('cost','Cost','Chi phí'),('condition','Condition','Điều kiện'),('target','Target','Mục tiêu'),('effect','Effect','Hiệu ứng'),('limit','Limit','Giới hạn'),('once_per_turn','Once per turn','Một lần mỗi lượt'),('once_per_duel','Once per duel','Một lần mỗi trận'),('none','None','Không có'),('disabled','Effect disabled','Hiệu ứng bị vô hiệu'),('turn_end','until the end of this turn','đến hết lượt này'),('next_turn_end','until the end of the next turn','đến hết lượt tiếp theo'),('permanent','while this card remains in its zone','khi lá bài còn trong khu vực này')]:entry('cardworlds.effect.'+key,english,vietnamese,False)
targets={'SELF':('this card','lá bài này'),'SOURCE':('the source','nguồn hiệu ứng'),'TARGET':('the selected target','Mục tiêu đã chọn'),'ALLY_MONSTER':('an allied Pokémon','một Pokémon của bạn'),'ENEMY_MONSTER':('an opposing Pokémon','một Pokémon đối thủ'),'ANY_MONSTER':('a Pokémon','một Pokémon'),'ALLY_CARD':('an allied card','một lá bài của bạn'),'ENEMY_CARD':('an enemy card','một lá bài đối thủ'),'ANY_FIELD_CARD':('a card on the field','một lá bài trên sân'),'ATTACKER':('the attacker','Pokémon tấn công'),'DEFENDER':('the defender','Pokémon phòng thủ'),'CHAIN_SOURCE':('the selected Chain source','nguồn Mắt xích đã chọn'),'CHAIN_TARGET':('the Chain target','Mục tiêu của Chuỗi'),'HAND':('your hand','Bài trên tay của bạn'),'GRAVEYARD':('your Graveyard','Nghĩa địa của bạn'),'BANISHED':('your Banished cards','các bài bị Loại bỏ của bạn'),'DECK':('your Deck','Bộ bài của bạn'),'EXTRA_DECK':('your Extra Deck','Bộ bài Phụ của bạn'),'TOP_DECK':('the top of your Deck','đầu Bộ bài của bạn'),'BOTTOM_DECK':('the bottom of your Deck','cuối Bộ bài của bạn'),'RANDOM_HAND':('a random hand card','một lá bài ngẫu nhiên trên tay'),'ALL_ALLIES':('all allied Pokémon','mọi Pokémon của bạn'),'ALL_ENEMIES':('all opposing Pokémon','mọi Pokémon đối thủ'),'ALL_FIELD':('all Pokémon on the field','mọi Pokémon trên sân')}
for key,(a,b) in targets.items():entry('cardworlds.target.'+key.lower(),a,b,False)
triggers={'ON_ACTIVATE':('On activation','Khi Kích hoạt'),'ON_SUMMON':('When Summoned','Khi được Triệu hồi'),'ON_SPECIAL_SUMMON':('When Special Summoned','Khi được Triệu hồi Đặc biệt'),'ON_FLIP':('When flipped face-up','Khi được lật ngửa'),'ON_ATTACK_DECLARE':('When an attack is declared','Khi tuyên bố tấn công'),'ON_ATTACKED':('When attacked','Khi bị tấn công'),'ON_DAMAGE':('When damage is dealt','Khi gây sát thương'),'ON_DESTROY':('When destroying a card','Khi Phá hủy bài'),'ON_DESTROYED':('When destroyed','Khi bị Phá hủy'),'ON_SEND_GRAVE':('When sent to the Graveyard','Khi được gửi vào Nghĩa địa'),'ON_BANISH':('When Banished','Khi bị Loại bỏ'),'ON_RETURN':('When returned','Khi được trả về'),'ON_DRAW':('When drawn','Khi được Rút'),'ON_DISCARD':('When discarded','Khi bị Bỏ'),'ON_CHAIN':('When a Chain is built','Khi tạo Chuỗi'),'ON_CHAINED':('When chained','Khi được nối vào Chuỗi'),'ON_CHAIN_RESOLVE':('When a Chain resolves','Khi Chuỗi giải quyết'),'ON_NEGATE':('When negated','Khi bị Vô hiệu hóa'),'ON_TURN_START':('At turn start','Đầu lượt'),'ON_TURN_END':('At turn end','Cuối lượt'),'ON_PHASE_START':('At phase start','Đầu giai đoạn'),'ON_PHASE_END':('At phase end','Cuối giai đoạn'),'ON_POSITION_CHANGE':('When position changes','Khi đổi Thế'),'CONTINUOUS':('Continuous','Liên tục')}
for key,(a,b) in triggers.items():entry('cardworlds.trigger.'+key.lower(),a,b,False)
costs={'LP_COST':('Pay %s LP','Trả %s Sinh lực'),'DISCARD':('Discard %s eligible hand card(s)','Bỏ %s lá bài hợp lệ trên tay'),'TRIBUTE':('Tribute %s allied Pokémon','Hiến tế %s Pokémon của bạn'),'BANISH_SELF':('Banish this card','Loại bỏ lá bài này'),'BANISH_GRAVE':('Banish %s other card(s) from your Graveyard','Loại bỏ %s lá bài khác khỏi Nghĩa địa của bạn'),'SEND_GRAVE':('Send %s eligible card(s) to the Graveyard','Gửi %s lá bài hợp lệ vào Nghĩa địa'),'REVEAL_HAND':('Reveal %s hand card(s)','Cho xem %s lá bài trên tay'),'RETURN_SELF':('Return this card','Trả lá bài này về'),'RETURN_OTHER':('Return %s eligible card(s)','Trả %s lá bài hợp lệ về'),'LOCK_AFTER_USE':('Cannot activate again this turn','Không thể Kích hoạt thêm trong lượt này')}
for key,(a,b) in costs.items():entry('cardworlds.cost.'+key.lower(),a,b,False)
operations={
'DAMAGE_LP':('Deal %s LP damage','Gây %s sát thương Sinh lực'),'HEAL_LP':('Recover %s LP','Hồi %s Sinh lực'),'MODIFY_POWER':('Modify %s Power of %s','Thay đổi %s Sức mạnh của %s'),'SET_POWER':('Set Power to %s for %s','Đặt Sức mạnh thành %s cho %s'),'SWAP_POWER':('Swap this card’s Power with %2$s','Đổi Sức mạnh của lá bài này với %2$s'),'DRAW':('Draw %s card(s)','Rút %s lá bài'),'SEARCH':('Add %s matching card(s) from %s to your hand; reveal them','Thêm %s lá bài phù hợp từ %s lên tay; cho xem chúng'),'REVEAL':('Reveal %2$s','Cho xem %2$s'),'SHUFFLE':('Shuffle your Deck','Xáo Bộ bài của bạn'),'DISCARD':('Discard a card from %2$s','Bỏ một lá bài từ %2$s'),'MILL':('Send the top %s Deck card(s) to the Graveyard','Gửi %s lá đầu Bộ bài vào Nghĩa địa'),'DESTROY':('Destroy %2$s','Phá hủy %2$s'),'SEND_GRAVE':('Send %2$s to the Graveyard','Gửi %2$s vào Nghĩa địa'),'BANISH':('Banish %2$s','Loại bỏ %2$s'),'RETURN_HAND':('Return %2$s to the hand (Extra forms return to Extra Deck)','Trả %2$s lên tay (dạng Phụ trở về Bộ bài Phụ)'),'RETURN_DECK':('Return %2$s to the Deck','Trả %2$s về Bộ bài'),'SPECIAL_SUMMON':('Special Summon %2$s','Triệu hồi Đặc biệt %2$s'),'REVIVE':('Special Summon %2$s from the Graveyard','Triệu hồi Đặc biệt %2$s từ Nghĩa địa'),'SUMMON_FROM_HAND':('Special Summon %2$s from the hand','Triệu hồi Đặc biệt %2$s từ tay'),'SUMMON_FROM_GRAVE':('Special Summon %2$s from the Graveyard','Triệu hồi Đặc biệt %2$s từ Nghĩa địa'),'SUMMON_FROM_BANISHED':('Special Summon %2$s from Banished','Triệu hồi Đặc biệt %2$s từ khu Loại bỏ'),'CHANGE_POSITION':('Change the position of %2$s','Đổi Thế của %2$s'),'FLIP_FACE_UP':('Flip %2$s face-up in Attack Position','Lật ngửa %2$s ở Thế Công'),'SET_FACE_DOWN':('Set %2$s face-down in Defense Position','Úp %2$s ở Thế Thủ'),'NEGATE_EFFECT':('Negate the selected Chain effect','Vô hiệu hóa hiệu ứng Mắt xích đã chọn'),'NEGATE_ACTIVATION':('Negate the selected Chain activation','Vô hiệu hóa lần Kích hoạt Mắt xích đã chọn'),'NEGATE_ATTACK':('Negate the attack','Vô hiệu hóa đòn tấn công'),'PREVENT_DESTROY':('Protect %2$s from destruction','Bảo vệ %2$s khỏi Phá hủy'),'PREVENT_TARGET':('Opponent cannot target %2$s','Đối thủ không thể chọn %2$s làm Mục tiêu'),'PREVENT_DAMAGE':('Prevent your LP damage while %2$s is on the field','Ngăn sát thương Sinh lực của bạn khi %2$s còn trên sân'),'REDIRECT_TARGET':('Redirect the prior Chain target to %2$s','Chuyển Mục tiêu của Mắt xích trước sang %2$s'),'REDIRECT_ATTACK':('Redirect the attack to %2$s','Chuyển đòn tấn công sang %2$s'),'CONTROL_CHANGE':('Take control of %2$s','Giành quyền điều khiển %2$s'),'COPY_EFFECT':('Copy the activated effect of %2$s to this card','Sao chép hiệu ứng Kích hoạt của %2$s cho lá bài này'),'EXTRA_ATTACK':('Grant %s additional attack(s) to %s','Cho %s lần tấn công bổ sung cho %s'),'CANNOT_ATTACK':('%2$s cannot attack','%2$s không thể tấn công'),'CANNOT_ACTIVATE':('%2$s cannot activate','%2$s không thể Kích hoạt'),'CANNOT_SUMMON':('The controller of %2$s cannot Summon','Người điều khiển %2$s không thể Triệu hồi'),'PIERCE':('%2$s inflicts piercing battle damage','%2$s gây sát thương xuyên Thế Thủ'),'REFLECT_DAMAGE':('Reflect your LP damage while %2$s is on the field','Phản sát thương Sinh lực khi %2$s còn trên sân'),'ADD_COUNTER':('Add %s counter(s) to %s','Thêm %s bộ đếm cho %s'),'REMOVE_COUNTER':('Remove up to %s counter(s) from %s','Bỏ tối đa %s bộ đếm khỏi %s'),'GRANT_EFFECT':('Grant this activated effect to %2$s','Trao hiệu ứng Kích hoạt này cho %2$s'),'REMOVE_EFFECT':('Disable the activated effect of %2$s','Vô hiệu hóa hiệu ứng Kích hoạt của %2$s'),'LOOK_TOP_DECK':('Look at the top %s Deck card(s); private until turn end','Xem %s lá đầu Bộ bài; giữ kín đến cuối lượt'),'REORDER_DECK':('Reorder the top %s Deck card(s)','Sắp lại %s lá đầu Bộ bài'),'MOVE_ZONE':('Move %2$s to the configured zone','Chuyển %2$s đến khu vực chỉ định'),'APPLY_STATUS':('Apply the specified restriction to %2$s','Áp dụng hạn chế chỉ định cho %2$s'),'REMOVE_STATUS':('Remove the specified restrictions from %2$s','Gỡ hạn chế chỉ định khỏi %2$s'),'IF':('If the condition is met','Nếu thỏa Điều kiện')}
for key,(a,b) in operations.items():entry('cardworlds.operation.'+key.lower(),a,b,False)
conditions={'AND':('All conditions','Mọi Điều kiện'),'OR':('Any condition','Một trong các Điều kiện'),'NOT':('Not','Không'),'TURN_OWNER':('Turn belongs to %s','Lượt thuộc về %s'),'EVENT_CONTROLLER':('Event belongs to %s','Sự kiện thuộc về %s'),'PHASE':('Phase: %s','Giai đoạn: %s'),'LP_AT_MOST':('LP at most %s','Sinh lực tối đa %s'),'LP_AT_LEAST':('LP at least %s','Sinh lực tối thiểu %s'),'CHAIN_AT_LEAST':('Chain has at least %s link(s)','Chuỗi có ít nhất %s Mắt xích'),'COUNT_AT_LEAST':('At least %s in %s','Ít nhất %s trong %s'),'COUNT_AT_MOST':('At most %s in %s','Tối đa %s trong %s'),'SOURCE_ZONE':('Source zone: %s','Khu vực nguồn: %s'),'TARGET_ZONE':('Target zone: %s','Khu vực Mục tiêu: %s'),'CATEGORY':('Category: %s','Loại bài: %s'),'TYPE':('Type: %s','Hệ: %s'),'TAG':('Tag: %s','Nhóm: %s'),'LEVEL_AT_LEAST':('Level at least %s','Cấp tối thiểu %s'),'POWER_AT_LEAST':('Power at least %s','Sức mạnh tối thiểu %s'),'POSITION':('Position: %s','Thế: %s'),'FACE_UP':('Face-up','Ngửa'),'FACE_DOWN':('Face-down','Úp'),'DESTROYED_BY_BATTLE':('Destroyed by battle','Bị Phá hủy do chiến đấu'),'DESTROYED_BY_EFFECT':('Destroyed by effect','Bị Phá hủy do hiệu ứng')}
for key,(a,b) in conditions.items():entry('cardworlds.condition.'+key.lower(),a,b,False)
for key,a,b in [('ally','your side','bên bạn'),('enemy','the opponent','đối thủ'),('counter','Counter','Bộ đếm'),('automatic_cost','Costs select the oldest eligible cards unless a target is required.','Chi phí chọn lá hợp lệ cũ nhất khi không yêu cầu Mục tiêu.'),('matching','Matching: %s','Phù hợp: %s'),('level_at_most','Level at most %s','Cấp tối đa %s'),('quick','Quick Effect','Hiệu ứng Nhanh'),('speed','Speed %s','Tốc độ %s'),('bottom','bottom','cuối'),('top','top','đầu'),('power_desc','Power descending','Sức mạnh giảm dần'),('power_asc','Power ascending','Sức mạnh tăng dần'),('reverse','reverse order','đảo thứ tự'),('owner_only','owner only','chỉ chủ sở hữu')]:entry('cardworlds.effect.'+key,a,b,False)
errors={'cannot_activate':('This card cannot activate.','Lá bài này không thể Kích hoạt.'),'cannot_attack':('This Pokémon cannot attack.','Pokémon này không thể tấn công.'),'cannot_summon':('Summoning is restricted.','Triệu hồi đang bị hạn chế.'),'once_per_duel':('Already used once this duel.','Đã dùng một lần trong trận này.'),'condition':('The effect conditions are not met.','Chưa thỏa Điều kiện hiệu ứng.'),'target':('Choose an eligible target.','Chọn một Mục tiêu hợp lệ.'),'protected_target':('This target is protected.','Mục tiêu này đang được bảo vệ.'),'cost':('Cannot pay the required cost.','Không thể trả Chi phí yêu cầu.'),'life_cost':('Not enough LP to pay the cost.','Không đủ Sinh lực để trả Chi phí.'),'already_registered':('This continuous effect is already active.','Hiệu ứng Liên tục này đã hoạt động.')}
for key,(a,b) in errors.items():entry('cardworlds.error.'+key,a,b,False)
types={'normal':'Thường','fire':'Lửa','water':'Nước','electric':'Điện','grass':'Cỏ','ice':'Băng','fighting':'Giác đấu','poison':'Độc','ground':'Đất','flying':'Bay','psychic':'Siêu linh','bug':'Bọ','rock':'Đá','ghost':'Ma','dragon':'Rồng','dark':'Bóng tối','steel':'Thép','fairy':'Tiên'}
for key,b in types.items():entry('cardworlds.type.'+key,key.capitalize(),b,False)
names={'shatter_gate':'Cổng Tan Vỡ','void_seal':'Phong Ấn Hư Không','tidal_recall':'Thu Hồi Thủy Triều','recruit_signal':'Tín Hiệu Chiêu Mộ','grave_bloom':'Nở Hoa Nghĩa Địa','counter_gate':'Cổng Phản Công','attack_mirror':'Gương Phản Kích','summon_snare':'Bẫy Triệu Hồi','ember_domain':'Lãnh Địa Tàn Lửa','guardian_domain':'Lãnh Địa Hộ Vệ','soul_exchange':'Trao Đổi Linh Hồn','mind_theft':'Chiếm Đoạt Tâm Trí','battle_drive':'Xung Lực Chiến Đấu','crystal_ward':'Kết Giới Pha Lê','memory_echo':'Tiếng Vọng Ký Ức','grave_harvest':'Thu Hoạch Nghĩa Địa','stellar_filter':'Bộ Lọc Tinh Tú','dark_current':'Dòng Chảy Bóng Tối','reversal_seal':'Phong Ấn Đảo Ngược','emergency_rise':'Trỗi Dậy Khẩn Cấp'}
content=json.loads((here/'resources/data/svarcade_tcg/deep_effects.json').read_text())
for id,b in names.items():entry('card.svarcade_tcg.'+id+'.name',content['cards'][id]['name'],b,False)
entry('cardworlds.ui.free_look','RMB drag: free look  |  Wheel: zoom','Kéo chuột phải: đổi góc nhìn  |  Lăn chuột: phóng')
entry('cardworlds.effect.else','Otherwise','Nếu không',False)
entry('cardworlds.type.any','Any type','Mọi hệ',False)
for key,a,b in [('cannot_attack','Cannot attack','Không thể tấn công'),('cannot_activate','Cannot activate','Không thể Kích hoạt'),('cannot_summon','Cannot Summon','Không thể Triệu hồi'),('growth','Growth','Sinh trưởng'),('fortitude','Fortitude','Kiên cố'),('charge','Charge','Nạp năng lượng'),('all','All restrictions','Mọi hạn chế')]:entry('cardworlds.status.'+key,a,b,False)
legacy_names={'flamethrower':('Flamethrower','Phun Lửa'),'protect':('Protect','Bảo Vệ'),'feint':('Feint','Đòn Giả'),'exit':('Emergency Exit','Lối Thoát Khẩn Cấp'),'potion':('Potion','Thuốc Hồi Phục'),'research':('Professor’s Research','Nghiên Cứu Của Giáo Sư'),'revival':('Revival Seed','Hạt Giống Hồi Sinh'),'lost_zone':('Lost Zone','Khu Vực Thất Lạc'),'rock_tomb':('Rock Tomb','Mộ Đá'),'hard_stone':('Hard Stone','Đá Cứng'),'battle_training':('Battle Training','Huấn Luyện Chiến Đấu'),'training_ground':('Training Ground','Sân Huấn Luyện'),'mirror_barrier':('Mirror Barrier','Kết Giới Phản Chiếu'),'counter_seal':('Counter Seal','Phong Ấn Phản Công')}
for key,(a,b) in legacy_names.items():entry('card.svarcade_tcg.'+key+'.name',a,b)
entry('cardworlds.effect.legacy_shield','Prevent the next %s destruction(s) of %s','Ngăn %s lần Phá hủy tiếp theo của %s',False)
for a,b in [('On activation','Khi Kích hoạt'),('Trigger','Phát động'),('Resolved','Đã giải quyết'),('Support','Phép / Bẫy'),('Stadium','Phép Khu vực'),('Trainer','Huấn luyện viên'),('Technique','Phép'),('Reaction','Bẫy'),('item','vật phẩm'),('pokemon','Pokémon'),('trainer','huấn luyện viên'),('technique','phép'),('reaction','bẫy'),('stadium','phép khu vực'),('Main Deck  ','Bộ bài Chính  '),('Extra Deck  ','Bộ bài Phụ  '),(' collected · ',' đã sưu tập · '),(' Fakemon/addon',' Fakemon/bổ sung'),('Main Deck: ','Bộ bài Chính: '),('ANCIENT SANDS','CÁT CỔ ĐẠI'),('CROSSROADS','GIAO LỘ'),('Kanto Crossroads','Giao lộ Kanto'),('National Dex','Danh mục Toàn quốc'),('Duel Tactics','Chiến thuật Đấu bài')]:
 key='cardworlds.ui.'+re.sub('[^a-z0-9]+','_',a.lower()).strip('_')+'_'+hashlib.sha1(a.encode()).hexdigest()[:5];entry(key,a,b)
entry('cardworlds.condition.counter_at_least','At least %s counters on %s','Ít nhất %s bộ đếm trên %s',False)
entry('cardworlds.error.automatic_trigger','This effect activates only when its trigger occurs.','Hiệu ứng này chỉ Kích hoạt khi xảy ra sự kiện Phát động.',False)
entry('cardworlds.effect.level_at_least','Level at least %s','Cấp tối thiểu %s',False)
entry('cardworlds.effect.power_at_least','Power at least %s','Sức mạnh tối thiểu %s',False)
entry('cardworlds.effect.power_at_most','Power at most %s','Sức mạnh tối đa %s',False)
path=here/'resources/assets/svarcade_tcg/lang';path.mkdir(parents=True,exist_ok=True)
for lang,data in [('en_us',en),('vi_vn',vi)]: (path/(lang+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
(here/'resources/data/svarcade_tcg/ui_translation_aliases.json').write_text(json.dumps(aliases,ensure_ascii=False,indent=2)+'\n')
print('EN/VI translation keys:',len(en))
