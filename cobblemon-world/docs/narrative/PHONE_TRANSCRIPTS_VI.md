# Phone transcripts — 0.3.2

Legacy message IDs and trigger flags are retained. Each choice below is paired with its own reply.

## aurelia / after_defeat

Kết quả trận đấu đã được ghi nhận. Bran đang giữ phần hồ sơ của Trường Sói; tôi sẽ gửi cho ông ấy bản xác nhận cậu cần.

Player: Bran biết gì về hồ sơ này?

NPC: Ông ấy biết việc tiếp nhận và lưu trữ tại trường. Những quyết định thuộc Liên Minh, tôi sẽ tự giải trình.

Gameplay: quest=aurelia_below; flag=aurelia_seven_record.

Player: Tôi có phải làm lại thử thách Liên Minh không?

NPC: Không. Kết quả của cậu đã được công nhận. Chúng ta đang xử lý hồ sơ, không thay đổi kết quả trận đấu.

Gameplay: quest=aurelia_below; flag=dialogue:aurelia:after_defeat:1.

Player: Tôi sẽ gặp Bran với bản xác nhận.

NPC: Được. Nếu trường cần xác minh chữ ký, bảo họ liên lạc trực tiếp với tôi.

Gameplay: quest=aurelia_below; flag=dialogue:aurelia:after_defeat:2.

## aurelia / after_below

Tôi đã chuyển bản sao cho Trường Sói. Các bài kiểm tra ở đó thuộc trách nhiệm của trường; chúng không thể thay thế phần giải trình của tôi.

Player: Tôi nên hỏi ai về các bài kiểm tra?

NPC: Bran tiếp nhận người đến trường. Ông ấy sẽ chỉ cậu tới đúng người phụ trách từng bài.

Gameplay: quest=none; flag=last_warning_received.

Player: Hồ sơ cũ sẽ được giữ nguyên chứ?

NPC: Có. Ta ghi bản bổ sung riêng; không được sửa bản cũ để làm như quyết định sai chưa từng tồn tại.

Gameplay: quest=none; flag=dialogue:aurelia:after_below:1.

Player: Tôi sẽ phân biệt việc thi đấu với việc điều tra.

NPC: Cảm ơn cậu. Tôi cũng phải làm như vậy khi trả lời nhóm điều tra.

Gameplay: quest=none; flag=dialogue:aurelia:after_below:2.

## dr_orin / after_defeat

Tôi đã sao lại phần hồ sơ cần đối chiếu. Rook giữ chiếc Thẻ Đen liên quan đến một lần truy cập cũ; chúng ta cần kiểm tra cả hai.

Player: Thẻ đó chứng minh ai đã truy cập à?

NPC: Chưa. Nó chứng minh có quyền truy cập. Người giữ thẻ bây giờ có thể không phải người đã dùng nó.

Gameplay: quest=orin_archive; flag=orin_doubts_anchor.

Player: Tôi nên hỏi Rook điều gì?

NPC: Nguồn gốc và thời điểm anh ta nhận thẻ. Nếu hai câu trả lời không khớp, giữ lại cả hai.

Gameplay: quest=orin_archive; flag=dialogue:dr_orin:after_defeat:1.

Player: Tôi sẽ kiểm tra thẻ trước khi kết luận.

NPC: Đúng. Một vật chứng không thay thế lời giải thích về cách nó được dùng.

Gameplay: quest=orin_archive; flag=dialogue:dr_orin:after_defeat:2.

## dr_orin / after_archive

Bản đối chiếu đã được lưu riêng. Những thuật ngữ trong hồ sơ là nhãn quản lý, không phải bằng chứng rằng sự việc được ghi đúng.

Player: Tôi có thể trích bản đối chiếu cho người khác xem không?

NPC: Có. Ghi rõ nguồn và ngày tạo bản sao để người sau biết mình đang đọc phiên bản nào.

Gameplay: quest=none; flag=seal_term_revealed.

Player: Có mục nào cần giữ kín không?

NPC: Thông tin cá nhân của người không liên quan. Ta cần chứng minh sự việc, không công bố mọi thứ họ từng làm.

Gameplay: quest=none; flag=dialogue:dr_orin:after_archive:1.

Player: Tôi sẽ mang cả bản gốc lẫn bản đối chiếu.

NPC: Tốt. Như vậy người đọc có thể kiểm tra cách chúng ta đi đến kết luận.

Gameplay: quest=none; flag=dialogue:dr_orin:after_archive:2.

## mara_voss / after_defeat

Trận vừa rồi kết thúc đúng một lần. Nhật ký lại ghi hai mốc giờ. Tôi đang giữ video; cậu xem bản ghi ở sân tập giúp tôi nhé.

Player: Hai mốc giờ lệch nhau bao lâu?

NPC: Một giây. Không nhiều, nhưng có một xác nhận được gửi trước lúc trận đấu kết thúc.

Gameplay: quest=voss_echo; flag=voss_suspicious.

Player: Video có bị cắt đoạn nào không?

NPC: Tôi đã xem lại. Video liền mạch từ lúc bắt đầu đến lúc đội tôi ngừng đấu.

Gameplay: quest=voss_echo; flag=dialogue:mara_voss:after_defeat:1.

Player: Tôi sẽ đối chiếu bản ghi với video.

NPC: Được. Cứ giữ bản gốc, đừng sửa giờ cho chúng khớp nhau.

Gameplay: quest=voss_echo; flag=dialogue:mara_voss:after_defeat:2.

## mara_voss / after_echo

Tôi đã giữ thêm một bản video. Hale muốn đối chiếu hồ sơ của Orin; tôi sẽ gửi phần của tôi nếu ông ấy cần.

Player: Cậu có thấy ai sửa bản ghi không?

NPC: Không. Tôi chỉ thấy kết quả lệch. Tôi không muốn gọi tên ai khi chưa có bằng chứng.

Gameplay: quest=none; flag=voss_found_old_signal.

Player: Tôi nhờ cậu gửi video trực tiếp cho Orin được chứ?

NPC: Được. Nhắn tôi khi Orin nhận hồ sơ, tôi sẽ gửi cùng mốc giờ của máy quay.

Gameplay: quest=none; flag=dialogue:mara_voss:after_echo:1.

Player: Tôi sẽ giữ nguyên các bản đang có.

NPC: Ừ. Có khác biệt thì ghi riêng, đừng xóa bản nào.

Gameplay: quest=none; flag=dialogue:mara_voss:after_echo:2.

## mysterious / first_contact

Tôi đã thấy bản ghi cậu mang cho Hale. Phần xác nhận sớm không phải do đồng hồ sân tập. Tôi giữ một bản cũ để cậu đối chiếu.

Player: Anh lấy bản ghi đó từ đâu?

NPC: Tôi từng có quyền đọc hồ sơ đó. Tôi sẽ đưa phần nguồn gốc cùng bản sao, để cậu không phải tin chỉ vì tôi nhắn.

Gameplay: quest=first_signal; flag=prologue_complete.

Player: Tôi có thể kiểm tra bản cũ bằng cách nào?

NPC: Giữ bản cậu đang có và hỏi Orin cách đọc mốc xác nhận. Khi có bản của tôi, đối chiếu cả thời điểm lẫn chữ ký.

Gameplay: quest=first_signal; flag=dialogue:mysterious:first_contact:1.

Player: Tôi sẽ đối chiếu, nhưng chưa tin lời anh ngay.

NPC: Được. Cứ kiểm tra. Tôi muốn bản ghi được đọc lại, không muốn cậu nhận lời trước khi thấy nó.

Gameplay: quest=first_signal; flag=dialogue:mysterious:first_contact:2.

## mysterious / you_are_asking

Orin đã đối chiếu được phần xác nhận. Cậu đang hỏi đúng chỗ. Tôi vẫn giữ bản sao của lần truy cập cũ.

Player: Bản của anh có trùng với bản Orin giữ không?

NPC: Phần thời điểm trùng. Chữ ký và quyền truy cập cần thêm thẻ của Rook để đọc hết.

Gameplay: quest=none; flag=mysterious_acknowledged_investigation.

Player: Vì sao anh chưa giao bản sao ngay?

NPC: Vì tôi cần ghi lại cách nó đến tay cậu. Nếu thiếu nguồn, người khác có thể gọi nó là một bản chèn mới.

Gameplay: quest=none; flag=dialogue:mysterious:you_are_asking:1.

Player: Tôi sẽ giữ riêng những điểm chưa khớp.

NPC: Ừ. Khi đối chiếu bản của tôi, ghi lại cả điều khớp và điều không khớp.

Gameplay: quest=none; flag=dialogue:mysterious:you_are_asking:2.

## mysterious / last_person

Nhóm đã được thông báo về hồ sơ lưu trữ. Tôi muốn gặp cậu sau khi cậu đọc xong phần đó. Có những việc tôi phải tự giải thích.

Player: Tôi cần đọc xong phần nào trước?

NPC: Phần lệnh cũ, bản của Selene và lời Iris. Đọc chúng cùng nhau; đừng chỉ đọc đoạn có tên tôi.

Gameplay: quest=none; flag=final_meeting_requested.

Player: Anh sẽ giải thích cả việc đã dùng chúng tôi chứ?

NPC: Có. Tôi đã tận dụng việc cậu điều tra để mở lại đường truy cập. Tôi không định bỏ qua phần đó.

Gameplay: quest=none; flag=dialogue:mysterious:last_person:1.

Player: Tôi sẽ đọc hồ sơ rồi mới nhận lời gặp.

NPC: Được. Điểm hẹn sẽ được gửi qua cùng cuộc trò chuyện này.

Gameplay: quest=none; flag=dialogue:mysterious:last_person:2.

## mysterious / meet_me

Tôi đã gửi điểm hẹn trên điện thoại. Hãy giữ những bản cậu đã đối chiếu. Tôi sẽ trả lời trước chúng, không đề nghị cậu để chúng lại.

Player: Tôi có thể cho nhóm biết điểm hẹn không?

NPC: Có. Tôi muốn chuyện này có người làm chứng. Cậu không cần tới mà giấu mọi người.

Gameplay: quest=none; flag=toba_meeting_revealed.

Player: Tôi có cần giao lại hồ sơ cho anh không?

NPC: Không. Cậu giữ chúng. Nếu tôi cần xem một mục, chúng ta sẽ xem tại chỗ.

Gameplay: quest=none; flag=dialogue:mysterious:meet_me:1.

Player: Tôi sẽ tới, nhưng vẫn giữ các bản đối chiếu.

NPC: Được. Cứ giữ chúng trong tay khi nói chuyện với tôi.

Gameplay: quest=none; flag=dialogue:mysterious:meet_me:2.

## mysterious / first_contact_who

Tôi chưa công bố tên ở tin nhắn này. Cậu có thể kiểm tra quyền truy cập cũ trước; nó có để lại dấu trong hồ sơ.

Player: Dấu đó nằm ở phần nào?

NPC: Phần xác nhận có thời điểm trước ngày mở sân. Giữ cả dòng phía trên và phía dưới để đối chiếu nguồn.

Gameplay: quest=none; flag=none.

Player: Anh có đồng ý để Orin kiểm tra nó không?

NPC: Có. Tôi muốn một người giữ bản độc lập đọc nó cùng cậu.

Gameplay: quest=none; flag=dialogue:mysterious:first_contact_who:1.

Player: Tôi sẽ kiểm tra dấu trước khi hỏi tiếp.

NPC: Được. Khi hỏi tiếp, cứ chỉ vào phần cậu chưa thấy khớp.

Gameplay: quest=none; flag=dialogue:mysterious:first_contact_who:2.

## mysterious / first_contact_what

Tôi muốn cậu giữ bản gốc và đối chiếu với hồ sơ của Orin. Đó là việc tôi nhờ; cậu chưa phải hứa điều gì khác.

Player: Nếu hai bản không khớp thì sao?

NPC: Ghi lại chỗ khác nhau. Đừng tự sửa bản nào; ta cần biết sự khác biệt xuất hiện từ lúc nào.

Gameplay: quest=none; flag=none.

Player: Tôi có được đưa bản này cho Hale không?

NPC: Có. Ông ấy đã nhận một phần và có thể kiểm tra nguồn của nó.

Gameplay: quest=none; flag=dialogue:mysterious:first_contact_what:1.

Player: Tôi đồng ý kiểm tra bản ghi.

NPC: Được. Cậu có thể dừng lại nếu thấy lời tôi khác với hồ sơ.

Gameplay: quest=none; flag=dialogue:mysterious:first_contact_what:2.

## mysterious / first_contact_why

Một phần hồ sơ đang coi người từng giữ quyền truy cập là giả mạo. Tôi cần nó được kiểm tra bằng bản cũ, không chỉ bằng kết luận đã ghi sẵn.

Player: Có bằng chứng nào đi ngược kết luận đó?

NPC: Có một quyền truy cập xuất hiện trước ngày tài khoản được ghi là tạo mới. Orin có thể giúp kiểm tra thời điểm.

Gameplay: quest=none; flag=none.

Player: Anh muốn chúng tôi xóa kết luận cũ à?

NPC: Không. Giữ nó để biết ai đã kết luận và dựa vào đâu. Nếu sai, phải bổ sung giải trình, không xóa dấu cũ.

Gameplay: quest=none; flag=dialogue:mysterious:first_contact_why:1.

Player: Tôi sẽ xem bằng chứng trước.

NPC: Được. Tôi sẽ đưa phần tôi giữ để cậu đối chiếu.

Gameplay: quest=none; flag=dialogue:mysterious:first_contact_why:2.

## rook / after_defeat

Cậu đã lấy Thẻ Đen rồi. Tôi giữ lại hóa đơn. Nếu hỏi Selene về dấu xác nhận, cứ đưa thẻ cho cô ấy xem trước.

Player: Anh vẫn giữ hóa đơn để làm gì?

NPC: Để chứng minh tôi mua nó, không tự làm ra nó. Hai chuyện đó khác nhau, kể cả khi cậu chẳng thích người bán.

Gameplay: quest=rook_black_card; flag=rook_black_card_found.

Player: Selene có nhận ra chiếc thẻ này không?

NPC: Cô ấy biết cách đọc dấu. Nhận ra người dùng hay không thì cậu phải hỏi cô ấy.

Gameplay: quest=rook_black_card; flag=dialogue:rook:after_defeat:1.

Player: Tôi sẽ hỏi cô ấy về dấu xác nhận.

NPC: Được. Nếu cô ấy nói khác tôi, nhắn lại nguyên câu. Tôi không thích bị trích thiếu một nửa.

Gameplay: quest=rook_black_card; flag=dialogue:rook:after_defeat:2.

## rook / after_card

Marlow muốn đối chiếu thẻ với hồ sơ của Iris. Tôi đã giao bản hóa đơn; phần còn lại cậu nên hỏi người giữ hồ sơ.

Player: Anh có giữ bản sao hóa đơn không?

NPC: Có. Lần này tôi ghi cả ngày giao bản sao, khỏi phải cãi nhau về việc ai đưa trước.

Gameplay: quest=none; flag=below_title_hint.

Player: Vì sao phải cần hồ sơ của Iris?

NPC: Tôi biết thẻ được bán thế nào. Iris biết nó đã được dùng ở phòng thí nghiệm ra sao.

Gameplay: quest=none; flag=dialogue:rook:after_card:1.

Player: Tôi sẽ đối chiếu cả hai trước.

NPC: Ừ. Cứ hỏi tôi phần mua bán. Đừng bắt tôi trả lời thay cô ấy chuyện trong phòng thí nghiệm.

Gameplay: quest=none; flag=dialogue:rook:after_card:2.

## selene_kade / after_defeat

Tôi đã ghi lại dấu xác nhận trên thẻ. Warden ở thị trấn 5 giữ một phần hồ sơ tiếp cận khu vực; cậu cần đối chiếu với họ.

Player: Dấu xác nhận có thể bị sao chép không?

NPC: Có thể. Vì thế tôi ghi cả thời điểm và nơi dấu xuất hiện, không chỉ hình của nó.

Gameplay: quest=selene_stalemate; flag=selene_sequence_noticed.

Player: Tôi cần mang gì cho Warden?

NPC: Bản thẻ và phần ghi chú này. Cho họ xem cả hai trước khi nói cậu nghi ai.

Gameplay: quest=selene_stalemate; flag=dialogue:selene_kade:after_defeat:1.

Player: Tôi sẽ hỏi họ về lần tiếp cận đó.

NPC: Được. Nếu họ không nhớ, đừng ép họ chọn một cái tên.

Gameplay: quest=selene_stalemate; flag=dialogue:selene_kade:after_defeat:2.

## selene_kade / after_stalemate

Có tin Rocket đã chiếm phòng thí nghiệm ở thị trấn 6. Iris còn ở đó. Tôi đang kiểm tra đường liên lạc với cô ấy.

Player: Tin đó đã được xác nhận chưa?

NPC: Việc họ chặn lối vào đã được xác nhận. Tôi chưa biết tất cả những người còn bên trong.

Gameplay: quest=none; flag=board_clear_revealed.

Player: Tôi có thể giúp giữ liên lạc thế nào?

NPC: Giữ điện thoại mở. Nếu gặp Iris, để cô ấy tự kể; tôi không muốn lời của cô ấy bị chuyển qua nhiều người.

Gameplay: quest=none; flag=dialogue:selene_kade:after_stalemate:1.

Player: Tôi sẽ tới kiểm tra tình hình của Iris.

NPC: Cẩn thận. Mục tiêu trước mắt là biết cô ấy có an toàn không, rồi mới thu thập hồ sơ.

Gameplay: quest=none; flag=dialogue:selene_kade:after_stalemate:2.

