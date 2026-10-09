# Hội thoại 0.3.2 — toàn bộ nhánh

Tài liệu xuất từ nội dung phát hành. Mỗi nhánh ghi lời NPC, lựa chọn, phản hồi kế tiếp và hành động server. Đây là kiểm tra nội dung, không phải bằng chứng gameplay.

## arrival

### start — professor_hale

Hale đang ở phòng nghiên cứu. Cửa mở; ông ấy có vẻ đang tìm thứ gì trên bàn.

- Người chơi: Tôi vào chào ông ấy.
  → NPC: Hale ngẩng lên khi bạn bước tới bàn nghiên cứu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi nên gặp ông ấy ngay à?
  → NPC: Ừ. Ông ấy đã để phần bàn gần cửa trống cho bạn.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Bạn có thể quay lại khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — professor_hale

Ừ. Ông ấy đã để phần bàn gần cửa trống cho bạn.

- Người chơi: Tôi vào chào ông ấy.
  → NPC: Hale ngẩng lên khi bạn bước tới bàn nghiên cứu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Bạn có thể quay lại khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — professor_hale

Hale ngẩng lên khi bạn bước tới bàn nghiên cứu.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: close

### declined — professor_hale

Bạn có thể quay lại khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## hale_phone

### start — professor_hale

À, cậu đến rồi. Đây là điện thoại huấn luyện viên. Ta đã lưu số của Mira và Mara; còn số của ta... để ta kiểm tra lại.

- Người chơi: Tôi nhận điện thoại. Cảm ơn giáo sư.
  → NPC: Ta đã lưu số rồi. Cầm điện thoại nhé; Mira sẽ giúp cậu kiểm tra đội trước chuyến đi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi dùng nó để liên lạc với ai trước?
  → NPC: Mira chăm sóc Pokémon ở gần đây. Nếu cần hỏi về chuyến đi, cứ gọi ta. Mara thì muốn gặp cậu ở sân tập.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được, ta giữ ở đây. Khi nào muốn bắt đầu, cậu cứ ghé lại.; hành động: chỉ chuyển nhánh

### answer — professor_hale

Mira chăm sóc Pokémon ở gần đây. Nếu cần hỏi về chuyến đi, cứ gọi ta. Mara thì muốn gặp cậu ở sân tập.

- Người chơi: Tôi nhận điện thoại. Cảm ơn giáo sư.
  → NPC: Ta đã lưu số rồi. Cầm điện thoại nhé; Mira sẽ giúp cậu kiểm tra đội trước chuyến đi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được, ta giữ ở đây. Khi nào muốn bắt đầu, cậu cứ ghé lại.; hành động: chỉ chuyển nhánh

### accepted — professor_hale

Ta đã lưu số rồi. Cầm điện thoại nhé; Mira sẽ giúp cậu kiểm tra đội trước chuyến đi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — professor_hale

Được, ta giữ ở đây. Khi nào muốn bắt đầu, cậu cứ ghé lại.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mira_visit

### start — daycare_mira

Chào bạn. Hale bảo bạn vừa bắt đầu chuyến đi. Bạn muốn mình kiểm tra sức khỏe cho đội Pokémon chứ?

- Người chơi: Nhờ Mira kiểm tra đội giúp tôi.
  → NPC: Được, đưa đội lại đây. Mình sẽ kiểm tra từng Pokémon.; hành động: chỉ chuyển nhánh
- Người chơi: Có cần chúng bị thương mới đến đây không?
  → NPC: Không đâu. Mình có thể kiểm tra cả đội ngay cả khi chúng đang khỏe. Chỉ cần đợi trận đấu kết thúc nếu bạn đang thi đấu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Không sao. Nếu đội cần chăm sóc, bạn cứ quay lại.; hành động: chỉ chuyển nhánh

### answer — daycare_mira

Không đâu. Mình có thể kiểm tra cả đội ngay cả khi chúng đang khỏe. Chỉ cần đợi trận đấu kết thúc nếu bạn đang thi đấu.

- Người chơi: Nhờ Mira kiểm tra đội giúp tôi.
  → NPC: Được, đưa đội lại đây. Mình sẽ kiểm tra từng Pokémon.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Không sao. Nếu đội cần chăm sóc, bạn cứ quay lại.; hành động: chỉ chuyển nhánh

### accepted — daycare_mira

Được, đưa đội lại đây. Mình sẽ kiểm tra từng Pokémon.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — daycare_mira

Không sao. Nếu đội cần chăm sóc, bạn cứ quay lại.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## ren_supplies

### start — pokemall_ren

Bạn cần Poké Ball hay thuốc hồi phục? Cứ xem giá trước, không nhất thiết phải mua hôm nay.

- Người chơi: Cho tôi xem quầy hàng.
  → NPC: Đây là quầy hàng. Giá ghi cạnh từng món; bạn chỉ mua những gì mình cần.; hành động: chỉ chuyển nhánh
- Người chơi: Không mua thì có lỡ việc gặp Mara không?
  → NPC: Không. Mara ở sân tập, chẳng cần hóa đơn của tôi để nhận lời thách đấu. Tôi chỉ bán đồ đi đường thôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi vẫn ở đây nếu bạn cần bổ sung đồ sau này.; hành động: chỉ chuyển nhánh

### answer — pokemall_ren

Không. Mara ở sân tập, chẳng cần hóa đơn của tôi để nhận lời thách đấu. Tôi chỉ bán đồ đi đường thôi.

- Người chơi: Cho tôi xem quầy hàng.
  → NPC: Đây là quầy hàng. Giá ghi cạnh từng món; bạn chỉ mua những gì mình cần.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi vẫn ở đây nếu bạn cần bổ sung đồ sau này.; hành động: chỉ chuyển nhánh

### accepted — pokemall_ren

Đây là quầy hàng. Giá ghi cạnh từng món; bạn chỉ mua những gì mình cần.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — pokemall_ren

Được. Tôi vẫn ở đây nếu bạn cần bổ sung đồ sau này.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## lan_errand

### start — town1_lan

Cháu gặp Hale chưa? Ông ấy lại để bữa trưa trên bàn nhà cô. Cô muốn nhờ cháu mang ít táo qua cho ông ấy.

- Người chơi: Tôi sẽ mang ba quả táo sang.
  → NPC: Cảm ơn cháu. Nhớ đưa táo tận tay ông ấy giúp cô.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi cần mang bao nhiêu quả táo?
  → NPC: Ba quả là đủ. Đưa tận tay ông ấy giúp cô nhé, không ông ấy lại quên cạnh chồng sách.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Ừ, khi nào cháu tiện thì giúp cô. Cô sẽ tìm cách nhắc ông ấy nữa.; hành động: chỉ chuyển nhánh

### answer — town1_lan

Ba quả là đủ. Đưa tận tay ông ấy giúp cô nhé, không ông ấy lại quên cạnh chồng sách.

- Người chơi: Tôi sẽ mang ba quả táo sang.
  → NPC: Cảm ơn cháu. Nhớ đưa táo tận tay ông ấy giúp cô.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Ừ, khi nào cháu tiện thì giúp cô. Cô sẽ tìm cách nhắc ông ấy nữa.; hành động: chỉ chuyển nhánh

### accepted — town1_lan

Cảm ơn cháu. Nhớ đưa táo tận tay ông ấy giúp cô.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_lan

Ừ, khi nào cháu tiện thì giúp cô. Cô sẽ tìm cách nhắc ông ấy nữa.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## hale_lunch

### start — professor_hale

Táo của Lan phải không? Ta cứ nghĩ mình mới ngồi đọc được một lát. Cảm ơn cậu đã mang sang.

- Người chơi: Đây là ba quả táo Lan nhờ tôi đưa.
  → NPC: Ta nhận đủ rồi. Cảm ơn cậu, và nhắn Lan là ta đang ăn nhé. Mara chờ ở sân tập.; hành động: chỉ chuyển nhánh
- Người chơi: Sau đây tôi gặp Mara ở đâu?
  → NPC: Ở sân tập. Cô ấy đã hỏi cậu đến chưa. Cứ mang đội mà cậu hiểu rõ nhất; chưa cần một đội thật đông.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Nếu chưa mang đủ, cậu cứ quay lại sau. Ta sẽ ăn phần ở đây trước.; hành động: chỉ chuyển nhánh

### answer — professor_hale

Ở sân tập. Cô ấy đã hỏi cậu đến chưa. Cứ mang đội mà cậu hiểu rõ nhất; chưa cần một đội thật đông.

- Người chơi: Đây là ba quả táo Lan nhờ tôi đưa.
  → NPC: Ta nhận đủ rồi. Cảm ơn cậu, và nhắn Lan là ta đang ăn nhé. Mara chờ ở sân tập.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Nếu chưa mang đủ, cậu cứ quay lại sau. Ta sẽ ăn phần ở đây trước.; hành động: chỉ chuyển nhánh

### accepted — professor_hale

Ta nhận đủ rồi. Cảm ơn cậu, và nhắn Lan là ta đang ăn nhé. Mara chờ ở sân tập.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — professor_hale

Nếu chưa mang đủ, cậu cứ quay lại sau. Ta sẽ ăn phần ở đây trước.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mara_intro

### start — mara_voss

Cậu là người Hale nhắc đến? Tôi là Mara. Tôi muốn đấu với cậu một trận, nếu cậu có thời gian chuẩn bị đội.

- Người chơi: Tôi sẽ chuẩn bị đội rồi thách đấu cậu.
  → NPC: Được. Tôi ở sân tập; khi nào đội sẵn sàng, cậu quay lại nhận trận.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu muốn kiểm tra điều gì trong trận này?
  → NPC: Tôi muốn biết cậu chọn nước đi thế nào khi kế hoạch ban đầu không còn dùng được. Và tôi cũng muốn thắng. Không có gì phức tạp hơn thế.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi tập ở đây; cậu chưa muốn đấu thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### answer — mara_voss

Tôi muốn biết cậu chọn nước đi thế nào khi kế hoạch ban đầu không còn dùng được. Và tôi cũng muốn thắng. Không có gì phức tạp hơn thế.

- Người chơi: Tôi sẽ chuẩn bị đội rồi thách đấu cậu.
  → NPC: Được. Tôi ở sân tập; khi nào đội sẵn sàng, cậu quay lại nhận trận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi tập ở đây; cậu chưa muốn đấu thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### accepted — mara_voss

Được. Tôi ở sân tập; khi nào đội sẵn sàng, cậu quay lại nhận trận.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — mara_voss

Được. Tôi tập ở đây; cậu chưa muốn đấu thì cứ nói thẳng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mara_first

### start — mara_voss

Đội sẵn sàng chưa? Tôi đã chuẩn bị xong. Cậu muốn bắt đầu trận đấu không?

- Người chơi: Sẵn sàng. Bắt đầu thôi.
  → NPC: Vậy bắt đầu. Tôi sẽ không giữ lại nước đi tốt nhất đâu.; hành động: chỉ chuyển nhánh
- Người chơi: Nếu tôi thua, chúng ta có thể đấu lại chứ?
  → NPC: Có. Chữa cho đội rồi quay lại. Tôi sẽ không coi một trận thua là lý do để cậu bỏ cuộc hẹn này.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được, tôi đợi. Đừng nhận lời khi đội chưa sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — mara_voss

Có. Chữa cho đội rồi quay lại. Tôi sẽ không coi một trận thua là lý do để cậu bỏ cuộc hẹn này.

- Người chơi: Sẵn sàng. Bắt đầu thôi.
  → NPC: Vậy bắt đầu. Tôi sẽ không giữ lại nước đi tốt nhất đâu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được, tôi đợi. Đừng nhận lời khi đội chưa sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — mara_voss

Vậy bắt đầu. Tôi sẽ không giữ lại nước đi tốt nhất đâu.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — mara_voss

Được, tôi đợi. Đừng nhận lời khi đội chưa sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## field_fault

### start — narrator

Nhật ký sân tập ghi hai thời điểm kết thúc cách nhau một giây. Video chỉ ghi một trận.

- Người chơi: Tôi giữ bản ghi này và mang cho Hale.
  → NPC: Bạn giữ lại hai thời điểm và chữ ký của tài khoản cũ để đối chiếu với Hale.; hành động: chỉ chuyển nhánh
- Người chơi: Có dấu hiệu nào cho thấy đây không chỉ là lỗi đồng hồ?
  → NPC: Một tài khoản cũ đã xác nhận kết quả trước khi trận kết thúc. Ngày tạo tài khoản còn sớm hơn ngày mở sân. Cần giữ nguyên bản ghi để đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Bạn có thể đọc lại bản ghi khi muốn đối chiếu.; hành động: chỉ chuyển nhánh

### answer — narrator

Một tài khoản cũ đã xác nhận kết quả trước khi trận kết thúc. Ngày tạo tài khoản còn sớm hơn ngày mở sân. Cần giữ nguyên bản ghi để đối chiếu.

- Người chơi: Tôi giữ bản ghi này và mang cho Hale.
  → NPC: Bạn giữ lại hai thời điểm và chữ ký của tài khoản cũ để đối chiếu với Hale.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Bạn có thể đọc lại bản ghi khi muốn đối chiếu.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ lại hai thời điểm và chữ ký của tài khoản cũ để đối chiếu với Hale.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Bạn có thể đọc lại bản ghi khi muốn đối chiếu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## hale_fault

### start — professor_hale

Mã xác nhận này... ta nhận ra cấu trúc của nó. Chưa đủ để kết luận ai đã gửi, nhưng Orin có bản sao hồ sơ cũ.

- Người chơi: Tôi sẽ mang bản gốc đến Orin.
  → NPC: Được. Ta báo Orin trước để ông ấy chuẩn bị hồ sơ đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Vì sao ông muốn tôi gặp Orin?
  → NPC: Ông ấy giữ hồ sơ độc lập ở thị trấn thứ hai. Hai bản sao sẽ cho ta biết lỗi nằm ở đâu. Đừng sửa bản cậu đang giữ, kể cả khi có người nhắn bảo sửa.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Cứ giữ bản gốc an toàn; ta chưa muốn cậu đi vội.; hành động: chỉ chuyển nhánh

### answer — professor_hale

Ông ấy giữ hồ sơ độc lập ở thị trấn thứ hai. Hai bản sao sẽ cho ta biết lỗi nằm ở đâu. Đừng sửa bản cậu đang giữ, kể cả khi có người nhắn bảo sửa.

- Người chơi: Tôi sẽ mang bản gốc đến Orin.
  → NPC: Được. Ta báo Orin trước để ông ấy chuẩn bị hồ sơ đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Cứ giữ bản gốc an toàn; ta chưa muốn cậu đi vội.; hành động: chỉ chuyển nhánh

### accepted — professor_hale

Được. Ta báo Orin trước để ông ấy chuẩn bị hồ sơ đối chiếu.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — professor_hale

Được. Cứ giữ bản gốc an toàn; ta chưa muốn cậu đi vội.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## unknown_first

### start — town1_uncle_phuc

Có người nhờ bác chuyển tin này cho cháu. Bác đã hỏi tên, nhưng họ chỉ nói: “Hale sẽ hiểu.”

- Người chơi: Tôi sẽ cho Mara xem tin nhắn.
  → NPC: Ừ, cho Mara xem nguyên tin nhắn. Đừng chỉ kể lại một phần.; hành động: chỉ chuyển nhánh
- Người chơi: Người đó nhắn gì?
  → NPC: “Đừng sửa nhật ký. Giữ bản gốc. Lần trước họ đã sửa cả tên người thắng.” Điện thoại chỉ hiện ba dấu hỏi. Cháu cho Mara xem nữa nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Ừ. Bác giữ lại lời nhắn, cháu không cần trả lời người đó ngay.; hành động: chỉ chuyển nhánh

### answer — town1_uncle_phuc

“Đừng sửa nhật ký. Giữ bản gốc. Lần trước họ đã sửa cả tên người thắng.” Điện thoại chỉ hiện ba dấu hỏi. Cháu cho Mara xem nữa nhé.

- Người chơi: Tôi sẽ cho Mara xem tin nhắn.
  → NPC: Ừ, cho Mara xem nguyên tin nhắn. Đừng chỉ kể lại một phần.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Ừ. Bác giữ lại lời nhắn, cháu không cần trả lời người đó ngay.; hành động: chỉ chuyển nhánh

### accepted — town1_uncle_phuc

Ừ, cho Mara xem nguyên tin nhắn. Đừng chỉ kể lại một phần.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_uncle_phuc

Ừ. Bác giữ lại lời nhắn, cháu không cần trả lời người đó ngay.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mara_aftermath

### start — mara_voss

Tôi đã đọc tin nhắn. Người đó biết chuyện nhật ký, nhưng như thế chưa đủ để tin họ. Tôi sẽ giữ một bản riêng.

- Người chơi: Được. Tôi đi gặp Orin và sẽ báo cậu.
  → NPC: Được. Tôi sẽ báo ngay nếu bản nhật ký ở đây có thay đổi.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu sẽ làm gì khi tôi đến gặp Orin?
  → NPC: Tôi đối chiếu bản ở sân. Nếu bản của tôi đổi trong lúc cậu đi, chúng ta sẽ biết. Có gì mới thì báo nhau qua điện thoại.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Cứ chuẩn bị đã. Tôi vẫn giữ bản này, không gửi cho người lạ.; hành động: chỉ chuyển nhánh

### answer — mara_voss

Tôi đối chiếu bản ở sân. Nếu bản của tôi đổi trong lúc cậu đi, chúng ta sẽ biết. Có gì mới thì báo nhau qua điện thoại.

- Người chơi: Được. Tôi đi gặp Orin và sẽ báo cậu.
  → NPC: Được. Tôi sẽ báo ngay nếu bản nhật ký ở đây có thay đổi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Cứ chuẩn bị đã. Tôi vẫn giữ bản này, không gửi cho người lạ.; hành động: chỉ chuyển nhánh

### accepted — mara_voss

Được. Tôi sẽ báo ngay nếu bản nhật ký ở đây có thay đổi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — mara_voss

Cứ chuẩn bị đã. Tôi vẫn giữ bản này, không gửi cho người lạ.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## orin_arrival

### start — town2_librarian_an

Bạn tìm Orin à? Ông ấy đang hỏi về một cuốn sổ kết quả giải đấu bị mang khỏi thư viện.

- Người chơi: Tôi sẽ kiểm tra danh sách trên bàn đọc.
  → NPC: Bản sao nằm trên bàn đọc. Bạn chú ý ngày mượn và chữ ký nhận sách nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi có thể xem dấu mượn ở đâu?
  → NPC: Trên bàn đọc còn bản sao danh sách mượn. Bạn xem ngày và đơn vị nhận sách trước khi nói chuyện với ông ấy.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi để bản sao trên bàn, bạn có thể xem sau.; hành động: chỉ chuyển nhánh

### answer — town2_librarian_an

Trên bàn đọc còn bản sao danh sách mượn. Bạn xem ngày và đơn vị nhận sách trước khi nói chuyện với ông ấy.

- Người chơi: Tôi sẽ kiểm tra danh sách trên bàn đọc.
  → NPC: Bản sao nằm trên bàn đọc. Bạn chú ý ngày mượn và chữ ký nhận sách nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi để bản sao trên bàn, bạn có thể xem sau.; hành động: chỉ chuyển nhánh

### accepted — town2_librarian_an

Bản sao nằm trên bàn đọc. Bạn chú ý ngày mượn và chữ ký nhận sách nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_librarian_an

Được. Tôi để bản sao trên bàn, bạn có thể xem sau.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## orin_records

### start — narrator

Bảy cuốn sách được mượn cùng ngày bởi một đơn vị của League. Trong ảnh trao giải, một dòng tên đã bị cắt bỏ.

- Người chơi: Tôi sẽ đưa phần chữ ký cho Orin kiểm tra.
  → NPC: Bạn ghi lại phần chữ ký và ngày mượn để đưa cho Orin.; hành động: chỉ chuyển nhánh
- Người chơi: Chữ ký này có liên quan đến bản ghi ở sân tập không?
  → NPC: Cấu trúc chữ ký xác nhận giống nhau. Đó là điều có thể đối chiếu; danh tính người bị cắt khỏi ảnh vẫn chưa rõ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Bản sao vẫn còn ở đây để bạn kiểm tra lại.; hành động: chỉ chuyển nhánh

### answer — narrator

Cấu trúc chữ ký xác nhận giống nhau. Đó là điều có thể đối chiếu; danh tính người bị cắt khỏi ảnh vẫn chưa rõ.

- Người chơi: Tôi sẽ đưa phần chữ ký cho Orin kiểm tra.
  → NPC: Bạn ghi lại phần chữ ký và ngày mượn để đưa cho Orin.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Bản sao vẫn còn ở đây để bạn kiểm tra lại.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn ghi lại phần chữ ký và ngày mượn để đưa cho Orin.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Bản sao vẫn còn ở đây để bạn kiểm tra lại.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## orin_challenge

### start — dr_orin

Tôi đã xem bản ghi của cậu. Trước khi bàn kết luận, tôi muốn xem cậu xử lý một tình huống thay đổi liên tục.

- Người chơi: Tôi đồng ý. Xin bắt đầu trận đấu.
  → NPC: Được. Chúng ta bắt đầu; hãy quan sát những lần đội tôi đổi Pokémon.; hành động: chỉ chuyển nhánh
- Người chơi: Ông muốn dùng trận đấu làm ví dụ à?
  → NPC: Đúng. Đội của tôi dùng trạng thái và đổi Pokémon. Hãy quan sát cả những lượt không gây sát thương. Chúng ta bàn bản ghi sau trận.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Chuẩn bị đội trước; chúng ta chưa cần bắt đầu ngay.; hành động: chỉ chuyển nhánh

### answer — dr_orin

Đúng. Đội của tôi dùng trạng thái và đổi Pokémon. Hãy quan sát cả những lượt không gây sát thương. Chúng ta bàn bản ghi sau trận.

- Người chơi: Tôi đồng ý. Xin bắt đầu trận đấu.
  → NPC: Được. Chúng ta bắt đầu; hãy quan sát những lần đội tôi đổi Pokémon.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Chuẩn bị đội trước; chúng ta chưa cần bắt đầu ngay.; hành động: chỉ chuyển nhánh

### accepted — dr_orin

Được. Chúng ta bắt đầu; hãy quan sát những lần đội tôi đổi Pokémon.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — dr_orin

Được. Chuẩn bị đội trước; chúng ta chưa cần bắt đầu ngay.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## orin_summary

### start — dr_orin

Hai kho độc lập có cùng dấu sửa, dù lịch lưu không trùng nhau. Người sửa đã biết những bản nào sẽ được đem đối chiếu.

- Người chơi: Tôi sẽ tìm Rook và kiểm tra thẻ truy cập.
  → NPC: Mang cả bản ở sân và bản thư viện. Đừng để Rook chỉ trả lời một nửa câu hỏi.; hành động: chỉ chuyển nhánh
- Người chơi: Hale có biết những bản này đã bị sửa không?
  → NPC: Hale muốn giữ kín hồ sơ để ngăn người khác dùng lại hệ thống. Tôi phản đối, nhưng cũng không đưa bản sao ra ngoài. Cậu nên đòi chứng cứ từ cả hai chúng tôi. Rook giữ thẻ truy cập kho vận cũ; đó là đầu mối tiếp theo.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Cậu có thể đọc lại những phần đã đối chiếu trước khi đi.; hành động: chỉ chuyển nhánh

### answer — dr_orin

Hale muốn giữ kín hồ sơ để ngăn người khác dùng lại hệ thống. Tôi phản đối, nhưng cũng không đưa bản sao ra ngoài. Cậu nên đòi chứng cứ từ cả hai chúng tôi. Rook giữ thẻ truy cập kho vận cũ; đó là đầu mối tiếp theo.

- Người chơi: Tôi sẽ tìm Rook và kiểm tra thẻ truy cập.
  → NPC: Mang cả bản ở sân và bản thư viện. Đừng để Rook chỉ trả lời một nửa câu hỏi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Cậu có thể đọc lại những phần đã đối chiếu trước khi đi.; hành động: chỉ chuyển nhánh

### accepted — dr_orin

Mang cả bản ở sân và bản thư viện. Đừng để Rook chỉ trả lời một nửa câu hỏi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — dr_orin

Được. Cậu có thể đọc lại những phần đã đối chiếu trước khi đi.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rook_contact

### start — town3_courier_nam

Tôi giao cho Rook mấy kiện có cùng dấu thẻ đen. Ông ấy bảo đó là hàng thường, nhưng toàn nhận sau giờ đóng kho.

- Người chơi: Tôi sẽ xem hóa đơn ở kho.
  → NPC: Được. Nhìn cả giờ mở kho, không chỉ tên món hàng.; hành động: chỉ chuyển nhánh
- Người chơi: Có giấy tờ nào để kiểm tra lời ông ấy không?
  → NPC: Kho còn hóa đơn của một chuyến. Xem nó trước rồi hãy hỏi Rook; ông ấy trả lời kỹ hơn khi biết người hỏi đã đọc giấy tờ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Hóa đơn vẫn còn, tôi không giao nó đi đâu.; hành động: chỉ chuyển nhánh

### answer — town3_courier_nam

Kho còn hóa đơn của một chuyến. Xem nó trước rồi hãy hỏi Rook; ông ấy trả lời kỹ hơn khi biết người hỏi đã đọc giấy tờ.

- Người chơi: Tôi sẽ xem hóa đơn ở kho.
  → NPC: Được. Nhìn cả giờ mở kho, không chỉ tên món hàng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Hóa đơn vẫn còn, tôi không giao nó đi đâu.; hành động: chỉ chuyển nhánh

### accepted — town3_courier_nam

Được. Nhìn cả giờ mở kho, không chỉ tên món hàng.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_courier_nam

Được. Hóa đơn vẫn còn, tôi không giao nó đi đâu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## black_invoice

### start — narrator

Thẻ đen vốn cấp quyền vận chuyển khẩn cấp cho Warden. Chuyến hàng này được mở sau giờ đóng kho, dưới tên người không có ca trực.

- Người chơi: Tôi sẽ mang hóa đơn này hỏi Rook.
  → NPC: Bạn giữ bản hóa đơn có giờ mở kho và điểm nhận hàng của Rocket.; hành động: chỉ chuyển nhánh
- Người chơi: Chuyến hàng được chuyển tới đâu?
  → NPC: Một kho mang tên công ty vận tải ở thị trấn thứ sáu. Dấu nhận hàng liên quan đến Rocket. Rook cần giải thích vì sao thẻ của ông ấy được dùng ở đó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Bạn có thể đối chiếu thêm trước khi đưa ra kết luận.; hành động: chỉ chuyển nhánh

### answer — narrator

Một kho mang tên công ty vận tải ở thị trấn thứ sáu. Dấu nhận hàng liên quan đến Rocket. Rook cần giải thích vì sao thẻ của ông ấy được dùng ở đó.

- Người chơi: Tôi sẽ mang hóa đơn này hỏi Rook.
  → NPC: Bạn giữ bản hóa đơn có giờ mở kho và điểm nhận hàng của Rocket.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Bạn có thể đối chiếu thêm trước khi đưa ra kết luận.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ bản hóa đơn có giờ mở kho và điểm nhận hàng của Rocket.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Bạn có thể đối chiếu thêm trước khi đưa ra kết luận.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rook_battle

### start — rook

Cậu đọc hóa đơn rồi à? Tôi có giữ thẻ. Muốn tôi giao nó, cậu phải cho tôi thấy cậu giữ nổi thứ này khi có người đòi lại.

- Người chơi: Tôi nhận lời. Bắt đầu đi.
  → NPC: Vậy đấu thôi. Cậu thắng, tôi giao thẻ cùng biên nhận.; hành động: chỉ chuyển nhánh
- Người chơi: Tại sao phải đấu để nhận thẻ?
  → NPC: Người ở kho sẽ không nhường đường chỉ vì cậu cầm giấy tờ. Tôi muốn biết cậu có thể đưa đội ra khỏi đó. Thắng trận này, tôi giao thẻ và nói phần tôi biết.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Thẻ vẫn ở chỗ tôi; cậu chưa muốn đấu thì không cần giả vờ.; hành động: chỉ chuyển nhánh

### answer — rook

Người ở kho sẽ không nhường đường chỉ vì cậu cầm giấy tờ. Tôi muốn biết cậu có thể đưa đội ra khỏi đó. Thắng trận này, tôi giao thẻ và nói phần tôi biết.

- Người chơi: Tôi nhận lời. Bắt đầu đi.
  → NPC: Vậy đấu thôi. Cậu thắng, tôi giao thẻ cùng biên nhận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Thẻ vẫn ở chỗ tôi; cậu chưa muốn đấu thì không cần giả vờ.; hành động: chỉ chuyển nhánh

### accepted — rook

Vậy đấu thôi. Cậu thắng, tôi giao thẻ cùng biên nhận.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — rook

Được. Thẻ vẫn ở chỗ tôi; cậu chưa muốn đấu thì không cần giả vờ.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rook_warning

### start — rook

Hóa đơn mới tuần trước. Selene lại nói kho đã đóng từ lâu. Tôi chưa biết ai đang kể thiếu phần nào.

- Người chơi: Tôi sẽ đối chiếu chuyến xe với Selene.
  → NPC: Được. Mang biên nhận theo; tôi không muốn cô ấy chỉ nghe lời tôi qua cậu.; hành động: chỉ chuyển nhánh
- Người chơi: Iris có liên quan đến chuyến hàng không?
  → NPC: Cô ấy từng nhờ tôi chuyển một bản sao hồ sơ ra ngoài. Tôi vẫn giữ biên nhận. Gặp Selene và kiểm tra chuyến xe trước khi quy chuyện này cho cô ấy.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Cứ đọc biên nhận đã. Tôi chưa cần cậu kết luận ngay.; hành động: chỉ chuyển nhánh

### answer — rook

Cô ấy từng nhờ tôi chuyển một bản sao hồ sơ ra ngoài. Tôi vẫn giữ biên nhận. Gặp Selene và kiểm tra chuyến xe trước khi quy chuyện này cho cô ấy.

- Người chơi: Tôi sẽ đối chiếu chuyến xe với Selene.
  → NPC: Được. Mang biên nhận theo; tôi không muốn cô ấy chỉ nghe lời tôi qua cậu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Cứ đọc biên nhận đã. Tôi chưa cần cậu kết luận ngay.; hành động: chỉ chuyển nhánh

### accepted — rook

Được. Mang biên nhận theo; tôi không muốn cô ấy chỉ nghe lời tôi qua cậu.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — rook

Cứ đọc biên nhận đã. Tôi chưa cần cậu kết luận ngay.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## selene_local

### start — town4_keeper_yen

Một xe League vào đây ban đêm. Sáng hôm sau lá cây sát đường bị cháy. Selene nói xe chở thuốc, nên tôi giữ lại sổ cổng.

- Người chơi: Tôi sẽ kiểm tra sổ ở chòi bảo vệ.
  → NPC: Sổ để trong chòi. Bạn nhớ đối chiếu món hàng trên giấy và trên lệnh giao.; hành động: chỉ chuyển nhánh
- Người chơi: Ai đã cho chuyến xe vào?
  → NPC: Selene ký giấy. Nhưng giấy vào cổng chỉ ghi chuyến thuốc, không ghi thứ thực sự có trên xe. Bạn xem cả lệnh giao hàng nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Sổ vẫn ở chòi, tôi chưa giao cho ai.; hành động: chỉ chuyển nhánh

### answer — town4_keeper_yen

Selene ký giấy. Nhưng giấy vào cổng chỉ ghi chuyến thuốc, không ghi thứ thực sự có trên xe. Bạn xem cả lệnh giao hàng nữa.

- Người chơi: Tôi sẽ kiểm tra sổ ở chòi bảo vệ.
  → NPC: Sổ để trong chòi. Bạn nhớ đối chiếu món hàng trên giấy và trên lệnh giao.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Sổ vẫn ở chòi, tôi chưa giao cho ai.; hành động: chỉ chuyển nhánh

### accepted — town4_keeper_yen

Sổ để trong chòi. Bạn nhớ đối chiếu món hàng trên giấy và trên lệnh giao.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town4_keeper_yen

Được. Sổ vẫn ở chòi, tôi chưa giao cho ai.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## selene_log

### start — narrator

Giấy của Selene ghi thuốc. Lệnh giao hàng lại bị đổi thành thiết bị lưu hồ sơ, bằng dấu xác nhận từ văn phòng League.

- Người chơi: Tôi sẽ hỏi Selene về lệnh đã bị đổi.
  → NPC: Bạn giữ hai bản có nội dung khác nhau để đưa cho Selene.; hành động: chỉ chuyển nhánh
- Người chơi: Vậy chữ ký của Selene không chứng minh cô ấy đổi hàng?
  → NPC: Đúng. Nó chỉ chứng minh cô ấy duyệt chuyến thuốc ban đầu. Rook nói đúng về chiếc xe; Selene có thể nói đúng về lô hàng cô ấy được báo.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Giữ cả hai giấy tờ để tránh nhầm chuyến xe với món hàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Đúng. Nó chỉ chứng minh cô ấy duyệt chuyến thuốc ban đầu. Rook nói đúng về chiếc xe; Selene có thể nói đúng về lô hàng cô ấy được báo.

- Người chơi: Tôi sẽ hỏi Selene về lệnh đã bị đổi.
  → NPC: Bạn giữ hai bản có nội dung khác nhau để đưa cho Selene.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Giữ cả hai giấy tờ để tránh nhầm chuyến xe với món hàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ hai bản có nội dung khác nhau để đưa cho Selene.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Giữ cả hai giấy tờ để tránh nhầm chuyến xe với món hàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## selene_battle

### start — selene_kade

Cậu mang đủ hai bản giấy tờ. Tốt. Tôi sẽ giúp truy lệnh sửa, nhưng trước hết tôi muốn biết cậu có thể bảo vệ bản gốc.

- Người chơi: Vậy chúng ta đấu một trận.
  → NPC: Được. Sau trận, tôi sẽ giúp cậu truy lệnh đã đổi món hàng.; hành động: chỉ chuyển nhánh
- Người chơi: Cô vẫn chưa tin tôi à?
  → NPC: Tôi tin cậu đã chịu kiểm tra. Còn ở kho, người ta sẽ gây áp lực để cậu bỏ thứ đang giữ. Trận này cho tôi biết cậu xử lý áp lực thế nào.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi không coi việc cậu cần chuẩn bị là từ chối giúp điều tra.; hành động: chỉ chuyển nhánh

### answer — selene_kade

Tôi tin cậu đã chịu kiểm tra. Còn ở kho, người ta sẽ gây áp lực để cậu bỏ thứ đang giữ. Trận này cho tôi biết cậu xử lý áp lực thế nào.

- Người chơi: Vậy chúng ta đấu một trận.
  → NPC: Được. Sau trận, tôi sẽ giúp cậu truy lệnh đã đổi món hàng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi không coi việc cậu cần chuẩn bị là từ chối giúp điều tra.; hành động: chỉ chuyển nhánh

### accepted — selene_kade

Được. Sau trận, tôi sẽ giúp cậu truy lệnh đã đổi món hàng.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — selene_kade

Được. Tôi không coi việc cậu cần chuẩn bị là từ chối giúp điều tra.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## selene_aftermath

### start — selene_kade

Tôi sẽ gửi nhật ký cho Mara. Warden quản lý quyền mở kho khẩn cấp; nếu thẻ bị sao chép, họ cần biết.

- Người chơi: Tôi sẽ mang chứng cứ đến trạm Warden.
  → NPC: Tốt. Tôi gửi bản cho Mara trong lúc cậu đi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi cần mang gì khi gặp Warden?
  → NPC: Mang bản lệnh sửa và dấu thẻ. Đừng gọi thẻ là bằng chứng mọi Warden có lỗi. Trước mắt, nó cho thấy quyền truy cập đã bị dùng sai.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi giữ một bản sao để chuyến đi này không làm mất đầu mối.; hành động: chỉ chuyển nhánh

### answer — selene_kade

Mang bản lệnh sửa và dấu thẻ. Đừng gọi thẻ là bằng chứng mọi Warden có lỗi. Trước mắt, nó cho thấy quyền truy cập đã bị dùng sai.

- Người chơi: Tôi sẽ mang chứng cứ đến trạm Warden.
  → NPC: Tốt. Tôi gửi bản cho Mara trong lúc cậu đi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi giữ một bản sao để chuyến đi này không làm mất đầu mối.; hành động: chỉ chuyển nhánh

### accepted — selene_kade

Tốt. Tôi gửi bản cho Mara trong lúc cậu đi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — selene_kade

Được. Tôi giữ một bản sao để chuyến đi này không làm mất đầu mối.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## warden_ranger

### start — town5_ranger_hanh

Pokémon tránh đoạn đường mỗi lần chiếc xe ấy tới. Chúng không tránh người đi bộ; tôi muốn kiểm tra thứ xe đang chở.

- Người chơi: Tôi sẽ đối chiếu bảng trực.
  → NPC: Cảm ơn bạn. Nếu ngày giờ khớp, báo Warden giúp tôi trước chuyến xe tới.; hành động: chỉ chuyển nhánh
- Người chơi: Trạm có ghi lại những lần xe tới không?
  → NPC: Có, trên bảng trực. Đọc ngày giờ rồi báo Warden. Tôi cần một tuyến cứu hộ an toàn, không chỉ một lời đồn về chiếc xe.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi đánh dấu những ngày đó để bạn đọc sau.; hành động: chỉ chuyển nhánh

### answer — town5_ranger_hanh

Có, trên bảng trực. Đọc ngày giờ rồi báo Warden. Tôi cần một tuyến cứu hộ an toàn, không chỉ một lời đồn về chiếc xe.

- Người chơi: Tôi sẽ đối chiếu bảng trực.
  → NPC: Cảm ơn bạn. Nếu ngày giờ khớp, báo Warden giúp tôi trước chuyến xe tới.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi đánh dấu những ngày đó để bạn đọc sau.; hành động: chỉ chuyển nhánh

### accepted — town5_ranger_hanh

Cảm ơn bạn. Nếu ngày giờ khớp, báo Warden giúp tôi trước chuyến xe tới.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town5_ranger_hanh

Được. Tôi đánh dấu những ngày đó để bạn đọc sau.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## warden_log

### start — narrator

Ba lệnh mở kho mang tên một nhân viên đã nghỉ từ nhiều năm trước. Tài khoản của người đó vẫn được chấp nhận.

- Người chơi: Tôi sẽ báo Warden tài khoản cần kiểm tra.
  → NPC: Bạn đánh dấu ba lệnh dùng tài khoản đã nghỉ để báo Warden.; hành động: chỉ chuyển nhánh
- Người chơi: Warden có thể khóa quyền này ngay không?
  → NPC: Có thể thu hồi tài khoản, nhưng kho vẫn phải mở cho cứu hộ. Cần chỉ rõ quyền bị lạm dụng để không cắt đường tiếp tế của cả trạm.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Bạn có thể đọc lại các lệnh trước khi báo.; hành động: chỉ chuyển nhánh

### answer — narrator

Có thể thu hồi tài khoản, nhưng kho vẫn phải mở cho cứu hộ. Cần chỉ rõ quyền bị lạm dụng để không cắt đường tiếp tế của cả trạm.

- Người chơi: Tôi sẽ báo Warden tài khoản cần kiểm tra.
  → NPC: Bạn đánh dấu ba lệnh dùng tài khoản đã nghỉ để báo Warden.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Bạn có thể đọc lại các lệnh trước khi báo.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn đánh dấu ba lệnh dùng tài khoản đã nghỉ để báo Warden.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Bạn có thể đọc lại các lệnh trước khi báo.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## warden_six

### start — sixth_warden

Ta sẽ thu hồi tài khoản cũ. Còn cậu, nếu muốn vào kho điều tra, ta phải biết cậu đưa được đội của mình về.

- Người chơi: Tôi sẵn sàng nhận bài thử.
  → NPC: Vậy bắt đầu. Ta muốn thấy cả đội của cậu còn sức khi trận kết thúc.; hành động: chỉ chuyển nhánh
- Người chơi: Bài thử của ông tập trung vào điều gì?
  → NPC: Giữ đội còn sức chiến đấu khi bị ép đổi Pokémon. Quyền vào kho không cho phép cậu phá thiết bị hay đuổi theo mọi người một mình.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Chuẩn bị đội rồi quay lại; quyền điều tra chưa cần cấp vội.; hành động: chỉ chuyển nhánh

### answer — sixth_warden

Giữ đội còn sức chiến đấu khi bị ép đổi Pokémon. Quyền vào kho không cho phép cậu phá thiết bị hay đuổi theo mọi người một mình.

- Người chơi: Tôi sẵn sàng nhận bài thử.
  → NPC: Vậy bắt đầu. Ta muốn thấy cả đội của cậu còn sức khi trận kết thúc.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Chuẩn bị đội rồi quay lại; quyền điều tra chưa cần cấp vội.; hành động: chỉ chuyển nhánh

### accepted — sixth_warden

Vậy bắt đầu. Ta muốn thấy cả đội của cậu còn sức khi trận kết thúc.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — sixth_warden

Được. Chuẩn bị đội rồi quay lại; quyền điều tra chưa cần cấp vội.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## warden_orders

### start — sixth_warden

Rocket thuê kho dưới tên công ty vận tải. Đức làm ca đêm ở đó; anh ấy có thể chỉ cửa vào mà không khiến cả kho báo động.

- Người chơi: Tôi sẽ gặp Đức trước khi vào kho.
  → NPC: Ta báo trạm giữ liên lạc. Đưa Iris ra trước nếu tìm thấy cô ấy.; hành động: chỉ chuyển nhánh
- Người chơi: Nếu gặp Iris trong kho, tôi nên làm gì trước?
  → NPC: Đưa cô ấy ra nơi an toàn. Giữ chứng cứ nếu có thể, nhưng không để một người ở lại chỉ để mang thêm máy móc về.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Ta giữ bản sao hồ sơ ở trạm trong lúc cậu chuẩn bị.; hành động: chỉ chuyển nhánh

### answer — sixth_warden

Đưa cô ấy ra nơi an toàn. Giữ chứng cứ nếu có thể, nhưng không để một người ở lại chỉ để mang thêm máy móc về.

- Người chơi: Tôi sẽ gặp Đức trước khi vào kho.
  → NPC: Ta báo trạm giữ liên lạc. Đưa Iris ra trước nếu tìm thấy cô ấy.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Ta giữ bản sao hồ sơ ở trạm trong lúc cậu chuẩn bị.; hành động: chỉ chuyển nhánh

### accepted — sixth_warden

Ta báo trạm giữ liên lạc. Đưa Iris ra trước nếu tìm thấy cô ấy.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — sixth_warden

Được. Ta giữ bản sao hồ sơ ở trạm trong lúc cậu chuẩn bị.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rocket_informant

### start — town6_informant_duc

Tôi nghe tiếng đấu Pokémon dưới kho. Sáng ra sổ ca lại ghi tầng hầm không hoạt động. Vex xem sổ đó mỗi ngày.

- Người chơi: Tôi sẽ kiểm tra bảng giao hàng.
  → NPC: Được. Tôi đánh dấu cửa phụ, cậu xem đường xuống trên bảng rồi hãy vào.; hành động: chỉ chuyển nhánh
- Người chơi: Có cách nào xác định đường xuống không?
  → NPC: Cửa phụ nhận thẻ đen. Bảng giao hàng ghi những kiện được đưa xuống tầng hầm. Xem nó trước khi đi qua các chốt.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi chưa dẫn ai xuống đó, cậu cứ chuẩn bị trước.; hành động: chỉ chuyển nhánh

### answer — town6_informant_duc

Cửa phụ nhận thẻ đen. Bảng giao hàng ghi những kiện được đưa xuống tầng hầm. Xem nó trước khi đi qua các chốt.

- Người chơi: Tôi sẽ kiểm tra bảng giao hàng.
  → NPC: Được. Tôi đánh dấu cửa phụ, cậu xem đường xuống trên bảng rồi hãy vào.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi chưa dẫn ai xuống đó, cậu cứ chuẩn bị trước.; hành động: chỉ chuyển nhánh

### accepted — town6_informant_duc

Được. Tôi đánh dấu cửa phụ, cậu xem đường xuống trên bảng rồi hãy vào.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town6_informant_duc

Được. Tôi chưa dẫn ai xuống đó, cậu cứ chuẩn bị trước.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rocket_route

### start — narrator

Máy sao chép dữ liệu được ghi là hàng dễ vỡ. Tầng hầm dùng đường điện riêng và có ba chốt kiểm tra.

- Người chơi: Tôi sẽ đi theo lối trên bản giao hàng.
  → NPC: Bạn giữ lối qua ba chốt và vị trí phòng Iris trên bản giao hàng.; hành động: chỉ chuyển nhánh
- Người chơi: Thẻ đen có mở được cả ba chốt không?
  → NPC: Không. Nó chỉ đủ vào cửa phụ. Sau đó lính gác vẫn kiểm tra người đi qua. Bản giao hàng chỉ ra lối đến phòng Iris.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Bạn có thể đọc lại đường đi trước khi vào.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Nó chỉ đủ vào cửa phụ. Sau đó lính gác vẫn kiểm tra người đi qua. Bản giao hàng chỉ ra lối đến phòng Iris.

- Người chơi: Tôi sẽ đi theo lối trên bản giao hàng.
  → NPC: Bạn giữ lối qua ba chốt và vị trí phòng Iris trên bản giao hàng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Bạn có thể đọc lại đường đi trước khi vào.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ lối qua ba chốt và vị trí phòng Iris trên bản giao hàng.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Bạn có thể đọc lại đường đi trước khi vào.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rocket_guard1

### start — rocket_grunt_01

Thẻ được chấp nhận. Người cầm thẻ thì chưa. Tôi được lệnh không để người lạ qua chốt này.

- Người chơi: Tôi sẽ đấu để qua chốt.
  → NPC: Được. Tôi giữ chốt bằng trận đấu này.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có biết người ở dưới kho đang bị giữ không?
  → NPC: Tôi biết có phòng cấm vào. Tôi không được xem danh sách người bên trong. Muốn qua chốt thì cậu phải thắng tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Vậy đứng ngoài chốt. Tôi không đuổi theo người chưa bước vào.; hành động: chỉ chuyển nhánh

### answer — rocket_grunt_01

Tôi biết có phòng cấm vào. Tôi không được xem danh sách người bên trong. Muốn qua chốt thì cậu phải thắng tôi.

- Người chơi: Tôi sẽ đấu để qua chốt.
  → NPC: Được. Tôi giữ chốt bằng trận đấu này.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Vậy đứng ngoài chốt. Tôi không đuổi theo người chưa bước vào.; hành động: chỉ chuyển nhánh

### accepted — rocket_grunt_01

Được. Tôi giữ chốt bằng trận đấu này.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — rocket_grunt_01

Vậy đứng ngoài chốt. Tôi không đuổi theo người chưa bước vào.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rocket_overhear

### start — narrator

Vex: “Giữ lịch sử trận đấu. Xóa tên người tham gia.” Iris: “Dữ liệu lỗi sẽ khiến Pokémon bị nhận nhầm.”

- Người chơi: Tôi sẽ tìm đường đến phòng Iris.
  → NPC: Bạn lưu nguyên đoạn trao đổi và tìm đường đến phòng Iris.; hành động: chỉ chuyển nhánh
- Người chơi: Vex dùng bản ghi để làm gì?
  → NPC: Rocket đang giả quyền truy cập rồi bán nó như thẻ hợp lệ. Một hồ sơ bị xóa lặp lại có tên bắt đầu bằng chữ T. Iris đang bị ép sửa những dữ liệu này.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Bạn vẫn có thể nghe lại đoạn trao đổi trước khi đi.; hành động: chỉ chuyển nhánh

### answer — narrator

Rocket đang giả quyền truy cập rồi bán nó như thẻ hợp lệ. Một hồ sơ bị xóa lặp lại có tên bắt đầu bằng chữ T. Iris đang bị ép sửa những dữ liệu này.

- Người chơi: Tôi sẽ tìm đường đến phòng Iris.
  → NPC: Bạn lưu nguyên đoạn trao đổi và tìm đường đến phòng Iris.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Bạn vẫn có thể nghe lại đoạn trao đổi trước khi đi.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn lưu nguyên đoạn trao đổi và tìm đường đến phòng Iris.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Bạn vẫn có thể nghe lại đoạn trao đổi trước khi đi.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rocket_guard2

### start — rocket_grunt_02

Chốt đầu để cậu qua rồi à? Tôi chưa nhận lệnh cho người ngoài vào khu máy.

- Người chơi: Tôi nhận trận đấu. Để xem anh giữ được bao lâu.
  → NPC: Vậy bắt đầu. Tôi vẫn chưa nhận lệnh mở cửa cho cậu.; hành động: chỉ chuyển nhánh
- Người chơi: Anh đang chờ lệnh của ai?
  → NPC: Vex. Trong lúc chờ, tôi giữ cậu ở đây. Đội của tôi không cần kết thúc nhanh; chỉ cần cậu không bước qua cửa.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Cậu đứng lại thì tôi chưa cần bắt đầu.; hành động: chỉ chuyển nhánh

### answer — rocket_grunt_02

Vex. Trong lúc chờ, tôi giữ cậu ở đây. Đội của tôi không cần kết thúc nhanh; chỉ cần cậu không bước qua cửa.

- Người chơi: Tôi nhận trận đấu. Để xem anh giữ được bao lâu.
  → NPC: Vậy bắt đầu. Tôi vẫn chưa nhận lệnh mở cửa cho cậu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Cậu đứng lại thì tôi chưa cần bắt đầu.; hành động: chỉ chuyển nhánh

### accepted — rocket_grunt_02

Vậy bắt đầu. Tôi vẫn chưa nhận lệnh mở cửa cho cậu.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — rocket_grunt_02

Được. Cậu đứng lại thì tôi chưa cần bắt đầu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rocket_guard3

### start — rocket_grunt_03

Sau cửa này là khu không có trên bản đồ khách. Cậu đã tới đây thì chắc không đi nhầm.

- Người chơi: Tôi sẽ đấu để mở đường.
  → NPC: Được. Thắng tôi, cậu qua cửa này.; hành động: chỉ chuyển nhánh
- Người chơi: Iris có ở phía sau cửa không?
  → NPC: Cô ấy làm việc bên kia, dưới sự giám sát của Vex. Tôi giữ cửa. Cậu thắng tôi mới vào được.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Nếu cậu lùi lại, trận này chưa bắt đầu.; hành động: chỉ chuyển nhánh

### answer — rocket_grunt_03

Cô ấy làm việc bên kia, dưới sự giám sát của Vex. Tôi giữ cửa. Cậu thắng tôi mới vào được.

- Người chơi: Tôi sẽ đấu để mở đường.
  → NPC: Được. Thắng tôi, cậu qua cửa này.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Nếu cậu lùi lại, trận này chưa bắt đầu.; hành động: chỉ chuyển nhánh

### accepted — rocket_grunt_03

Được. Thắng tôi, cậu qua cửa này.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — rocket_grunt_03

Nếu cậu lùi lại, trận này chưa bắt đầu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rocket_vex

### start — rocket_admin_vex

League đã xóa những cái tên này trước khi tôi tìm được chúng. Cậu đến bắt tôi trả lời, vậy cậu có hỏi họ cùng câu đó không?

- Người chơi: Tôi sẽ đánh bại cô và đưa Iris ra.
  → NPC: Vậy đấu. Tôi sẽ không giao máy chỉ vì cậu kể cho tôi nghe điều đúng.; hành động: chỉ chuyển nhánh
- Người chơi: Việc League làm sai cho cô quyền giữ Iris sao?
  → NPC: Không. Tôi cần cô ấy vận hành máy, và tôi đã giữ cô ấy lại. Cậu muốn đưa người và hồ sơ ra khỏi đây thì phải thắng tôi trước.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Cậu chưa muốn đấu thì dừng ở đó. Tôi chưa giao quyền truy cập cho cậu.; hành động: chỉ chuyển nhánh

### answer — rocket_admin_vex

Không. Tôi cần cô ấy vận hành máy, và tôi đã giữ cô ấy lại. Cậu muốn đưa người và hồ sơ ra khỏi đây thì phải thắng tôi trước.

- Người chơi: Tôi sẽ đánh bại cô và đưa Iris ra.
  → NPC: Vậy đấu. Tôi sẽ không giao máy chỉ vì cậu kể cho tôi nghe điều đúng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Cậu chưa muốn đấu thì dừng ở đó. Tôi chưa giao quyền truy cập cho cậu.; hành động: chỉ chuyển nhánh

### accepted — rocket_admin_vex

Vậy đấu. Tôi sẽ không giao máy chỉ vì cậu kể cho tôi nghe điều đúng.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — rocket_admin_vex

Cậu chưa muốn đấu thì dừng ở đó. Tôi chưa giao quyền truy cập cho cậu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## iris_rescue

### start — lab_scientist_iris

Tôi từng sửa báo cáo để nhóm nghiên cứu không bị đóng cửa. Rocket tìm ra bản gốc rồi dùng nó ép tôi làm tiếp. Tôi không vô can.

- Người chơi: Tôi sẽ lấy bản gốc và đưa cô ra.
  → NPC: Tôi chỉ máy đọc cho cậu. Lấy bản gốc rồi chúng ta đi cùng nhau.; hành động: chỉ chuyển nhánh
- Người chơi: Cô đã giữ lại chứng cứ nào?
  → NPC: Một máy đọc riêng chứa bản chưa bị sửa. Lấy bản đó rồi chúng ta ra cửa giao hàng. Tôi sẽ giải thích phần mình đã làm bằng chính bản gốc.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi giữ máy đọc ở đây; đừng tự tay xóa dữ liệu.; hành động: chỉ chuyển nhánh

### answer — lab_scientist_iris

Một máy đọc riêng chứa bản chưa bị sửa. Lấy bản đó rồi chúng ta ra cửa giao hàng. Tôi sẽ giải thích phần mình đã làm bằng chính bản gốc.

- Người chơi: Tôi sẽ lấy bản gốc và đưa cô ra.
  → NPC: Tôi chỉ máy đọc cho cậu. Lấy bản gốc rồi chúng ta đi cùng nhau.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi giữ máy đọc ở đây; đừng tự tay xóa dữ liệu.; hành động: chỉ chuyển nhánh

### accepted — lab_scientist_iris

Tôi chỉ máy đọc cho cậu. Lấy bản gốc rồi chúng ta đi cùng nhau.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — lab_scientist_iris

Được. Tôi giữ máy đọc ở đây; đừng tự tay xóa dữ liệu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## iris_terminal

### start — narrator

Trong bản gốc, tên huấn luyện viên bị xóa là TOBA. Có chữ ký của Hale, Aurelia và một người thuộc School of Wolf.

- Người chơi: Tôi sẽ mang bản gốc đến Marlow.
  → NPC: Giữ bản gốc này. Tôi mang bản sao ra ngoài để không còn một kho duy nhất có thể xóa nó.; hành động: chỉ chuyển nhánh
- Người chơi: Những chữ ký đó cho biết ai đã ra lệnh xóa tên không?
  → NPC: Chưa. Hồ sơ không ghi lý do. Tôi giữ bản sao, cậu giữ bản gốc. Marlow biết hệ thống lưu trữ trước League hiện tại; hãy nhờ ông ấy đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Giữ nguyên bản này khi muốn kiểm tra thêm.; hành động: chỉ chuyển nhánh

### answer — narrator

Chưa. Hồ sơ không ghi lý do. Tôi giữ bản sao, cậu giữ bản gốc. Marlow biết hệ thống lưu trữ trước League hiện tại; hãy nhờ ông ấy đối chiếu.

- Người chơi: Tôi sẽ mang bản gốc đến Marlow.
  → NPC: Giữ bản gốc này. Tôi mang bản sao ra ngoài để không còn một kho duy nhất có thể xóa nó.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Giữ nguyên bản này khi muốn kiểm tra thêm.; hành động: chỉ chuyển nhánh

### accepted — narrator

Giữ bản gốc này. Tôi mang bản sao ra ngoài để không còn một kho duy nhất có thể xóa nó.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Giữ nguyên bản này khi muốn kiểm tra thêm.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## marlow_account

### start — archaeologist_marlow

Ảnh giấy và bản số cùng thiếu một người. Tuy vậy, ảnh cũ còn dấu ép chữ mà bản quét bỏ qua.

- Người chơi: Tôi sẽ xem bản ảnh cũ.
  → NPC: Ta đặt ảnh cạnh bản quét. Cậu xem phần dấu ép chữ ở dưới ngày chụp.; hành động: chỉ chuyển nhánh
- Người chơi: Ông đọc được gì từ dấu trên ảnh?
  → NPC: Thẻ đen là mẫu cứu hộ cũ, không phải phát minh của Rocket. Dấu ngày dưới ảnh có thể nối hồ sơ bị xóa với chuyến cứu hộ năm đó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Ta để bản ảnh ở chỗ đối chiếu, không mang nó đi đâu.; hành động: chỉ chuyển nhánh

### answer — archaeologist_marlow

Thẻ đen là mẫu cứu hộ cũ, không phải phát minh của Rocket. Dấu ngày dưới ảnh có thể nối hồ sơ bị xóa với chuyến cứu hộ năm đó.

- Người chơi: Tôi sẽ xem bản ảnh cũ.
  → NPC: Ta đặt ảnh cạnh bản quét. Cậu xem phần dấu ép chữ ở dưới ngày chụp.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Ta để bản ảnh ở chỗ đối chiếu, không mang nó đi đâu.; hành động: chỉ chuyển nhánh

### accepted — archaeologist_marlow

Ta đặt ảnh cạnh bản quét. Cậu xem phần dấu ép chữ ở dưới ngày chụp.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — archaeologist_marlow

Được. Ta để bản ảnh ở chỗ đối chiếu, không mang nó đi đâu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## marlow_photo

### start — narrator

TOBA đứng cạnh Hale trong đội cứu hộ. Ngày trên ảnh trùng với một sự cố mà hồ sơ League gọi là diễn tập.

- Người chơi: Tôi sẽ mang đầu mối đến Seventh Warden.
  → NPC: Bạn ghi lại ngày chụp và vị trí TOBA đứng cạnh Hale để hỏi Warden.; hành động: chỉ chuyển nhánh
- Người chơi: Ai có thể xác nhận chuyện đã xảy ra tại đó?
  → NPC: Seventh Warden giữ khu vực ấy. Ông ấy có quyền cấp tiếp cận sau bài kiểm tra an toàn. Ảnh này là đầu mối để hỏi, chưa phải toàn bộ câu trả lời.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Bạn có thể đối chiếu thêm ảnh trước khi đi.; hành động: chỉ chuyển nhánh

### answer — narrator

Seventh Warden giữ khu vực ấy. Ông ấy có quyền cấp tiếp cận sau bài kiểm tra an toàn. Ảnh này là đầu mối để hỏi, chưa phải toàn bộ câu trả lời.

- Người chơi: Tôi sẽ mang đầu mối đến Seventh Warden.
  → NPC: Bạn ghi lại ngày chụp và vị trí TOBA đứng cạnh Hale để hỏi Warden.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Bạn có thể đối chiếu thêm ảnh trước khi đi.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn ghi lại ngày chụp và vị trí TOBA đứng cạnh Hale để hỏi Warden.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Bạn có thể đối chiếu thêm ảnh trước khi đi.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## warden_seven

### start — seventh_warden

Ta biết hồ sơ có phần bị giấu. Ta cũng biết khu vực đó vẫn nguy hiểm. Muốn vào, cậu cần vượt bài thử của ta.

- Người chơi: Tôi sẵn sàng đấu để nhận quyền tiếp cận.
  → NPC: Được. Qua bài thử này, ta cấp quyền để cậu tới cảng đọc hồ sơ.; hành động: chỉ chuyển nhánh
- Người chơi: Sau bài thử, tôi nên gặp ai để đọc hồ sơ cứu hộ?
  → NPC: Liora ở cảng thị trấn tám giữ hồ sơ chuyến tàu cuối. Mang ảnh và ngày sự cố, đừng chỉ mang một câu chuyện nghe được.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Ta chưa cấp quyền cho người chưa sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — seventh_warden

Liora ở cảng thị trấn tám giữ hồ sơ chuyến tàu cuối. Mang ảnh và ngày sự cố, đừng chỉ mang một câu chuyện nghe được.

- Người chơi: Tôi sẵn sàng đấu để nhận quyền tiếp cận.
  → NPC: Được. Qua bài thử này, ta cấp quyền để cậu tới cảng đọc hồ sơ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Ta chưa cấp quyền cho người chưa sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — seventh_warden

Được. Qua bài thử này, ta cấp quyền để cậu tới cảng đọc hồ sơ.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — seventh_warden

Được. Ta chưa cấp quyền cho người chưa sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## liora_harbour

### start — harbour_marshal_liora

Cho tôi xem ảnh và ngày trên hồ sơ. Nếu cậu muốn đọc danh sách cứu hộ, tôi cần biết cậu đang đối chiếu chuyến nào.

- Người chơi: Tôi sẽ đối chiếu danh sách trên bảng.
  → NPC: Tôi ghi chuyến tàu cần đối chiếu. Đọc danh sách rồi gặp Dorian nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Danh sách gốc còn ở cảng không?
  → NPC: Còn trên bảng điều hành. Tàu trở về thiếu một người. Đọc danh sách trước; Dorian sẽ tiếp cậu ở vòng loại cuối sau đó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Hồ sơ được giữ ở cảng, cậu không cần vội.; hành động: chỉ chuyển nhánh

### answer — harbour_marshal_liora

Còn trên bảng điều hành. Tàu trở về thiếu một người. Đọc danh sách trước; Dorian sẽ tiếp cậu ở vòng loại cuối sau đó.

- Người chơi: Tôi sẽ đối chiếu danh sách trên bảng.
  → NPC: Tôi ghi chuyến tàu cần đối chiếu. Đọc danh sách rồi gặp Dorian nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Hồ sơ được giữ ở cảng, cậu không cần vội.; hành động: chỉ chuyển nhánh

### accepted — harbour_marshal_liora

Tôi ghi chuyến tàu cần đối chiếu. Đọc danh sách rồi gặp Dorian nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — harbour_marshal_liora

Được. Hồ sơ được giữ ở cảng, cậu không cần vội.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## harbour_manifest

### start — narrator

Bản gốc ghi TOBA không lên tàu. Bản công khai đổi thành không tham gia chuyến cứu hộ.

- Người chơi: Tôi sẽ gặp Dorian và tiếp tục đối chiếu.
  → NPC: Bạn giữ cả bản gốc và dòng đã bị đổi để tiếp tục điều tra.; hành động: chỉ chuyển nhánh
- Người chơi: Hai cách ghi này làm mất thông tin gì?
  → NPC: Bản công khai xóa việc TOBA đã đi cùng đội. Liora không có quyền mở hồ sơ tầng sâu; Battle Tower giữ bản xác nhận cuối. Vòng loại của Dorian là bước tiếp theo để vào đó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Giữ cả hai cách ghi để không đánh đồng chúng.; hành động: chỉ chuyển nhánh

### answer — narrator

Bản công khai xóa việc TOBA đã đi cùng đội. Liora không có quyền mở hồ sơ tầng sâu; Battle Tower giữ bản xác nhận cuối. Vòng loại của Dorian là bước tiếp theo để vào đó.

- Người chơi: Tôi sẽ gặp Dorian và tiếp tục đối chiếu.
  → NPC: Bạn giữ cả bản gốc và dòng đã bị đổi để tiếp tục điều tra.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Giữ cả hai cách ghi để không đánh đồng chúng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ cả bản gốc và dòng đã bị đổi để tiếp tục điều tra.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Giữ cả hai cách ghi để không đánh đồng chúng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## dorian_qualifier

### start — captain_dorian

Liora đã gửi hồ sơ của cậu. Vòng cảng là chặng cuối trước Battle Tower. Đội mưa của tôi sẵn sàng.

- Người chơi: Tôi nhận thử thách. Bắt đầu thôi.
  → NPC: Vậy bắt đầu vòng cảng. Đội mưa của tôi vào sân.; hành động: chỉ chuyển nhánh
- Người chơi: Ông muốn kiểm tra điều gì qua đội mưa?
  → NPC: Cách cậu giữ đội khi thời tiết đổi và không để một Pokémon gánh cả trận. Qua vòng này, cậu được vào nơi giữ bản xác nhận trận cuối.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Chuẩn bị trước; tôi vẫn giữ lượt thử của cậu.; hành động: chỉ chuyển nhánh

### answer — captain_dorian

Cách cậu giữ đội khi thời tiết đổi và không để một Pokémon gánh cả trận. Qua vòng này, cậu được vào nơi giữ bản xác nhận trận cuối.

- Người chơi: Tôi nhận thử thách. Bắt đầu thôi.
  → NPC: Vậy bắt đầu vòng cảng. Đội mưa của tôi vào sân.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Chuẩn bị trước; tôi vẫn giữ lượt thử của cậu.; hành động: chỉ chuyển nhánh

### accepted — captain_dorian

Vậy bắt đầu vòng cảng. Đội mưa của tôi vào sân.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — captain_dorian

Được. Chuẩn bị trước; tôi vẫn giữ lượt thử của cậu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## false_victory

### start — mara_voss

Cậu qua vòng cuối rồi. Chúc mừng. Tôi vừa nhận một xác nhận mới cho hồ sơ của cậu, và có chỗ rất lạ.

- Người chơi: Tôi sẽ mang xác nhận đến Battle Tower.
  → NPC: Được. Báo tôi khi Tower đối chiếu xong; tôi vẫn giữ chữ ký mới ở đây.; hành động: chỉ chuyển nhánh
- Người chơi: Xác nhận đó có vấn đề gì?
  → NPC: Nó mang chữ ký TOBA, đề ngày hôm nay. Người mất tích đang được hệ thống ghi nhận như người duyệt kết quả. Battle Tower phải giải thích được chữ ký này.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Cứ xem lại chữ ký đã. Tôi giữ thêm một bản cho cậu.; hành động: chỉ chuyển nhánh

### answer — mara_voss

Nó mang chữ ký TOBA, đề ngày hôm nay. Người mất tích đang được hệ thống ghi nhận như người duyệt kết quả. Battle Tower phải giải thích được chữ ký này.

- Người chơi: Tôi sẽ mang xác nhận đến Battle Tower.
  → NPC: Được. Báo tôi khi Tower đối chiếu xong; tôi vẫn giữ chữ ký mới ở đây.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Cứ xem lại chữ ký đã. Tôi giữ thêm một bản cho cậu.; hành động: chỉ chuyển nhánh

### accepted — mara_voss

Được. Báo tôi khi Tower đối chiếu xong; tôi vẫn giữ chữ ký mới ở đây.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — mara_voss

Cứ xem lại chữ ký đã. Tôi giữ thêm một bản cho cậu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## tower_entry

### start — battle_tower_receptionist

Hồ sơ vòng loại hợp lệ. Bạn sẽ gặp Rowan, Nyx rồi Orion. Mỗi người giữ một bản xác nhận của hệ thống cũ.

- Người chơi: Tôi sẽ bắt đầu với Rowan.
  → NPC: Tôi ghi bạn vào bài thử của Rowan. Hồ sơ sẽ mở theo kết quả từng trận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi có thể đọc các bản đó ngay không?
  → NPC: Các bản thuộc hồ sơ bài thử. Bạn cần hoàn thành từng trận theo thứ tự để được mở bản kế tiếp. Tôi đã ghi lượt của bạn.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Lượt của bạn vẫn được giữ khi quay lại.; hành động: chỉ chuyển nhánh

### answer — battle_tower_receptionist

Các bản thuộc hồ sơ bài thử. Bạn cần hoàn thành từng trận theo thứ tự để được mở bản kế tiếp. Tôi đã ghi lượt của bạn.

- Người chơi: Tôi sẽ bắt đầu với Rowan.
  → NPC: Tôi ghi bạn vào bài thử của Rowan. Hồ sơ sẽ mở theo kết quả từng trận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Lượt của bạn vẫn được giữ khi quay lại.; hành động: chỉ chuyển nhánh

### accepted — battle_tower_receptionist

Tôi ghi bạn vào bài thử của Rowan. Hồ sơ sẽ mở theo kết quả từng trận.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — battle_tower_receptionist

Được. Lượt của bạn vẫn được giữ khi quay lại.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## tower_rowan

### start — battle_tower_trainer_01

Bản tôi giữ ghi TOBA là người thắng. Bản công khai không có trận đó. Muốn đọc đủ hồ sơ, cậu cần qua bài thử của tôi.

- Người chơi: Tôi sẵn sàng đấu.
  → NPC: Được. Sau trận, cậu nhận bản xác nhận tôi đang giữ.; hành động: chỉ chuyển nhánh
- Người chơi: Bài thử của anh khác vòng trước thế nào?
  → NPC: Tôi xem cách cậu chuẩn bị cho lượt sau. Một nước đi tốt cần để đội còn chỗ xử lý khi đối thủ đổi kế hoạch.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Hồ sơ này chưa đi đâu, cậu cứ chuẩn bị.; hành động: chỉ chuyển nhánh

### answer — battle_tower_trainer_01

Tôi xem cách cậu chuẩn bị cho lượt sau. Một nước đi tốt cần để đội còn chỗ xử lý khi đối thủ đổi kế hoạch.

- Người chơi: Tôi sẵn sàng đấu.
  → NPC: Được. Sau trận, cậu nhận bản xác nhận tôi đang giữ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Hồ sơ này chưa đi đâu, cậu cứ chuẩn bị.; hành động: chỉ chuyển nhánh

### accepted — battle_tower_trainer_01

Được. Sau trận, cậu nhận bản xác nhận tôi đang giữ.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — battle_tower_trainer_01

Được. Hồ sơ này chưa đi đâu, cậu cứ chuẩn bị.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## tower_nyx

### start — battle_tower_trainer_02

Bản của tôi tính đủ điểm cho trận TOBA. League lại ghi trận bị hủy. Tôi sẽ cho cậu đối chiếu sau bài thử nhịp đấu.

- Người chơi: Tôi nhận bài thử của cô.
  → NPC: Vậy bắt đầu. Tôi muốn xem cậu chọn đúng lúc hành động.; hành động: chỉ chuyển nhánh
- Người chơi: Cô muốn tôi chú ý điều gì về nhịp đấu?
  → NPC: Đừng đánh chỉ vì lượt đến. Đội tôi có thể đổi thứ tự hành động; cậu cần nhận ra lúc nào việc chờ lại có lợi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi chưa tính thời gian khi cậu còn chuẩn bị.; hành động: chỉ chuyển nhánh

### answer — battle_tower_trainer_02

Đừng đánh chỉ vì lượt đến. Đội tôi có thể đổi thứ tự hành động; cậu cần nhận ra lúc nào việc chờ lại có lợi.

- Người chơi: Tôi nhận bài thử của cô.
  → NPC: Vậy bắt đầu. Tôi muốn xem cậu chọn đúng lúc hành động.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi chưa tính thời gian khi cậu còn chuẩn bị.; hành động: chỉ chuyển nhánh

### accepted — battle_tower_trainer_02

Vậy bắt đầu. Tôi muốn xem cậu chọn đúng lúc hành động.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — battle_tower_trainer_02

Được. Tôi chưa tính thời gian khi cậu còn chuẩn bị.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## tower_orion

### start — battle_tower_trainer_03

Lệnh Aurelia ký là giữ kín hồ sơ. Trong bản công khai, nó trở thành xóa hồ sơ. Cậu cần phân biệt hai quyết định ấy.

- Người chơi: Tôi sẽ đấu để mở hồ sơ tiếp theo.
  → NPC: Được. Qua trận này, cậu có bản lệnh để hỏi Aurelia trực tiếp.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có biết ai đã đổi lệnh không?
  → NPC: Chưa đủ chứng cứ để chỉ tên. Qua bài thử này, cậu được mang bản lệnh đến hỏi Aurelia trực tiếp.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Cứ giữ câu hỏi đó; nó không mất giá trị vì cậu cần chuẩn bị.; hành động: chỉ chuyển nhánh

### answer — battle_tower_trainer_03

Chưa đủ chứng cứ để chỉ tên. Qua bài thử này, cậu được mang bản lệnh đến hỏi Aurelia trực tiếp.

- Người chơi: Tôi sẽ đấu để mở hồ sơ tiếp theo.
  → NPC: Được. Qua trận này, cậu có bản lệnh để hỏi Aurelia trực tiếp.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Cứ giữ câu hỏi đó; nó không mất giá trị vì cậu cần chuẩn bị.; hành động: chỉ chuyển nhánh

### accepted — battle_tower_trainer_03

Được. Qua trận này, cậu có bản lệnh để hỏi Aurelia trực tiếp.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — battle_tower_trainer_03

Được. Cứ giữ câu hỏi đó; nó không mất giá trị vì cậu cần chuẩn bị.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## league_entry

### start — royal_league_receptionist

Aurelia đã nhận được yêu cầu đối chiếu của bạn. Hồ sơ sẽ được giữ nguyên, kể cả những phần văn phòng này phải giải trình.

- Người chơi: Tôi bắt đầu với Cassian.
  → NPC: Tôi xác nhận lượt của bạn. Cassian là người giữ lời chứng đầu tiên.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi cần gặp những ai trước Aurelia?
  → NPC: Cassian, Seraph và Kael, theo thứ tự. Họ giữ lời chứng riêng về thử nghiệm cũ. Sau ba trận, Champion sẽ tiếp bạn.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Chúng tôi giữ yêu cầu của bạn trong hồ sơ.; hành động: chỉ chuyển nhánh

### answer — royal_league_receptionist

Cassian, Seraph và Kael, theo thứ tự. Họ giữ lời chứng riêng về thử nghiệm cũ. Sau ba trận, Champion sẽ tiếp bạn.

- Người chơi: Tôi bắt đầu với Cassian.
  → NPC: Tôi xác nhận lượt của bạn. Cassian là người giữ lời chứng đầu tiên.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Chúng tôi giữ yêu cầu của bạn trong hồ sơ.; hành động: chỉ chuyển nhánh

### accepted — royal_league_receptionist

Tôi xác nhận lượt của bạn. Cassian là người giữ lời chứng đầu tiên.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — royal_league_receptionist

Được. Chúng tôi giữ yêu cầu của bạn trong hồ sơ.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## elite_cassian

### start — league_elite_01

Tôi có mặt trong nhóm đã lưu kết quả của TOBA. Trận đấu vẫn được giữ, nhưng tên anh ấy biến mất khỏi danh sách.

- Người chơi: Tôi nhận trận đấu của ông.
  → NPC: Được. Sau trận, tôi ghi rõ phần mình biết vào bản lời chứng.; hành động: chỉ chuyển nhánh
- Người chơi: Vì sao kết quả còn mà người tham gia lại bị xóa?
  → NPC: Hệ thống cứu hộ cần kết quả để chạy tiếp. Khi không xác định được TOBA còn sống hay không, chúng tôi đã để việc xóa tên xảy ra. Tôi sẽ đưa phần hồ sơ mình giữ sau trận.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi vẫn phải trả lời câu hỏi đó khi cậu quay lại.; hành động: chỉ chuyển nhánh

### answer — league_elite_01

Hệ thống cứu hộ cần kết quả để chạy tiếp. Khi không xác định được TOBA còn sống hay không, chúng tôi đã để việc xóa tên xảy ra. Tôi sẽ đưa phần hồ sơ mình giữ sau trận.

- Người chơi: Tôi nhận trận đấu của ông.
  → NPC: Được. Sau trận, tôi ghi rõ phần mình biết vào bản lời chứng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi vẫn phải trả lời câu hỏi đó khi cậu quay lại.; hành động: chỉ chuyển nhánh

### accepted — league_elite_01

Được. Sau trận, tôi ghi rõ phần mình biết vào bản lời chứng.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — league_elite_01

Được. Tôi vẫn phải trả lời câu hỏi đó khi cậu quay lại.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## elite_seraph

### start — league_elite_02

Tôi từng viết những văn bản này. Có những câu không sai chữ nào mà vẫn giấu đúng điều người đọc cần biết.

- Người chơi: Tôi sẽ đấu và giữ bản đối chiếu.
  → NPC: Vậy bắt đầu. Tôi sẽ giao bản có cả lệnh giữ kín và lệnh đã đổi.; hành động: chỉ chuyển nhánh
- Người chơi: Cô đã đổi lệnh giữ kín thành lệnh xóa sao?
  → NPC: Văn phòng đã đổi lệnh. Tôi có phần trách nhiệm trong đường chuyển hồ sơ, nhưng chưa thể chứng minh Aurelia không biết. Qua trận này, cậu nhận được bản đối chiếu của tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi chưa coi việc cậu tạm dừng là bỏ câu hỏi.; hành động: chỉ chuyển nhánh

### answer — league_elite_02

Văn phòng đã đổi lệnh. Tôi có phần trách nhiệm trong đường chuyển hồ sơ, nhưng chưa thể chứng minh Aurelia không biết. Qua trận này, cậu nhận được bản đối chiếu của tôi.

- Người chơi: Tôi sẽ đấu và giữ bản đối chiếu.
  → NPC: Vậy bắt đầu. Tôi sẽ giao bản có cả lệnh giữ kín và lệnh đã đổi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi chưa coi việc cậu tạm dừng là bỏ câu hỏi.; hành động: chỉ chuyển nhánh

### accepted — league_elite_02

Vậy bắt đầu. Tôi sẽ giao bản có cả lệnh giữ kín và lệnh đã đổi.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — league_elite_02

Được. Tôi chưa coi việc cậu tạm dừng là bỏ câu hỏi.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## elite_kael

### start — league_elite_03

Tôi rời phòng thử cùng nhóm. TOBA ở lại giữ đội Pokémon sống. Lời kể của những người đã về không đủ để thay anh ấy nói.

- Người chơi: Tôi nhận trận đấu. Sau đó tôi muốn nghe đủ lời chứng.
  → NPC: Được. Tôi sẽ không bỏ phần TOBA ở lại khỏi lời chứng nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Khi rút đi, ông có biết TOBA còn ở trong không?
  → NPC: Có. Vargan phản đối đóng kho; Aurelia chọn giữ kín. Tôi đã kể quá ít về việc mình biết anh ấy ở lại. Sau trận, tôi sẽ ghi lời chứng bằng tên mình.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi giữ lời, dù cậu cần thêm thời gian.; hành động: chỉ chuyển nhánh

### answer — league_elite_03

Có. Vargan phản đối đóng kho; Aurelia chọn giữ kín. Tôi đã kể quá ít về việc mình biết anh ấy ở lại. Sau trận, tôi sẽ ghi lời chứng bằng tên mình.

- Người chơi: Tôi nhận trận đấu. Sau đó tôi muốn nghe đủ lời chứng.
  → NPC: Được. Tôi sẽ không bỏ phần TOBA ở lại khỏi lời chứng nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi giữ lời, dù cậu cần thêm thời gian.; hành động: chỉ chuyển nhánh

### accepted — league_elite_03

Được. Tôi sẽ không bỏ phần TOBA ở lại khỏi lời chứng nữa.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — league_elite_03

Được. Tôi giữ lời, dù cậu cần thêm thời gian.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## champion_aurelia

### start — aurelia

Ta đã nghe từ Hale, Mara và Iris. Cậu mang đến đủ hồ sơ để ta không thể trả lời bằng một lời hẹn nữa.

- Người chơi: Tôi sẵn sàng đấu với bà.
  → NPC: Ta nhận lời thách đấu. Sau trận, cậu sẽ nghe câu trả lời của ta.; hành động: chỉ chuyển nhánh
- Người chơi: Bà có trả lời về TOBA sau trận này không?
  → NPC: Có. Ta sẽ nói phần mình biết và phần mình đã quyết định, bằng tên mình. Trước đó, cậu phải hoàn thành trận Champion.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Cậu có thể chuẩn bị; hồ sơ của cậu không bị đóng lại.; hành động: chỉ chuyển nhánh

### answer — aurelia

Có. Ta sẽ nói phần mình biết và phần mình đã quyết định, bằng tên mình. Trước đó, cậu phải hoàn thành trận Champion.

- Người chơi: Tôi sẵn sàng đấu với bà.
  → NPC: Ta nhận lời thách đấu. Sau trận, cậu sẽ nghe câu trả lời của ta.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Cậu có thể chuẩn bị; hồ sơ của cậu không bị đóng lại.; hành động: chỉ chuyển nhánh

### accepted — aurelia

Ta nhận lời thách đấu. Sau trận, cậu sẽ nghe câu trả lời của ta.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — aurelia

Được. Cậu có thể chuẩn bị; hồ sơ của cậu không bị đóng lại.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## aurelia_aftermath

### start — aurelia

Ta ký giữ kín để ngăn thử nghiệm bị lặp lại. Khi hồ sơ chuyển thành xóa, ta biết. Ta đã để nó tiếp tục.

- Người chơi: Tôi sẽ đến School of Wolf đọc bản gốc.
  → NPC: Ta gửi quyền đọc hồ sơ đến School. Vargan biết cậu sẽ tới.; hành động: chỉ chuyển nhánh
- Người chơi: Vậy Vargan còn giữ điều gì mà bà chưa cho tôi xem?
  → NPC: Bản gốc tại School of Wolf. Ta đã chọn im lặng thay vì thừa nhận người bị bỏ lại. Cậu có quyền đọc bản đó và hỏi Vargan; ta phải công khai phần của mình.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Quyền đọc của cậu vẫn còn, dù cậu chưa đi ngay.; hành động: chỉ chuyển nhánh

### answer — aurelia

Bản gốc tại School of Wolf. Ta đã chọn im lặng thay vì thừa nhận người bị bỏ lại. Cậu có quyền đọc bản đó và hỏi Vargan; ta phải công khai phần của mình.

- Người chơi: Tôi sẽ đến School of Wolf đọc bản gốc.
  → NPC: Ta gửi quyền đọc hồ sơ đến School. Vargan biết cậu sẽ tới.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Quyền đọc của cậu vẫn còn, dù cậu chưa đi ngay.; hành động: chỉ chuyển nhánh

### accepted — aurelia

Ta gửi quyền đọc hồ sơ đến School. Vargan biết cậu sẽ tới.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — aurelia

Được. Quyền đọc của cậu vẫn còn, dù cậu chưa đi ngay.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## wolf_bran

### start — school_wolf_gatekeeper

Giấy giới thiệu hợp lệ. Trước khi vào kho, cậu cần qua các bài thử cứu hộ của School. Ta sẽ chỉ rõ thứ tự.

- Người chơi: Tôi sẽ bắt đầu với Fen.
  → NPC: Ta ghi tên cậu vào bài thử của Fen. Cậu có thể xem lại thứ tự trên điện thoại.; hành động: chỉ chuyển nhánh
- Người chơi: Các bài thử gồm những gì?
  → NPC: Fen kiểm tra kỷ luật, Skoll kiểm tra quan sát đường và Hati kiểm tra chuẩn bị cứu hộ. Phần vật tư được dùng cho trạm, không phải tiền vào cổng.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Ta giữ giấy giới thiệu, cậu quay lại khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — school_wolf_gatekeeper

Fen kiểm tra kỷ luật, Skoll kiểm tra quan sát đường và Hati kiểm tra chuẩn bị cứu hộ. Phần vật tư được dùng cho trạm, không phải tiền vào cổng.

- Người chơi: Tôi sẽ bắt đầu với Fen.
  → NPC: Ta ghi tên cậu vào bài thử của Fen. Cậu có thể xem lại thứ tự trên điện thoại.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Ta giữ giấy giới thiệu, cậu quay lại khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_gatekeeper

Ta ghi tên cậu vào bài thử của Fen. Cậu có thể xem lại thứ tự trên điện thoại.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — school_wolf_gatekeeper

Được. Ta giữ giấy giới thiệu, cậu quay lại khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## wolf_fen

### start — school_wolf_trainer_01

Muốn giữ đội trong một cuộc cứu hộ, cậu phải biết lúc nào nên dừng tấn công. Ta bắt đầu bằng một trận đấu.

- Người chơi: Tôi sẵn sàng nhận bài thử.
  → NPC: Vậy bắt đầu. Ta muốn thấy cậu biết giữ đội, không chỉ tấn công.; hành động: chỉ chuyển nhánh
- Người chơi: Sau trận tôi cần làm gì?
  → NPC: Đọc dấu đường của Skoll trước khi nhận bài thử tiếp. Quan sát ngoài đường và quan sát trong trận là hai phần của cùng việc chuẩn bị.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Hãy chuẩn bị đội; ta chưa bắt đầu khi cậu chưa nhận lời.; hành động: chỉ chuyển nhánh

### answer — school_wolf_trainer_01

Đọc dấu đường của Skoll trước khi nhận bài thử tiếp. Quan sát ngoài đường và quan sát trong trận là hai phần của cùng việc chuẩn bị.

- Người chơi: Tôi sẵn sàng nhận bài thử.
  → NPC: Vậy bắt đầu. Ta muốn thấy cậu biết giữ đội, không chỉ tấn công.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Hãy chuẩn bị đội; ta chưa bắt đầu khi cậu chưa nhận lời.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_trainer_01

Vậy bắt đầu. Ta muốn thấy cậu biết giữ đội, không chỉ tấn công.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — school_wolf_trainer_01

Được. Hãy chuẩn bị đội; ta chưa bắt đầu khi cậu chưa nhận lời.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## wolf_track

### start — narrator

Dấu giày quay trở lại, còn dấu Pokémon đi tiếp. Nhóm cứu hộ đã mất liên lạc tại đây.

- Người chơi: Tôi sẽ mang nhận xét này đến Skoll.
  → NPC: Bạn ghi lại hướng dấu chân quay đầu và phần chưa thể kết luận.; hành động: chỉ chuyển nhánh
- Người chơi: Dấu này có chứng minh người phía trước muốn ở lại không?
  → NPC: Không. Nó chỉ cho thấy nhóm quay đầu khi không còn nhận được trả lời. Skoll muốn cậu phân biệt điều nhìn thấy với điều tự suy ra.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Bạn có thể quan sát dấu đường thêm trước khi trả lời.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Nó chỉ cho thấy nhóm quay đầu khi không còn nhận được trả lời. Skoll muốn cậu phân biệt điều nhìn thấy với điều tự suy ra.

- Người chơi: Tôi sẽ mang nhận xét này đến Skoll.
  → NPC: Bạn ghi lại hướng dấu chân quay đầu và phần chưa thể kết luận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Bạn có thể quan sát dấu đường thêm trước khi trả lời.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn ghi lại hướng dấu chân quay đầu và phần chưa thể kết luận.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Bạn có thể quan sát dấu đường thêm trước khi trả lời.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## wolf_skoll

### start — school_wolf_trainer_02

Cậu đã đọc dấu đường. Giờ tôi muốn xem cậu quan sát đối thủ khi không có thời gian dừng lại.

- Người chơi: Tôi sẵn sàng đấu.
  → NPC: Được. Tôi bắt đầu bài thử quan sát đối thủ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi cần chú ý những dấu hiệu nào?
  → NPC: Những lần đổi Pokémon, đòn đã dùng và Pokémon đối thủ đang muốn giữ lại. Tôi dùng đổi nhịp; cậu không cần đoán, nhưng phải nhớ những gì đã thấy.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Quan sát đội mình trước cũng là một phần chuẩn bị.; hành động: chỉ chuyển nhánh

### answer — school_wolf_trainer_02

Những lần đổi Pokémon, đòn đã dùng và Pokémon đối thủ đang muốn giữ lại. Tôi dùng đổi nhịp; cậu không cần đoán, nhưng phải nhớ những gì đã thấy.

- Người chơi: Tôi sẵn sàng đấu.
  → NPC: Được. Tôi bắt đầu bài thử quan sát đối thủ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Quan sát đội mình trước cũng là một phần chuẩn bị.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_trainer_02

Được. Tôi bắt đầu bài thử quan sát đối thủ.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — school_wolf_trainer_02

Được. Quan sát đội mình trước cũng là một phần chuẩn bị.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## wolf_supply

### start — school_wolf_trainer_03

Trạm cần vật tư cho chuyến cứu hộ kế tiếp. Tôi muốn người nhận bài thử hiểu phần chuẩn bị này trước khi đấu.

- Người chơi: Tôi giao phần vật tư theo danh sách.
  → NPC: Tôi kiểm đếm rồi đưa vật tư vào kho. Phần đã nhận sẽ được ghi lại.; hành động: chỉ chuyển nhánh
- Người chơi: Số vật tư này có được dùng thật không?
  → NPC: Có. Chúng được đưa vào kho cứu hộ, không trả lại sau cuộc nói chuyện. Xem danh sách trên điện thoại và chỉ giao khi đã chuẩn bị đủ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Chưa đủ thì cậu quay lại sau, đừng lấy vật tư của đội khác.; hành động: chỉ chuyển nhánh

### answer — school_wolf_trainer_03

Có. Chúng được đưa vào kho cứu hộ, không trả lại sau cuộc nói chuyện. Xem danh sách trên điện thoại và chỉ giao khi đã chuẩn bị đủ.

- Người chơi: Tôi giao phần vật tư theo danh sách.
  → NPC: Tôi kiểm đếm rồi đưa vật tư vào kho. Phần đã nhận sẽ được ghi lại.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Chưa đủ thì cậu quay lại sau, đừng lấy vật tư của đội khác.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_trainer_03

Tôi kiểm đếm rồi đưa vật tư vào kho. Phần đã nhận sẽ được ghi lại.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — school_wolf_trainer_03

Được. Chưa đủ thì cậu quay lại sau, đừng lấy vật tư của đội khác.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## wolf_hati

### start — school_wolf_trainer_03

Vật tư đã được nhận. Bây giờ là phần giữ đội còn sức khi phải đi nhanh. Cậu muốn bắt đầu bài thử chứ?

- Người chơi: Tôi nhận bài thử. Bắt đầu thôi.
  → NPC: Vậy bắt đầu. Vargan sẽ nhận kết quả sau bài thử này.; hành động: chỉ chuyển nhánh
- Người chơi: Bài thử này khác Skoll thế nào?
  → NPC: Tôi gây áp lực bằng tốc độ. Cậu phải chọn điều cần làm trước, thay vì cố xử lý mọi thứ trong một lượt. Qua đây rồi cậu gặp Vargan.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Vật tư đã được ghi nhận; cậu chưa cần giao lại khi quay lại.; hành động: chỉ chuyển nhánh

### answer — school_wolf_trainer_03

Tôi gây áp lực bằng tốc độ. Cậu phải chọn điều cần làm trước, thay vì cố xử lý mọi thứ trong một lượt. Qua đây rồi cậu gặp Vargan.

- Người chơi: Tôi nhận bài thử. Bắt đầu thôi.
  → NPC: Vậy bắt đầu. Vargan sẽ nhận kết quả sau bài thử này.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Vật tư đã được ghi nhận; cậu chưa cần giao lại khi quay lại.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_trainer_03

Vậy bắt đầu. Vargan sẽ nhận kết quả sau bài thử này.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — school_wolf_trainer_03

Được. Vật tư đã được ghi nhận; cậu chưa cần giao lại khi quay lại.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## wolf_vargan

### start — school_wolf_master

Ta đã xem kết quả ba bài thử. Kho ở sau cửa này. Cậu còn một trận với ta trước khi được vào.

- Người chơi: Tôi sẵn sàng đấu với ông.
  → NPC: Ta nhận lời. Thắng trận này, cậu đọc cả ba bản ghi.; hành động: chỉ chuyển nhánh
- Người chơi: Thắng trận này có cho tôi đọc toàn bộ bản gốc không?
  → NPC: Có. Ba bản ghi được giữ riêng, cậu đọc cả ba. Ta không chọn hộ phần nào cậu được biết.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Cửa vẫn ở đây; chuẩn bị cho đội trước.; hành động: chỉ chuyển nhánh

### answer — school_wolf_master

Có. Ba bản ghi được giữ riêng, cậu đọc cả ba. Ta không chọn hộ phần nào cậu được biết.

- Người chơi: Tôi sẵn sàng đấu với ông.
  → NPC: Ta nhận lời. Thắng trận này, cậu đọc cả ba bản ghi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Cửa vẫn ở đây; chuẩn bị cho đội trước.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_master

Ta nhận lời. Thắng trận này, cậu đọc cả ba bản ghi.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — school_wolf_master

Được. Cửa vẫn ở đây; chuẩn bị cho đội trước.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## archive_record1

### start — narrator

TOBA tự nguyện đưa đội vào phòng thử để cứu Pokémon mắc kẹt. Hồ sơ không có lệnh buộc anh ấy làm việc đó.

- Người chơi: Tôi đọc tiếp bản ghi thứ hai.
  → NPC: Bạn giữ lời hứa của Hale cùng thời điểm mất kết nối trước khi đọc tiếp.; hành động: chỉ chuyển nhánh
- Người chơi: Hale đã cam kết điều gì trước khi TOBA vào?
  → NPC: “Tôi hứa giữ đường về.” Bản ghi dừng ngay sau khi hệ thống mất kết nối. Hai bản còn lại ghi những gì diễn ra sau thời điểm ấy.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Bạn có thể đọc lại lời cam kết trước khi sang bản kế tiếp.; hành động: chỉ chuyển nhánh

### answer — narrator

“Tôi hứa giữ đường về.” Bản ghi dừng ngay sau khi hệ thống mất kết nối. Hai bản còn lại ghi những gì diễn ra sau thời điểm ấy.

- Người chơi: Tôi đọc tiếp bản ghi thứ hai.
  → NPC: Bạn giữ lời hứa của Hale cùng thời điểm mất kết nối trước khi đọc tiếp.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Bạn có thể đọc lại lời cam kết trước khi sang bản kế tiếp.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ lời hứa của Hale cùng thời điểm mất kết nối trước khi đọc tiếp.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Bạn có thể đọc lại lời cam kết trước khi sang bản kế tiếp.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## archive_record2

### start — narrator

Bản này giữ hai thời điểm của trận cuối. Bản công khai chỉ giữ một, giống bất thường từng xuất hiện ở sân tập.

- Người chơi: Tôi sẽ đọc bản lệnh lưu trữ cuối cùng.
  → NPC: Bạn ghi lại các lệnh vẫn được gửi sau lúc nhóm rút.; hành động: chỉ chuyển nhánh
- Người chơi: TOBA còn gửi lệnh sau khi nhóm rút không?
  → NPC: Có, các lệnh cứu hộ vẫn tới. Không ai xác minh anh ấy còn sống; hồ sơ cũng không chứng minh anh ấy đã chết.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Giữ hai thời điểm để đối chiếu khi đọc tiếp.; hành động: chỉ chuyển nhánh

### answer — narrator

Có, các lệnh cứu hộ vẫn tới. Không ai xác minh anh ấy còn sống; hồ sơ cũng không chứng minh anh ấy đã chết.

- Người chơi: Tôi sẽ đọc bản lệnh lưu trữ cuối cùng.
  → NPC: Bạn ghi lại các lệnh vẫn được gửi sau lúc nhóm rút.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Giữ hai thời điểm để đối chiếu khi đọc tiếp.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn ghi lại các lệnh vẫn được gửi sau lúc nhóm rút.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Giữ hai thời điểm để đối chiếu khi đọc tiếp.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## archive_record3

### start — narrator

Aurelia ký giữ kín. Văn phòng chuyển thành xóa. Vargan giữ bản gốc. Hale nhận bản sửa và không lên tiếng.

- Người chơi: Tôi sẽ đưa bản này hỏi Hale.
  → NPC: Bạn giữ phần trách nhiệm riêng của từng người để hỏi Hale.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi cần hỏi Hale về phần nào của bản này?
  → NPC: Vì sao ông ấy nhận bản sửa mà không đòi kiểm tra đường về đã hứa với TOBA. Hồ sơ cho thấy quyết định của từng người, không chỉ một thủ phạm duy nhất.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Bạn có thể đối chiếu lại cả ba bản trước khi hỏi.; hành động: chỉ chuyển nhánh

### answer — narrator

Vì sao ông ấy nhận bản sửa mà không đòi kiểm tra đường về đã hứa với TOBA. Hồ sơ cho thấy quyết định của từng người, không chỉ một thủ phạm duy nhất.

- Người chơi: Tôi sẽ đưa bản này hỏi Hale.
  → NPC: Bạn giữ phần trách nhiệm riêng của từng người để hỏi Hale.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Bạn có thể đối chiếu lại cả ba bản trước khi hỏi.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ phần trách nhiệm riêng của từng người để hỏi Hale.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Bạn có thể đối chiếu lại cả ba bản trước khi hỏi.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## archive_hale

### start — professor_hale

Ta nghĩ chờ thêm một ngày sẽ có câu trả lời chắc chắn. Rồi ta chờ tiếp. Cuối cùng ta sống như thể lời hứa đó không còn ai đòi.

- Người chơi: Tôi sẽ giữ liên lạc và đi cùng nhóm.
  → NPC: Ta sẽ báo cả nhóm. Lần này không để một người phải tự tìm đường về.; hành động: chỉ chuyển nhánh
- Người chơi: Ông có định tìm lại TOBA cùng chúng tôi không?
  → NPC: Có. Ta sẽ gọi Mara, Aurelia và Orin, rồi mở lại đường đối chiếu. Nếu người nhắn là TOBA, ta phải nghe anh ấy, không để cậu nghe một mình.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Ta vẫn chuẩn bị phần của mình; không đẩy cậu đi thay ta nữa.; hành động: chỉ chuyển nhánh

### answer — professor_hale

Có. Ta sẽ gọi Mara, Aurelia và Orin, rồi mở lại đường đối chiếu. Nếu người nhắn là TOBA, ta phải nghe anh ấy, không để cậu nghe một mình.

- Người chơi: Tôi sẽ giữ liên lạc và đi cùng nhóm.
  → NPC: Ta sẽ báo cả nhóm. Lần này không để một người phải tự tìm đường về.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Ta vẫn chuẩn bị phần của mình; không đẩy cậu đi thay ta nữa.; hành động: chỉ chuyển nhánh

### accepted — professor_hale

Ta sẽ báo cả nhóm. Lần này không để một người phải tự tìm đường về.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — professor_hale

Được. Ta vẫn chuẩn bị phần của mình; không đẩy cậu đi thay ta nữa.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## final_unknown

### start — archive_keeper

Người gửi gọi qua máy trực: “Tôi biết cậu đã đọc. Tôi muốn cậu nghe phần còn lại từ tôi.”

- Người chơi: Tôi sẽ đến điểm hẹn và giữ liên lạc.
  → NPC: Bạn xác nhận điểm hẹn và lưu kênh liên lạc với nhóm.; hành động: chỉ chuyển nhánh
- Người chơi: Nhóm đã kiểm tra người gửi chưa?
  → NPC: Orin xác nhận chữ ký khớp. Mara giữ liên lạc, Aurelia đã được báo. Điểm hẹn được lưu trên điện thoại; chưa ai kết luận người ở đó là ai.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Bạn có thể chuẩn bị trước; nhóm chưa mất liên lạc với người gửi.; hành động: chỉ chuyển nhánh

### answer — archive_keeper

Orin xác nhận chữ ký khớp. Mara giữ liên lạc, Aurelia đã được báo. Điểm hẹn được lưu trên điện thoại; chưa ai kết luận người ở đó là ai.

- Người chơi: Tôi sẽ đến điểm hẹn và giữ liên lạc.
  → NPC: Bạn xác nhận điểm hẹn và lưu kênh liên lạc với nhóm.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Bạn có thể chuẩn bị trước; nhóm chưa mất liên lạc với người gửi.; hành động: chỉ chuyển nhánh

### accepted — archive_keeper

Bạn xác nhận điểm hẹn và lưu kênh liên lạc với nhóm.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — archive_keeper

Bạn có thể chuẩn bị trước; nhóm chưa mất liên lạc với người gửi.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## final_confrontation

### start — mysterious

Cậu giữ bản gốc. Tôi đã giúp cậu, giấu cậu vài điều và nhờ cậu mở những cửa tôi không thể tự mở.

- Người chơi: Tôi nhận trận đấu, với bản ghi được giữ nguyên.
  → NPC: Được. Trận này sẽ có bản ghi với cả hai người tham gia.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có phải TOBA không?
  → NPC: Sau trận này tôi sẽ trả lời bằng tên mình. Tôi muốn kết quả có cả hai người tham gia, không thêm một xác nhận bị sửa nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi không sửa bản cậu đang giữ. Chuẩn bị đội rồi quay lại.; hành động: chỉ chuyển nhánh

### answer — mysterious

Sau trận này tôi sẽ trả lời bằng tên mình. Tôi muốn kết quả có cả hai người tham gia, không thêm một xác nhận bị sửa nữa.

- Người chơi: Tôi nhận trận đấu, với bản ghi được giữ nguyên.
  → NPC: Được. Trận này sẽ có bản ghi với cả hai người tham gia.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi không sửa bản cậu đang giữ. Chuẩn bị đội rồi quay lại.; hành động: chỉ chuyển nhánh

### accepted — mysterious

Được. Trận này sẽ có bản ghi với cả hai người tham gia.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — mysterious

Được. Tôi không sửa bản cậu đang giữ. Chuẩn bị đội rồi quay lại.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## final_explanation

### start — mysterious

Tên tôi là TOBA. Đội Pokémon đã đưa tôi qua cửa cứu hộ phụ. Tôi sống, nhưng hệ thống coi mọi yêu cầu trở về của tôi là giả mạo.

- Người chơi: Tôi sẽ đưa lời anh kể về cho nhóm.
  → NPC: Cứ mang lời tôi về và kiểm chứng. Tôi sẽ đứng tên cho phần mình đã làm.; hành động: chỉ chuyển nhánh
- Người chơi: Vì sao anh nhờ cả Rocket mở lại hệ thống?
  → NPC: Tôi giữ bản sao cũ và chèn xác nhận khi cửa mở. Rocket muốn bán quyền truy cập; tôi lợi dụng họ để mở đường. Tôi cũng đã lợi dụng cậu, và không định gọi điều đó là vô can.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Cậu có quyền đối chiếu lời tôi với bản gốc trước khi tin.; hành động: chỉ chuyển nhánh

### answer — mysterious

Tôi giữ bản sao cũ và chèn xác nhận khi cửa mở. Rocket muốn bán quyền truy cập; tôi lợi dụng họ để mở đường. Tôi cũng đã lợi dụng cậu, và không định gọi điều đó là vô can.

- Người chơi: Tôi sẽ đưa lời anh kể về cho nhóm.
  → NPC: Cứ mang lời tôi về và kiểm chứng. Tôi sẽ đứng tên cho phần mình đã làm.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Cậu có quyền đối chiếu lời tôi với bản gốc trước khi tin.; hành động: chỉ chuyển nhánh

### accepted — mysterious

Cứ mang lời tôi về và kiểm chứng. Tôi sẽ đứng tên cho phần mình đã làm.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — mysterious

Được. Cậu có quyền đối chiếu lời tôi với bản gốc trước khi tin.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## final_choice

### start — aurelia

Ta sẽ khôi phục tên TOBA, giữ chứng cứ Rocket và công khai lệnh cũ. Nhưng ta không thể yêu cầu cậu coi như mọi chuyện đã được giải quyết.

- Người chơi: Tôi đồng ý giữ lời chứng trong hồ sơ công khai.
  → NPC: Ta ghi xác nhận của cậu cùng các lời chứng. Những quyết định cũ cũng được giữ lại.; hành động: chỉ chuyển nhánh
- Người chơi: Ai sẽ đứng tên làm chứng và giữ hồ sơ?
  → NPC: Iris làm chứng. Rook giao bản sao, Vargan mở kho cho đội điều tra. Ta đứng tên giải trình lệnh cũ. TOBA không phải tự chứng minh mình tồn tại thêm lần nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Cậu chưa muốn xác nhận thì có thể đọc lại; ta vẫn phải thực hiện phần đã hứa.; hành động: chỉ chuyển nhánh

### answer — aurelia

Iris làm chứng. Rook giao bản sao, Vargan mở kho cho đội điều tra. Ta đứng tên giải trình lệnh cũ. TOBA không phải tự chứng minh mình tồn tại thêm lần nữa.

- Người chơi: Tôi đồng ý giữ lời chứng trong hồ sơ công khai.
  → NPC: Ta ghi xác nhận của cậu cùng các lời chứng. Những quyết định cũ cũng được giữ lại.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Cậu chưa muốn xác nhận thì có thể đọc lại; ta vẫn phải thực hiện phần đã hứa.; hành động: chỉ chuyển nhánh

### accepted — aurelia

Ta ghi xác nhận của cậu cùng các lời chứng. Những quyết định cũ cũng được giữ lại.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — aurelia

Được. Cậu chưa muốn xác nhận thì có thể đọc lại; ta vẫn phải thực hiện phần đã hứa.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## epilogue_hale

### start — professor_hale

Ta nhận được tin TOBA rồi. Anh ấy ký tên đầy đủ. Còn cậu, hôm nay có định về ăn tối không?

- Người chơi: Tôi sẽ về ăn tối. Lần này ông cũng nghỉ nhé.
  → NPC: Ta cất sách đây. Bữa tối này không cần thêm một hồ sơ nào cả.; hành động: chỉ chuyển nhánh
- Người chơi: Mọi người sẽ làm gì sau cuộc điều tra?
  → NPC: Iris cần chỗ làm việc mới. Orin đang đối chiếu báo cáo, Mara thì hẹn tập tiếp. Ta muốn cả nhóm gặp nhau ngoài những lúc có việc khẩn cấp.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Khi nào tiện thì ghé; ta sẽ không biến bữa tối thành một cuộc họp.; hành động: chỉ chuyển nhánh

### answer — professor_hale

Iris cần chỗ làm việc mới. Orin đang đối chiếu báo cáo, Mara thì hẹn tập tiếp. Ta muốn cả nhóm gặp nhau ngoài những lúc có việc khẩn cấp.

- Người chơi: Tôi sẽ về ăn tối. Lần này ông cũng nghỉ nhé.
  → NPC: Ta cất sách đây. Bữa tối này không cần thêm một hồ sơ nào cả.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Khi nào tiện thì ghé; ta sẽ không biến bữa tối thành một cuộc họp.; hành động: chỉ chuyển nhánh

### accepted — professor_hale

Ta cất sách đây. Bữa tối này không cần thêm một hồ sơ nào cả.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — professor_hale

Được. Khi nào tiện thì ghé; ta sẽ không biến bữa tối thành một cuộc họp.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## epilogue_mara

### start — mara_voss

Tôi đã xếp lại đội. Nhưng trận sau để hôm khác. Cậu vừa đi cả một chặng dài rồi.

- Người chơi: Tôi sẽ nhắn cậu để hẹn trận sau.
  → NPC: Được. Nhắn một ngày cụ thể nhé, để tôi không lại đợi cậu cả buổi.; hành động: chỉ chuyển nhánh
- Người chơi: Chúng ta vẫn sẽ đấu lại chứ?
  → NPC: Có. Cậu chọn ngày rồi nhắn tôi. Hôm nay Hale đang đợi bữa tối, tôi cũng không muốn bỏ thêm một cuộc hẹn.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Cứ nghỉ trước, tôi chưa mang đội đi đâu.; hành động: chỉ chuyển nhánh

### answer — mara_voss

Có. Cậu chọn ngày rồi nhắn tôi. Hôm nay Hale đang đợi bữa tối, tôi cũng không muốn bỏ thêm một cuộc hẹn.

- Người chơi: Tôi sẽ nhắn cậu để hẹn trận sau.
  → NPC: Được. Nhắn một ngày cụ thể nhé, để tôi không lại đợi cậu cả buổi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Cứ nghỉ trước, tôi chưa mang đội đi đâu.; hành động: chỉ chuyển nhánh

### accepted — mara_voss

Được. Nhắn một ngày cụ thể nhé, để tôi không lại đợi cậu cả buổi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — mara_voss

Được. Cứ nghỉ trước, tôi chưa mang đội đi đâu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## var_36.1

### start — town4_referee_tuan

Bảo và Minh cùng nhận mình thắng trận vừa rồi. Tôi chưa muốn xử lý chỉ bằng lời kể của người đứng gần mình nhất.

- Người chơi: Tôi sẽ hỏi Bảo rồi Minh.
  → NPC: Cảm ơn. Đừng kết luận chỉ từ người kể đầu tiên.; hành động: chỉ chuyển nhánh
- Người chơi: Hai người đang cãi nhau về điều gì?
  → NPC: Bảo nói mình ra lệnh trước. Minh nói Pokémon đã bị tê liệt từ lượt trước. Tôi cần lời kể của cả hai rồi mới xem camera.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town4_referee_tuan

Bảo nói mình ra lệnh trước. Minh nói Pokémon đã bị tê liệt từ lượt trước. Tôi cần lời kể của cả hai rồi mới xem camera.

- Người chơi: Tôi sẽ hỏi Bảo rồi Minh.
  → NPC: Cảm ơn. Đừng kết luận chỉ từ người kể đầu tiên.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town4_referee_tuan

Cảm ơn. Đừng kết luận chỉ từ người kể đầu tiên.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town4_referee_tuan

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## var_36.2

### start — town1_bao

Tôi nhớ đã ra lệnh trước. Có người nhìn thấy, nhưng Minh bảo tôi đang bỏ qua một lượt cũ.

- Người chơi: Tôi sẽ nghe Minh kể phần cậu ấy nhớ.
  → NPC: Ừ. Đừng bỏ mất phần tôi không nhớ khi kể lại nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu nhớ chuông hết lượt kêu lúc nào không?
  → NPC: Không chắc. Tôi nhớ có người nhìn thấy tôi ra lệnh, nhưng không nhớ tiếng chuông trước hay sau. Cậu hỏi Minh nữa đi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_bao

Không chắc. Tôi nhớ có người nhìn thấy tôi ra lệnh, nhưng không nhớ tiếng chuông trước hay sau. Cậu hỏi Minh nữa đi.

- Người chơi: Tôi sẽ nghe Minh kể phần cậu ấy nhớ.
  → NPC: Ừ. Đừng bỏ mất phần tôi không nhớ khi kể lại nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_bao

Ừ. Đừng bỏ mất phần tôi không nhớ khi kể lại nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_bao

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## var_36.3

### start — town1_minh

Bảo chỉ kể đòn cuối. Tôi nghĩ phải xem cả lúc Pokémon bị tê liệt trước đó.

- Người chơi: Tôi sẽ xem đoạn camera trước đòn cuối.
  → NPC: Được. Nó cho biết thứ tự rõ hơn trí nhớ của chúng tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Trạng thái tê liệt xuất hiện từ lượt nào?
  → NPC: Từ lượt trước. Bảo chỉ nhớ lệnh cuối. Camera ở sân còn ghi lại; cậu xem cả hai lượt giúp tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_minh

Từ lượt trước. Bảo chỉ nhớ lệnh cuối. Camera ở sân còn ghi lại; cậu xem cả hai lượt giúp tôi.

- Người chơi: Tôi sẽ xem đoạn camera trước đòn cuối.
  → NPC: Được. Nó cho biết thứ tự rõ hơn trí nhớ của chúng tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_minh

Được. Nó cho biết thứ tự rõ hơn trí nhớ của chúng tôi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_minh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## var_36.4

### start — narrator

Camera còn đủ đoạn của hai lượt cuối. Bạn có thể đối chiếu thứ tự hành động trên các góc quay.

- Người chơi: Tôi sẽ đưa đoạn này cho Tuấn.
  → NPC: Bạn giữ đoạn có cả lượt gây tê liệt và lượt kết thúc.; hành động: chỉ chuyển nhánh
- Người chơi: Các góc quay có mâu thuẫn nhau không?
  → NPC: Không. Thunder Wave đã có hiệu lực từ lượt trước. Bảo quên trạng thái, nhưng không thấy dấu hiệu ai sửa kết quả.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Thunder Wave đã có hiệu lực từ lượt trước. Bảo quên trạng thái, nhưng không thấy dấu hiệu ai sửa kết quả.

- Người chơi: Tôi sẽ đưa đoạn này cho Tuấn.
  → NPC: Bạn giữ đoạn có cả lượt gây tê liệt và lượt kết thúc.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ đoạn có cả lượt gây tê liệt và lượt kết thúc.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## var_36.5

### start — town4_referee_tuan

Tôi đã xem đoạn camera. Kết quả được giữ, còn hai người đã nhận ra phần mình nhớ thiếu.

- Người chơi: Vậy tôi báo hai người là đã đối chiếu xong.
  → NPC: Cảm ơn. Lần sau tôi sẽ xem đủ lượt trước khi nghe người ở sân cãi.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có xử thua Bảo thêm lần nữa không?
  → NPC: Không. Kết quả cũ được giữ, hai người đã đồng ý đấu lại sau khi hiểu chỗ nhầm. Tôi chỉ cần nói rõ lý do.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town4_referee_tuan

Không. Kết quả cũ được giữ, hai người đã đồng ý đấu lại sau khi hiểu chỗ nhầm. Tôi chỉ cần nói rõ lý do.

- Người chơi: Vậy tôi báo hai người là đã đối chiếu xong.
  → NPC: Cảm ơn. Lần sau tôi sẽ xem đủ lượt trước khi nghe người ở sân cãi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town4_referee_tuan

Cảm ơn. Lần sau tôi sẽ xem đủ lượt trước khi nghe người ở sân cãi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town4_referee_tuan

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## other_peoples_child.1

### start — town1_lan

Bảo ít kể chuyện sân tập với cô dạo này. Cô hỏi càng nhiều, nó lại càng im. Cháu nói chuyện với nó giúp cô được không?

- Người chơi: Tôi sẽ hỏi Bảo về chuyện của cậu ấy.
  → NPC: Cảm ơn cháu. Cô sẽ nghe, không chen thêm chuyện người khác.; hành động: chỉ chuyển nhánh
- Người chơi: Cô muốn tôi giúp Bảo thế nào?
  → NPC: Nói chuyện với nó giúp cô. Cô cứ hỏi huy hiệu, nó lại càng ít kể trận của mình. Cô chưa biết bắt đầu lại từ đâu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_lan

Nói chuyện với nó giúp cô. Cô cứ hỏi huy hiệu, nó lại càng ít kể trận của mình. Cô chưa biết bắt đầu lại từ đâu.

- Người chơi: Tôi sẽ hỏi Bảo về chuyện của cậu ấy.
  → NPC: Cảm ơn cháu. Cô sẽ nghe, không chen thêm chuyện người khác.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_lan

Cảm ơn cháu. Cô sẽ nghe, không chen thêm chuyện người khác.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_lan

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## other_peoples_child.2

### start — town1_bao

Mỗi lần đấu tôi lại nghĩ mẹ sẽ nói gì nếu thua. Đến lúc nhìn đối thủ thì đã qua mất lượt cần quyết định.

- Người chơi: Tôi sẽ qua Mira rồi cùng cậu đấu tập.
  → NPC: Được. Tôi muốn một trận thật, không cần cậu cố nhường.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu có muốn đấu tập mà không phải chứng minh với mẹ không?
  → NPC: Có. Tôi muốn nhìn được đối thủ thay vì chỉ nghĩ mẹ sẽ nói gì nếu thua. Mira đang giúp tôi chăm đội.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_bao

Có. Tôi muốn nhìn được đối thủ thay vì chỉ nghĩ mẹ sẽ nói gì nếu thua. Mira đang giúp tôi chăm đội.

- Người chơi: Tôi sẽ qua Mira rồi cùng cậu đấu tập.
  → NPC: Được. Tôi muốn một trận thật, không cần cậu cố nhường.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_bao

Được. Tôi muốn một trận thật, không cần cậu cố nhường.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_bao

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## other_peoples_child.3

### start — daycare_mira

Bảo đang ở đây chuẩn bị đội. Mình muốn buổi tập này giúp em ấy, chứ không thành thêm một lần phải chứng minh với mẹ.

- Người chơi: Tôi giao ba quả táo cho buổi tập.
  → NPC: Cảm ơn. Mình chuẩn bị phần ăn, rồi bạn gặp Bảo nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Bảo cần một trận tập kiểu nào?
  → NPC: Một trận công bằng. Bạn không cần nhường thắng; để Bảo biết mình làm tốt và sai ở đâu. Mình cần ba quả táo cho phần ăn của đội trước buổi tập.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — daycare_mira

Một trận công bằng. Bạn không cần nhường thắng; để Bảo biết mình làm tốt và sai ở đâu. Mình cần ba quả táo cho phần ăn của đội trước buổi tập.

- Người chơi: Tôi giao ba quả táo cho buổi tập.
  → NPC: Cảm ơn. Mình chuẩn bị phần ăn, rồi bạn gặp Bảo nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — daycare_mira

Cảm ơn. Mình chuẩn bị phần ăn, rồi bạn gặp Bảo nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — daycare_mira

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## other_peoples_child.4

### start — town1_bao

Tôi đã chăm đội xong. Lần này tôi muốn thử tập trung vào trận của mình.

- Người chơi: Vậy chúng ta bắt đầu trận tập.
  → NPC: Được. Tôi nhận trận này cho mình.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu vẫn muốn tôi đánh hết sức chứ?
  → NPC: Ừ. Tôi muốn biết mình sửa được gì. Lần này tôi sẽ nhìn trạng thái của đội, không nhìn xem mẹ có đứng gần không.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_bao

Ừ. Tôi muốn biết mình sửa được gì. Lần này tôi sẽ nhìn trạng thái của đội, không nhìn xem mẹ có đứng gần không.

- Người chơi: Vậy chúng ta bắt đầu trận tập.
  → NPC: Được. Tôi nhận trận này cho mình.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_bao

Được. Tôi nhận trận này cho mình.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — town1_bao

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## other_peoples_child.5

### start — town1_lan

Bảo đã kể cô nghe trận vừa rồi. Nó còn nói một câu mà trước đây cô thường ngắt lời.

- Người chơi: Tôi mừng vì cô và Bảo đã nói chuyện.
  → NPC: Cảm ơn cháu. Cô để nó tự kể nốt, không biến chuyện này thành một cuộc so sánh nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Cô đã nghe Bảo kể trận vừa rồi chưa?
  → NPC: Rồi. Nó bảo cô hỏi chuyện của nó, đừng hỏi thành tích của người khác. Cô nghe rồi; lần này cô sẽ hỏi nó chọn nước đi thế nào.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_lan

Rồi. Nó bảo cô hỏi chuyện của nó, đừng hỏi thành tích của người khác. Cô nghe rồi; lần này cô sẽ hỏi nó chọn nước đi thế nào.

- Người chơi: Tôi mừng vì cô và Bảo đã nói chuyện.
  → NPC: Cảm ơn cháu. Cô để nó tự kể nốt, không biến chuyện này thành một cuộc so sánh nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_lan

Cảm ơn cháu. Cô để nó tự kể nốt, không biến chuyện này thành một cuộc so sánh nữa.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_lan

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## old_man_worries.1

### start — town1_uncle_phuc

Biển ở khúc rẽ lại đổ rồi. Bác muốn có người kiểm tra trước chuyến giao hàng tiếp theo.

- Người chơi: Tôi sẽ kiểm tra biển ở khúc rẽ.
  → NPC: Ừ. Biết đường trơn vẫn tốt hơn tới nơi rồi mới biết.; hành động: chỉ chuyển nhánh
- Người chơi: Bác đang lo ở đoạn đường nào?
  → NPC: Khúc rẽ có cầu trơn sau mưa. Biển bị đổ, nên người mới tới không thấy cảnh báo. Cháu kiểm tra xem còn ai đi lối đó nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_uncle_phuc

Khúc rẽ có cầu trơn sau mưa. Biển bị đổ, nên người mới tới không thấy cảnh báo. Cháu kiểm tra xem còn ai đi lối đó nhé.

- Người chơi: Tôi sẽ kiểm tra biển ở khúc rẽ.
  → NPC: Ừ. Biết đường trơn vẫn tốt hơn tới nơi rồi mới biết.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_uncle_phuc

Ừ. Biết đường trơn vẫn tốt hơn tới nơi rồi mới biết.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_uncle_phuc

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## old_man_worries.2

### start — narrator

Biển nằm úp ở lề đường. Phía cầu còn dấu giày mới qua sau trận mưa.

- Người chơi: Tôi sẽ hỏi Nam về chuyến sáng nay.
  → NPC: Bạn ghi lại vị trí biển đổ và hướng dấu giày.; hành động: chỉ chuyển nhánh
- Người chơi: Có người đi qua sau khi biển đổ không?
  → NPC: Dấu giày mới vẫn đi thẳng về phía cầu. Nam giao hàng sáng nay; cần hỏi anh ấy trước khi cho rằng mọi người đã biết đường nguy hiểm.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Dấu giày mới vẫn đi thẳng về phía cầu. Nam giao hàng sáng nay; cần hỏi anh ấy trước khi cho rằng mọi người đã biết đường nguy hiểm.

- Người chơi: Tôi sẽ hỏi Nam về chuyến sáng nay.
  → NPC: Bạn ghi lại vị trí biển đổ và hướng dấu giày.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn ghi lại vị trí biển đổ và hướng dấu giày.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## old_man_worries.3

### start — town3_courier_nam

Bác Phúc bảo cậu hỏi chuyện cầu à? Tôi có đi qua sáng nay, cùng một kiện hàng khá nặng.

- Người chơi: Tôi sẽ mang hai tấm ván cho Bình.
  → NPC: Cảm ơn. Lần này tôi không sửa biển bằng mặt sau hóa đơn nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có bị thương khi đi qua cầu không?
  → NPC: Không nặng. Tôi và kiện hàng cùng trượt một đoạn. Biển bằng bìa tôi dựng tạm đã ướt; Bình cần hai tấm ván để sửa cho chắc.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_courier_nam

Không nặng. Tôi và kiện hàng cùng trượt một đoạn. Biển bằng bìa tôi dựng tạm đã ướt; Bình cần hai tấm ván để sửa cho chắc.

- Người chơi: Tôi sẽ mang hai tấm ván cho Bình.
  → NPC: Cảm ơn. Lần này tôi không sửa biển bằng mặt sau hóa đơn nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_courier_nam

Cảm ơn. Lần này tôi không sửa biển bằng mặt sau hóa đơn nữa.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_courier_nam

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## old_man_worries.4

### start — town8_dockworker_binh

Tôi giữ phần chân biển ở đây. Cần gỗ thật để nó chịu được mưa, không dựng tạm bằng bìa nữa.

- Người chơi: Tôi giao hai tấm ván cho anh.
  → NPC: Cảm ơn. Cháu về báo bác Phúc là biển đã được sửa nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Hai tấm ván này đủ để dựng lại biển chứ?
  → NPC: Đủ. Bác Phúc nhờ từ tuần trước, nhưng tôi chưa có gỗ. Tôi sẽ đặt biển trước đoạn trơn để người ta kịp dừng.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town8_dockworker_binh

Đủ. Bác Phúc nhờ từ tuần trước, nhưng tôi chưa có gỗ. Tôi sẽ đặt biển trước đoạn trơn để người ta kịp dừng.

- Người chơi: Tôi giao hai tấm ván cho anh.
  → NPC: Cảm ơn. Cháu về báo bác Phúc là biển đã được sửa nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town8_dockworker_binh

Cảm ơn. Cháu về báo bác Phúc là biển đã được sửa nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town8_dockworker_binh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## old_man_worries.5

### start — town1_uncle_phuc

Bình báo biển đã đứng lại rồi. Cảm ơn cháu quay về nói cho bác biết.

- Người chơi: Tôi hiểu vì sao bác nhắc chuyện này.
  → NPC: Ừ. Cảm ơn cháu đã làm việc bác nhờ, thay vì chỉ bảo bác đừng lo.; hành động: chỉ chuyển nhánh
- Người chơi: Biển đã được sửa. Bác còn muốn báo ai không?
  → NPC: Báo người đi đường là được. Bác cứ nhắc vì muốn người ta trở về, không phải vì nghĩ ai cũng không biết đi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_uncle_phuc

Báo người đi đường là được. Bác cứ nhắc vì muốn người ta trở về, không phải vì nghĩ ai cũng không biết đi.

- Người chơi: Tôi hiểu vì sao bác nhắc chuyện này.
  → NPC: Ừ. Cảm ơn cháu đã làm việc bác nhờ, thay vì chỉ bảo bác đừng lo.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_uncle_phuc

Ừ. Cảm ơn cháu đã làm việc bác nhờ, thay vì chỉ bảo bác đừng lo.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_uncle_phuc

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## short_explanation.1

### start — dr_orin

Tôi muốn kiểm tra cách mình giải thích bản ghi trận đấu. Linh đã nghe, nhưng tôi chưa biết em ấy hiểu phần nào.

- Người chơi: Tôi sẽ nghe Linh giải thích trước.
  → NPC: Được. Ghi đúng điều em ấy nói, kể cả khi câu đó không đúng.; hành động: chỉ chuyển nhánh
- Người chơi: Ông muốn tôi hỏi Linh điều gì?
  → NPC: Hỏi em ấy hiểu bản ghi trận đấu là gì. Đừng gợi ý; tôi cần biết phần nào trong cách giải thích của mình chưa rõ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.; hành động: chỉ chuyển nhánh

### answer — dr_orin

Hỏi em ấy hiểu bản ghi trận đấu là gì. Đừng gợi ý; tôi cần biết phần nào trong cách giải thích của mình chưa rõ.

- Người chơi: Tôi sẽ nghe Linh giải thích trước.
  → NPC: Được. Ghi đúng điều em ấy nói, kể cả khi câu đó không đúng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.; hành động: chỉ chuyển nhánh

### accepted — dr_orin

Được. Ghi đúng điều em ấy nói, kể cả khi câu đó không đúng.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — dr_orin

Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## short_explanation.2

### start — town2_student_linh

Orin vừa giảng về bản ghi. Tôi nhớ các từ ông ấy nói, nhưng chưa chắc mình dùng chúng đúng.

- Người chơi: Tôi sẽ kiểm tra mẫu ở bàn học.
  → NPC: Ừ. Có ví dụ chắc dễ hiểu hơn câu tôi học thuộc.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu nghĩ bản ghi dự đoán được nước đi tiếp theo à?
  → NPC: Tôi tưởng máy biết hết nên sẽ biết cả đáp án. Orin nói nó chỉ ghi điều đã xảy ra. Tôi muốn xem một bản thật.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_student_linh

Tôi tưởng máy biết hết nên sẽ biết cả đáp án. Orin nói nó chỉ ghi điều đã xảy ra. Tôi muốn xem một bản thật.

- Người chơi: Tôi sẽ kiểm tra mẫu ở bàn học.
  → NPC: Ừ. Có ví dụ chắc dễ hiểu hơn câu tôi học thuộc.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_student_linh

Ừ. Có ví dụ chắc dễ hiểu hơn câu tôi học thuộc.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_student_linh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## short_explanation.3

### start — narrator

Bàn học có một mẫu ghi lệnh và thời điểm nhận. Không có thêm lời giải thích bên cạnh.

- Người chơi: Tôi sẽ dùng mẫu này giải thích với bác Phúc.
  → NPC: Bạn giữ phần lệnh và thời điểm làm ví dụ, không thêm điều bản ghi không có.; hành động: chỉ chuyển nhánh
- Người chơi: Mẫu này ghi được ý định của người chơi không?
  → NPC: Không. Nó ghi lệnh đã gửi và lúc nhận lệnh. Muốn biết vì sao người chơi chọn lệnh đó vẫn phải hỏi người ấy.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Nó ghi lệnh đã gửi và lúc nhận lệnh. Muốn biết vì sao người chơi chọn lệnh đó vẫn phải hỏi người ấy.

- Người chơi: Tôi sẽ dùng mẫu này giải thích với bác Phúc.
  → NPC: Bạn giữ phần lệnh và thời điểm làm ví dụ, không thêm điều bản ghi không có.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ phần lệnh và thời điểm làm ví dụ, không thêm điều bản ghi không có.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## short_explanation.4

### start — town1_uncle_phuc

Cháu mang ví dụ của Orin tới à? Đưa bác xem, bác muốn biết trong đó ghi cái gì trước khi nghe tên máy.

- Người chơi: Tôi sẽ kể cách ví dụ này cho Orin.
  → NPC: Ừ. Nếu giúp ông ấy nói ngắn hơn thì cháu làm được việc khó đấy.; hành động: chỉ chuyển nhánh
- Người chơi: Bác hiểu bản ghi này giống thứ gì?
  → NPC: Giống sổ chợ. Ghi sai tên người mua thì hỏi người viết và so hóa đơn, không nghĩ cái sổ tự chọn người mua.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_uncle_phuc

Giống sổ chợ. Ghi sai tên người mua thì hỏi người viết và so hóa đơn, không nghĩ cái sổ tự chọn người mua.

- Người chơi: Tôi sẽ kể cách ví dụ này cho Orin.
  → NPC: Ừ. Nếu giúp ông ấy nói ngắn hơn thì cháu làm được việc khó đấy.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_uncle_phuc

Ừ. Nếu giúp ông ấy nói ngắn hơn thì cháu làm được việc khó đấy.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_uncle_phuc

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## short_explanation.5

### start — dr_orin

Linh và bác Phúc hiểu ví dụ rõ hơn bản giải thích đầu của tôi. Tôi cần sửa thứ tự mình nói.

- Người chơi: Tôi nghĩ câu này rõ hơn bản đầu.
  → NPC: Được. Tôi sẽ dùng ví dụ trước khi thêm thuật ngữ.; hành động: chỉ chuyển nhánh
- Người chơi: Ông có thể nói lại bằng một câu không?
  → NPC: Máy ghi điều đã xảy ra; nó không quyết định điều nên xảy ra. Ví dụ cái sổ của bác Phúc đủ để giải thích phần đó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.; hành động: chỉ chuyển nhánh

### answer — dr_orin

Máy ghi điều đã xảy ra; nó không quyết định điều nên xảy ra. Ví dụ cái sổ của bác Phúc đủ để giải thích phần đó.

- Người chơi: Tôi nghĩ câu này rõ hơn bản đầu.
  → NPC: Được. Tôi sẽ dùng ví dụ trước khi thêm thuật ngữ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.; hành động: chỉ chuyển nhánh

### accepted — dr_orin

Được. Tôi sẽ dùng ví dụ trước khi thêm thuật ngữ.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — dr_orin

Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## shady_business.1

### start — town3_shady_trader

Hàng cổ hiếm, hộp còn niêm phong. Giá thử mười BeastCoin. Cậu muốn nghe thêm hay xem lời của người đã mua?

- Người chơi: Tôi sẽ hỏi Mai trước, chưa mua hộp.
  → NPC: Được. Cậu chưa trả tiền thì tôi chưa giao hộp.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có cho xem thứ trong hộp trước khi bán không?
  → NPC: Không. Tôi bán gói thử mười BeastCoin. Nhưng nếu cậu nghi ngờ, Mai ở gần đây đã mua một hộp; cứ hỏi cô ấy.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi muốn trả 10 BeastCoin để xem hộp.
  → NPC: Được, cậu đã trả tiền. Đây là hộp. Nếu không đúng lời quảng cáo, cậu có thể đối chiếu hóa đơn với Mai.; hành động: scam
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_shady_trader

Không. Tôi bán gói thử mười BeastCoin. Nhưng nếu cậu nghi ngờ, Mai ở gần đây đã mua một hộp; cứ hỏi cô ấy.

- Người chơi: Tôi sẽ hỏi Mai trước, chưa mua hộp.
  → NPC: Được. Cậu chưa trả tiền thì tôi chưa giao hộp.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_shady_trader

Được. Cậu chưa trả tiền thì tôi chưa giao hộp.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_shady_trader

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

### paid — town3_shady_trader

Được, cậu đã trả tiền. Đây là hộp. Nếu không đúng lời quảng cáo, cậu có thể đối chiếu hóa đơn với Mai.

- Người chơi: Tôi sẽ hỏi Mai và giữ hóa đơn này.
  → NPC: Được. Cậu chưa trả tiền thì tôi chưa giao hộp.; hành động: chỉ chuyển nhánh

## shady_business.2

### start — town3_victim_mai

Tôi có mua một hộp của Đạt. Tôi giữ cả thứ bên trong và hóa đơn, không chỉ lời hắn quảng cáo.

- Người chơi: Tôi sẽ hỏi Nam và đối chiếu lô hàng.
  → NPC: Cảm ơn. Tôi muốn lấy lại tiền, nhưng trước hết cần chứng minh hắn biết mình bán gì.; hành động: chỉ chuyển nhánh
- Người chơi: Trong hộp cô nhận có gì?
  → NPC: Một bụi cây khô. Hóa đơn ngoài ghi vật phẩm cổ. Tôi giữ giấy đây; Nam nhận hàng từ kho của Đạt.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_victim_mai

Một bụi cây khô. Hóa đơn ngoài ghi vật phẩm cổ. Tôi giữ giấy đây; Nam nhận hàng từ kho của Đạt.

- Người chơi: Tôi sẽ hỏi Nam và đối chiếu lô hàng.
  → NPC: Cảm ơn. Tôi muốn lấy lại tiền, nhưng trước hết cần chứng minh hắn biết mình bán gì.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_victim_mai

Cảm ơn. Tôi muốn lấy lại tiền, nhưng trước hết cần chứng minh hắn biết mình bán gì.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_victim_mai

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## shady_business.3

### start — town3_courier_nam

Tôi nhận hàng cho Đạt từ kho này. Tên đại lý trên phiếu trông khác, nhưng chữ ký thì quen.

- Người chơi: Tôi sẽ kiểm tra nhãn trong kho.
  → NPC: Được. Đối chiếu cả tên người đặt, đừng chỉ xem chữ ngoài hộp.; hành động: chỉ chuyển nhánh
- Người chơi: Tên đại lý trên hóa đơn có phải một người khác không?
  → NPC: Không. Vẫn là Đạt, chỉ thêm chữ đại lý. Kho còn những hộp chưa bán; nhãn trong đó cho biết hắn đặt thứ gì.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_courier_nam

Không. Vẫn là Đạt, chỉ thêm chữ đại lý. Kho còn những hộp chưa bán; nhãn trong đó cho biết hắn đặt thứ gì.

- Người chơi: Tôi sẽ kiểm tra nhãn trong kho.
  → NPC: Được. Đối chiếu cả tên người đặt, đừng chỉ xem chữ ngoài hộp.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_courier_nam

Được. Đối chiếu cả tên người đặt, đừng chỉ xem chữ ngoài hộp.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_courier_nam

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## shady_business.4

### start — narrator

Trong kho còn một hộp chưa bán. Nhãn bên trong và hóa đơn ngoài ghi hai tên món khác nhau.

- Người chơi: Tôi sẽ mang hai bản hỏi Đạt.
  → NPC: Bạn giữ cả nhãn kho và hóa đơn bán để chứng minh việc đổi tên hàng.; hành động: chỉ chuyển nhánh
- Người chơi: Hai nhãn này có cùng người ký không?
  → NPC: Có. Nhãn kho ghi bụi cây trang trí, hóa đơn bán ghi vật phẩm Pokémon cổ. Cùng một chữ ký trên hai nội dung khác nhau.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Có. Nhãn kho ghi bụi cây trang trí, hóa đơn bán ghi vật phẩm Pokémon cổ. Cùng một chữ ký trên hai nội dung khác nhau.

- Người chơi: Tôi sẽ mang hai bản hỏi Đạt.
  → NPC: Bạn giữ cả nhãn kho và hóa đơn bán để chứng minh việc đổi tên hàng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ cả nhãn kho và hóa đơn bán để chứng minh việc đổi tên hàng.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## shady_business.5

### start — town3_shady_trader

Cậu có đủ hai nhãn rồi. Tôi không định bảo đó là một hiểu nhầm về màu hộp nữa.

- Người chơi: Tôi nhận trận đấu và giữ anh với lời hứa đó.
  → NPC: Được. Thắng trận này rồi lấy đủ giấy tờ, không chỉ hộp hàng.; hành động: chỉ chuyển nhánh
- Người chơi: Anh sẽ hoàn tiền cho những người đã mua chứ?
  → NPC: Nếu cậu thắng, tôi giao hóa đơn và hoàn khoản đã thu. Tôi không còn định gọi đống cây đó là vật phẩm cổ nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_shady_trader

Nếu cậu thắng, tôi giao hóa đơn và hoàn khoản đã thu. Tôi không còn định gọi đống cây đó là vật phẩm cổ nữa.

- Người chơi: Tôi nhận trận đấu và giữ anh với lời hứa đó.
  → NPC: Được. Thắng trận này rồi lấy đủ giấy tờ, không chỉ hộp hàng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_shady_trader

Được. Thắng trận này rồi lấy đủ giấy tờ, không chỉ hộp hàng.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — town3_shady_trader

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## shady_business.6

### start — town3_victim_mai

Đạt đã giao giấy hoàn tiền. Tôi muốn kiểm tra phần nào trả lại khoản đã thu, phần nào là công điều tra.

- Người chơi: Vậy tôi xác nhận chuyện lô hàng đã được làm rõ.
  → NPC: Cảm ơn. Lần sau tôi hỏi xem hàng trước khi nghe lời quảng cáo.; hành động: chỉ chuyển nhánh
- Người chơi: Cô đã nhận lại khoản bị thu chưa?
  → NPC: Rồi. Tôi giữ hóa đơn hoàn tiền nữa. Nếu bạn có trả mười BeastCoin, khoản đó được hoàn riêng, không trừ vào tiền công điều tra.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_victim_mai

Rồi. Tôi giữ hóa đơn hoàn tiền nữa. Nếu bạn có trả mười BeastCoin, khoản đó được hoàn riêng, không trừ vào tiền công điều tra.

- Người chơi: Vậy tôi xác nhận chuyện lô hàng đã được làm rõ.
  → NPC: Cảm ơn. Lần sau tôi hỏi xem hàng trước khi nghe lời quảng cáo.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_victim_mai

Cảm ơn. Lần sau tôi hỏi xem hàng trước khi nghe lời quảng cáo.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_victim_mai

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## delivery_hell.1

### start — town3_courier_nam

Tôi có kiện hàng ghi người nhận là cô Mai. Phiếu không ghi địa chỉ đầy đủ, nên chưa dám giao cho người đầu tiên tên Mai.

- Người chơi: Tôi sẽ hỏi Mai ở phố xem cô ấy có đặt hàng không.
  → NPC: Cảm ơn. Đừng giao bừa để cô ấy phải tìm người trả lại hộ.; hành động: chỉ chuyển nhánh
- Người chơi: Người nhận nào tên Mai trên phiếu này?
  → NPC: Phiếu chỉ ghi cô Mai. Mai ở phố, tiến sĩ Mai ở cảng... tôi đã hỏi người gửi nhưng chưa có trả lời. Cậu hỏi Mai ở phố trước giúp tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_courier_nam

Phiếu chỉ ghi cô Mai. Mai ở phố, tiến sĩ Mai ở cảng... tôi đã hỏi người gửi nhưng chưa có trả lời. Cậu hỏi Mai ở phố trước giúp tôi.

- Người chơi: Tôi sẽ hỏi Mai ở phố xem cô ấy có đặt hàng không.
  → NPC: Cảm ơn. Đừng giao bừa để cô ấy phải tìm người trả lại hộ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_courier_nam

Cảm ơn. Đừng giao bừa để cô ấy phải tìm người trả lại hộ.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_courier_nam

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## delivery_hell.2

### start — town3_victim_mai

Kiện hàng cảm biến à? Tôi chưa từng đặt món đó. Bạn cho tôi nhìn phần nhãn bên trong nhé.

- Người chơi: Tôi sẽ mang thông tin đến tiến sĩ Mai.
  → NPC: Được. Bạn nhớ dùng tên đơn vị, không chỉ tên Mai nữa nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Cô có đặt kiện hàng cảm biến này không?
  → NPC: Không. Nhãn bên trong ghi phòng nghiên cứu thời tiết. Bạn cần gặp tiến sĩ Mai ở cảng, chứ không phải tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_victim_mai

Không. Nhãn bên trong ghi phòng nghiên cứu thời tiết. Bạn cần gặp tiến sĩ Mai ở cảng, chứ không phải tôi.

- Người chơi: Tôi sẽ mang thông tin đến tiến sĩ Mai.
  → NPC: Được. Bạn nhớ dùng tên đơn vị, không chỉ tên Mai nữa nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_victim_mai

Được. Bạn nhớ dùng tên đơn vị, không chỉ tên Mai nữa nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_victim_mai

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## delivery_hell.3

### start — weather_researcher_mai

Tôi đã đợi cảm biến mấy ngày. Chiếc hộp tới rồi, nhưng thiết bị không ở trong nó.

- Người chơi: Tôi sẽ kiểm tra chỗ Wingull tha lớp bọc.
  → NPC: Cảm ơn. Nếu còn thiết bị, đừng kéo dây buộc trước khi xem nó mắc ở đâu.; hành động: chỉ chuyển nhánh
- Người chơi: Cô nhận được cảm biến hay chỉ chiếc hộp?
  → NPC: Chỉ hộp rỗng. Bình thấy Wingull mang lớp bọc ra bến. Có thể cảm biến rơi ở chỗ nó làm tổ; xem trước khi báo mất.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — weather_researcher_mai

Chỉ hộp rỗng. Bình thấy Wingull mang lớp bọc ra bến. Có thể cảm biến rơi ở chỗ nó làm tổ; xem trước khi báo mất.

- Người chơi: Tôi sẽ kiểm tra chỗ Wingull tha lớp bọc.
  → NPC: Cảm ơn. Nếu còn thiết bị, đừng kéo dây buộc trước khi xem nó mắc ở đâu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — weather_researcher_mai

Cảm ơn. Nếu còn thiết bị, đừng kéo dây buộc trước khi xem nó mắc ở đâu.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — weather_researcher_mai

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## delivery_hell.4

### start — narrator

Bên tổ Wingull có lớp bọc và cảm biến. Dây buộc bị kéo ra, còn phần thiết bị chưa có vết nứt.

- Người chơi: Tôi sẽ đưa ba sợi dây đến Mai.
  → NPC: Bạn ghi nhận cảm biến còn nguyên và vị trí tìm thấy.; hành động: chỉ chuyển nhánh
- Người chơi: Cảm biến có hỏng khi bị tha đi không?
  → NPC: Nó còn nguyên. Wingull lấy dây làm tổ, nên cần ba sợi dây mới để đóng gói lại. Thiết bị vẫn cần giao đúng phòng nghiên cứu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Nó còn nguyên. Wingull lấy dây làm tổ, nên cần ba sợi dây mới để đóng gói lại. Thiết bị vẫn cần giao đúng phòng nghiên cứu.

- Người chơi: Tôi sẽ đưa ba sợi dây đến Mai.
  → NPC: Bạn ghi nhận cảm biến còn nguyên và vị trí tìm thấy.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn ghi nhận cảm biến còn nguyên và vị trí tìm thấy.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## delivery_hell.5

### start — weather_researcher_mai

Cảm biến đã được tìm lại. Tôi muốn đóng gói chắc và ký đúng tên người nhận trước khi Nam ghi xong chuyến này.

- Người chơi: Tôi giao ba sợi dây cho cô.
  → NPC: Cảm ơn. Kiện hàng đã được nhận đúng chỗ, tôi sẽ báo Nam.; hành động: chỉ chuyển nhánh
- Người chơi: Ba sợi dây này đủ để đóng gói lại chứ?
  → NPC: Đủ. Tôi sẽ buộc lại rồi ký nhận bằng tên đầy đủ và tên phòng. Nam cũng cần ghi rõ đơn vị ngay từ phiếu gửi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — weather_researcher_mai

Đủ. Tôi sẽ buộc lại rồi ký nhận bằng tên đầy đủ và tên phòng. Nam cũng cần ghi rõ đơn vị ngay từ phiếu gửi.

- Người chơi: Tôi giao ba sợi dây cho cô.
  → NPC: Cảm ơn. Kiện hàng đã được nhận đúng chỗ, tôi sẽ báo Nam.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — weather_researcher_mai

Cảm ơn. Kiện hàng đã được nhận đúng chỗ, tôi sẽ báo Nam.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — weather_researcher_mai

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## sleep_is_joy.1

### start — professor_hale

Mira bảo Pokémon thức nhiều về đêm. Ta muốn xem lịch nghỉ trong phòng trước khi cho rằng chúng bị bệnh.

- Người chơi: Tôi sẽ hỏi Mira về giờ chúng nghỉ.
  → NPC: Được. Ta kiểm tra những thứ trong phòng trước khi đặt tên cho một bí ẩn.; hành động: chỉ chuyển nhánh
- Người chơi: Ông nghĩ Pokémon mất ngủ vì việc gì?
  → NPC: Phòng bật đèn muộn, chuông giao hàng còn kêu ban đêm. Hỏi Mira lịch nghỉ của chúng trước khi nghĩ đến một bệnh lạ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.; hành động: chỉ chuyển nhánh

### answer — professor_hale

Phòng bật đèn muộn, chuông giao hàng còn kêu ban đêm. Hỏi Mira lịch nghỉ của chúng trước khi nghĩ đến một bệnh lạ.

- Người chơi: Tôi sẽ hỏi Mira về giờ chúng nghỉ.
  → NPC: Được. Ta kiểm tra những thứ trong phòng trước khi đặt tên cho một bí ẩn.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.; hành động: chỉ chuyển nhánh

### accepted — professor_hale

Được. Ta kiểm tra những thứ trong phòng trước khi đặt tên cho một bí ẩn.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — professor_hale

Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## sleep_is_joy.2

### start — daycare_mira

Có những con tỉnh ngay lúc vừa nằm xuống. Bạn giúp mình kiểm tra tiếng chuông ngoài phòng nhé.

- Người chơi: Tôi sẽ kiểm tra lịch chuông.
  → NPC: Cảm ơn. Mình giữ chỗ nghỉ yên trong lúc bạn xem.; hành động: chỉ chuyển nhánh
- Người chơi: Chỗ ngủ có tiếng ồn vào giờ nào?
  → NPC: Còi giao hàng kêu cả lúc không có chuyến. Snorlax vẫn ngủ, nhưng những con khác tỉnh. Bạn xem giờ chuông ngoài phòng giúp mình.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — daycare_mira

Còi giao hàng kêu cả lúc không có chuyến. Snorlax vẫn ngủ, nhưng những con khác tỉnh. Bạn xem giờ chuông ngoài phòng giúp mình.

- Người chơi: Tôi sẽ kiểm tra lịch chuông.
  → NPC: Cảm ơn. Mình giữ chỗ nghỉ yên trong lúc bạn xem.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — daycare_mira

Cảm ơn. Mình giữ chỗ nghỉ yên trong lúc bạn xem.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — daycare_mira

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## sleep_is_joy.3

### start — narrator

Bảng chuông và bảng giao hàng treo cạnh nhau. Một vài giờ không khớp giữa hai bảng.

- Người chơi: Tôi sẽ mang len cho Mira và báo lịch chuông sai.
  → NPC: Bạn lưu lại những giờ không có chuyến nhưng chuông vẫn kêu.; hành động: chỉ chuyển nhánh
- Người chơi: Chuông có khớp lịch giao hàng không?
  → NPC: Không. Nam dùng lịch báo thức riêng, nên chuông kêu cả giờ không có chuyến. Mira cần hai tấm len trắng lót lại chỗ nghỉ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Nam dùng lịch báo thức riêng, nên chuông kêu cả giờ không có chuyến. Mira cần hai tấm len trắng lót lại chỗ nghỉ.

- Người chơi: Tôi sẽ mang len cho Mira và báo lịch chuông sai.
  → NPC: Bạn lưu lại những giờ không có chuyến nhưng chuông vẫn kêu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn lưu lại những giờ không có chuyến nhưng chuông vẫn kêu.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## sleep_is_joy.4

### start — daycare_mira

Mình đã dời chỗ nghỉ khỏi gần chuông. Phần đệm còn thiếu, nhưng giờ ở đó yên hơn.

- Người chơi: Tôi giao hai tấm len cho Mira.
  → NPC: Cảm ơn. Bạn về báo Hale chỗ nghỉ đã sẵn sàng nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Len này dùng cho chỗ nghỉ đã dời khỏi chuông chứ?
  → NPC: Đúng. Chỗ mới yên hơn, Hale sẽ chỉnh giờ nhận hàng. Hai tấm len trắng đủ để lót thêm phần còn thiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — daycare_mira

Đúng. Chỗ mới yên hơn, Hale sẽ chỉnh giờ nhận hàng. Hai tấm len trắng đủ để lót thêm phần còn thiếu.

- Người chơi: Tôi giao hai tấm len cho Mira.
  → NPC: Cảm ơn. Bạn về báo Hale chỗ nghỉ đã sẵn sàng nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — daycare_mira

Cảm ơn. Bạn về báo Hale chỗ nghỉ đã sẵn sàng nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — daycare_mira

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## sleep_is_joy.5

### start — professor_hale

Ta nhận được lịch chuông mới rồi. Pokémon được nghỉ, giờ ta cũng phải thôi giữ phòng sáng suốt đêm.

- Người chơi: Vậy tối nay cả phòng nghỉ sớm nhé.
  → NPC: Được. Ta cất phần đang đọc, không đọc thêm một chương nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Chuông và chỗ nghỉ đã được sửa. Ông có nghỉ sớm hơn không?
  → NPC: Có. Ta đã đổi giờ nhận hàng và sẽ tắt đèn sớm. Không thể bảo cậu ngủ đủ rồi chính mình giữ phòng sáng cả đêm.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.; hành động: chỉ chuyển nhánh

### answer — professor_hale

Có. Ta đã đổi giờ nhận hàng và sẽ tắt đèn sớm. Không thể bảo cậu ngủ đủ rồi chính mình giữ phòng sáng cả đêm.

- Người chơi: Vậy tối nay cả phòng nghỉ sớm nhé.
  → NPC: Được. Ta cất phần đang đọc, không đọc thêm một chương nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.; hành động: chỉ chuyển nhánh

### accepted — professor_hale

Được. Ta cất phần đang đọc, không đọc thêm một chương nữa.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — professor_hale

Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## bread_queue.1

### start — town1_baker_quynh

Bánh mất trước giờ mở cửa. Khay còn được xếp lại rất ngay ngắn, nhưng tôi chưa biết ai đã lấy phần chưa ghi tên.

- Người chơi: Tôi sẽ hỏi Minh và xem sổ đặt bánh.
  → NPC: Cảm ơn. Tôi cần biết phần nào đã được trả tiền trước khi gọi ai là người lấy trộm.; hành động: chỉ chuyển nhánh
- Người chơi: Chị biết bánh mất vào lúc nào không?
  → NPC: Trước giờ mở cửa. Minh thấy người tới sớm; hỏi em ấy rồi so với sổ đặt bánh giúp tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_baker_quynh

Trước giờ mở cửa. Minh thấy người tới sớm; hỏi em ấy rồi so với sổ đặt bánh giúp tôi.

- Người chơi: Tôi sẽ hỏi Minh và xem sổ đặt bánh.
  → NPC: Cảm ơn. Tôi cần biết phần nào đã được trả tiền trước khi gọi ai là người lấy trộm.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_baker_quynh

Cảm ơn. Tôi cần biết phần nào đã được trả tiền trước khi gọi ai là người lấy trộm.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_baker_quynh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## bread_queue.2

### start — town1_minh

Tôi thấy Bảo mang bánh ra cho Pokémon. Cậu ấy nghĩ đã đặt phần đó với Quỳnh từ hôm trước.

- Người chơi: Tôi sẽ đối chiếu số phần trong sổ.
  → NPC: Được. Đừng để tôi đoán hộ ý Bảo rồi thành một câu chuyện mới.; hành động: chỉ chuyển nhánh
- Người chơi: Bảo có trả tiền phần bánh mang đi không?
  → NPC: Ngày đầu có. Tôi nghĩ cậu ấy tưởng khoản đó tính cho cả tuần. Sổ ở tiệm cho biết đã đặt bao nhiêu phần.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_minh

Ngày đầu có. Tôi nghĩ cậu ấy tưởng khoản đó tính cho cả tuần. Sổ ở tiệm cho biết đã đặt bao nhiêu phần.

- Người chơi: Tôi sẽ đối chiếu số phần trong sổ.
  → NPC: Được. Đừng để tôi đoán hộ ý Bảo rồi thành một câu chuyện mới.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_minh

Được. Đừng để tôi đoán hộ ý Bảo rồi thành một câu chuyện mới.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_minh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## bread_queue.3

### start — narrator

Sổ có một khoản đã trả và nhiều phần chưa ghi tên. Bạn có thể đối chiếu số phần theo ngày.

- Người chơi: Tôi sẽ mang tám bó lúa mì đến Quỳnh.
  → NPC: Bạn giữ lại khoản đã trả và những phần chưa được ghi tên.; hành động: chỉ chuyển nhánh
- Người chơi: Khoản tiền ghi trong sổ đủ mấy ngày?
  → NPC: Chỉ một ngày. Những phần sau không có tên người đặt. Quỳnh cần tám bó lúa mì nếu muốn làm thêm mẻ cho Pokémon ngoài đường.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Chỉ một ngày. Những phần sau không có tên người đặt. Quỳnh cần tám bó lúa mì nếu muốn làm thêm mẻ cho Pokémon ngoài đường.

- Người chơi: Tôi sẽ mang tám bó lúa mì đến Quỳnh.
  → NPC: Bạn giữ lại khoản đã trả và những phần chưa được ghi tên.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ lại khoản đã trả và những phần chưa được ghi tên.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## bread_queue.4

### start — town1_baker_quynh

Tôi đồng ý làm phần bánh riêng. Nhưng nguyên liệu và người nhận cần được ghi rõ, không thể để ai cũng tự lấy rồi tính sau.

- Người chơi: Tôi giao tám bó lúa mì cho mẻ bánh.
  → NPC: Cảm ơn. Nhắn Bảo tới ghi lịch nhận giúp tôi nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tám bó lúa mì này dùng cho mẻ bánh riêng chứ?
  → NPC: Đúng. Tôi sẽ giữ một phần riêng, nhưng Bảo phải ghi tên và giờ nhận. Tôi cần tính được bột trước mỗi mẻ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_baker_quynh

Đúng. Tôi sẽ giữ một phần riêng, nhưng Bảo phải ghi tên và giờ nhận. Tôi cần tính được bột trước mỗi mẻ.

- Người chơi: Tôi giao tám bó lúa mì cho mẻ bánh.
  → NPC: Cảm ơn. Nhắn Bảo tới ghi lịch nhận giúp tôi nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_baker_quynh

Cảm ơn. Nhắn Bảo tới ghi lịch nhận giúp tôi nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_baker_quynh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## bread_queue.5

### start — town1_bao

Quỳnh nhận lúa mì rồi à? Tôi đã đến ghi lịch, lần này có tên tôi trên từng phần nhận.

- Người chơi: Vậy tôi xác nhận cậu sẽ tự ghi lịch.
  → NPC: Ừ. Lần sau tôi hỏi trước khi lấy phần chưa được đặt.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu có đồng ý ghi lịch với Quỳnh không?
  → NPC: Có. Tôi tưởng mang bánh cho Pokémon là đủ, nhưng Quỳnh còn phải tính nguyên liệu. Tôi sẽ tới ký phần nhận của mình.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_bao

Có. Tôi tưởng mang bánh cho Pokémon là đủ, nhưng Quỳnh còn phải tính nguyên liệu. Tôi sẽ tới ký phần nhận của mình.

- Người chơi: Vậy tôi xác nhận cậu sẽ tự ghi lịch.
  → NPC: Ừ. Lần sau tôi hỏi trước khi lấy phần chưa được đặt.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_bao

Ừ. Lần sau tôi hỏi trước khi lấy phần chưa được đặt.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_bao

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mira_missing_bell.1

### start — daycare_mira

Pokémon cứ kéo áo gọi mình. Chuông cạnh giường vẫn treo, nên mình muốn kiểm tra vì sao nó không kêu.

- Người chơi: Tôi sẽ kiểm tra chuông gọi.
  → NPC: Cảm ơn. Mình ở lại bên giường trong lúc bạn xem.; hành động: chỉ chuyển nhánh
- Người chơi: Chuông bị hỏng hay Pokémon không với tới?
  → NPC: Nó treo đúng tầm, nhưng không kêu. Bạn kiểm tra nút buộc ở cạnh giường giúp mình; mình không muốn đoán rằng Pokémon không cần gì.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — daycare_mira

Nó treo đúng tầm, nhưng không kêu. Bạn kiểm tra nút buộc ở cạnh giường giúp mình; mình không muốn đoán rằng Pokémon không cần gì.

- Người chơi: Tôi sẽ kiểm tra chuông gọi.
  → NPC: Cảm ơn. Mình ở lại bên giường trong lúc bạn xem.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — daycare_mira

Cảm ơn. Mình ở lại bên giường trong lúc bạn xem.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — daycare_mira

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mira_missing_bell.2

### start — narrator

Dây chuông được buộc sát khung giường. Có thể xem nút nào đang giữ phần rung lại.

- Người chơi: Tôi sẽ hỏi bác Phúc về nút thắt.
  → NPC: Bạn ghi lại dây bị buộc, không kết luận trước về lý do.; hành động: chỉ chuyển nhánh
- Người chơi: Điều gì giữ chuông không kêu?
  → NPC: Một nút thắt chặn dây chuông. Có người buộc lại để bớt ồn rồi chưa tháo ra. Bác Phúc từng ngồi ở giường bên cạnh.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Một nút thắt chặn dây chuông. Có người buộc lại để bớt ồn rồi chưa tháo ra. Bác Phúc từng ngồi ở giường bên cạnh.

- Người chơi: Tôi sẽ hỏi bác Phúc về nút thắt.
  → NPC: Bạn ghi lại dây bị buộc, không kết luận trước về lý do.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn ghi lại dây bị buộc, không kết luận trước về lý do.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mira_missing_bell.3

### start — town1_uncle_phuc

Mira hỏi về chuông à? Bác có ngồi ở giường bên cạnh hôm qua. Bác nhớ mình đã chỉnh dây.

- Người chơi: Tôi sẽ mang hai sợi dây làm chuông thay thế.
  → NPC: Ừ. Cảm ơn cháu. Bác tháo nút cũ ngay.; hành động: chỉ chuyển nhánh
- Người chơi: Bác có biết dây chuông bị buộc không?
  → NPC: Bác buộc, rồi quên tháo. Bác sẽ xin lỗi Mira. Cần yên không có nghĩa được làm chuông gọi người chăm sóc im đi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_uncle_phuc

Bác buộc, rồi quên tháo. Bác sẽ xin lỗi Mira. Cần yên không có nghĩa được làm chuông gọi người chăm sóc im đi.

- Người chơi: Tôi sẽ mang hai sợi dây làm chuông thay thế.
  → NPC: Ừ. Cảm ơn cháu. Bác tháo nút cũ ngay.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_uncle_phuc

Ừ. Cảm ơn cháu. Bác tháo nút cũ ngay.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_uncle_phuc

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mira_missing_bell.4

### start — daycare_mira

Mình chuẩn bị chỗ treo chuông mới rồi. Dây cần đủ dài để Pokémon với tới mà không vướng vào giường.

- Người chơi: Tôi giao hai sợi dây cho Mira.
  → NPC: Cảm ơn. Bạn báo bác Phúc là người nằm ở đây đã gọi được mình nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Hai sợi dây này đủ làm chuông mới chứ?
  → NPC: Đủ. Mình sẽ treo trong tầm với và thử tiếng chuông sau mỗi ca, không chỉ nhìn xem nó còn treo hay không.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — daycare_mira

Đủ. Mình sẽ treo trong tầm với và thử tiếng chuông sau mỗi ca, không chỉ nhìn xem nó còn treo hay không.

- Người chơi: Tôi giao hai sợi dây cho Mira.
  → NPC: Cảm ơn. Bạn báo bác Phúc là người nằm ở đây đã gọi được mình nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — daycare_mira

Cảm ơn. Bạn báo bác Phúc là người nằm ở đây đã gọi được mình nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — daycare_mira

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mira_missing_bell.5

### start — town1_uncle_phuc

Bác đã qua chỗ Mira. Chuông kêu lại, còn bác có chuyện phải nói với Pokémon nằm ở đó.

- Người chơi: Tôi mừng vì hai người đã nói chuyện.
  → NPC: Ừ. Cảm ơn cháu đã quay lại báo, không để bác tự đoán.; hành động: chỉ chuyển nhánh
- Người chơi: Chuông đã hoạt động. Bác có quay lại xin lỗi không?
  → NPC: Bác đã tới rồi. Nó vẫn kéo áo, nhưng lần này muốn bác ngồi cùng. Bác sẽ hỏi Mira trước khi chỉnh thứ gì cạnh giường.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_uncle_phuc

Bác đã tới rồi. Nó vẫn kéo áo, nhưng lần này muốn bác ngồi cùng. Bác sẽ hỏi Mira trước khi chỉnh thứ gì cạnh giường.

- Người chơi: Tôi mừng vì hai người đã nói chuyện.
  → NPC: Ừ. Cảm ơn cháu đã quay lại báo, không để bác tự đoán.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_uncle_phuc

Ừ. Cảm ơn cháu đã quay lại báo, không để bác tự đoán.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_uncle_phuc

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## tomo_spare_parts.1

### start — bicycle_tomo

Khách vừa mang xe trở lại. Xe chạy tốt, nhưng giấy bảo dưỡng ghi một tên khác với phiếu giao.

- Người chơi: Tôi sẽ hỏi Nam về nhãn giao xe.
  → NPC: Cảm ơn. Mang số khung đối chiếu cùng nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Khách nghi chiếc xe có vấn đề gì?
  → NPC: Số xe đúng, nhưng giấy bảo dưỡng ghi tên người khác. Tôi không muốn khách phải tin lời nói của tôi thay cho giấy tờ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — bicycle_tomo

Số xe đúng, nhưng giấy bảo dưỡng ghi tên người khác. Tôi không muốn khách phải tin lời nói của tôi thay cho giấy tờ.

- Người chơi: Tôi sẽ hỏi Nam về nhãn giao xe.
  → NPC: Cảm ơn. Mang số khung đối chiếu cùng nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — bicycle_tomo

Cảm ơn. Mang số khung đối chiếu cùng nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — bicycle_tomo

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## tomo_spare_parts.2

### start — town3_courier_nam

Tôi còn giữ hai phiếu của chuyến xe đó. Chúng đã bị ghim cùng nhau từ lúc xếp hàng.

- Người chơi: Tôi sẽ đối chiếu số khung trong sổ.
  → NPC: Được. Tôi giữ lại phiếu giao, không sửa tên trước khi kiểm tra.; hành động: chỉ chuyển nhánh
- Người chơi: Anh dán hai nhãn vào cùng một xe sao?
  → NPC: Ừ. Hai phiếu đi cùng một chuyến, tôi ghim nhầm rồi dán cả hai. Sổ bảo dưỡng còn số khung gốc.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_courier_nam

Ừ. Hai phiếu đi cùng một chuyến, tôi ghim nhầm rồi dán cả hai. Sổ bảo dưỡng còn số khung gốc.

- Người chơi: Tôi sẽ đối chiếu số khung trong sổ.
  → NPC: Được. Tôi giữ lại phiếu giao, không sửa tên trước khi kiểm tra.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_courier_nam

Được. Tôi giữ lại phiếu giao, không sửa tên trước khi kiểm tra.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_courier_nam

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## tomo_spare_parts.3

### start — narrator

Sổ bảo dưỡng có số khung riêng cho từng xe. Bạn có thể đối chiếu số với hai phiếu đã bị ghim.

- Người chơi: Tôi sẽ mang bốn thỏi sắt cho Tomo.
  → NPC: Bạn giữ số khung đúng cùng hai hồ sơ bị ghim nhầm.; hành động: chỉ chuyển nhánh
- Người chơi: Số khung có bị đổi không?
  → NPC: Không. Hai hồ sơ bị ghim chung làm sai tên chủ xe. Tomo cần bốn thỏi sắt sửa giá để khách tự xem được số khung.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Hai hồ sơ bị ghim chung làm sai tên chủ xe. Tomo cần bốn thỏi sắt sửa giá để khách tự xem được số khung.

- Người chơi: Tôi sẽ mang bốn thỏi sắt cho Tomo.
  → NPC: Bạn giữ số khung đúng cùng hai hồ sơ bị ghim nhầm.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ số khung đúng cùng hai hồ sơ bị ghim nhầm.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## tomo_spare_parts.4

### start — bicycle_tomo

Tôi muốn khách nhìn được số khung trực tiếp. Giá sửa xe còn thiếu phần giữ bên này.

- Người chơi: Tôi giao bốn thỏi sắt làm giá sửa xe.
  → NPC: Cảm ơn. Nhờ Nam tách hai phiếu và ký lại biên nhận nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Giá mới giúp khách kiểm tra xe thế nào?
  → NPC: Nó giữ xe chắc để nhìn số khung trực tiếp. Tôi sẽ sửa tên trên giấy theo sổ gốc, không bắt khách chỉ nghe lời tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — bicycle_tomo

Nó giữ xe chắc để nhìn số khung trực tiếp. Tôi sẽ sửa tên trên giấy theo sổ gốc, không bắt khách chỉ nghe lời tôi.

- Người chơi: Tôi giao bốn thỏi sắt làm giá sửa xe.
  → NPC: Cảm ơn. Nhờ Nam tách hai phiếu và ký lại biên nhận nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — bicycle_tomo

Cảm ơn. Nhờ Nam tách hai phiếu và ký lại biên nhận nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — bicycle_tomo

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## tomo_spare_parts.5

### start — town3_courier_nam

Tôi đã tách hai phiếu. Lần giao lại này cần đúng một xe và một bản giấy của nó.

- Người chơi: Vậy tôi xác nhận giấy bảo dưỡng đã sửa.
  → NPC: Cảm ơn. Tôi báo lại Tomo để khách nhận đúng bản.; hành động: chỉ chuyển nhánh
- Người chơi: Anh đã tách phiếu và ký lại chưa?
  → NPC: Rồi. Một xe, một số khung, một tên người nhận. Tôi sẽ chụp phiếu trước khi giao để còn đối chiếu nếu nhãn bị ướt.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_courier_nam

Rồi. Một xe, một số khung, một tên người nhận. Tôi sẽ chụp phiếu trước khi giao để còn đối chiếu nếu nhãn bị ướt.

- Người chơi: Vậy tôi xác nhận giấy bảo dưỡng đã sửa.
  → NPC: Cảm ơn. Tôi báo lại Tomo để khách nhận đúng bản.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_courier_nam

Cảm ơn. Tôi báo lại Tomo để khách nhận đúng bản.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_courier_nam

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## elle_mended_scarf.1

### start — fashion_elle

Linh trả lại khăn dù vẫn nhìn màu đó rất lâu. Tôi nghĩ em ấy không đổi ý về màu, nhưng có điều chưa dám nói.

- Người chơi: Tôi sẽ hỏi Linh muốn chiếc khăn của mình thế nào.
  → NPC: Cảm ơn. Đừng hỏi em ấy đã đủ huy hiệu để mặc nó chưa nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Linh trả khăn vì không thích nó nữa sao?
  → NPC: Em ấy vẫn thích màu, nhưng bị trêu vì mặc giống một huấn luyện viên nổi tiếng. Bạn hỏi xem em ấy muốn giữ phần nào giúp tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — fashion_elle

Em ấy vẫn thích màu, nhưng bị trêu vì mặc giống một huấn luyện viên nổi tiếng. Bạn hỏi xem em ấy muốn giữ phần nào giúp tôi.

- Người chơi: Tôi sẽ hỏi Linh muốn chiếc khăn của mình thế nào.
  → NPC: Cảm ơn. Đừng hỏi em ấy đã đủ huy hiệu để mặc nó chưa nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — fashion_elle

Cảm ơn. Đừng hỏi em ấy đã đủ huy hiệu để mặc nó chưa nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — fashion_elle

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## elle_mended_scarf.2

### start — town2_student_linh

Tôi vẫn thích chiếc khăn. Chỉ là mọi người cứ hỏi tôi đã giỏi giống người thường đeo nó chưa.

- Người chơi: Tôi sẽ xem mẫu sửa khăn của Elle.
  → NPC: Ừ. Tôi muốn chọn chiếc khăn, không chọn cách mọi người so tôi với ai.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu muốn giữ màu hay giữ cả biểu tượng League?
  → NPC: Giữ màu thôi. Tôi thích màu đó từ trước khi xem Champion. Elle có thể bỏ biểu tượng mà không đổi cả chiếc khăn không?; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_student_linh

Giữ màu thôi. Tôi thích màu đó từ trước khi xem Champion. Elle có thể bỏ biểu tượng mà không đổi cả chiếc khăn không?

- Người chơi: Tôi sẽ xem mẫu sửa khăn của Elle.
  → NPC: Ừ. Tôi muốn chọn chiếc khăn, không chọn cách mọi người so tôi với ai.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_student_linh

Ừ. Tôi muốn chọn chiếc khăn, không chọn cách mọi người so tôi với ai.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_student_linh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## elle_mended_scarf.3

### start — narrator

Elle để một mẫu sửa cạnh chiếc khăn. Màu và phần hình đã được đánh dấu riêng.

- Người chơi: Tôi sẽ mang bốn sợi dây cho Elle.
  → NPC: Bạn ghi nhận đúng mẫu Linh muốn, không chọn thay một biểu tượng khác.; hành động: chỉ chuyển nhánh
- Người chơi: Mẫu sửa giữ lại phần nào?
  → NPC: Màu cũ được giữ, biểu tượng League được bỏ. Elle cần bốn sợi dây để may lại mép khăn sau khi tháo hình.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Màu cũ được giữ, biểu tượng League được bỏ. Elle cần bốn sợi dây để may lại mép khăn sau khi tháo hình.

- Người chơi: Tôi sẽ mang bốn sợi dây cho Elle.
  → NPC: Bạn ghi nhận đúng mẫu Linh muốn, không chọn thay một biểu tượng khác.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn ghi nhận đúng mẫu Linh muốn, không chọn thay một biểu tượng khác.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## elle_mended_scarf.4

### start — fashion_elle

Linh đã chọn mẫu rồi. Tôi muốn sửa đúng phần em ấy muốn, không biến nó thành một chiếc khăn hoàn toàn khác.

- Người chơi: Tôi giao bốn sợi dây sửa khăn.
  → NPC: Cảm ơn. Bạn báo Linh ghé nhận nhé; tôi muốn em ấy tự thử lại.; hành động: chỉ chuyển nhánh
- Người chơi: Cô sửa theo màu Linh chọn chứ?
  → NPC: Đúng. Tôi tháo hình, giữ màu và may lại mép. Em ấy không cần chờ một huy hiệu để được thích chiếc khăn này.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — fashion_elle

Đúng. Tôi tháo hình, giữ màu và may lại mép. Em ấy không cần chờ một huy hiệu để được thích chiếc khăn này.

- Người chơi: Tôi giao bốn sợi dây sửa khăn.
  → NPC: Cảm ơn. Bạn báo Linh ghé nhận nhé; tôi muốn em ấy tự thử lại.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — fashion_elle

Cảm ơn. Bạn báo Linh ghé nhận nhé; tôi muốn em ấy tự thử lại.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — fashion_elle

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## elle_mended_scarf.5

### start — town2_student_linh

Elle báo khăn đã sửa xong. Tôi muốn thử lại, lần này theo màu mình chọn.

- Người chơi: Tôi sẽ báo Elle cậu ghé nhận.
  → NPC: Cảm ơn. Lần này tôi sẽ nói thẳng mình thích màu gì.; hành động: chỉ chuyển nhánh
- Người chơi: Elle đã sửa khăn theo ý cậu. Cậu có muốn thử lại không?
  → NPC: Có. Tôi vẫn luyện đội, nhưng không muốn mỗi lần chọn màu lại phải nghĩ mình đã đủ giỏi chưa. Tôi sẽ qua quầy.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_student_linh

Có. Tôi vẫn luyện đội, nhưng không muốn mỗi lần chọn màu lại phải nghĩ mình đã đủ giỏi chưa. Tôi sẽ qua quầy.

- Người chơi: Tôi sẽ báo Elle cậu ghé nhận.
  → NPC: Cảm ơn. Lần này tôi sẽ nói thẳng mình thích màu gì.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_student_linh

Cảm ơn. Lần này tôi sẽ nói thẳng mình thích màu gì.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_student_linh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## library_blank_page.1

### start — town2_librarian_an

Một cuốn sách được trả đủ bìa nhưng mất trang tên tác giả. Bạn giúp tôi xem phần chữ còn hằn ở trang kế nhé.

- Người chơi: Tôi sẽ kiểm tra dấu chữ ở trang kế.
  → NPC: Cảm ơn. Tôi muốn biết người bị mất tên trước khi đặt một bản khác.; hành động: chỉ chuyển nhánh
- Người chơi: Trang nào đã bị lấy khỏi cuốn sách?
  → NPC: Trang có tên tác giả. Phần hướng dẫn vẫn còn. Bạn xem dấu chữ in hằn trên trang kế bên để biết ai đã viết nó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_librarian_an

Trang có tên tác giả. Phần hướng dẫn vẫn còn. Bạn xem dấu chữ in hằn trên trang kế bên để biết ai đã viết nó.

- Người chơi: Tôi sẽ kiểm tra dấu chữ ở trang kế.
  → NPC: Cảm ơn. Tôi muốn biết người bị mất tên trước khi đặt một bản khác.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_librarian_an

Cảm ơn. Tôi muốn biết người bị mất tên trước khi đặt một bản khác.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_librarian_an

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## library_blank_page.2

### start — narrator

Trang kế có vết chữ mờ qua giấy. Tên và một phần tiêu đề vẫn còn đọc được.

- Người chơi: Tôi sẽ hỏi Kim về cuốn sách.
  → NPC: Bạn giữ dấu tên cùng tên phương pháp để hỏi đúng bản sách.; hành động: chỉ chuyển nhánh
- Người chơi: Có đọc được tên bị bỏ đi không?
  → NPC: Tên Kim còn hằn qua giấy. Sách ghi cách chăm Pokémon sau căng thẳng. Cần hỏi bác sĩ Kim vì sao trang tên bị lấy ra.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Tên Kim còn hằn qua giấy. Sách ghi cách chăm Pokémon sau căng thẳng. Cần hỏi bác sĩ Kim vì sao trang tên bị lấy ra.

- Người chơi: Tôi sẽ hỏi Kim về cuốn sách.
  → NPC: Bạn giữ dấu tên cùng tên phương pháp để hỏi đúng bản sách.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ dấu tên cùng tên phương pháp để hỏi đúng bản sách.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## library_blank_page.3

### start — town2_doctor_kim

An nhờ cậu hỏi về sách của tôi à? Tôi biết trang nào đã bị lấy ra. Tôi là người lấy nó.

- Người chơi: Tôi sẽ mang ba tờ giấy cho An chuẩn bị bản đính chính.
  → NPC: Cảm ơn. Tôi đánh dấu phần cần sửa để người đọc đối chiếu được.; hành động: chỉ chuyển nhánh
- Người chơi: Bác tự bỏ tên mình khỏi sách à?
  → NPC: Ừ. Bản đầu có lỗi, tôi bị nhắc mãi rồi xé tên thay vì sửa hướng dẫn. Tôi muốn viết bản đính chính có tên mình.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_doctor_kim

Ừ. Bản đầu có lỗi, tôi bị nhắc mãi rồi xé tên thay vì sửa hướng dẫn. Tôi muốn viết bản đính chính có tên mình.

- Người chơi: Tôi sẽ mang ba tờ giấy cho An chuẩn bị bản đính chính.
  → NPC: Cảm ơn. Tôi đánh dấu phần cần sửa để người đọc đối chiếu được.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_doctor_kim

Cảm ơn. Tôi đánh dấu phần cần sửa để người đọc đối chiếu được.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_doctor_kim

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## library_blank_page.4

### start — town2_librarian_an

Kim đã đánh dấu phần cần sửa. Tôi muốn đặt bản đính chính ở chỗ người đọc tìm được cùng cuốn sách.

- Người chơi: Tôi giao ba tờ giấy cho An.
  → NPC: Cảm ơn. Nhờ Kim đến ký bản sửa giúp tôi nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Bản đính chính có đặt cạnh bản cũ không?
  → NPC: Có. Ba tờ giấy đủ để ghi phần sửa và lý do. Tôi giữ cả bản cũ để người đọc biết hướng dẫn đã thay đổi ở đâu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_librarian_an

Có. Ba tờ giấy đủ để ghi phần sửa và lý do. Tôi giữ cả bản cũ để người đọc biết hướng dẫn đã thay đổi ở đâu.

- Người chơi: Tôi giao ba tờ giấy cho An.
  → NPC: Cảm ơn. Nhờ Kim đến ký bản sửa giúp tôi nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_librarian_an

Cảm ơn. Nhờ Kim đến ký bản sửa giúp tôi nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_librarian_an

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## library_blank_page.5

### start — town2_doctor_kim

An đã chuẩn bị bản đính chính. Tôi muốn tên mình trở lại cùng phần đã sửa.

- Người chơi: Tôi sẽ báo An bác tới ký.
  → NPC: Cảm ơn. Tôi muốn người đọc biết ai chịu trách nhiệm cho hướng dẫn này.; hành động: chỉ chuyển nhánh
- Người chơi: An đã chuẩn bị bản sửa. Bác muốn ký lại chứ?
  → NPC: Có. Tên tôi sẽ đứng cạnh phần đã sửa, không phải trên một trang giả vờ chưa có lỗi. Tôi sẽ đến thư viện hôm nay.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_doctor_kim

Có. Tên tôi sẽ đứng cạnh phần đã sửa, không phải trên một trang giả vờ chưa có lỗi. Tôi sẽ đến thư viện hôm nay.

- Người chơi: Tôi sẽ báo An bác tới ký.
  → NPC: Cảm ơn. Tôi muốn người đọc biết ai chịu trách nhiệm cho hướng dẫn này.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_doctor_kim

Cảm ơn. Tôi muốn người đọc biết ai chịu trách nhiệm cho hướng dẫn này.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_doctor_kim

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## exam_morning.1

### start — town2_student_linh

Tôi học thuộc tên đòn rồi, nhưng lúc vào trận đầu chỉ còn tiếng giám thị ho. Tôi cần một cách luyện khác.

- Người chơi: Tôi sẽ hỏi Orin cách luyện bằng tình huống.
  → NPC: Cảm ơn. Tôi muốn biết sửa từ đâu, không học thuộc thêm một danh sách nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu quên tên đòn hay không biết lúc nào nên dùng?
  → NPC: Tôi thuộc tên, nhưng vào trận lại không biết vì sao chọn đòn nào. Orin nói có cách luyện bằng tình huống; bạn hỏi giúp tôi được không?; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_student_linh

Tôi thuộc tên, nhưng vào trận lại không biết vì sao chọn đòn nào. Orin nói có cách luyện bằng tình huống; bạn hỏi giúp tôi được không?

- Người chơi: Tôi sẽ hỏi Orin cách luyện bằng tình huống.
  → NPC: Cảm ơn. Tôi muốn biết sửa từ đâu, không học thuộc thêm một danh sách nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_student_linh

Cảm ơn. Tôi muốn biết sửa từ đâu, không học thuộc thêm một danh sách nữa.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_student_linh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## exam_morning.2

### start — dr_orin

Linh không thiếu một danh sách tên. Em ấy cần thử giải thích quyết định trong một lượt cụ thể.

- Người chơi: Tôi sẽ đọc tình huống trên bàn học.
  → NPC: Được. Nhớ hỏi lý do trước khi cho đáp án.; hành động: chỉ chuyển nhánh
- Người chơi: Ông muốn Linh luyện khác cách học thuộc thế nào?
  → NPC: Cho em ấy một lượt cụ thể, hỏi lý do chọn đòn rồi đối chiếu kết quả. Mẫu trên bàn học bắt đầu bằng đối thủ vừa dùng Protect.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.; hành động: chỉ chuyển nhánh

### answer — dr_orin

Cho em ấy một lượt cụ thể, hỏi lý do chọn đòn rồi đối chiếu kết quả. Mẫu trên bàn học bắt đầu bằng đối thủ vừa dùng Protect.

- Người chơi: Tôi sẽ đọc tình huống trên bàn học.
  → NPC: Được. Nhớ hỏi lý do trước khi cho đáp án.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.; hành động: chỉ chuyển nhánh

### accepted — dr_orin

Được. Nhớ hỏi lý do trước khi cho đáp án.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — dr_orin

Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## exam_morning.3

### start — narrator

Thẻ luyện ghi đối thủ vừa dùng Protect. Bên dưới là vài phương án, chưa đánh dấu đáp án.

- Người chơi: Tôi sẽ nhận trận minh họa với Minh.
  → NPC: Bạn giữ các phương án và lý do để đối chiếu sau trận.; hành động: chỉ chuyển nhánh
- Người chơi: Đối thủ vừa dùng Protect thì cần cân nhắc gì?
  → NPC: Không chỉ lặp lại đòn mạnh nhất. Có thể dùng lượt đó để đổi nhịp hoặc chuẩn bị. Minh sẽ đấu minh họa để Linh có tình huống thật.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không chỉ lặp lại đòn mạnh nhất. Có thể dùng lượt đó để đổi nhịp hoặc chuẩn bị. Minh sẽ đấu minh họa để Linh có tình huống thật.

- Người chơi: Tôi sẽ nhận trận minh họa với Minh.
  → NPC: Bạn giữ các phương án và lý do để đối chiếu sau trận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ các phương án và lý do để đối chiếu sau trận.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## exam_morning.4

### start — town1_minh

Tôi nhận làm đối thủ cho trận minh họa. Linh sẽ xem lại đoạn có quyết định quan trọng sau trận.

- Người chơi: Vậy chúng ta đấu một trận thật.
  → NPC: Được. Sau trận tôi sẽ nói rõ phần mình đã tính sai.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu đồng ý dùng trận này làm ví dụ cho Linh chứ?
  → NPC: Ừ. Tôi không nhường kết quả, nhưng sẽ cùng xem lại lượt quan trọng. Như thế Linh biết vì sao chọn một nước đi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_minh

Ừ. Tôi không nhường kết quả, nhưng sẽ cùng xem lại lượt quan trọng. Như thế Linh biết vì sao chọn một nước đi.

- Người chơi: Vậy chúng ta đấu một trận thật.
  → NPC: Được. Sau trận tôi sẽ nói rõ phần mình đã tính sai.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_minh

Được. Sau trận tôi sẽ nói rõ phần mình đã tính sai.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — town1_minh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## exam_morning.5

### start — town2_student_linh

Tôi đã xem lại trận. Có một lượt trước đây tôi chỉ nhớ tên đòn, giờ tôi biết vì sao chọn nó.

- Người chơi: Tôi nghĩ cậu đã có cách luyện tiếp rồi.
  → NPC: Ừ. Tôi sẽ đem lý do của mình tới lần thi sau.; hành động: chỉ chuyển nhánh
- Người chơi: Sau trận, cậu giải thích được một lượt quan trọng không?
  → NPC: Được. Tôi biết mình đổi Pokémon vì muốn giữ một nước đáp, không phải vì nhớ một câu mẫu. Nếu sai, tôi cũng biết phần nào cần thử lại.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_student_linh

Được. Tôi biết mình đổi Pokémon vì muốn giữ một nước đáp, không phải vì nhớ một câu mẫu. Nếu sai, tôi cũng biết phần nào cần thử lại.

- Người chơi: Tôi nghĩ cậu đã có cách luyện tiếp rồi.
  → NPC: Ừ. Tôi sẽ đem lý do của mình tới lần thi sau.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_student_linh

Ừ. Tôi sẽ đem lý do của mình tới lần thi sau.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_student_linh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## orin_hale_coffee.1

### start — dr_orin

Hale và tôi nhớ khác nhau về buổi cà phê cũ. Tôi muốn kiểm tra xem ai đang bỏ mất phần nào.

- Người chơi: Tôi sẽ nghe Hale kể rồi xem hóa đơn.
  → NPC: Được. Đừng coi trí nhớ của tôi là bản gốc chỉ vì tôi nói chắc hơn.; hành động: chỉ chuyển nhánh
- Người chơi: Hai ông đang cãi nhau về buổi gặp cũ à?
  → NPC: Hale nhớ tôi mời, tôi nhớ ông ấy không chịu kết thúc. Muốn kiểm tra thì hỏi cả ông ấy và xem hóa đơn ở quán.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.; hành động: chỉ chuyển nhánh

### answer — dr_orin

Hale nhớ tôi mời, tôi nhớ ông ấy không chịu kết thúc. Muốn kiểm tra thì hỏi cả ông ấy và xem hóa đơn ở quán.

- Người chơi: Tôi sẽ nghe Hale kể rồi xem hóa đơn.
  → NPC: Được. Đừng coi trí nhớ của tôi là bản gốc chỉ vì tôi nói chắc hơn.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.; hành động: chỉ chuyển nhánh

### accepted — dr_orin

Được. Đừng coi trí nhớ của tôi là bản gốc chỉ vì tôi nói chắc hơn.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — dr_orin

Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## orin_hale_coffee.2

### start — professor_hale

Orin muốn đối chiếu hóa đơn à? Ta nhớ lời mời, nhưng không chắc nhớ được giờ chúng ta ra về.

- Người chơi: Tôi sẽ đối chiếu hóa đơn và ghi chú quán.
  → NPC: Được. Nếu hóa đơn làm ta nhớ sai một phần thì cứ nói.; hành động: chỉ chuyển nhánh
- Người chơi: Ông có nhớ vì sao buổi gặp kéo dài không?
  → NPC: Orin bảo nói đến khi rõ. Ta nghĩ ông ấy vẫn còn điều chưa nói, rồi gọi thêm cà phê. Quán giữ hóa đơn, xem nó sẽ biết cả hai đã ngồi bao lâu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.; hành động: chỉ chuyển nhánh

### answer — professor_hale

Orin bảo nói đến khi rõ. Ta nghĩ ông ấy vẫn còn điều chưa nói, rồi gọi thêm cà phê. Quán giữ hóa đơn, xem nó sẽ biết cả hai đã ngồi bao lâu.

- Người chơi: Tôi sẽ đối chiếu hóa đơn và ghi chú quán.
  → NPC: Được. Nếu hóa đơn làm ta nhớ sai một phần thì cứ nói.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.; hành động: chỉ chuyển nhánh

### accepted — professor_hale

Được. Nếu hóa đơn làm ta nhớ sai một phần thì cứ nói.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — professor_hale

Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## orin_hale_coffee.3

### start — narrator

Hóa đơn quán còn nguyên. Mặt sau có ghi chú của chủ quán về buổi gặp.

- Người chơi: Tôi sẽ mang hai hạt cacao cho Quỳnh.
  → NPC: Bạn giữ ghi chú của quán, không biến hóa đơn đã trả thành một món nợ.; hành động: chỉ chuyển nhánh
- Người chơi: Hai ông đã trả đủ tiền buổi đó chưa?
  → NPC: Đã trả. Chủ quán ghi xin hai người dừng tranh luận. Quỳnh đề nghị buổi gặp mới mỗi người một cốc, cần thêm hai hạt cacao.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Đã trả. Chủ quán ghi xin hai người dừng tranh luận. Quỳnh đề nghị buổi gặp mới mỗi người một cốc, cần thêm hai hạt cacao.

- Người chơi: Tôi sẽ mang hai hạt cacao cho Quỳnh.
  → NPC: Bạn giữ ghi chú của quán, không biến hóa đơn đã trả thành một món nợ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ ghi chú của quán, không biến hóa đơn đã trả thành một món nợ.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## orin_hale_coffee.4

### start — town1_baker_quynh

Tôi nhận chuẩn bị một buổi gặp nữa cho hai ông. Lần này bàn có cả phần ăn, không chỉ cà phê.

- Người chơi: Tôi giao hai hạt cacao cho buổi gặp.
  → NPC: Cảm ơn. Nhắn Orin là bàn có chỗ ngồi, không có bảng tính người thắng.; hành động: chỉ chuyển nhánh
- Người chơi: Chị có nhận hai ông tới nói chuyện lần nữa không?
  → NPC: Có, nhưng mỗi người một cốc và phải ăn trước. Tôi không muốn cuộc tranh luận lại thay cả bữa trưa.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_baker_quynh

Có, nhưng mỗi người một cốc và phải ăn trước. Tôi không muốn cuộc tranh luận lại thay cả bữa trưa.

- Người chơi: Tôi giao hai hạt cacao cho buổi gặp.
  → NPC: Cảm ơn. Nhắn Orin là bàn có chỗ ngồi, không có bảng tính người thắng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_baker_quynh

Cảm ơn. Nhắn Orin là bàn có chỗ ngồi, không có bảng tính người thắng.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_baker_quynh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## orin_hale_coffee.5

### start — dr_orin

Quỳnh nhận chuẩn bị bàn rồi. Tôi muốn gặp Hale, không muốn mở lại đúng cuộc cãi của năm đó.

- Người chơi: Vậy tôi báo Quỳnh hai ông sẽ gặp lại.
  → NPC: Được. Lần này tôi tự chọn giờ kết thúc.; hành động: chỉ chuyển nhánh
- Người chơi: Ông có muốn gặp Hale mà không phân thắng thua không?
  → NPC: Có. Tôi sẽ hỏi ông ấy dạo này ngủ được không trước. Chuyện cũ vẫn cần nói, nhưng không nhất thiết nói bằng một cuộc tranh luận kéo dài.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.; hành động: chỉ chuyển nhánh

### answer — dr_orin

Có. Tôi sẽ hỏi ông ấy dạo này ngủ được không trước. Chuyện cũ vẫn cần nói, nhưng không nhất thiết nói bằng một cuộc tranh luận kéo dài.

- Người chơi: Vậy tôi báo Quỳnh hai ông sẽ gặp lại.
  → NPC: Được. Lần này tôi tự chọn giờ kết thúc.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.; hành động: chỉ chuyển nhánh

### accepted — dr_orin

Được. Lần này tôi tự chọn giờ kết thúc.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — dr_orin

Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rook_returned_wallet.1

### start — town3_accountant_tram

Tôi mất ví ở khu trao đổi. Rook nhắc tới nó trước khi tôi báo mất, nên tôi muốn hỏi từ chỗ đó.

- Người chơi: Tôi sẽ hỏi Rook về chiếc ví.
  → NPC: Cảm ơn. Tôi cần lấy đồ của mình, chưa cần nghe một định nghĩa mới về việc mượn.; hành động: chỉ chuyển nhánh
- Người chơi: Cô mất ví ở đâu?
  → NPC: Ở khu trao đổi. Rook biết trước khi tôi nói với ai. Bạn hỏi ông ấy xem có giữ ví hay giữ giấy nhận lại ví không.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_accountant_tram

Ở khu trao đổi. Rook biết trước khi tôi nói với ai. Bạn hỏi ông ấy xem có giữ ví hay giữ giấy nhận lại ví không.

- Người chơi: Tôi sẽ hỏi Rook về chiếc ví.
  → NPC: Cảm ơn. Tôi cần lấy đồ của mình, chưa cần nghe một định nghĩa mới về việc mượn.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_accountant_tram

Cảm ơn. Tôi cần lấy đồ của mình, chưa cần nghe một định nghĩa mới về việc mượn.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_accountant_tram

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rook_returned_wallet.2

### start — rook

Ví của Trâm à? Tôi có một tờ giấy liên quan. Trước khi cậu nhìn tôi như vậy, hãy hỏi rõ tôi đang giữ thứ gì.

- Người chơi: Tôi sẽ kiểm tra sổ đồ thất lạc.
  → NPC: Được. Sổ sẽ ghi rõ ví đã được giao, không phải tôi đang giữ tiền của cô ấy.; hành động: chỉ chuyển nhánh
- Người chơi: Ông đang giữ ví hay giữ biên nhận?
  → NPC: Biên nhận. Ví ở quầy thất lạc. Tôi định thu phí mang giấy tới, nhưng nghe cậu hỏi thì có vẻ Trâm chưa đồng ý khoản đó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — rook

Biên nhận. Ví ở quầy thất lạc. Tôi định thu phí mang giấy tới, nhưng nghe cậu hỏi thì có vẻ Trâm chưa đồng ý khoản đó.

- Người chơi: Tôi sẽ kiểm tra sổ đồ thất lạc.
  → NPC: Được. Sổ sẽ ghi rõ ví đã được giao, không phải tôi đang giữ tiền của cô ấy.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — rook

Được. Sổ sẽ ghi rõ ví đã được giao, không phải tôi đang giữ tiền của cô ấy.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — rook

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rook_returned_wallet.3

### start — narrator

Quầy thất lạc có một dòng nhận ví của Trâm. Biên nhận giao trả chưa ở cùng cuốn sổ.

- Người chơi: Tôi sẽ báo Trâm ví đã về quầy.
  → NPC: Bạn giữ tên quầy và số biên nhận để Trâm tự nhận được đồ.; hành động: chỉ chuyển nhánh
- Người chơi: Ví có được ghi nhận còn đủ tiền không?
  → NPC: Có. Quầy đã nhận, Rook giữ biên nhận và định thu phí. Trâm cần biết ví ở đâu trước khi quyết định có nhờ ai lấy hộ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Có. Quầy đã nhận, Rook giữ biên nhận và định thu phí. Trâm cần biết ví ở đâu trước khi quyết định có nhờ ai lấy hộ.

- Người chơi: Tôi sẽ báo Trâm ví đã về quầy.
  → NPC: Bạn giữ tên quầy và số biên nhận để Trâm tự nhận được đồ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ tên quầy và số biên nhận để Trâm tự nhận được đồ.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rook_returned_wallet.4

### start — town3_accountant_tram

Ví đã ở quầy rồi, nhưng Rook giữ biên nhận. Tôi chưa từng nhờ ông ấy đặt một dịch vụ mới cho mình.

- Người chơi: Tôi sẽ yêu cầu Rook trả biên nhận không thu phí.
  → NPC: Cảm ơn. Nói đúng là tôi không chấp nhận phí, không phải chưa đủ tiền.; hành động: chỉ chuyển nhánh
- Người chơi: Cô có muốn trả khoản phí Rook đặt ra không?
  → NPC: Không. Ông ấy đưa giấy ngay thì tôi đã cảm ơn. Tôi không nhờ giữ biên nhận để rồi phải mua đường lấy lại ví.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_accountant_tram

Không. Ông ấy đưa giấy ngay thì tôi đã cảm ơn. Tôi không nhờ giữ biên nhận để rồi phải mua đường lấy lại ví.

- Người chơi: Tôi sẽ yêu cầu Rook trả biên nhận không thu phí.
  → NPC: Cảm ơn. Nói đúng là tôi không chấp nhận phí, không phải chưa đủ tiền.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_accountant_tram

Cảm ơn. Nói đúng là tôi không chấp nhận phí, không phải chưa đủ tiền.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_accountant_tram

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## rook_returned_wallet.5

### start — rook

Trâm không nhận khoản phí à? Được. Tôi vẫn còn biên nhận ở đây, chưa chuyển cho ai khác.

- Người chơi: Tôi nhận biên nhận để Trâm lấy ví.
  → NPC: Được. Lần này không có khoản phụ nào khi cậu quay lưng.; hành động: chỉ chuyển nhánh
- Người chơi: Trâm không nhận khoản phí. Ông có trả biên nhận không?
  → NPC: Có. Tôi sẽ đưa giấy và ngừng gọi nó là dịch vụ được đặt trước. Cô ấy cũng nhắc tôi giữ hóa đơn thuế; nhắc rất kỹ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — rook

Có. Tôi sẽ đưa giấy và ngừng gọi nó là dịch vụ được đặt trước. Cô ấy cũng nhắc tôi giữ hóa đơn thuế; nhắc rất kỹ.

- Người chơi: Tôi nhận biên nhận để Trâm lấy ví.
  → NPC: Được. Lần này không có khoản phụ nào khi cậu quay lưng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — rook

Được. Lần này không có khoản phụ nào khi cậu quay lưng.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — rook

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## courier_route.1

### start — town3_courier_nam

Bản tuyến bắt tôi qua cùng ngã ba mấy lần. Tôi muốn đối chiếu đoạn nào đã đổi trước khi sửa lịch giao.

- Người chơi: Tôi sẽ kiểm tra bản tuyến giao cũ.
  → NPC: Cảm ơn. Tôi đánh dấu những đoạn mình đã đi lại, không tự sửa đường trước khi xác nhận.; hành động: chỉ chuyển nhánh
- Người chơi: Anh đang bị bản đồ dẫn vòng ở đoạn nào?
  → NPC: Ngã ba gần cây cầu cũ. Tôi phải quay lại chỗ đã giao rồi mới tới bến. Bạn xem bản tuyến ở bàn giúp tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_courier_nam

Ngã ba gần cây cầu cũ. Tôi phải quay lại chỗ đã giao rồi mới tới bến. Bạn xem bản tuyến ở bàn giúp tôi.

- Người chơi: Tôi sẽ kiểm tra bản tuyến giao cũ.
  → NPC: Cảm ơn. Tôi đánh dấu những đoạn mình đã đi lại, không tự sửa đường trước khi xác nhận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_courier_nam

Cảm ơn. Tôi đánh dấu những đoạn mình đã đi lại, không tự sửa đường trước khi xác nhận.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_courier_nam

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## courier_route.2

### start — narrator

Bản đồ giao hàng còn giữ cây cầu cũ. Cạnh nó có một ghi chú đóng lối mới được thêm sau này.

- Người chơi: Tôi sẽ đến bến hỏi Bình về lối còn mở.
  → NPC: Bạn ghi lại đoạn cầu đã đóng để đối chiếu với lối mới.; hành động: chỉ chuyển nhánh
- Người chơi: Cây cầu trên bản đồ còn mở không?
  → NPC: Cầu đã đóng. Bản tuyến vẫn dùng lối đó, khiến Nam đi vòng rồi quay lại điểm cũ. Bình ở bến biết lối thay thế.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Cầu đã đóng. Bản tuyến vẫn dùng lối đó, khiến Nam đi vòng rồi quay lại điểm cũ. Bình ở bến biết lối thay thế.

- Người chơi: Tôi sẽ đến bến hỏi Bình về lối còn mở.
  → NPC: Bạn ghi lại đoạn cầu đã đóng để đối chiếu với lối mới.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn ghi lại đoạn cầu đã đóng để đối chiếu với lối mới.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## courier_route.3

### start — town8_dockworker_binh

Nam đang hỏi đường tới bến à? Có một lối chúng tôi dùng gần đây, nhưng chưa ghi vào bản tuyến của anh ấy.

- Người chơi: Tôi sẽ mang thông tin và giấy về cho Nam.
  → NPC: Cảm ơn. Tôi sẽ ghi người nhận tin mỗi khi cảng đổi lối.; hành động: chỉ chuyển nhánh
- Người chơi: Bến có đường nào thay cây cầu cũ?
  → NPC: Có lối mới vào phía bên kia. Chúng tôi chưa báo Nam vì ai cũng tưởng anh ấy đã biết. Anh ấy cần hai tờ giấy cập nhật tuyến.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town8_dockworker_binh

Có lối mới vào phía bên kia. Chúng tôi chưa báo Nam vì ai cũng tưởng anh ấy đã biết. Anh ấy cần hai tờ giấy cập nhật tuyến.

- Người chơi: Tôi sẽ mang thông tin và giấy về cho Nam.
  → NPC: Cảm ơn. Tôi sẽ ghi người nhận tin mỗi khi cảng đổi lối.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town8_dockworker_binh

Cảm ơn. Tôi sẽ ghi người nhận tin mỗi khi cảng đổi lối.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: close

### declined — town8_dockworker_binh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## courier_route.4

### start — town3_courier_nam

Tôi có thông tin lối mới rồi. Giờ cần cập nhật bản tuyến và giữ một bản ở bàn giao hàng.

- Người chơi: Tôi giao hai tờ giấy để anh cập nhật tuyến.
  → NPC: Cảm ơn. Nhờ Bình xác nhận anh ấy sẽ báo thay đổi giúp tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có đủ thông tin để vẽ tuyến mới chưa?
  → NPC: Có. Hai tờ giấy đủ để ghi đường vào bến và giữ một bản đối chiếu. Bình cần báo cho tôi trực tiếp nếu lối ấy đổi nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_courier_nam

Có. Hai tờ giấy đủ để ghi đường vào bến và giữ một bản đối chiếu. Bình cần báo cho tôi trực tiếp nếu lối ấy đổi nữa.

- Người chơi: Tôi giao hai tờ giấy để anh cập nhật tuyến.
  → NPC: Cảm ơn. Nhờ Bình xác nhận anh ấy sẽ báo thay đổi giúp tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_courier_nam

Cảm ơn. Nhờ Bình xác nhận anh ấy sẽ báo thay đổi giúp tôi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_courier_nam

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## courier_route.5

### start — town8_dockworker_binh

Nam đã đổi tuyến. Phần còn lại là ai báo cho anh ấy nếu cảng thay lối thêm lần nữa.

- Người chơi: Vậy tuyến giao đã có người cập nhật rõ ràng.
  → NPC: Cảm ơn. Nam sẽ nhận được tin trước chuyến đầu, không phải sau khi đi vòng.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có nhận làm đầu mối báo đổi lối cho Nam không?
  → NPC: Có. Tôi đã ghi tên Nam và cách liên lạc. Lần sau tôi báo anh ấy, không chỉ dán giấy rồi nghĩ mọi người tự biết.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town8_dockworker_binh

Có. Tôi đã ghi tên Nam và cách liên lạc. Lần sau tôi báo anh ấy, không chỉ dán giấy rồi nghĩ mọi người tự biết.

- Người chơi: Vậy tuyến giao đã có người cập nhật rõ ràng.
  → NPC: Cảm ơn. Nam sẽ nhận được tin trước chuyến đầu, không phải sau khi đi vòng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town8_dockworker_binh

Cảm ơn. Nam sẽ nhận được tin trước chuyến đầu, không phải sau khi đi vòng.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town8_dockworker_binh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## selene_wrong_rumor.1

### start — town4_keeper_yen

Tôi nghe chuyện Selene nhận một phong bì. Người kể thêm nhiều chi tiết, nhưng tôi chưa biết họ thật sự nhìn thấy phần nào.

- Người chơi: Tôi sẽ hỏi Selene về phong bì.
  → NPC: Cảm ơn. Tôi không muốn kể tiếp phần mình chưa kiểm tra.; hành động: chỉ chuyển nhánh
- Người chơi: Người ta thấy gì trước khi đồn Selene nhận tiền?
  → NPC: Chỉ một phong bì. Số tiền và lý do nhận đều là phần kể thêm. Bạn hỏi Selene rồi đối chiếu giấy tờ giúp tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town4_keeper_yen

Chỉ một phong bì. Số tiền và lý do nhận đều là phần kể thêm. Bạn hỏi Selene rồi đối chiếu giấy tờ giúp tôi.

- Người chơi: Tôi sẽ hỏi Selene về phong bì.
  → NPC: Cảm ơn. Tôi không muốn kể tiếp phần mình chưa kiểm tra.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town4_keeper_yen

Cảm ơn. Tôi không muốn kể tiếp phần mình chưa kiểm tra.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town4_keeper_yen

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## selene_wrong_rumor.2

### start — selene_kade

Phong bì hôm đó liên quan đến thuốc. Tôi có thể cho cậu xem phần biên nhận không lộ người đang điều trị.

- Người chơi: Tôi sẽ xem biên nhận đã che tên.
  → NPC: Được. Tôi giữ bản đầy đủ cho người có trách nhiệm kiểm tra điều trị.; hành động: chỉ chuyển nhánh
- Người chơi: Phong bì chị nhận có gì?
  → NPC: Danh sách thuốc. Tôi có biên nhận đã che tên bệnh nhân; bạn xem giá và số lượng được, không cần biết người đang điều trị là ai.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — selene_kade

Danh sách thuốc. Tôi có biên nhận đã che tên bệnh nhân; bạn xem giá và số lượng được, không cần biết người đang điều trị là ai.

- Người chơi: Tôi sẽ xem biên nhận đã che tên.
  → NPC: Được. Tôi giữ bản đầy đủ cho người có trách nhiệm kiểm tra điều trị.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — selene_kade

Được. Tôi giữ bản đầy đủ cho người có trách nhiệm kiểm tra điều trị.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — selene_kade

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## selene_wrong_rumor.3

### start — narrator

Biên nhận giữ giá, số lượng và số lô. Phần tên bệnh nhân được che riêng.

- Người chơi: Tôi sẽ nhờ Kim xác nhận lô thuốc.
  → NPC: Bạn giữ số lô và tổng tiền, không chép phần tên đã được che.; hành động: chỉ chuyển nhánh
- Người chơi: Che tên bệnh nhân có khiến khoản chi không kiểm tra được không?
  → NPC: Không. Giá, số lượng và số lô vẫn khớp kho. Bác sĩ Kim có thể xác nhận thuốc đã tới mà không công khai bệnh nhân.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Giá, số lượng và số lô vẫn khớp kho. Bác sĩ Kim có thể xác nhận thuốc đã tới mà không công khai bệnh nhân.

- Người chơi: Tôi sẽ nhờ Kim xác nhận lô thuốc.
  → NPC: Bạn giữ số lô và tổng tiền, không chép phần tên đã được che.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ số lô và tổng tiền, không chép phần tên đã được che.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## selene_wrong_rumor.4

### start — town2_doctor_kim

Lô thuốc đó có hồ sơ ở chỗ tôi. Bạn cần xác nhận hàng nhận, không cần danh sách bệnh nhân.

- Người chơi: Tôi sẽ báo Yến số lượng và khoản chi đã khớp.
  → NPC: Cảm ơn. Nhớ giữ phần riêng tư của người bệnh ngoài cuộc nói chuyện.; hành động: chỉ chuyển nhánh
- Người chơi: Lô thuốc trên biên nhận có tới đủ không?
  → NPC: Có. Tôi xác nhận số lượng, nhưng không đưa tên bệnh nhân ra để giải thích một lời đồn. Yến chỉ cần những phần liên quan khoản chi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_doctor_kim

Có. Tôi xác nhận số lượng, nhưng không đưa tên bệnh nhân ra để giải thích một lời đồn. Yến chỉ cần những phần liên quan khoản chi.

- Người chơi: Tôi sẽ báo Yến số lượng và khoản chi đã khớp.
  → NPC: Cảm ơn. Nhớ giữ phần riêng tư của người bệnh ngoài cuộc nói chuyện.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_doctor_kim

Cảm ơn. Nhớ giữ phần riêng tư của người bệnh ngoài cuộc nói chuyện.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_doctor_kim

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## selene_wrong_rumor.5

### start — town4_keeper_yen

Tôi đã nghe phần đối chiếu. Giờ tôi phải nói lại với những người từng nghe câu chuyện từ mình.

- Người chơi: Vậy tôi xác nhận phần giấy tờ đã được kiểm tra.
  → NPC: Cảm ơn. Tôi tự nói lại với những người đã nghe từ mình.; hành động: chỉ chuyển nhánh
- Người chơi: Chị có đính chính ở nơi đã kể lời đồn không?
  → NPC: Có. Tôi sẽ nói rõ mình chỉ thấy phong bì, còn biên nhận chứng minh đó là thuốc. Không thể im đi rồi coi như mọi người đã hiểu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town4_keeper_yen

Có. Tôi sẽ nói rõ mình chỉ thấy phong bì, còn biên nhận chứng minh đó là thuốc. Không thể im đi rồi coi như mọi người đã hiểu.

- Người chơi: Vậy tôi xác nhận phần giấy tờ đã được kiểm tra.
  → NPC: Cảm ơn. Tôi tự nói lại với những người đã nghe từ mình.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town4_keeper_yen

Cảm ơn. Tôi tự nói lại với những người đã nghe từ mình.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town4_keeper_yen

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## garden_water.1

### start — town4_gardener_luc

Tối nào tôi cũng ra tưới mà nước ít dần. Tôi đã cãi với Yến, nhưng chưa hỏi máy bơm chạy giờ nào.

- Người chơi: Tôi sẽ hỏi Yến giờ bơm thực tế.
  → NPC: Cảm ơn. Tôi muốn cây có nước trước khi biết ai thắng cuộc cãi này.; hành động: chỉ chuyển nhánh
- Người chơi: Anh nghĩ Yến lấy nước vào giờ nào?
  → NPC: Buổi sáng, còn tôi tưới tối. Gần đây tối không có nước, tôi chưa hỏi giờ bơm đã đổi chưa. Bạn giúp tôi kiểm tra nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town4_gardener_luc

Buổi sáng, còn tôi tưới tối. Gần đây tối không có nước, tôi chưa hỏi giờ bơm đã đổi chưa. Bạn giúp tôi kiểm tra nhé.

- Người chơi: Tôi sẽ hỏi Yến giờ bơm thực tế.
  → NPC: Cảm ơn. Tôi muốn cây có nước trước khi biết ai thắng cuộc cãi này.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town4_gardener_luc

Cảm ơn. Tôi muốn cây có nước trước khi biết ai thắng cuộc cãi này.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town4_gardener_luc

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## garden_water.2

### start — town4_keeper_yen

Lực đang đợi nước vào buổi tối à? Tôi nghĩ anh ấy chưa thấy bảng vận hành mới.

- Người chơi: Tôi sẽ đối chiếu hai bảng giờ ở van.
  → NPC: Được. Tôi chưa tăng phần nước của mình; cứ kiểm tra giờ trước.; hành động: chỉ chuyển nhánh
- Người chơi: Chị có đổi giờ bơm chung không?
  → NPC: Bảng vận hành đổi sang ban ngày. Tôi tưởng Lực đã thấy thông báo. Cậu xem cả bảng cũ ở van sẽ hiểu vì sao anh ấy chờ ban đêm.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town4_keeper_yen

Bảng vận hành đổi sang ban ngày. Tôi tưởng Lực đã thấy thông báo. Cậu xem cả bảng cũ ở van sẽ hiểu vì sao anh ấy chờ ban đêm.

- Người chơi: Tôi sẽ đối chiếu hai bảng giờ ở van.
  → NPC: Được. Tôi chưa tăng phần nước của mình; cứ kiểm tra giờ trước.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town4_keeper_yen

Được. Tôi chưa tăng phần nước của mình; cứ kiểm tra giờ trước.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town4_keeper_yen

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## garden_water.3

### start — narrator

Ở van có hai bảng lịch, một cũ và một mới. Bạn có thể đối chiếu giờ chạy máy trên từng bảng.

- Người chơi: Tôi sẽ mang hai thỏi sắt cho Lực.
  → NPC: Bạn giữ lại giờ đã đổi để viết đúng trên bảng thay thế.; hành động: chỉ chuyển nhánh
- Người chơi: Hai bảng đang ghi giờ khác nhau ở đâu?
  → NPC: Bảng cũ ghi chạy đêm, bảng vận hành mới ghi chạy ngày. Lực cần hai thỏi sắt làm bảng giờ mới ở van chung.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Bảng cũ ghi chạy đêm, bảng vận hành mới ghi chạy ngày. Lực cần hai thỏi sắt làm bảng giờ mới ở van chung.

- Người chơi: Tôi sẽ mang hai thỏi sắt cho Lực.
  → NPC: Bạn giữ lại giờ đã đổi để viết đúng trên bảng thay thế.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ lại giờ đã đổi để viết đúng trên bảng thay thế.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## garden_water.4

### start — town4_gardener_luc

Tôi muốn lịch tưới rõ trước khi nhận thêm nước. Bảng ở van cần thay để không treo hai giờ khác nhau.

- Người chơi: Tôi giao hai thỏi sắt làm bảng giờ mới.
  → NPC: Cảm ơn. Nhờ Yến xác nhận khung giờ của cô ấy giúp tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Anh muốn ghi phần tưới của cả hai lên bảng chứ?
  → NPC: Có. Tôi nhận buổi chiều nếu Yến nhận buổi sáng. Hai thỏi sắt đủ làm bảng chắc để giờ cũ không còn treo bên cạnh.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town4_gardener_luc

Có. Tôi nhận buổi chiều nếu Yến nhận buổi sáng. Hai thỏi sắt đủ làm bảng chắc để giờ cũ không còn treo bên cạnh.

- Người chơi: Tôi giao hai thỏi sắt làm bảng giờ mới.
  → NPC: Cảm ơn. Nhờ Yến xác nhận khung giờ của cô ấy giúp tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town4_gardener_luc

Cảm ơn. Nhờ Yến xác nhận khung giờ của cô ấy giúp tôi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town4_gardener_luc

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## garden_water.5

### start — town4_keeper_yen

Lực đã đề nghị chia giờ. Tôi muốn ghi cả phần của mình lên bảng mới để khỏi phải đoán nhau nữa.

- Người chơi: Vậy lịch tưới chung đã thống nhất.
  → NPC: Cảm ơn. Tôi sẽ báo Lực ngay nếu có thay đổi.; hành động: chỉ chuyển nhánh
- Người chơi: Chị đồng ý tưới sáng và để Lực tưới chiều không?
  → NPC: Đồng ý. Tôi đã ghi cả hai tên lên bảng. Nếu bơm đổi giờ nữa, chúng tôi báo nhau trước khi cho rằng người kia lấy thêm nước.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town4_keeper_yen

Đồng ý. Tôi đã ghi cả hai tên lên bảng. Nếu bơm đổi giờ nữa, chúng tôi báo nhau trước khi cho rằng người kia lấy thêm nước.

- Người chơi: Vậy lịch tưới chung đã thống nhất.
  → NPC: Cảm ơn. Tôi sẽ báo Lực ngay nếu có thay đổi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town4_keeper_yen

Cảm ơn. Tôi sẽ báo Lực ngay nếu có thay đổi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town4_keeper_yen

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mara_missed_call.1

### start — mara_voss

Tôi gọi cho cậu nhưng không thấy hồi âm. Không có chuyện khẩn ở sân; tôi chỉ muốn biết cậu đã tới nơi chưa.

- Người chơi: Tôi sẽ nói chuyện với Hale rồi báo cậu.
  → NPC: Được. Lần sau chỉ một tin “đã tới” cũng đủ.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu gọi vì có chuyện ở sân tập sao?
  → NPC: Không. Tôi không nhận được tin cậu về, nên muốn kiểm tra. Nếu thấy tôi lo quá, hỏi Hale; ông ấy biết vì sao tôi không thích những cuộc hẹn mất hồi âm.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### answer — mara_voss

Không. Tôi không nhận được tin cậu về, nên muốn kiểm tra. Nếu thấy tôi lo quá, hỏi Hale; ông ấy biết vì sao tôi không thích những cuộc hẹn mất hồi âm.

- Người chơi: Tôi sẽ nói chuyện với Hale rồi báo cậu.
  → NPC: Được. Lần sau chỉ một tin “đã tới” cũng đủ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### accepted — mara_voss

Được. Lần sau chỉ một tin “đã tới” cũng đủ.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — mara_voss

Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mara_missed_call.2

### start — professor_hale

Mara hỏi cậu đã về chưa à? Ta biết vì sao cô ấy khó yên tâm khi một cuộc hẹn mất hồi âm.

- Người chơi: Tôi sẽ đọc giấy hẹn rồi gặp Mara.
  → NPC: Được. Đừng dùng chuyện cô ấy lo làm trò trêu nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Vì sao Mara lo khi không nhận được hồi âm?
  → NPC: Một người trong nhóm cũ của cô ấy từng không trả lời rồi không trở về. Giấy hẹn ở sân cho thấy cô ấy chỉ muốn cậu tới, không muốn đặt thêm một bài thử.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.; hành động: chỉ chuyển nhánh

### answer — professor_hale

Một người trong nhóm cũ của cô ấy từng không trả lời rồi không trở về. Giấy hẹn ở sân cho thấy cô ấy chỉ muốn cậu tới, không muốn đặt thêm một bài thử.

- Người chơi: Tôi sẽ đọc giấy hẹn rồi gặp Mara.
  → NPC: Được. Đừng dùng chuyện cô ấy lo làm trò trêu nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.; hành động: chỉ chuyển nhánh

### accepted — professor_hale

Được. Đừng dùng chuyện cô ấy lo làm trò trêu nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — professor_hale

Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mara_missed_call.3

### start — narrator

Giấy hẹn của Mara nằm cạnh sân. Có một dòng được gạch rồi viết lại dưới giờ hẹn.

- Người chơi: Tôi sẽ tới nhận trận tập với Mara.
  → NPC: Bạn giữ đúng lời hẹn, không thêm điều kiện mà Mara chưa đặt.; hành động: chỉ chuyển nhánh
- Người chơi: Mara có đặt điều kiện phải thắng trong giấy hẹn không?
  → NPC: Không. Giấy ghi không cần thắng, chỉ cần tới. Cô ấy muốn có một trận tập và một cuộc hẹn được giữ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Giấy ghi không cần thắng, chỉ cần tới. Cô ấy muốn có một trận tập và một cuộc hẹn được giữ.

- Người chơi: Tôi sẽ tới nhận trận tập với Mara.
  → NPC: Bạn giữ đúng lời hẹn, không thêm điều kiện mà Mara chưa đặt.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ đúng lời hẹn, không thêm điều kiện mà Mara chưa đặt.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mara_missed_call.4

### start — mara_voss

Cậu tới rồi. Tôi vẫn mang đội tập ra sân, nếu cậu muốn giữ cuộc hẹn hôm nay.

- Người chơi: Vậy bắt đầu trận tập.
  → NPC: Được. Sau trận cậu đừng rời đi trước khi hẹn giờ báo tin nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu muốn trận này vẫn là một trận nghiêm túc chứ?
  → NPC: Có. Tôi lo cho cậu, nhưng vẫn muốn thắng. Chúng ta đấu xong rồi nói cách báo tin khi đi xa.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### answer — mara_voss

Có. Tôi lo cho cậu, nhưng vẫn muốn thắng. Chúng ta đấu xong rồi nói cách báo tin khi đi xa.

- Người chơi: Vậy bắt đầu trận tập.
  → NPC: Được. Sau trận cậu đừng rời đi trước khi hẹn giờ báo tin nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### accepted — mara_voss

Được. Sau trận cậu đừng rời đi trước khi hẹn giờ báo tin nhé.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — mara_voss

Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mara_missed_call.5

### start — mara_voss

Trận xong rồi. Tôi muốn thống nhất một cách báo tin khi một trong hai người đi xa.

- Người chơi: Tôi đồng ý báo khi tới nơi hoặc đổi kế hoạch.
  → NPC: Được. Tôi cũng làm như thế với cậu.; hành động: chỉ chuyển nhánh
- Người chơi: Một tin khi tới nơi và một tin khi đổi kế hoạch có đủ không?
  → NPC: Đủ. Tôi không cần cậu báo từng bước. Nếu trễ thì nói trễ, để tôi biết đang chờ chứ không phải đang tìm người.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### answer — mara_voss

Đủ. Tôi không cần cậu báo từng bước. Nếu trễ thì nói trễ, để tôi biết đang chờ chứ không phải đang tìm người.

- Người chơi: Tôi đồng ý báo khi tới nơi hoặc đổi kế hoạch.
  → NPC: Được. Tôi cũng làm như thế với cậu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### accepted — mara_voss

Được. Tôi cũng làm như thế với cậu.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — mara_voss

Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## ranger_lost_boot.1

### start — town5_ranger_hanh

Đội tìm thấy một chiếc ủng mắc bùn. Tôi muốn đọc thêm dấu đường trước khi gửi báo mất người.

- Người chơi: Tôi sẽ đọc dấu bùn gần chiếc ủng.
  → NPC: Cảm ơn. Tôi giữ trạm liên lạc trong lúc bạn kiểm tra.; hành động: chỉ chuyển nhánh
- Người chơi: Có dấu hiệu người đi rừng chưa trở về không?
  → NPC: Chỉ mới thấy một chiếc ủng mắc bùn. Chúng tôi chưa tìm chiếc còn lại. Bạn xem hướng dấu chân trước khi báo một tình huống xấu nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town5_ranger_hanh

Chỉ mới thấy một chiếc ủng mắc bùn. Chúng tôi chưa tìm chiếc còn lại. Bạn xem hướng dấu chân trước khi báo một tình huống xấu nhé.

- Người chơi: Tôi sẽ đọc dấu bùn gần chiếc ủng.
  → NPC: Cảm ơn. Tôi giữ trạm liên lạc trong lúc bạn kiểm tra.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town5_ranger_hanh

Cảm ơn. Tôi giữ trạm liên lạc trong lúc bạn kiểm tra.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town5_ranger_hanh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## ranger_lost_boot.2

### start — narrator

Dấu quanh chiếc ủng có cả giày và chân trần. Bạn có thể theo hướng dấu mới về phía trạm.

- Người chơi: Tôi sẽ đến trạm Vũ kiểm tra.
  → NPC: Bạn ghi lại hướng dấu chân, không kết thúc tìm kiếm chỉ từ một suy đoán.; hành động: chỉ chuyển nhánh
- Người chơi: Dấu chân đi sâu vào rừng hay về trạm?
  → NPC: Dấu chân trần quay về trạm. Người đi có vẻ bỏ chiếc ủng bị kẹt. Cần đến chỗ Vũ xác nhận tên người đã trở về.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Dấu chân trần quay về trạm. Người đi có vẻ bỏ chiếc ủng bị kẹt. Cần đến chỗ Vũ xác nhận tên người đã trở về.

- Người chơi: Tôi sẽ đến trạm Vũ kiểm tra.
  → NPC: Bạn ghi lại hướng dấu chân, không kết thúc tìm kiếm chỉ từ một suy đoán.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn ghi lại hướng dấu chân, không kết thúc tìm kiếm chỉ từ một suy đoán.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## ranger_lost_boot.3

### start — town5_firewatch_vu

Có người vừa tới hong tất ở đây. Tôi chưa nhận được tin bên trạm Hạnh đang tìm ai.

- Người chơi: Tôi sẽ báo Hạnh và mang len cho trạm.
  → NPC: Cảm ơn. Tôi ghi tên người tới ngay, không chỉ những người bị thương.; hành động: chỉ chuyển nhánh
- Người chơi: Người bỏ chiếc ủng có tới trạm anh không?
  → NPC: Có, đang hong tất. Máy liên lạc hết pin, tôi chưa biết bên Hạnh đang tìm. Cô ấy cần hai tấm len trắng cho đồ giữ ấm ở trạm.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town5_firewatch_vu

Có, đang hong tất. Máy liên lạc hết pin, tôi chưa biết bên Hạnh đang tìm. Cô ấy cần hai tấm len trắng cho đồ giữ ấm ở trạm.

- Người chơi: Tôi sẽ báo Hạnh và mang len cho trạm.
  → NPC: Cảm ơn. Tôi ghi tên người tới ngay, không chỉ những người bị thương.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town5_firewatch_vu

Cảm ơn. Tôi ghi tên người tới ngay, không chỉ những người bị thương.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: close

### declined — town5_firewatch_vu

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## ranger_lost_boot.4

### start — town5_ranger_hanh

Vũ đã gửi tên người trở về. Tôi muốn chuẩn bị sẵn đồ giữ ấm cho trường hợp tương tự.

- Người chơi: Tôi giao hai tấm len trắng cho Hạnh.
  → NPC: Cảm ơn. Nhờ Vũ xác nhận quy trình ghi tên mới nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Hai tấm len này sẽ được giữ sẵn ở trạm chứ?
  → NPC: Đúng. Tôi đã nhận tên người về an toàn. Len dùng cho bộ giữ ấm, còn Vũ cần ghi tên mọi người tới để chúng tôi báo nhau được.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town5_ranger_hanh

Đúng. Tôi đã nhận tên người về an toàn. Len dùng cho bộ giữ ấm, còn Vũ cần ghi tên mọi người tới để chúng tôi báo nhau được.

- Người chơi: Tôi giao hai tấm len trắng cho Hạnh.
  → NPC: Cảm ơn. Nhờ Vũ xác nhận quy trình ghi tên mới nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town5_ranger_hanh

Cảm ơn. Nhờ Vũ xác nhận quy trình ghi tên mới nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town5_ranger_hanh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## ranger_lost_boot.5

### start — town5_firewatch_vu

Tôi đã nhận cách ghi tên từ Hạnh. Có người không bị thương nhưng vẫn cần được báo là đã về.

- Người chơi: Vậy hai trạm đã xác nhận người về đủ.
  → NPC: Cảm ơn. Tôi gửi tin bằng tên người, không chỉ nói chắc mọi thứ ổn.; hành động: chỉ chuyển nhánh
- Người chơi: Anh đã ghi cả người tới mà không bị thương chưa?
  → NPC: Rồi. Tôi giữ tên và trạm cần báo. Người không bị thương vẫn có thể đang được đội khác tìm, tôi không bỏ qua phần đó nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town5_firewatch_vu

Rồi. Tôi giữ tên và trạm cần báo. Người không bị thương vẫn có thể đang được đội khác tìm, tôi không bỏ qua phần đó nữa.

- Người chơi: Vậy hai trạm đã xác nhận người về đủ.
  → NPC: Cảm ơn. Tôi gửi tin bằng tên người, không chỉ nói chắc mọi thứ ổn.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town5_firewatch_vu

Cảm ơn. Tôi gửi tin bằng tên người, không chỉ nói chắc mọi thứ ổn.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town5_firewatch_vu

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## firewatch_false_alarm.1

### start — town5_firewatch_vu

Ba lần báo trước đều không phải cháy rừng. Tôi muốn sửa nguồn báo sai trước khi gọi mọi người tập lại.

- Người chơi: Tôi sẽ kiểm tra cảm biến.
  → NPC: Cảm ơn. Tôi giữ bản giờ báo động để bạn so.; hành động: chỉ chuyển nhánh
- Người chơi: Ba báo động trước đều xuất phát từ đâu?
  → NPC: Từ khói bếp. Lần thứ tư không ai đứng lên. Bạn kiểm tra hướng cảm biến giúp tôi; cứ gọi mọi người đứng dậy mà không sửa lỗi thì chẳng được gì.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town5_firewatch_vu

Từ khói bếp. Lần thứ tư không ai đứng lên. Bạn kiểm tra hướng cảm biến giúp tôi; cứ gọi mọi người đứng dậy mà không sửa lỗi thì chẳng được gì.

- Người chơi: Tôi sẽ kiểm tra cảm biến.
  → NPC: Cảm ơn. Tôi giữ bản giờ báo động để bạn so.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town5_firewatch_vu

Cảm ơn. Tôi giữ bản giờ báo động để bạn so.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town5_firewatch_vu

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## firewatch_false_alarm.2

### start — narrator

Cảm biến đặt giữa bếp và phía rừng. Hướng đầu đo có thể giải thích những giờ báo sai.

- Người chơi: Tôi sẽ báo Warden hướng đặt đang sai.
  → NPC: Bạn giữ sơ đồ hướng bếp và rừng để chọn vị trí mới.; hành động: chỉ chuyển nhánh
- Người chơi: Cảm biến đang nhìn về rừng không?
  → NPC: Không. Nó hướng vào bếp và quay lưng với rừng. Cần xin Warden đổi vị trí rồi thử báo động lại, không bỏ hẳn máy.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Nó hướng vào bếp và quay lưng với rừng. Cần xin Warden đổi vị trí rồi thử báo động lại, không bỏ hẳn máy.

- Người chơi: Tôi sẽ báo Warden hướng đặt đang sai.
  → NPC: Bạn giữ sơ đồ hướng bếp và rừng để chọn vị trí mới.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ sơ đồ hướng bếp và rừng để chọn vị trí mới.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## firewatch_false_alarm.3

### start — sixth_warden

Vũ gửi sơ đồ cảm biến rồi. Ta sẽ đổi chỗ đặt và thử lại với các trạm được báo trước.

- Người chơi: Tôi sẽ mang hai redstone cho Vũ.
  → NPC: Được. Ta báo các trạm đây là buổi thử để không gây thêm hoảng loạn.; hành động: chỉ chuyển nhánh
- Người chơi: Ông có cho đổi vị trí cảm biến không?
  → NPC: Có. Chúng ta sửa nguồn báo sai rồi tập nhận báo mới. Vũ cần hai bụi redstone cho bộ báo thay thế.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — sixth_warden

Có. Chúng ta sửa nguồn báo sai rồi tập nhận báo mới. Vũ cần hai bụi redstone cho bộ báo thay thế.

- Người chơi: Tôi sẽ mang hai redstone cho Vũ.
  → NPC: Được. Ta báo các trạm đây là buổi thử để không gây thêm hoảng loạn.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — sixth_warden

Được. Ta báo các trạm đây là buổi thử để không gây thêm hoảng loạn.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — sixth_warden

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## firewatch_false_alarm.4

### start — town5_firewatch_vu

Bộ báo mới đã có tên trạm và vị trí. Mạch còn một phần trước buổi thử hôm nay.

- Người chơi: Tôi giao hai redstone cho bộ báo.
  → NPC: Cảm ơn. Bạn xác nhận Hạnh đã nhận được tin thử nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Bộ báo mới có gửi rõ vị trí không?
  → NPC: Có, nó ghi tên trạm và khu vực. Hai redstone đủ cho phần mạch còn thiếu. Tôi sẽ thử bằng nhiệt đúng hướng rừng.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town5_firewatch_vu

Có, nó ghi tên trạm và khu vực. Hai redstone đủ cho phần mạch còn thiếu. Tôi sẽ thử bằng nhiệt đúng hướng rừng.

- Người chơi: Tôi giao hai redstone cho bộ báo.
  → NPC: Cảm ơn. Bạn xác nhận Hạnh đã nhận được tin thử nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town5_firewatch_vu

Cảm ơn. Bạn xác nhận Hạnh đã nhận được tin thử nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town5_firewatch_vu

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## firewatch_false_alarm.5

### start — town5_ranger_hanh

Tôi nhận được tin thử của Vũ. Lần này có nội dung cụ thể để đối chiếu, không chỉ tiếng chuông.

- Người chơi: Vậy tôi xác nhận bộ báo mới đã được kiểm tra.
  → NPC: Cảm ơn. Lần sau chúng tôi có cơ sở để nhận báo thật nghiêm túc.; hành động: chỉ chuyển nhánh
- Người chơi: Tin thử có tới đủ vị trí và tên trạm không?
  → NPC: Có. Tôi biết phải tới đâu, không phải đoán từ một tiếng chuông. Tôi sẽ báo lại Vũ để anh ấy ghi kết quả thử.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town5_ranger_hanh

Có. Tôi biết phải tới đâu, không phải đoán từ một tiếng chuông. Tôi sẽ báo lại Vũ để anh ấy ghi kết quả thử.

- Người chơi: Vậy tôi xác nhận bộ báo mới đã được kiểm tra.
  → NPC: Cảm ơn. Lần sau chúng tôi có cơ sở để nhận báo thật nghiêm túc.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town5_ranger_hanh

Cảm ơn. Lần sau chúng tôi có cơ sở để nhận báo thật nghiêm túc.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town5_ranger_hanh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## warehouse_unpaid.1

### start — town6_informant_duc

Vex nói ca tôi làm không có trong sổ. Tôi còn thẻ giấy, nhưng cần người đối chiếu giúp.

- Người chơi: Tôi sẽ kiểm tra thẻ chấm công.
  → NPC: Cảm ơn. Tôi muốn chứng minh mình đã làm ca đó trước khi đòi tiền công.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có giấy nào ghi ca mà Vex phủ nhận không?
  → NPC: Có thẻ chấm công giấy. Bản máy mất tên tôi, nhưng thẻ vẫn ghi giờ. Nó bị bỏ riêng ở chỗ hồ sơ kho.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town6_informant_duc

Có thẻ chấm công giấy. Bản máy mất tên tôi, nhưng thẻ vẫn ghi giờ. Nó bị bỏ riêng ở chỗ hồ sơ kho.

- Người chơi: Tôi sẽ kiểm tra thẻ chấm công.
  → NPC: Cảm ơn. Tôi muốn chứng minh mình đã làm ca đó trước khi đòi tiền công.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town6_informant_duc

Cảm ơn. Tôi muốn chứng minh mình đã làm ca đó trước khi đòi tiền công.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town6_informant_duc

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## warehouse_unpaid.2

### start — narrator

Thẻ giấy và sổ máy được đặt cạnh nhau. Một ca có giờ trên giấy nhưng thiếu dòng tương ứng trong sổ.

- Người chơi: Tôi sẽ nhờ Trâm kiểm tra số giờ.
  → NPC: Bạn giữ nguyên thẻ giấy và phần ca bị thiếu trong sổ máy.; hành động: chỉ chuyển nhánh
- Người chơi: Thẻ giấy và sổ máy khác nhau thế nào?
  → NPC: Giấy còn tên và giờ, sổ máy mất cả ca. Chuyến hàng Rocket đã bị xóa cùng ca làm. Trâm có thể đối chiếu tổng giờ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Giấy còn tên và giờ, sổ máy mất cả ca. Chuyến hàng Rocket đã bị xóa cùng ca làm. Trâm có thể đối chiếu tổng giờ.

- Người chơi: Tôi sẽ nhờ Trâm kiểm tra số giờ.
  → NPC: Bạn giữ nguyên thẻ giấy và phần ca bị thiếu trong sổ máy.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ nguyên thẻ giấy và phần ca bị thiếu trong sổ máy.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## warehouse_unpaid.3

### start — town3_accountant_tram

Tôi đã tính số giờ Đức đưa. Cần lập hồ sơ có bản sao để người làm ca giữ được chứng cứ của mình.

- Người chơi: Tôi sẽ mang ba tờ giấy cho Châu.
  → NPC: Cảm ơn. Tôi ghi phép tính và ký phần đã đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Cô có xác nhận được số giờ dù kho của Rocket không hợp pháp không?
  → NPC: Có. Kho vi phạm không làm người đã làm việc mất quyền đòi tiền công. Châu cần ba tờ giấy để lập hồ sơ cho từng người.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_accountant_tram

Có. Kho vi phạm không làm người đã làm việc mất quyền đòi tiền công. Châu cần ba tờ giấy để lập hồ sơ cho từng người.

- Người chơi: Tôi sẽ mang ba tờ giấy cho Châu.
  → NPC: Cảm ơn. Tôi ghi phép tính và ký phần đã đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_accountant_tram

Cảm ơn. Tôi ghi phép tính và ký phần đã đối chiếu.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_accountant_tram

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## warehouse_unpaid.4

### start — town6_guard_chau

Tôi nhận phần lập hồ sơ. Giấy cần được chia cho từng người, không chỉ giữ một bản ở kho.

- Người chơi: Tôi giao ba tờ giấy lập hồ sơ.
  → NPC: Cảm ơn. Báo Đức đơn đã được nộp và anh ấy giữ bản nào nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có giữ bản sao cho người lao động không?
  → NPC: Có. Một bản ở mỗi người, một bản nộp hồ sơ. Tôi không giữ tất cả ở cùng một chỗ để lại có thể bị làm mất.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town6_guard_chau

Có. Một bản ở mỗi người, một bản nộp hồ sơ. Tôi không giữ tất cả ở cùng một chỗ để lại có thể bị làm mất.

- Người chơi: Tôi giao ba tờ giấy lập hồ sơ.
  → NPC: Cảm ơn. Báo Đức đơn đã được nộp và anh ấy giữ bản nào nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town6_guard_chau

Cảm ơn. Báo Đức đơn đã được nộp và anh ấy giữ bản nào nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town6_guard_chau

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## warehouse_unpaid.5

### start — town6_informant_duc

Tôi đã nhận tin đơn được nộp. Tôi muốn biết bản mình giữ và người cần hỏi ở lần tiếp theo.

- Người chơi: Vậy tôi xác nhận hồ sơ đã tới tay anh.
  → NPC: Cảm ơn. Tôi tự hỏi người nhận đơn từ đây, không bắt cậu đi thay mãi.; hành động: chỉ chuyển nhánh
- Người chơi: Anh đã nhận được bản sao và biết ai xử lý đơn chưa?
  → NPC: Rồi. Tôi chưa nhận tiền hôm nay, nhưng có giấy chứng minh giờ làm và tên người nhận đơn. Tôi sẽ giữ phần đó để hỏi tiếp.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town6_informant_duc

Rồi. Tôi chưa nhận tiền hôm nay, nhưng có giấy chứng minh giờ làm và tên người nhận đơn. Tôi sẽ giữ phần đó để hỏi tiếp.

- Người chơi: Vậy tôi xác nhận hồ sơ đã tới tay anh.
  → NPC: Cảm ơn. Tôi tự hỏi người nhận đơn từ đây, không bắt cậu đi thay mãi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town6_informant_duc

Cảm ơn. Tôi tự hỏi người nhận đơn từ đây, không bắt cậu đi thay mãi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town6_informant_duc

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## iris_first_repair.1

### start — lab_scientist_iris

Tôi muốn sửa phần dữ liệu còn đang ảnh hưởng công việc của Kim. Cậu giúp tôi hỏi phần nào cô ấy cần trước nhé.

- Người chơi: Tôi sẽ hỏi Kim phần còn thiếu.
  → NPC: Cảm ơn. Tôi chuẩn bị bản gốc để cô ấy kiểm tra được lời tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Cô muốn bắt đầu sửa phần dữ liệu nào?
  → NPC: Kim đang thiếu dữ liệu thuốc đúng. Tôi muốn tìm phần mình đã sửa sai và làm bản đối chiếu; lời xin lỗi không thay được dữ liệu cô ấy cần.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — lab_scientist_iris

Kim đang thiếu dữ liệu thuốc đúng. Tôi muốn tìm phần mình đã sửa sai và làm bản đối chiếu; lời xin lỗi không thay được dữ liệu cô ấy cần.

- Người chơi: Tôi sẽ hỏi Kim phần còn thiếu.
  → NPC: Cảm ơn. Tôi chuẩn bị bản gốc để cô ấy kiểm tra được lời tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — lab_scientist_iris

Cảm ơn. Tôi chuẩn bị bản gốc để cô ấy kiểm tra được lời tôi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — lab_scientist_iris

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## iris_first_repair.2

### start — town2_doctor_kim

Iris gửi lời muốn sửa dữ liệu rồi. Tôi cần bản đối chiếu cụ thể để biết liều nào đã bị đổi.

- Người chơi: Tôi sẽ đọc bảng liều lượng gốc của Iris.
  → NPC: Cảm ơn. Đối chiếu từng hàng bị đánh dấu, đừng chỉ đọc lời kết luận.; hành động: chỉ chuyển nhánh
- Người chơi: Bác cần Iris cung cấp điều gì để dùng lại dữ liệu?
  → NPC: Chỉ đúng hàng đã bị sửa, kèm bản gốc. Tôi không cần cô ấy chứng minh mình chưa từng sai; tôi cần biết liều nào có thể dùng.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_doctor_kim

Chỉ đúng hàng đã bị sửa, kèm bản gốc. Tôi không cần cô ấy chứng minh mình chưa từng sai; tôi cần biết liều nào có thể dùng.

- Người chơi: Tôi sẽ đọc bảng liều lượng gốc của Iris.
  → NPC: Cảm ơn. Đối chiếu từng hàng bị đánh dấu, đừng chỉ đọc lời kết luận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_doctor_kim

Cảm ơn. Đối chiếu từng hàng bị đánh dấu, đừng chỉ đọc lời kết luận.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_doctor_kim

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## iris_first_repair.3

### start — narrator

Bảng gốc có những hàng Iris đánh dấu. Mỗi hàng được đặt cạnh nội dung trên bản đã sửa sai.

- Người chơi: Tôi sẽ mang bốn tờ giấy cho Iris.
  → NPC: Bạn giữ cả liều gốc, liều đã sửa sai và dấu xác nhận.; hành động: chỉ chuyển nhánh
- Người chơi: Hàng bị sửa thay đổi nội dung gì?
  → NPC: Liều ghi bị giảm để che hao hụt kho. Iris đánh dấu bằng chính chữ ký từng dùng trên bản sai. Cô ấy cần bốn tờ giấy in bản sửa gửi các trạm.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Liều ghi bị giảm để che hao hụt kho. Iris đánh dấu bằng chính chữ ký từng dùng trên bản sai. Cô ấy cần bốn tờ giấy in bản sửa gửi các trạm.

- Người chơi: Tôi sẽ mang bốn tờ giấy cho Iris.
  → NPC: Bạn giữ cả liều gốc, liều đã sửa sai và dấu xác nhận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ cả liều gốc, liều đã sửa sai và dấu xác nhận.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## iris_first_repair.4

### start — lab_scientist_iris

Tôi đã ghi phần sửa và ký tên. Các trạm cần bản giấy để đối chiếu, không chỉ một lời báo qua điện thoại.

- Người chơi: Tôi giao bốn tờ giấy in bản sửa.
  → NPC: Cảm ơn. Nhờ Kim xác nhận đã nhận và kiểm tra bản mới nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Cô sẽ ký tên trên mọi bản sửa chứ?
  → NPC: Có. Ai muốn biết vì sao tin bản này có thể so với bản gốc và hỏi tôi. Bốn tờ giấy đủ để gửi các trạm cần đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — lab_scientist_iris

Có. Ai muốn biết vì sao tin bản này có thể so với bản gốc và hỏi tôi. Bốn tờ giấy đủ để gửi các trạm cần đối chiếu.

- Người chơi: Tôi giao bốn tờ giấy in bản sửa.
  → NPC: Cảm ơn. Nhờ Kim xác nhận đã nhận và kiểm tra bản mới nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — lab_scientist_iris

Cảm ơn. Nhờ Kim xác nhận đã nhận và kiểm tra bản mới nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — lab_scientist_iris

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## iris_first_repair.5

### start — town2_doctor_kim

Bản sửa của Iris tới rồi. Tôi đã đối chiếu với bản cũ và giữ cả hai trong hồ sơ điều trị.

- Người chơi: Vậy tôi xác nhận bản sửa đã được kiểm tra.
  → NPC: Cảm ơn. Tôi sẽ báo Iris nếu có thêm hàng cần đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Bác đã kiểm tra và dùng được bản sửa chưa?
  → NPC: Rồi. Tôi giữ cả bản cũ để biết lỗi nằm ở đâu. Iris vẫn phải trả lời về việc trước đây, nhưng phần dữ liệu cần cho điều trị đã đúng.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_doctor_kim

Rồi. Tôi giữ cả bản cũ để biết lỗi nằm ở đâu. Iris vẫn phải trả lời về việc trước đây, nhưng phần dữ liệu cần cho điều trị đã đúng.

- Người chơi: Vậy tôi xác nhận bản sửa đã được kiểm tra.
  → NPC: Cảm ơn. Tôi sẽ báo Iris nếu có thêm hàng cần đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_doctor_kim

Cảm ơn. Tôi sẽ báo Iris nếu có thêm hàng cần đối chiếu.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_doctor_kim

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mechanic_silent_motor.1

### start — town6_mechanic_son

Máy lại kêu ở cùng chỗ. Tôi nghe thấy, nhưng lần nào báo cũng thành lỗi ca của mình.

- Người chơi: Tôi sẽ đọc các ghi chú đã bị gạch.
  → NPC: Cảm ơn. Tôi cần có người đối chiếu, không chỉ nghe tôi nói máy kêu lạ.; hành động: chỉ chuyển nhánh
- Người chơi: Anh đã báo lỗi máy này chưa?
  → NPC: Bốn lần. Mỗi lần hồ sơ đổi thành lỗi thao tác và tôi bị tính vào ca. Ghi chú sửa máy vẫn còn, cậu xem trước khi hỏi Châu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town6_mechanic_son

Bốn lần. Mỗi lần hồ sơ đổi thành lỗi thao tác và tôi bị tính vào ca. Ghi chú sửa máy vẫn còn, cậu xem trước khi hỏi Châu.

- Người chơi: Tôi sẽ đọc các ghi chú đã bị gạch.
  → NPC: Cảm ơn. Tôi cần có người đối chiếu, không chỉ nghe tôi nói máy kêu lạ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town6_mechanic_son

Cảm ơn. Tôi cần có người đối chiếu, không chỉ nghe tôi nói máy kêu lạ.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town6_mechanic_son

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mechanic_silent_motor.2

### start — narrator

Ghi chú sửa có nhiều dòng bị gạch. Bạn có thể đối chiếu các mô tả trước và sau khi người duyệt sửa.

- Người chơi: Tôi sẽ hỏi Châu về quyền báo hỏng.
  → NPC: Bạn giữ những lần đã báo và chữ ký người đổi kết luận.; hành động: chỉ chuyển nhánh
- Người chơi: Những ghi chú bị gạch có cùng mô tả lỗi không?
  → NPC: Có. Cùng một lỗi được Sơn báo bốn lần, nhưng người duyệt đổi thành thao tác sai. Châu có thể xác nhận quy trình báo hỏng mà không phạt người báo.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Có. Cùng một lỗi được Sơn báo bốn lần, nhưng người duyệt đổi thành thao tác sai. Châu có thể xác nhận quy trình báo hỏng mà không phạt người báo.

- Người chơi: Tôi sẽ hỏi Châu về quyền báo hỏng.
  → NPC: Bạn giữ những lần đã báo và chữ ký người đổi kết luận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ những lần đã báo và chữ ký người đổi kết luận.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mechanic_silent_motor.3

### start — town6_guard_chau

Tôi đã xem báo của Sơn. Việc báo hỏng phải tách khỏi việc xác định ai làm hỏng máy.

- Người chơi: Tôi sẽ mang ba thỏi sắt cho Sơn.
  → NPC: Cảm ơn. Tôi ký xác nhận quyền báo lỗi trước khi sửa máy.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có bảo đảm báo lỗi máy không bị tính thành lỗi ca không?
  → NPC: Có. Tôi đưa báo hỏng vào hồ sơ an toàn. Sơn cần ba thỏi sắt sửa vỏ máy; phần đã báo phải được giữ để kiểm tra sau.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town6_guard_chau

Có. Tôi đưa báo hỏng vào hồ sơ an toàn. Sơn cần ba thỏi sắt sửa vỏ máy; phần đã báo phải được giữ để kiểm tra sau.

- Người chơi: Tôi sẽ mang ba thỏi sắt cho Sơn.
  → NPC: Cảm ơn. Tôi ký xác nhận quyền báo lỗi trước khi sửa máy.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town6_guard_chau

Cảm ơn. Tôi ký xác nhận quyền báo lỗi trước khi sửa máy.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town6_guard_chau

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mechanic_silent_motor.4

### start — town6_mechanic_son

Châu đã xác nhận quy trình báo lỗi. Tôi muốn sửa vỏ máy và giữ đúng tên công việc mình đang làm.

- Người chơi: Tôi giao ba thỏi sắt sửa máy.
  → NPC: Cảm ơn. Nhờ Châu kiểm tra hồ sơ ca thử sau khi tôi thay vỏ nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có ghi lại đúng phần máy hỏng lần này không?
  → NPC: Có. Tôi ký tên người sửa, không ký nhận rằng mình gây ra lỗi chưa được chứng minh. Ba thỏi sắt đủ thay phần vỏ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town6_mechanic_son

Có. Tôi ký tên người sửa, không ký nhận rằng mình gây ra lỗi chưa được chứng minh. Ba thỏi sắt đủ thay phần vỏ.

- Người chơi: Tôi giao ba thỏi sắt sửa máy.
  → NPC: Cảm ơn. Nhờ Châu kiểm tra hồ sơ ca thử sau khi tôi thay vỏ nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town6_mechanic_son

Cảm ơn. Nhờ Châu kiểm tra hồ sơ ca thử sau khi tôi thay vỏ nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town6_mechanic_son

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mechanic_silent_motor.5

### start — town6_guard_chau

Ca thử đã có kết quả. Tôi giữ báo ban đầu cùng phần sửa, không chỉ bản cuối cho đẹp hồ sơ.

- Người chơi: Vậy tôi xác nhận máy và hồ sơ đã được sửa.
  → NPC: Cảm ơn. Tôi kiểm tra cách ghi này trong các ca sau nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Ca thử đã được ghi vào hồ sơ an toàn chưa?
  → NPC: Rồi. Lỗi nằm ở máy, báo của Sơn đã được giữ. Nếu lần sau có vấn đề, anh ấy có thể báo mà không tự nhận một khoản phạt.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town6_guard_chau

Rồi. Lỗi nằm ở máy, báo của Sơn đã được giữ. Nếu lần sau có vấn đề, anh ấy có thể báo mà không tự nhận một khoản phạt.

- Người chơi: Vậy tôi xác nhận máy và hồ sơ đã được sửa.
  → NPC: Cảm ơn. Tôi kiểm tra cách ghi này trong các ca sau nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town6_guard_chau

Cảm ơn. Tôi kiểm tra cách ghi này trong các ca sau nữa.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town6_guard_chau

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## marlow_false_tablet.1

### start — archaeologist_marlow

Ta nghi tấm bia này không có tuổi người tặng nói. Nhưng muốn đính chính thì phải có thứ đối chiếu tốt hơn lời nghi ngờ.

- Người chơi: Tôi sẽ đối chiếu catalog bảo tàng.
  → NPC: Được. Ta muốn một lý do kiểm tra được, không chỉ một nhận xét của cái mũi.; hành động: chỉ chuyển nhánh
- Người chơi: Ông nghi tấm bia vì dấu hiệu nào?
  → NPC: Mùi sơn còn mới, nhưng Orin nói thế chưa đủ. Bản vẽ trong catalog có thể cho biết dòng khắc đã lấy từ đâu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — archaeologist_marlow

Mùi sơn còn mới, nhưng Orin nói thế chưa đủ. Bản vẽ trong catalog có thể cho biết dòng khắc đã lấy từ đâu.

- Người chơi: Tôi sẽ đối chiếu catalog bảo tàng.
  → NPC: Được. Ta muốn một lý do kiểm tra được, không chỉ một nhận xét của cái mũi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — archaeologist_marlow

Được. Ta muốn một lý do kiểm tra được, không chỉ một nhận xét của cái mũi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — archaeologist_marlow

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## marlow_false_tablet.2

### start — narrator

Catalog có bản vẽ và trang sách dùng để tham khảo. Một đoạn khắc trên bia giống cả một chỗ in sai trong sách.

- Người chơi: Tôi sẽ hỏi Hương về nguồn tấm bia.
  → NPC: Bạn giữ trang sách có lỗi giống nhau để đối chiếu với dòng khắc.; hành động: chỉ chuyển nhánh
- Người chơi: Dòng khắc có dấu hiệu chép từ tài liệu mới không?
  → NPC: Nó lặp đúng lỗi đánh máy trong sách năm ngoái. Bản phục dựng đã chép từ sách. Hương giữ thông tin người tặng.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Nó lặp đúng lỗi đánh máy trong sách năm ngoái. Bản phục dựng đã chép từ sách. Hương giữ thông tin người tặng.

- Người chơi: Tôi sẽ hỏi Hương về nguồn tấm bia.
  → NPC: Bạn giữ trang sách có lỗi giống nhau để đối chiếu với dòng khắc.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ trang sách có lỗi giống nhau để đối chiếu với dòng khắc.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## marlow_false_tablet.3

### start — town7_curator_huong

Gia đình người tặng không đưa giấy về tuổi hiện vật. Tôi cần hỏi lại nguồn rồi sửa nhãn trưng bày.

- Người chơi: Tôi sẽ mang giấy làm nhãn đúng nguồn.
  → NPC: Cảm ơn. Tôi nói chuyện với gia đình người tặng, không tự bỏ tên họ khỏi hồ sơ.; hành động: chỉ chuyển nhánh
- Người chơi: Người tặng có đưa giấy chứng minh tuổi tấm bia không?
  → NPC: Không. Họ muốn ghi tên gia đình ở gian trưng bày. Tôi cần ba tờ giấy làm nhãn đính chính để không giữ một tuổi sai cho hiện vật.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town7_curator_huong

Không. Họ muốn ghi tên gia đình ở gian trưng bày. Tôi cần ba tờ giấy làm nhãn đính chính để không giữ một tuổi sai cho hiện vật.

- Người chơi: Tôi sẽ mang giấy làm nhãn đúng nguồn.
  → NPC: Cảm ơn. Tôi nói chuyện với gia đình người tặng, không tự bỏ tên họ khỏi hồ sơ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town7_curator_huong

Cảm ơn. Tôi nói chuyện với gia đình người tặng, không tự bỏ tên họ khỏi hồ sơ.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town7_curator_huong

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## marlow_false_tablet.4

### start — town7_curator_huong

Tôi đã nói chuyện với gia đình người tặng. Nhãn mới cần ghi đúng hiện vật và phần họ đã đóng góp.

- Người chơi: Tôi giao ba tờ giấy làm nhãn đính chính.
  → NPC: Cảm ơn. Nhờ bạn báo Marlow cách trưng bày mới nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Nhãn mới có giữ tên người tặng không?
  → NPC: Có, nhưng ghi đây là bản phục dựng hiện đại. Gia đình được nhắc tên vì đã tặng, không vì một tuổi hiện vật không có thật.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town7_curator_huong

Có, nhưng ghi đây là bản phục dựng hiện đại. Gia đình được nhắc tên vì đã tặng, không vì một tuổi hiện vật không có thật.

- Người chơi: Tôi giao ba tờ giấy làm nhãn đính chính.
  → NPC: Cảm ơn. Nhờ bạn báo Marlow cách trưng bày mới nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town7_curator_huong

Cảm ơn. Nhờ bạn báo Marlow cách trưng bày mới nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town7_curator_huong

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## marlow_false_tablet.5

### start — archaeologist_marlow

Hương báo cách ghi nhãn mới rồi. Ta muốn giữ hiện vật như một bản có nguồn rõ, không như chứng cứ cho một tuổi sai.

- Người chơi: Vậy tôi xác nhận tuổi và nguồn đã được đính chính.
  → NPC: Được. Ta sẽ xem nhãn mới trước buổi hướng dẫn tiếp theo.; hành động: chỉ chuyển nhánh
- Người chơi: Ông đồng ý trưng bản phục dựng với tuổi đúng chứ?
  → NPC: Đồng ý. Nó còn cho biết người ta đã đọc sai lịch sử như thế nào. Ta không cần ném nó đi, chỉ cần thôi gọi nó là bia cổ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — archaeologist_marlow

Đồng ý. Nó còn cho biết người ta đã đọc sai lịch sử như thế nào. Ta không cần ném nó đi, chỉ cần thôi gọi nó là bia cổ.

- Người chơi: Vậy tôi xác nhận tuổi và nguồn đã được đính chính.
  → NPC: Được. Ta sẽ xem nhãn mới trước buổi hướng dẫn tiếp theo.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — archaeologist_marlow

Được. Ta sẽ xem nhãn mới trước buổi hướng dẫn tiếp theo.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — archaeologist_marlow

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## photo_without_face.1

### start — town7_photographer_ly

Warden muốn giữ một ảnh chuyến cứu hộ, nhưng chưa muốn nhìn bản đầy đủ. Tôi chưa in hay cắt theo ý mình.

- Người chơi: Tôi sẽ hỏi Warden về bộ ảnh.
  → NPC: Cảm ơn. Tôi không cắt hay in một bản mới trước khi ông ấy đồng ý.; hành động: chỉ chuyển nhánh
- Người chơi: Người trong ảnh muốn giữ lại phần nào?
  → NPC: Ông ấy muốn chứng minh đã có mặt ở buổi cứu hộ, nhưng tránh nhìn chính mình. Bạn hỏi Warden xem ông ấy có đồng ý xem cả khung không.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town7_photographer_ly

Ông ấy muốn chứng minh đã có mặt ở buổi cứu hộ, nhưng tránh nhìn chính mình. Bạn hỏi Warden xem ông ấy có đồng ý xem cả khung không.

- Người chơi: Tôi sẽ hỏi Warden về bộ ảnh.
  → NPC: Cảm ơn. Tôi không cắt hay in một bản mới trước khi ông ấy đồng ý.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town7_photographer_ly

Cảm ơn. Tôi không cắt hay in một bản mới trước khi ông ấy đồng ý.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town7_photographer_ly

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## photo_without_face.2

### start — seventh_warden

Ly nói muốn hỏi ta về bộ ảnh à? Ta có mặt ở đó, và đã tránh xem nó rất lâu.

- Người chơi: Tôi sẽ xem bộ ảnh rồi hỏi Ly cách in.
  → NPC: Được. Đừng cắt phần khó nhìn chỉ để ta dễ nhận hơn.; hành động: chỉ chuyển nhánh
- Người chơi: Ông có muốn xem ảnh gốc chưa cắt không?
  → NPC: Có. Ta đứng ở mép, người không về đứng giữa. Ta đã tránh nhìn cả hai chỗ. Bộ ảnh gốc vẫn còn để cậu kiểm tra.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — seventh_warden

Có. Ta đứng ở mép, người không về đứng giữa. Ta đã tránh nhìn cả hai chỗ. Bộ ảnh gốc vẫn còn để cậu kiểm tra.

- Người chơi: Tôi sẽ xem bộ ảnh rồi hỏi Ly cách in.
  → NPC: Được. Đừng cắt phần khó nhìn chỉ để ta dễ nhận hơn.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — seventh_warden

Được. Đừng cắt phần khó nhìn chỉ để ta dễ nhận hơn.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — seventh_warden

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## photo_without_face.3

### start — narrator

Bộ ảnh gốc giữ cả những khung từng bị cắt. Bạn có thể đối chiếu người và vị trí đứng trên từng bản.

- Người chơi: Tôi sẽ mang giấy để Ly in ảnh đầy đủ.
  → NPC: Bạn ghi nhận sự đồng ý của Warden cùng khung ảnh cần giữ.; hành động: chỉ chuyển nhánh
- Người chơi: Ảnh gốc có giữ đủ những người tham gia không?
  → NPC: Có. Có người cười, người mệt và người quay mặt. Ly cần hai tờ giấy in cả khung thay cho bản đã cắt.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Có. Có người cười, người mệt và người quay mặt. Ly cần hai tờ giấy in cả khung thay cho bản đã cắt.

- Người chơi: Tôi sẽ mang giấy để Ly in ảnh đầy đủ.
  → NPC: Bạn ghi nhận sự đồng ý của Warden cùng khung ảnh cần giữ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn ghi nhận sự đồng ý của Warden cùng khung ảnh cần giữ.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## photo_without_face.4

### start — town7_photographer_ly

Warden đã đồng ý bản đầy đủ. Tôi chuẩn bị in đúng khung, không chỉnh ai thành người hùng của bức ảnh.

- Người chơi: Tôi giao hai tờ giấy in ảnh.
  → NPC: Cảm ơn. Nhờ Warden tới nhận đúng bản ông ấy đã chọn nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Cô sẽ in đúng cả khung Warden đã đồng ý chứ?
  → NPC: Đúng. Tôi không sửa để ông ấy trông như một biểu tượng. Hai tờ giấy đủ cho bản nhận và bản đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town7_photographer_ly

Đúng. Tôi không sửa để ông ấy trông như một biểu tượng. Hai tờ giấy đủ cho bản nhận và bản đối chiếu.

- Người chơi: Tôi giao hai tờ giấy in ảnh.
  → NPC: Cảm ơn. Nhờ Warden tới nhận đúng bản ông ấy đã chọn nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town7_photographer_ly

Cảm ơn. Nhờ Warden tới nhận đúng bản ông ấy đã chọn nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town7_photographer_ly

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## photo_without_face.5

### start — seventh_warden

Ly báo ảnh đã in. Ta muốn nhận đúng bản mình đã đồng ý nhìn.

- Người chơi: Tôi xác nhận ông nhận bản đầy đủ.
  → NPC: Cảm ơn. Ta sẽ ghi tên những người trong ảnh ở bên cạnh, không bỏ phần ở giữa nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Ông có nhận bản ảnh giữ đủ mọi người không?
  → NPC: Có. Ta giữ nó để nhớ mình đã có mặt và vẫn còn điều phải làm. Không cần ai khen ta chỉ vì cuối cùng chịu nhìn bức ảnh.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — seventh_warden

Có. Ta giữ nó để nhớ mình đã có mặt và vẫn còn điều phải làm. Không cần ai khen ta chỉ vì cuối cùng chịu nhìn bức ảnh.

- Người chơi: Tôi xác nhận ông nhận bản đầy đủ.
  → NPC: Cảm ơn. Ta sẽ ghi tên những người trong ảnh ở bên cạnh, không bỏ phần ở giữa nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — seventh_warden

Cảm ơn. Ta sẽ ghi tên những người trong ảnh ở bên cạnh, không bỏ phần ở giữa nữa.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — seventh_warden

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## museum_open_hours.1

### start — town7_curator_huong

Báo cáo nói ít học sinh tới. Nhưng tôi muốn kiểm tra giờ mở trước khi viết rằng các em không quan tâm.

- Người chơi: Tôi sẽ hỏi Linh về giờ có thể tới.
  → NPC: Cảm ơn. Tôi muốn sửa lịch theo người tới, không sửa báo cáo cho dễ nhìn.; hành động: chỉ chuyển nhánh
- Người chơi: Học sinh có tới được trong giờ bảo tàng mở không?
  → NPC: Có lẽ không. Tôi mở lúc các em ở trường, đóng lúc tan học rồi lại trách không có khách. Bạn hỏi Linh giờ nào em ấy tới được giúp tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town7_curator_huong

Có lẽ không. Tôi mở lúc các em ở trường, đóng lúc tan học rồi lại trách không có khách. Bạn hỏi Linh giờ nào em ấy tới được giúp tôi.

- Người chơi: Tôi sẽ hỏi Linh về giờ có thể tới.
  → NPC: Cảm ơn. Tôi muốn sửa lịch theo người tới, không sửa báo cáo cho dễ nhìn.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town7_curator_huong

Cảm ơn. Tôi muốn sửa lịch theo người tới, không sửa báo cáo cho dễ nhìn.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town7_curator_huong

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## museum_open_hours.2

### start — town2_student_linh

Tôi có muốn tới bảo tàng. Chỉ là giờ mở thường trùng giờ học, còn tan học thì cửa đã đóng.

- Người chơi: Tôi sẽ đối chiếu giờ trong sổ khách.
  → NPC: Cảm ơn. Nếu có ca chiều, tôi sẽ rủ Bảo đi cùng.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu có thể tới bảo tàng sau giờ học lúc nào?
  → NPC: Buổi chiều. Tôi không muốn trốn lớp để chứng minh mình thích học lịch sử. Sổ khách có thể cho thấy những bạn khác cũng gặp chuyện đó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_student_linh

Buổi chiều. Tôi không muốn trốn lớp để chứng minh mình thích học lịch sử. Sổ khách có thể cho thấy những bạn khác cũng gặp chuyện đó.

- Người chơi: Tôi sẽ đối chiếu giờ trong sổ khách.
  → NPC: Cảm ơn. Nếu có ca chiều, tôi sẽ rủ Bảo đi cùng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_student_linh

Cảm ơn. Nếu có ca chiều, tôi sẽ rủ Bảo đi cùng.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_student_linh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## museum_open_hours.3

### start — narrator

Sổ khách và đơn xin mở muộn còn được giữ chung. Giờ tới của các nhóm học sinh có thể đối chiếu theo ngày.

- Người chơi: Tôi sẽ mang redstone để Hương mở ca mới.
  → NPC: Bạn giữ giờ học sinh tới cùng đơn đã bị bỏ qua.; hành động: chỉ chuyển nhánh
- Người chơi: Sổ có cho thấy học sinh chỉ tới ngày nghỉ không?
  → NPC: Có. Đơn xin mở muộn còn nằm dưới báo cáo ít khách. Hương cần hai redstone cho chuông báo ca chiều.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Có. Đơn xin mở muộn còn nằm dưới báo cáo ít khách. Hương cần hai redstone cho chuông báo ca chiều.

- Người chơi: Tôi sẽ mang redstone để Hương mở ca mới.
  → NPC: Bạn giữ giờ học sinh tới cùng đơn đã bị bỏ qua.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ giờ học sinh tới cùng đơn đã bị bỏ qua.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## museum_open_hours.4

### start — town7_curator_huong

Tôi đã chọn ca mở thêm. Phần còn lại là người trực và chuông báo đúng giờ.

- Người chơi: Tôi giao hai redstone cho ca chiều.
  → NPC: Cảm ơn. Bạn báo Linh ngày và giờ mở mới nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Cô đã chọn người phụ trách ca chiều chưa?
  → NPC: Rồi. Một ngày mỗi tuần có người trực rõ tên. Hai redstone đủ nối chuông báo; tôi không chỉ dán lịch rồi để ca trống.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town7_curator_huong

Rồi. Một ngày mỗi tuần có người trực rõ tên. Hai redstone đủ nối chuông báo; tôi không chỉ dán lịch rồi để ca trống.

- Người chơi: Tôi giao hai redstone cho ca chiều.
  → NPC: Cảm ơn. Bạn báo Linh ngày và giờ mở mới nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town7_curator_huong

Cảm ơn. Bạn báo Linh ngày và giờ mở mới nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town7_curator_huong

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## museum_open_hours.5

### start — town2_student_linh

Hương gửi lịch chiều rồi à? Tôi muốn rủ Bảo cùng xem, không muốn trốn lớp để tới một buổi học khác.

- Người chơi: Vậy tôi báo Hương hai cậu sẽ ghé.
  → NPC: Cảm ơn. Chúng tôi sẽ xem đúng lịch, không đứng đợi sau cửa đã đóng nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu có muốn tới theo lịch chiều mới không?
  → NPC: Có. Tôi rủ Bảo rồi. Cậu ấy sợ bị hỏi bài; tôi bảo lần này chúng tôi được hỏi người hướng dẫn.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_student_linh

Có. Tôi rủ Bảo rồi. Cậu ấy sợ bị hỏi bài; tôi bảo lần này chúng tôi được hỏi người hướng dẫn.

- Người chơi: Vậy tôi báo Hương hai cậu sẽ ghé.
  → NPC: Cảm ơn. Chúng tôi sẽ xem đúng lịch, không đứng đợi sau cửa đã đóng nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_student_linh

Cảm ơn. Chúng tôi sẽ xem đúng lịch, không đứng đợi sau cửa đã đóng nữa.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_student_linh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## harbour_lost_catch.1

### start — town8_fisher_thao

Thùng ở đó lúc tôi về. Mọi người gọi nó là thùng của tôi, nhưng chuyến của tôi chưa giao thứ gì tại bến này.

- Người chơi: Tôi sẽ kiểm tra nhãn chuyến tàu.
  → NPC: Cảm ơn. Tôi muốn thùng tới đúng người, không chỉ được gán một chủ cho xong.; hành động: chỉ chuyển nhánh
- Người chơi: Vì sao mọi người cho rằng thùng cá là của chị?
  → NPC: Vì tôi đánh cá. Tôi vừa đi chuyến khác về, chưa ai hỏi tôi có nhận thùng này không. Nhãn tàu trên thùng sẽ cho biết người gửi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town8_fisher_thao

Vì tôi đánh cá. Tôi vừa đi chuyến khác về, chưa ai hỏi tôi có nhận thùng này không. Nhãn tàu trên thùng sẽ cho biết người gửi.

- Người chơi: Tôi sẽ kiểm tra nhãn chuyến tàu.
  → NPC: Cảm ơn. Tôi muốn thùng tới đúng người, không chỉ được gán một chủ cho xong.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town8_fisher_thao

Cảm ơn. Tôi muốn thùng tới đúng người, không chỉ được gán một chủ cho xong.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town8_fisher_thao

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## harbour_lost_catch.2

### start — narrator

Nhãn trên thùng bị ướt ở phần giờ. Tên tàu và số chuyến vẫn còn đọc được.

- Người chơi: Tôi sẽ hỏi Hoa về người nhận.
  → NPC: Bạn giữ tên tàu và phần giờ bị nhòe để đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Nhãn còn đọc được tên tàu không?
  → NPC: Tên tàu Hoa còn rõ, giờ về bị nước làm nhòe. Người nhận tưởng tàu chưa cập bến. Cần hỏi Hoa người đặt mẻ cá.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Tên tàu Hoa còn rõ, giờ về bị nước làm nhòe. Người nhận tưởng tàu chưa cập bến. Cần hỏi Hoa người đặt mẻ cá.

- Người chơi: Tôi sẽ hỏi Hoa về người nhận.
  → NPC: Bạn giữ tên tàu và phần giờ bị nhòe để đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ tên tàu và phần giờ bị nhòe để đối chiếu.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## harbour_lost_catch.3

### start — town8_captain_hoa

Đó là thùng từ tàu tôi. Tôi có người đặt, nhưng đã viết người nhận quá mơ hồ trên phiếu.

- Người chơi: Tôi sẽ mang vật tư cho Bình làm điểm nhận.
  → NPC: Cảm ơn. Lần sau tôi ghi người nhận cụ thể ngay trên phiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Ai đặt mẻ cá trên tàu chị?
  → NPC: Đội cứu hộ đặt bữa ăn. Tôi nghĩ người ở bến sẽ nhận, nhưng không ghi tên ai. Bình cần hai tấm ván sồi làm điểm nhận có nắp.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town8_captain_hoa

Đội cứu hộ đặt bữa ăn. Tôi nghĩ người ở bến sẽ nhận, nhưng không ghi tên ai. Bình cần hai tấm ván sồi làm điểm nhận có nắp.

- Người chơi: Tôi sẽ mang vật tư cho Bình làm điểm nhận.
  → NPC: Cảm ơn. Lần sau tôi ghi người nhận cụ thể ngay trên phiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town8_captain_hoa

Cảm ơn. Lần sau tôi ghi người nhận cụ thể ngay trên phiếu.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town8_captain_hoa

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## harbour_lost_catch.4

### start — town8_dockworker_binh

Tôi nhận giữ điểm giao mới. Cần phần che để nhãn và giấy nhận không lại ướt trong chuyến sau.

- Người chơi: Tôi giao hai tấm ván sồi cho anh.
  → NPC: Cảm ơn. Báo Thảo là mẻ cá đã có người nhận đúng nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Anh nhận làm đầu mối cho chuyến này chứ?
  → NPC: Có. Tôi ký nhận và đặt thùng ở chỗ có nắp. Hai tấm ván đủ sửa phần che để nhãn không bị ướt nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town8_dockworker_binh

Có. Tôi ký nhận và đặt thùng ở chỗ có nắp. Hai tấm ván đủ sửa phần che để nhãn không bị ướt nữa.

- Người chơi: Tôi giao hai tấm ván sồi cho anh.
  → NPC: Cảm ơn. Báo Thảo là mẻ cá đã có người nhận đúng nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town8_dockworker_binh

Cảm ơn. Báo Thảo là mẻ cá đã có người nhận đúng nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town8_dockworker_binh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## harbour_lost_catch.5

### start — town8_fisher_thao

Bình đã báo thùng tới đúng người rồi. Cảm ơn bạn quay lại xác nhận với tôi.

- Người chơi: Vậy tôi xác nhận chuyện người nhận đã rõ.
  → NPC: Cảm ơn. Mai tôi vẫn ra tàu của mình, không phải đi giải thích thùng của tàu khác.; hành động: chỉ chuyển nhánh
- Người chơi: Mẻ cá đã tới đội cứu hộ. Chị còn cần xác nhận gì không?
  → NPC: Không, cảm ơn bạn. Tôi chỉ muốn lần sau người ta hỏi chuyến tôi đi trước khi gán một thùng ngoài bến cho tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town8_fisher_thao

Không, cảm ơn bạn. Tôi chỉ muốn lần sau người ta hỏi chuyến tôi đi trước khi gán một thùng ngoài bến cho tôi.

- Người chơi: Vậy tôi xác nhận chuyện người nhận đã rõ.
  → NPC: Cảm ơn. Mai tôi vẫn ra tàu của mình, không phải đi giải thích thùng của tàu khác.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town8_fisher_thao

Cảm ơn. Mai tôi vẫn ra tàu của mình, không phải đi giải thích thùng của tàu khác.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town8_fisher_thao

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## harbour_breathing.1

### start — town8_trainer_hai

Tôi đang tập một đội xoay quanh Cloyster. Tôi đặt cả tên cho bài, nhưng Dorian bảo nên xem đội hoạt động đã.

- Người chơi: Tôi sẽ hỏi Dorian về đội của cậu.
  → NPC: Được. Tôi vẫn luyện, không đổi tên bài để giả vờ đã sửa được đội.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu đang luyện chiến thuật gì với Cloyster?
  → NPC: Tăng sức mạnh rồi kết thúc nhanh. Tôi đặt tên bài hơi dài; Dorian bảo tôi nên lo giữ lượt chuẩn bị hơn. Hỏi ông ấy phần đội tôi còn thiếu đi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town8_trainer_hai

Tăng sức mạnh rồi kết thúc nhanh. Tôi đặt tên bài hơi dài; Dorian bảo tôi nên lo giữ lượt chuẩn bị hơn. Hỏi ông ấy phần đội tôi còn thiếu đi.

- Người chơi: Tôi sẽ hỏi Dorian về đội của cậu.
  → NPC: Được. Tôi vẫn luyện, không đổi tên bài để giả vờ đã sửa được đội.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town8_trainer_hai

Được. Tôi vẫn luyện, không đổi tên bài để giả vờ đã sửa được đội.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town8_trainer_hai

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## harbour_breathing.2

### start — captain_dorian

Hải hỏi ta góp ý đội mới. Ta muốn cậu xem chỗ đội cần chuẩn bị trước khi nhận trận.

- Người chơi: Tôi sẽ đọc bảng về Shell Smash.
  → NPC: Được. Đọc cả cái giá của tăng sức mạnh, không chỉ con số được tăng.; hành động: chỉ chuyển nhánh
- Người chơi: Điểm yếu của đội Hải nằm ở lượt nào?
  → NPC: Lượt dùng Shell Smash. Nếu không giữ được cơ hội chuẩn bị, phần còn lại khó chạy. Bảng luyện ghi các cách gây áp lực vào lượt đó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — captain_dorian

Lượt dùng Shell Smash. Nếu không giữ được cơ hội chuẩn bị, phần còn lại khó chạy. Bảng luyện ghi các cách gây áp lực vào lượt đó.

- Người chơi: Tôi sẽ đọc bảng về Shell Smash.
  → NPC: Được. Đọc cả cái giá của tăng sức mạnh, không chỉ con số được tăng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — captain_dorian

Được. Đọc cả cái giá của tăng sức mạnh, không chỉ con số được tăng.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — captain_dorian

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## harbour_breathing.3

### start — narrator

Bảng luyện ghi Shell Smash và những chỉ số đổi sau khi dùng. Các cách đáp được để riêng bên cạnh.

- Người chơi: Tôi sẽ đấu với Hải và thử một nước đáp.
  → NPC: Bạn giữ những phương án hợp lệ, không coi đòn của đối thủ là điều cần cấm.; hành động: chỉ chuyển nhánh
- Người chơi: Shell Smash đổi lấy những rủi ro nào?
  → NPC: Đội mạnh và nhanh hơn, nhưng phòng thủ giảm. Đòn ưu tiên hoặc ép đổi có thể tận dụng chỗ đó. Hải nhận một trận tập để thử cách giữ lượt.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Đội mạnh và nhanh hơn, nhưng phòng thủ giảm. Đòn ưu tiên hoặc ép đổi có thể tận dụng chỗ đó. Hải nhận một trận tập để thử cách giữ lượt.

- Người chơi: Tôi sẽ đấu với Hải và thử một nước đáp.
  → NPC: Bạn giữ những phương án hợp lệ, không coi đòn của đối thủ là điều cần cấm.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ những phương án hợp lệ, không coi đòn của đối thủ là điều cần cấm.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## harbour_breathing.4

### start — town8_trainer_hai

Tôi đã mang đội ra. Cậu xem bảng rồi thì chúng ta có thể thử bằng một trận thật.

- Người chơi: Vậy bắt đầu trận tập.
  → NPC: Được. Lần này tôi xem lại nước đi trước khi nghĩ tên bài mới.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu vẫn dùng đội tăng sức mạnh trong trận này chứ?
  → NPC: Ừ. Tôi muốn biết mình giữ được lượt chuẩn bị không. Cậu cứ đánh thật, đừng để tôi dựng đội chỉ vì đang tập.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town8_trainer_hai

Ừ. Tôi muốn biết mình giữ được lượt chuẩn bị không. Cậu cứ đánh thật, đừng để tôi dựng đội chỉ vì đang tập.

- Người chơi: Vậy bắt đầu trận tập.
  → NPC: Được. Lần này tôi xem lại nước đi trước khi nghĩ tên bài mới.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town8_trainer_hai

Được. Lần này tôi xem lại nước đi trước khi nghĩ tên bài mới.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — town8_trainer_hai

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## harbour_breathing.5

### start — captain_dorian

Hải gửi bản trận vừa rồi. Ta sẽ cùng cậu ấy đọc lại, không chỉ ghi thắng hoặc thua vào sổ.

- Người chơi: Tôi xác nhận trận tập đã kết thúc.
  → NPC: Cảm ơn. Tôi giữ bản ghi để Hải tự giải thích lượt cần sửa.; hành động: chỉ chuyển nhánh
- Người chơi: Buổi tập đã giúp Hải sửa được phần nào?
  → NPC: Cậu ấy bắt đầu giữ lượt chuẩn bị thay vì chỉ trông chờ đòn mạnh. Tôi sẽ cùng xem lại trận; kết quả chưa làm bài tập hết giá trị.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — captain_dorian

Cậu ấy bắt đầu giữ lượt chuẩn bị thay vì chỉ trông chờ đòn mạnh. Tôi sẽ cùng xem lại trận; kết quả chưa làm bài tập hết giá trị.

- Người chơi: Tôi xác nhận trận tập đã kết thúc.
  → NPC: Cảm ơn. Tôi giữ bản ghi để Hải tự giải thích lượt cần sửa.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — captain_dorian

Cảm ơn. Tôi giữ bản ghi để Hải tự giải thích lượt cần sửa.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — captain_dorian

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## tower_waiting_chair.1

### start — battle_tower_receptionist

Rowan giữ chiếc ghế đó cho một người bạn. Tôi nghĩ người nhận lời mời cần được biết đến xem cũng có chỗ.

- Người chơi: Tôi sẽ nói chuyện với Rowan về lời mời.
  → NPC: Cảm ơn. Quyền vào xem không đòi một kết quả thi mới.; hành động: chỉ chuyển nhánh
- Người chơi: Chiếc ghế Rowan giữ có dành cho người đang thi không?
  → NPC: Cho một người bạn đã không thi lại. Rowan muốn mời, nhưng cứ chờ bạn thắng thêm giải. Bạn hỏi xem anh ấy có muốn mời người tới xem không.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — battle_tower_receptionist

Cho một người bạn đã không thi lại. Rowan muốn mời, nhưng cứ chờ bạn thắng thêm giải. Bạn hỏi xem anh ấy có muốn mời người tới xem không.

- Người chơi: Tôi sẽ nói chuyện với Rowan về lời mời.
  → NPC: Cảm ơn. Quyền vào xem không đòi một kết quả thi mới.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — battle_tower_receptionist

Cảm ơn. Quyền vào xem không đòi một kết quả thi mới.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — battle_tower_receptionist

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## tower_waiting_chair.2

### start — battle_tower_trainer_01

Tôi đã muốn mời bạn tới nhiều lần. Rồi cứ nghĩ phải chờ một thành tích mới của cậu ấy trước.

- Người chơi: Tôi sẽ xem quyền quan sát trên thẻ.
  → NPC: Cảm ơn. Tôi muốn lời mời này không thành thêm một bài thử cho bạn mình.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có muốn mời bạn tới dù người ấy không đấu không?
  → NPC: Có. Tôi cứ nghĩ phải chờ cậu ấy sẵn sàng thi lại. Thẻ cũ ở phòng chờ có quy định người quan sát; xem giúp tôi phần đó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — battle_tower_trainer_01

Có. Tôi cứ nghĩ phải chờ cậu ấy sẵn sàng thi lại. Thẻ cũ ở phòng chờ có quy định người quan sát; xem giúp tôi phần đó.

- Người chơi: Tôi sẽ xem quyền quan sát trên thẻ.
  → NPC: Cảm ơn. Tôi muốn lời mời này không thành thêm một bài thử cho bạn mình.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — battle_tower_trainer_01

Cảm ơn. Tôi muốn lời mời này không thành thêm một bài thử cho bạn mình.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — battle_tower_trainer_01

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## tower_waiting_chair.3

### start — narrator

Thẻ dự thi cũ có một mục riêng về người quan sát. Phần ấy không nằm cùng điều kiện vào bài thử.

- Người chơi: Tôi sẽ mang giấy làm lời mời không bắt buộc đấu.
  → NPC: Bạn giữ đúng quyền quan sát để không thêm điều kiện thi đấu vào thư.; hành động: chỉ chuyển nhánh
- Người chơi: Thẻ yêu cầu người quan sát tham gia một trận không?
  → NPC: Không. Người có lời mời được vào xem mà không phải đấu. Lễ tân cần hai tờ giấy viết lời mời rõ điều đó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Người có lời mời được vào xem mà không phải đấu. Lễ tân cần hai tờ giấy viết lời mời rõ điều đó.

- Người chơi: Tôi sẽ mang giấy làm lời mời không bắt buộc đấu.
  → NPC: Bạn giữ đúng quyền quan sát để không thêm điều kiện thi đấu vào thư.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ đúng quyền quan sát để không thêm điều kiện thi đấu vào thư.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## tower_waiting_chair.4

### start — battle_tower_receptionist

Rowan muốn gửi lời mời rồi. Tôi sẽ ghi rõ phần người nhận được làm, để bức thư không giống một yêu cầu thi lại.

- Người chơi: Tôi giao hai tờ giấy làm lời mời.
  → NPC: Cảm ơn. Đưa Rowan xác nhận rồi gửi cho bạn nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Thư mới có nói rõ đến xem cũng được không?
  → NPC: Có. Tôi ghi phần đó ngay cạnh giờ hẹn. Hai tờ giấy đủ cho thư và bản lưu, để người nhận không phải đoán có được vào hay không.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — battle_tower_receptionist

Có. Tôi ghi phần đó ngay cạnh giờ hẹn. Hai tờ giấy đủ cho thư và bản lưu, để người nhận không phải đoán có được vào hay không.

- Người chơi: Tôi giao hai tờ giấy làm lời mời.
  → NPC: Cảm ơn. Đưa Rowan xác nhận rồi gửi cho bạn nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — battle_tower_receptionist

Cảm ơn. Đưa Rowan xác nhận rồi gửi cho bạn nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — battle_tower_receptionist

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## tower_waiting_chair.5

### start — battle_tower_trainer_01

Lễ tân chuẩn bị thư rồi à? Tôi muốn gửi nó ngay, không chọn thêm một điều kiện trước khi mời.

- Người chơi: Vậy tôi xác nhận lời mời đã được chuẩn bị.
  → NPC: Cảm ơn. Tôi sẽ tự báo lễ tân khi bạn trả lời.; hành động: chỉ chuyển nhánh
- Người chơi: Anh sẽ gửi lời mời không kèm yêu cầu thắng giải chứ?
  → NPC: Ừ. Ghế này dành cho cậu ấy, không dành cho thành tích của cậu ấy. Tôi gửi ngay, không chờ một kết quả khác nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — battle_tower_trainer_01

Ừ. Ghế này dành cho cậu ấy, không dành cho thành tích của cậu ấy. Tôi gửi ngay, không chờ một kết quả khác nữa.

- Người chơi: Vậy tôi xác nhận lời mời đã được chuẩn bị.
  → NPC: Cảm ơn. Tôi sẽ tự báo lễ tân khi bạn trả lời.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — battle_tower_trainer_01

Cảm ơn. Tôi sẽ tự báo lễ tân khi bạn trả lời.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — battle_tower_trainer_01

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## nyx_clock.1

### start — battle_tower_trainer_02

Tôi đo thời gian của mọi việc gần đây. Có những cuộc hẹn càng đo tôi lại càng không muốn tới.

- Người chơi: Tôi sẽ nghe lễ tân kể rồi quay lại.
  → NPC: Được. Tôi muốn một lời kể thật, không chỉ thêm con số vào lịch.; hành động: chỉ chuyển nhánh
- Người chơi: Cô đo thời gian để tránh việc gì?
  → NPC: Để khỏi chờ mà không biết có ai trả lời không. Nhưng giờ tôi đo cả những việc chẳng cần kết thúc nhanh. Hỏi lễ tân xem tôi đã bỏ lỡ điều gì nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — battle_tower_trainer_02

Để khỏi chờ mà không biết có ai trả lời không. Nhưng giờ tôi đo cả những việc chẳng cần kết thúc nhanh. Hỏi lễ tân xem tôi đã bỏ lỡ điều gì nhé.

- Người chơi: Tôi sẽ nghe lễ tân kể rồi quay lại.
  → NPC: Được. Tôi muốn một lời kể thật, không chỉ thêm con số vào lịch.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — battle_tower_trainer_02

Được. Tôi muốn một lời kể thật, không chỉ thêm con số vào lịch.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — battle_tower_trainer_02

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## nyx_clock.2

### start — battle_tower_receptionist

Nyx bỏ qua vài cuộc hẹn dù lịch còn trống. Rowan để lời mời mới trên bàn, không giống mẫu lịch cô ấy thường dùng.

- Người chơi: Tôi sẽ đọc giấy mời của Rowan.
  → NPC: Cảm ơn. Nhớ giữ nguyên phần không có giờ kết thúc, đó là điều anh ấy muốn nhắn.; hành động: chỉ chuyển nhánh
- Người chơi: Nyx thường bỏ lỡ những cuộc hẹn nào?
  → NPC: Bữa trưa, chuyện với bạn và cả phần xem lại trận đấu. Rowan để giấy mời ăn ở phòng chờ, lần này không ghi giờ kết thúc.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — battle_tower_receptionist

Bữa trưa, chuyện với bạn và cả phần xem lại trận đấu. Rowan để giấy mời ăn ở phòng chờ, lần này không ghi giờ kết thúc.

- Người chơi: Tôi sẽ đọc giấy mời của Rowan.
  → NPC: Cảm ơn. Nhớ giữ nguyên phần không có giờ kết thúc, đó là điều anh ấy muốn nhắn.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — battle_tower_receptionist

Cảm ơn. Nhớ giữ nguyên phần không có giờ kết thúc, đó là điều anh ấy muốn nhắn.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — battle_tower_receptionist

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## nyx_clock.3

### start — narrator

Giấy mời ăn của Rowan có hai dòng bị Nyx gạch. Không có dòng từ chối ở phía dưới.

- Người chơi: Tôi sẽ nhận trận tập với Nyx.
  → NPC: Bạn giữ đúng lời mời, không tự thêm lịch thay cho Rowan.; hành động: chỉ chuyển nhánh
- Người chơi: Giấy mời có yêu cầu Nyx rời đi vào một giờ nhất định không?
  → NPC: Không. Rowan ghi tới khi đói, về khi muốn. Nyx đã gạch hai câu, nhưng chưa từ chối lời mời. Cô ấy nhận một trận tập nhịp chậm trước khi gặp lại bạn.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Rowan ghi tới khi đói, về khi muốn. Nyx đã gạch hai câu, nhưng chưa từ chối lời mời. Cô ấy nhận một trận tập nhịp chậm trước khi gặp lại bạn.

- Người chơi: Tôi sẽ nhận trận tập với Nyx.
  → NPC: Bạn giữ đúng lời mời, không tự thêm lịch thay cho Rowan.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ đúng lời mời, không tự thêm lịch thay cho Rowan.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## nyx_clock.4

### start — battle_tower_trainer_02

Tôi nhận trận tập này. Tôi muốn xem mình có chọn được nước đúng mà không chỉ nhìn giờ kết thúc không.

- Người chơi: Vậy tôi nhận trận tập nhịp chậm.
  → NPC: Được. Tôi cất đồng hồ đếm khỏi bàn này trước khi bắt đầu.; hành động: chỉ chuyển nhánh
- Người chơi: Cô có tính phần thưởng theo thời gian kết thúc trận không?
  → NPC: Không. Tôi muốn cậu chọn nước đúng dù phải suy nghĩ lâu hơn. Sau trận, nhắc tôi về lời mời ăn trưa nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — battle_tower_trainer_02

Không. Tôi muốn cậu chọn nước đúng dù phải suy nghĩ lâu hơn. Sau trận, nhắc tôi về lời mời ăn trưa nhé.

- Người chơi: Vậy tôi nhận trận tập nhịp chậm.
  → NPC: Được. Tôi cất đồng hồ đếm khỏi bàn này trước khi bắt đầu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — battle_tower_trainer_02

Được. Tôi cất đồng hồ đếm khỏi bàn này trước khi bắt đầu.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — battle_tower_trainer_02

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## nyx_clock.5

### start — battle_tower_trainer_02

Trận đã xong. Tôi còn lời mời của Rowan chưa trả lời, và lần này tôi muốn trả lời rõ.

- Người chơi: Tôi sẽ báo lễ tân cô nhận lời mời.
  → NPC: Cảm ơn. Lần này tôi tự gặp Rowan, không nhờ cậu nói thay phần còn lại.; hành động: chỉ chuyển nhánh
- Người chơi: Cô có tới bữa trưa Rowan mời không?
  → NPC: Có. Tôi vẫn mang đồng hồ, nhưng để trong túi. Tôi muốn thử một cuộc hẹn không đo xem bao giờ mới được về.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — battle_tower_trainer_02

Có. Tôi vẫn mang đồng hồ, nhưng để trong túi. Tôi muốn thử một cuộc hẹn không đo xem bao giờ mới được về.

- Người chơi: Tôi sẽ báo lễ tân cô nhận lời mời.
  → NPC: Cảm ơn. Lần này tôi tự gặp Rowan, không nhờ cậu nói thay phần còn lại.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — battle_tower_trainer_02

Cảm ơn. Lần này tôi tự gặp Rowan, không nhờ cậu nói thay phần còn lại.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — battle_tower_trainer_02

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## orion_public_record.1

### start — battle_tower_trainer_03

Một người chơi xin xóa trận thua vì sợ gia đình đọc. Tôi muốn tìm cách giúp mà không làm sai kết quả.

- Người chơi: Tôi sẽ hỏi Bảo muốn đọc gì trong bản ghi.
  → NPC: Cảm ơn. Tôi muốn thêm thông tin đúng, không viết một chiến thắng khác.; hành động: chỉ chuyển nhánh
- Người chơi: Người chơi muốn xóa trận vì điều gì?
  → NPC: Sợ gia đình nhìn thấy một chữ thua. Tôi không muốn đổi kết quả, nhưng có thể giữ thêm phần họ đã làm tốt. Bảo có thể cho tôi một góc nhìn.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — battle_tower_trainer_03

Sợ gia đình nhìn thấy một chữ thua. Tôi không muốn đổi kết quả, nhưng có thể giữ thêm phần họ đã làm tốt. Bảo có thể cho tôi một góc nhìn.

- Người chơi: Tôi sẽ hỏi Bảo muốn đọc gì trong bản ghi.
  → NPC: Cảm ơn. Tôi muốn thêm thông tin đúng, không viết một chiến thắng khác.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — battle_tower_trainer_03

Cảm ơn. Tôi muốn thêm thông tin đúng, không viết một chiến thắng khác.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — battle_tower_trainer_03

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## orion_public_record.2

### start — town1_bao

Tôi từng sợ chỉ một chữ thua trong hồ sơ. Nhưng nếu xóa nó, tôi cũng mất phần muốn học lại từ trận đó.

- Người chơi: Tôi sẽ xem mẫu ghi có chú thích lượt đấu.
  → NPC: Ừ. Tôi muốn đọc lại trận của mình, không chỉ một kết luận về mình.; hành động: chỉ chuyển nhánh
- Người chơi: Nếu giữ kết quả thua, cậu muốn ghi thêm phần nào?
  → NPC: Những lượt tôi đổi Pokémon đúng lúc và chỗ tôi tự nhận mình sai. Chỉ chữ thua không cho tôi biết phần nào đáng giữ lại.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_bao

Những lượt tôi đổi Pokémon đúng lúc và chỗ tôi tự nhận mình sai. Chỉ chữ thua không cho tôi biết phần nào đáng giữ lại.

- Người chơi: Tôi sẽ xem mẫu ghi có chú thích lượt đấu.
  → NPC: Ừ. Tôi muốn đọc lại trận của mình, không chỉ một kết luận về mình.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_bao

Ừ. Tôi muốn đọc lại trận của mình, không chỉ một kết luận về mình.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_bao

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## orion_public_record.3

### start — narrator

Mẫu bản ghi có chỗ cho phân tích lượt và lời người chơi. Kết quả gốc vẫn nằm ở đầu trang.

- Người chơi: Tôi sẽ mang giấy để Orion bổ sung chú thích.
  → NPC: Bạn giữ phần phân tích kiểm chứng được, không đổi kết quả gốc.; hành động: chỉ chuyển nhánh
- Người chơi: Mẫu có thay đổi người thắng không?
  → NPC: Không. Nó giữ kết quả và thêm lượt tốt, lỗi cần sửa cùng lời người chơi. Orion cần ba tờ giấy cho bản có chú thích.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Nó giữ kết quả và thêm lượt tốt, lỗi cần sửa cùng lời người chơi. Orion cần ba tờ giấy cho bản có chú thích.

- Người chơi: Tôi sẽ mang giấy để Orion bổ sung chú thích.
  → NPC: Bạn giữ phần phân tích kiểm chứng được, không đổi kết quả gốc.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ phần phân tích kiểm chứng được, không đổi kết quả gốc.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## orion_public_record.4

### start — battle_tower_trainer_03

Tôi đã chọn các lượt có thể đối chiếu. Bản mới cần thêm chú thích, không thêm một kết quả khác.

- Người chơi: Tôi giao ba tờ giấy cho bản chú thích.
  → NPC: Cảm ơn. Nhờ Bảo đọc thử xem phần giải thích có rõ không nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Anh sẽ giữ cả kết quả và phần giải thích chứ?
  → NPC: Có. Ba tờ giấy đủ cho bản bổ sung. Người đọc thấy người chơi đã thử gì, không chỉ nhìn một chữ rồi tự đoán cả trận.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — battle_tower_trainer_03

Có. Ba tờ giấy đủ cho bản bổ sung. Người đọc thấy người chơi đã thử gì, không chỉ nhìn một chữ rồi tự đoán cả trận.

- Người chơi: Tôi giao ba tờ giấy cho bản chú thích.
  → NPC: Cảm ơn. Nhờ Bảo đọc thử xem phần giải thích có rõ không nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — battle_tower_trainer_03

Cảm ơn. Nhờ Bảo đọc thử xem phần giải thích có rõ không nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — battle_tower_trainer_03

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## orion_public_record.5

### start — town1_bao

Tôi đọc thử bản Orion gửi rồi. Nó còn chữ thua, nhưng không chỉ có chữ đó.

- Người chơi: Vậy tôi báo Orion phần chú thích đọc được rõ.
  → NPC: Cảm ơn. Tôi giữ bản này để xem sau trận tới.; hành động: chỉ chuyển nhánh
- Người chơi: Đọc bản mới, cậu biết mình cần sửa lượt nào không?
  → NPC: Biết. Tôi vẫn thua, nhưng thấy được cả lần đổi đúng và chỗ sai. Tôi muốn đấu lại để thử sửa chỗ đó, không phải xóa kết quả cũ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town1_bao

Biết. Tôi vẫn thua, nhưng thấy được cả lần đổi đúng và chỗ sai. Tôi muốn đấu lại để thử sửa chỗ đó, không phải xóa kết quả cũ.

- Người chơi: Vậy tôi báo Orion phần chú thích đọc được rõ.
  → NPC: Cảm ơn. Tôi giữ bản này để xem sau trận tới.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town1_bao

Cảm ơn. Tôi giữ bản này để xem sau trận tới.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town1_bao

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## cassian_family_name.1

### start — league_elite_01

Gia đình ta kể thiếu một người đang sống. Ta muốn hỏi chính anh ấy trước khi sửa dòng tên trong gia phả.

- Người chơi: Tôi sẽ hỏi Bình về cách ghi tên.
  → NPC: Cảm ơn. Đừng mời anh ấy trở lại thi đấu thay cho câu hỏi đó.; hành động: chỉ chuyển nhánh
- Người chơi: Người trong gia đình ông có thật sự mất tích không?
  → NPC: Không. Bình bỏ giải rồi làm ở cảng. Gia đình kể như anh ấy đã không còn. Tôi muốn biết Bình có muốn tên và nghề mới được ghi lại không.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — league_elite_01

Không. Bình bỏ giải rồi làm ở cảng. Gia đình kể như anh ấy đã không còn. Tôi muốn biết Bình có muốn tên và nghề mới được ghi lại không.

- Người chơi: Tôi sẽ hỏi Bình về cách ghi tên.
  → NPC: Cảm ơn. Đừng mời anh ấy trở lại thi đấu thay cho câu hỏi đó.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — league_elite_01

Cảm ơn. Đừng mời anh ấy trở lại thi đấu thay cho câu hỏi đó.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — league_elite_01

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## cassian_family_name.2

### start — town8_dockworker_binh

Cassian nhờ cậu hỏi à? Tôi đang làm ở cảng, không phải đi đâu mất tích.

- Người chơi: Tôi sẽ xem bản gia phả chưa sửa.
  → NPC: Cảm ơn. Giữ đúng nghề tôi đã nói, đừng gọi đó là một giai đoạn sa ngã.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có muốn gia đình ghi rõ nghề ở cảng không?
  → NPC: Có. Tôi chọn việc có lương đều, không mất tích và cũng không chờ ai kéo về giải đấu. Bản gia phả cũ còn ở League để đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town8_dockworker_binh

Có. Tôi chọn việc có lương đều, không mất tích và cũng không chờ ai kéo về giải đấu. Bản gia phả cũ còn ở League để đối chiếu.

- Người chơi: Tôi sẽ xem bản gia phả chưa sửa.
  → NPC: Cảm ơn. Giữ đúng nghề tôi đã nói, đừng gọi đó là một giai đoạn sa ngã.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town8_dockworker_binh

Cảm ơn. Giữ đúng nghề tôi đã nói, đừng gọi đó là một giai đoạn sa ngã.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town8_dockworker_binh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## cassian_family_name.3

### start — narrator

Bản gia phả gốc giữ dòng tên và nghề cũ của Bình. Phần công việc hiện tại chưa được bổ sung.

- Người chơi: Tôi sẽ mang giấy cho bản cập nhật.
  → NPC: Bạn giữ dòng tên gốc và nghề mới theo lời Bình.; hành động: chỉ chuyển nhánh
- Người chơi: Bản gốc còn dòng tên Bình không?
  → NPC: Còn, cùng nghề huấn luyện viên cũ. Có thể thêm nghề mới mà không cắt dòng người. Cassian cần hai tờ giấy làm bản cập nhật Bình đã đồng ý.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Còn, cùng nghề huấn luyện viên cũ. Có thể thêm nghề mới mà không cắt dòng người. Cassian cần hai tờ giấy làm bản cập nhật Bình đã đồng ý.

- Người chơi: Tôi sẽ mang giấy cho bản cập nhật.
  → NPC: Bạn giữ dòng tên gốc và nghề mới theo lời Bình.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ dòng tên gốc và nghề mới theo lời Bình.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## cassian_family_name.4

### start — league_elite_01

Bình đã nói rõ nghề muốn ghi. Ta muốn bản cập nhật giữ tên người, không kèm một đánh giá về chuyện thôi thi đấu.

- Người chơi: Tôi giao hai tờ giấy cập nhật gia phả.
  → NPC: Cảm ơn. Nhờ bạn báo Bình tên đã được ghi đúng cùng công việc nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Ông sẽ ghi nghề mới mà không gán một đánh giá chứ?
  → NPC: Có. Tôi ghi làm việc tại cảng. Hai tờ giấy đủ cho bản cập nhật và bản đối chiếu. Việc Bình không thi nữa không cho tôi quyền gọi anh ấy là thất bại.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — league_elite_01

Có. Tôi ghi làm việc tại cảng. Hai tờ giấy đủ cho bản cập nhật và bản đối chiếu. Việc Bình không thi nữa không cho tôi quyền gọi anh ấy là thất bại.

- Người chơi: Tôi giao hai tờ giấy cập nhật gia phả.
  → NPC: Cảm ơn. Nhờ bạn báo Bình tên đã được ghi đúng cùng công việc nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — league_elite_01

Cảm ơn. Nhờ bạn báo Bình tên đã được ghi đúng cùng công việc nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — league_elite_01

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## cassian_family_name.5

### start — town8_dockworker_binh

Cassian đã gửi bản cập nhật. Tôi muốn xem tên và công việc được ghi theo đúng lời mình.

- Người chơi: Tôi xác nhận anh đồng ý tên và nghề được ghi.
  → NPC: Cảm ơn. Tôi tự nói phần còn lại với gia đình.; hành động: chỉ chuyển nhánh
- Người chơi: Tên và nghề của anh đã được ghi lại. Anh có đồng ý cách đó không?
  → NPC: Có. Mai tôi vẫn đi làm, nhưng không phải sống như một dòng đã bị gia đình bỏ khỏi bản kể nữa. Tôi sẽ xem bản cập nhật khi về.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town8_dockworker_binh

Có. Mai tôi vẫn đi làm, nhưng không phải sống như một dòng đã bị gia đình bỏ khỏi bản kể nữa. Tôi sẽ xem bản cập nhật khi về.

- Người chơi: Tôi xác nhận anh đồng ý tên và nghề được ghi.
  → NPC: Cảm ơn. Tôi tự nói phần còn lại với gia đình.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town8_dockworker_binh

Cảm ơn. Tôi tự nói phần còn lại với gia đình.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town8_dockworker_binh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## seraph_unsigned_order.1

### start — league_elite_02

Một bản dự thảo của văn phòng khiến ba gia đình chuẩn bị chuyển đi. Tôi cần đối chiếu đường nó tới người nhận.

- Người chơi: Tôi sẽ kiểm tra bản lệnh gốc.
  → NPC: Cảm ơn. Tôi cần giải thích đường chuyển của nó, không chỉ bảo người nhận đọc nhầm.; hành động: chỉ chuyển nhánh
- Người chơi: Vì sao một lệnh không có chữ ký vẫn khiến người ta chuyển nhà?
  → NPC: Bản dự thảo mất trang đầu khi được sao. Người nhận tưởng quyết định đã có hiệu lực. Cậu xem bản gốc để biết phần nào bị mất.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — league_elite_02

Bản dự thảo mất trang đầu khi được sao. Người nhận tưởng quyết định đã có hiệu lực. Cậu xem bản gốc để biết phần nào bị mất.

- Người chơi: Tôi sẽ kiểm tra bản lệnh gốc.
  → NPC: Cảm ơn. Tôi cần giải thích đường chuyển của nó, không chỉ bảo người nhận đọc nhầm.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — league_elite_02

Cảm ơn. Tôi cần giải thích đường chuyển của nó, không chỉ bảo người nhận đọc nhầm.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — league_elite_02

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## seraph_unsigned_order.2

### start — narrator

Bản lệnh gốc còn trang đầu. Bản sao được giao thiếu đúng trang ghi tình trạng văn bản.

- Người chơi: Tôi sẽ hỏi Yến về ảnh hưởng của bản lệnh.
  → NPC: Bạn giữ dấu dự thảo cùng phần trang bị thiếu để đính chính đúng.; hành động: chỉ chuyển nhánh
- Người chơi: Trang đầu đã ghi đây là dự thảo chưa?
  → NPC: Có. Bản sao thiếu trang đó nên mất cả trạng thái dự thảo. Yến biết những gia đình đã bắt đầu chuyển đồ theo bản sao.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Có. Bản sao thiếu trang đó nên mất cả trạng thái dự thảo. Yến biết những gia đình đã bắt đầu chuyển đồ theo bản sao.

- Người chơi: Tôi sẽ hỏi Yến về ảnh hưởng của bản lệnh.
  → NPC: Bạn giữ dấu dự thảo cùng phần trang bị thiếu để đính chính đúng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ dấu dự thảo cùng phần trang bị thiếu để đính chính đúng.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## seraph_unsigned_order.3

### start — town4_keeper_yen

Các gia đình đã đóng nhiều đồ theo tờ giấy đó. Tôi muốn bản đính chính tới đủ từng nhà, không chỉ dán ngoài văn phòng.

- Người chơi: Tôi sẽ mang giấy cho thông báo có chữ ký.
  → NPC: Cảm ơn. Nhớ yêu cầu người chịu trách nhiệm tới giải thích trực tiếp.; hành động: chỉ chuyển nhánh
- Người chơi: Những gia đình đã chuyển đi hết chưa?
  → NPC: Chưa hết, nhưng họ đã đóng đồ và lo mấy ngày. Seraph cần ba tờ giấy làm thông báo hủy có người ký. Một câu nói lệnh không thật chưa đủ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town4_keeper_yen

Chưa hết, nhưng họ đã đóng đồ và lo mấy ngày. Seraph cần ba tờ giấy làm thông báo hủy có người ký. Một câu nói lệnh không thật chưa đủ.

- Người chơi: Tôi sẽ mang giấy cho thông báo có chữ ký.
  → NPC: Cảm ơn. Nhớ yêu cầu người chịu trách nhiệm tới giải thích trực tiếp.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town4_keeper_yen

Cảm ơn. Nhớ yêu cầu người chịu trách nhiệm tới giải thích trực tiếp.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town4_keeper_yen

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## seraph_unsigned_order.4

### start — league_elite_02

Tôi đã chuẩn bị phần giải thích. Thông báo hủy cần tên người ký và bản cho từng gia đình.

- Người chơi: Tôi giao ba tờ giấy làm thông báo hủy.
  → NPC: Cảm ơn. Sau đó nhờ Yến xác nhận từng gia đình đã nhận nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Cô có ký và tự giải thích với các gia đình không?
  → NPC: Có. Tôi ký thông báo hủy, đưa bản sao và đến nói trực tiếp. Ba tờ giấy đủ cho những hộ cần nhận bản đính chính.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — league_elite_02

Có. Tôi ký thông báo hủy, đưa bản sao và đến nói trực tiếp. Ba tờ giấy đủ cho những hộ cần nhận bản đính chính.

- Người chơi: Tôi giao ba tờ giấy làm thông báo hủy.
  → NPC: Cảm ơn. Sau đó nhờ Yến xác nhận từng gia đình đã nhận nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — league_elite_02

Cảm ơn. Sau đó nhờ Yến xác nhận từng gia đình đã nhận nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — league_elite_02

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## seraph_unsigned_order.5

### start — town4_keeper_yen

Seraph đã tới đưa giấy. Tôi giữ danh sách người nhận để không bỏ sót một nhà vì nghĩ họ đã nghe rồi.

- Người chơi: Vậy tôi xác nhận thông báo đã tới đủ người.
  → NPC: Cảm ơn. Tôi ghi ngày nhận và người đã tới giải thích.; hành động: chỉ chuyển nhánh
- Người chơi: Các gia đình đã nhận thông báo có chữ ký chưa?
  → NPC: Rồi. Họ giữ cả bản cũ và bản mới để sau này còn hỏi nếu ai phủ nhận chuyện đã xảy ra. Tôi sẽ không yêu cầu họ bỏ giấy cũ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town4_keeper_yen

Rồi. Họ giữ cả bản cũ và bản mới để sau này còn hỏi nếu ai phủ nhận chuyện đã xảy ra. Tôi sẽ không yêu cầu họ bỏ giấy cũ.

- Người chơi: Vậy tôi xác nhận thông báo đã tới đủ người.
  → NPC: Cảm ơn. Tôi ghi ngày nhận và người đã tới giải thích.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town4_keeper_yen

Cảm ơn. Tôi ghi ngày nhận và người đã tới giải thích.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town4_keeper_yen

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## kael_empty_uniform.1

### start — league_elite_03

Elle có thể sửa chiếc áo này. Trước khi mang đi, ta phải nói rõ phần mình muốn giữ lại.

- Người chơi: Tôi sẽ hỏi Elle về đường may.
  → NPC: Cảm ơn. Tôi không nhờ cô ấy quyết định hộ mình nên nhớ ai.; hành động: chỉ chuyển nhánh
- Người chơi: Ông giữ bộ áo vì vẫn mặc được hay vì chuyện cũ?
  → NPC: Cả hai, nhưng nói vừa cỡ dễ hơn. Tôi muốn xem lại đường may che tên người cùng đội. Elle có thể sửa nếu tôi chọn giữ cái tên đó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — league_elite_03

Cả hai, nhưng nói vừa cỡ dễ hơn. Tôi muốn xem lại đường may che tên người cùng đội. Elle có thể sửa nếu tôi chọn giữ cái tên đó.

- Người chơi: Tôi sẽ hỏi Elle về đường may.
  → NPC: Cảm ơn. Tôi không nhờ cô ấy quyết định hộ mình nên nhớ ai.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — league_elite_03

Cảm ơn. Tôi không nhờ cô ấy quyết định hộ mình nên nhớ ai.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — league_elite_03

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## kael_empty_uniform.2

### start — fashion_elle

Kael nhờ sửa đồng phục rồi. Tôi chưa tháo phần che tên khi ông ấy chưa chọn muốn nhìn lại phần nào.

- Người chơi: Tôi sẽ kiểm tra dấu tên dưới đường may.
  → NPC: Được. Tôi chưa cắt thứ gì khi khách chưa chọn.; hành động: chỉ chuyển nhánh
- Người chơi: Cô có thể tháo phần che tên mà giữ nguyên áo không?
  → NPC: Có. Nhưng Kael phải chọn giữ tên nào. Bạn xem dấu dưới đường may để ông ấy biết chính xác phần sẽ được mở lại.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — fashion_elle

Có. Nhưng Kael phải chọn giữ tên nào. Bạn xem dấu dưới đường may để ông ấy biết chính xác phần sẽ được mở lại.

- Người chơi: Tôi sẽ kiểm tra dấu tên dưới đường may.
  → NPC: Được. Tôi chưa cắt thứ gì khi khách chưa chọn.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — fashion_elle

Được. Tôi chưa cắt thứ gì khi khách chưa chọn.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — fashion_elle

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## kael_empty_uniform.3

### start — narrator

Dưới đường may còn dấu tên của người cùng đội. Miếng vải phủ bên ngoài được may sau bộ áo gốc.

- Người chơi: Tôi sẽ mang dây cho Elle sửa mép áo.
  → NPC: Bạn giữ ghi nhận tên còn nguyên để Kael không nhầm việc che với việc mất.; hành động: chỉ chuyển nhánh
- Người chơi: Tên dưới miếng vải có bị cháy mất không?
  → NPC: Không. Tên TOBA còn nguyên, bị một miếng vải may đè lên. Elle cần ba sợi dây để sửa mép sau khi tháo miếng che.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Tên TOBA còn nguyên, bị một miếng vải may đè lên. Elle cần ba sợi dây để sửa mép sau khi tháo miếng che.

- Người chơi: Tôi sẽ mang dây cho Elle sửa mép áo.
  → NPC: Bạn giữ ghi nhận tên còn nguyên để Kael không nhầm việc che với việc mất.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ ghi nhận tên còn nguyên để Kael không nhầm việc che với việc mất.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## kael_empty_uniform.4

### start — fashion_elle

Kael đã chọn tháo miếng che. Tôi sẽ sửa mép, không xóa những dấu cho thấy chiếc áo từng bị đổi.

- Người chơi: Tôi giao ba sợi dây sửa áo.
  → NPC: Cảm ơn. Nhờ Kael tới nhận và tự nói lý do chọn giữ tên nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Kael đã đồng ý tháo miếng che tên chứ?
  → NPC: Rồi. Tôi tháo miếng che và giữ những đường kim cũ. Ba sợi dây đủ gia cố mép, không biến bộ áo thành một món mới chưa từng bị sửa.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — fashion_elle

Rồi. Tôi tháo miếng che và giữ những đường kim cũ. Ba sợi dây đủ gia cố mép, không biến bộ áo thành một món mới chưa từng bị sửa.

- Người chơi: Tôi giao ba sợi dây sửa áo.
  → NPC: Cảm ơn. Nhờ Kael tới nhận và tự nói lý do chọn giữ tên nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — fashion_elle

Cảm ơn. Nhờ Kael tới nhận và tự nói lý do chọn giữ tên nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — fashion_elle

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## kael_empty_uniform.5

### start — league_elite_03

Elle báo áo đã sửa. Ta muốn nhận lại nó với phần tên mình từng che đi.

- Người chơi: Tôi xác nhận ông chọn giữ tên trên áo.
  → NPC: Cảm ơn. Tôi sẽ tự mặc lại, không để nó mãi trong tủ.; hành động: chỉ chuyển nhánh
- Người chơi: Ông nhận lại áo có tên TOBA chứ?
  → NPC: Có. Tôi giữ cái tên, và cũng giữ dấu mình từng che nó. Tôi không muốn người khác gọi việc này là đã sửa hết phần tôi còn nợ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — league_elite_03

Có. Tôi giữ cái tên, và cũng giữ dấu mình từng che nó. Tôi không muốn người khác gọi việc này là đã sửa hết phần tôi còn nợ.

- Người chơi: Tôi xác nhận ông chọn giữ tên trên áo.
  → NPC: Cảm ơn. Tôi sẽ tự mặc lại, không để nó mãi trong tủ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — league_elite_03

Cảm ơn. Tôi sẽ tự mặc lại, không để nó mãi trong tủ.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — league_elite_03

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## aurelia_public_day.1

### start — aurelia

Ta muốn có một buổi để người dân hỏi lại câu chưa được trả lời. Đứng trên bục khiến ta thường chỉ nghe lượt đầu.

- Người chơi: Tôi sẽ hỏi Đức điều anh ấy muốn hỏi bà.
  → NPC: Được. Đừng chỉ đưa ta những câu dễ trả lời.; hành động: chỉ chuyển nhánh
- Người chơi: Bà muốn buổi hỏi đáp khác những lần đứng trên bục thế nào?
  → NPC: Người hỏi phải có lượt hỏi lại. Ta muốn nghe điều chưa được giải đáp, nhất là từ người đã phải chờ vì giấy tờ của chúng ta.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — aurelia

Người hỏi phải có lượt hỏi lại. Ta muốn nghe điều chưa được giải đáp, nhất là từ người đã phải chờ vì giấy tờ của chúng ta.

- Người chơi: Tôi sẽ hỏi Đức điều anh ấy muốn hỏi bà.
  → NPC: Được. Đừng chỉ đưa ta những câu dễ trả lời.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — aurelia

Được. Đừng chỉ đưa ta những câu dễ trả lời.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — aurelia

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## aurelia_public_day.2

### start — town6_informant_duc

Tôi có một câu muốn hỏi Champion. Tôi muốn giữ nguyên câu ấy, không đổi nó thành lời dễ nghe hơn.

- Người chơi: Tôi sẽ kiểm tra hòm câu hỏi chưa lọc.
  → NPC: Cảm ơn. Giữ nguyên câu hỏi của tôi, không đổi thành một lời cảm ơn cho dễ nghe.; hành động: chỉ chuyển nhánh
- Người chơi: Anh muốn hỏi Champion điều gì nhất?
  → NPC: Vì sao giấy của tôi mất thì tôi phải chứng minh, còn văn phòng mất giấy thì tôi chỉ được chờ? Hòm câu hỏi còn những câu bị gạt ra, cậu xem cả phần đó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town6_informant_duc

Vì sao giấy của tôi mất thì tôi phải chứng minh, còn văn phòng mất giấy thì tôi chỉ được chờ? Hòm câu hỏi còn những câu bị gạt ra, cậu xem cả phần đó.

- Người chơi: Tôi sẽ kiểm tra hòm câu hỏi chưa lọc.
  → NPC: Cảm ơn. Giữ nguyên câu hỏi của tôi, không đổi thành một lời cảm ơn cho dễ nghe.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town6_informant_duc

Cảm ơn. Giữ nguyên câu hỏi của tôi, không đổi thành một lời cảm ơn cho dễ nghe.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town6_informant_duc

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## aurelia_public_day.3

### start — narrator

Hòm có cả ngăn đã được chọn và ngăn bị loại khỏi buổi hỏi đáp. Nhiều câu hỏi về hồ sơ nằm ở ngăn thứ hai.

- Người chơi: Tôi sẽ mang giấy và cả ngăn câu hỏi bị loại.
  → NPC: Bạn giữ đủ câu hỏi, không lọc lại những phần gây khó chịu.; hành động: chỉ chuyển nhánh
- Người chơi: Những câu bị gạt ra có thuộc chuyện League phải xử lý không?
  → NPC: Có. Chúng bị chuyển sang ngăn ngoài nội dung, trong khi hỏi về chính hồ sơ của văn phòng. Aurelia cần bốn tờ giấy ghi câu trả lời công khai.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Có. Chúng bị chuyển sang ngăn ngoài nội dung, trong khi hỏi về chính hồ sơ của văn phòng. Aurelia cần bốn tờ giấy ghi câu trả lời công khai.

- Người chơi: Tôi sẽ mang giấy và cả ngăn câu hỏi bị loại.
  → NPC: Bạn giữ đủ câu hỏi, không lọc lại những phần gây khó chịu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ đủ câu hỏi, không lọc lại những phần gây khó chịu.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## aurelia_public_day.4

### start — aurelia

Ta sẽ giữ những câu bị gạt ra. Bản trả lời cần cho người hỏi biết ai nhận trách nhiệm với phần chưa có đáp án.

- Người chơi: Tôi giao bốn tờ giấy làm bản trả lời.
  → NPC: Cảm ơn. Báo Đức câu hỏi của anh ấy đã có người nhận trách nhiệm nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Bà có giữ cả câu chưa trả lời được trong bản công khai không?
  → NPC: Có. Câu chưa có đáp án sẽ ghi người chịu trách nhiệm và ngày trả lời tiếp. Bốn tờ giấy đủ cho bản niêm yết đầu tiên.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — aurelia

Có. Câu chưa có đáp án sẽ ghi người chịu trách nhiệm và ngày trả lời tiếp. Bốn tờ giấy đủ cho bản niêm yết đầu tiên.

- Người chơi: Tôi giao bốn tờ giấy làm bản trả lời.
  → NPC: Cảm ơn. Báo Đức câu hỏi của anh ấy đã có người nhận trách nhiệm nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — aurelia

Cảm ơn. Báo Đức câu hỏi của anh ấy đã có người nhận trách nhiệm nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — aurelia

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## aurelia_public_day.5

### start — town6_informant_duc

Tôi nhận được tên người phải trả lời rồi. Giờ tôi muốn tự đến hỏi phần còn lại.

- Người chơi: Vậy tôi xác nhận anh đã nhận thông tin buổi gặp.
  → NPC: Cảm ơn. Tôi giữ bản công khai để còn đối chiếu câu trả lời lần sau.; hành động: chỉ chuyển nhánh
- Người chơi: Anh có tới buổi hỏi đáp để tự hỏi tiếp không?
  → NPC: Có. Tôi biết ai phải trả lời và khi nào. Tôi không muốn cậu đứng hỏi thay mình cả đời; lần này tôi sẽ tới.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town6_informant_duc

Có. Tôi biết ai phải trả lời và khi nào. Tôi không muốn cậu đứng hỏi thay mình cả đời; lần này tôi sẽ tới.

- Người chơi: Vậy tôi xác nhận anh đã nhận thông tin buổi gặp.
  → NPC: Cảm ơn. Tôi giữ bản công khai để còn đối chiếu câu trả lời lần sau.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town6_informant_duc

Cảm ơn. Tôi giữ bản công khai để còn đối chiếu câu trả lời lần sau.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town6_informant_duc

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## bran_open_gate.1

### start — school_wolf_gatekeeper

Ta đã bảo một học viên quay về vì chưa có huy hiệu. Nhưng có lẽ ta chưa hỏi đúng lớp em ấy muốn học.

- Người chơi: Tôi sẽ hỏi Linh muốn học lớp nào.
  → NPC: Cảm ơn. Tôi kiểm tra lại quy định trước khi giữ cách trả lời cũ.; hành động: chỉ chuyển nhánh
- Người chơi: Anh đã dùng điều kiện chiến đấu cho cả lớp sơ cứu sao?
  → NPC: Đúng. Tôi thấy Linh chưa có huy hiệu rồi bảo quay về. Sau đó thấy em ấy giúp người trượt dốc. Bạn hỏi vì sao em ấy tới đây giúp tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — school_wolf_gatekeeper

Đúng. Tôi thấy Linh chưa có huy hiệu rồi bảo quay về. Sau đó thấy em ấy giúp người trượt dốc. Bạn hỏi vì sao em ấy tới đây giúp tôi.

- Người chơi: Tôi sẽ hỏi Linh muốn học lớp nào.
  → NPC: Cảm ơn. Tôi kiểm tra lại quy định trước khi giữ cách trả lời cũ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_gatekeeper

Cảm ơn. Tôi kiểm tra lại quy định trước khi giữ cách trả lời cũ.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — school_wolf_gatekeeper

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## bran_open_gate.2

### start — town2_student_linh

Tôi tới School vì muốn học cứu hộ. Tôi chưa xin vượt bài thử chiến đấu hôm nay.

- Người chơi: Tôi sẽ đọc điều kiện nhận học viên.
  → NPC: Cảm ơn. Tôi muốn biết đúng cửa mình đang xin vào.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu muốn vào bài thử chiến đấu hay học sơ cứu trước?
  → NPC: Sơ cứu. Tôi vẫn muốn vượt bài thử sau này, nhưng hôm nay muốn biết giúp người bị thương thế nào. Quy định có phân hai lớp không?; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_student_linh

Sơ cứu. Tôi vẫn muốn vượt bài thử sau này, nhưng hôm nay muốn biết giúp người bị thương thế nào. Quy định có phân hai lớp không?

- Người chơi: Tôi sẽ đọc điều kiện nhận học viên.
  → NPC: Cảm ơn. Tôi muốn biết đúng cửa mình đang xin vào.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_student_linh

Cảm ơn. Tôi muốn biết đúng cửa mình đang xin vào.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_student_linh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## bran_open_gate.3

### start — narrator

Quy định nhận học viên có phần sơ cứu và phần thử chiến đấu riêng. Mỗi phần ghi điều kiện ở một mục khác.

- Người chơi: Tôi sẽ mang giấy làm bảng điều kiện riêng.
  → NPC: Bạn giữ nguyên điều kiện của từng lớp, không bỏ yêu cầu bài thử chiến đấu.; hành động: chỉ chuyển nhánh
- Người chơi: Quy định có yêu cầu huy hiệu cho lớp sơ cứu không?
  → NPC: Không. Điều kiện League áp cho bài thử chiến đấu, không áp cho lớp sơ cứu. Bran cần hai tờ giấy viết hướng dẫn rõ từng lớp.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Điều kiện League áp cho bài thử chiến đấu, không áp cho lớp sơ cứu. Bran cần hai tờ giấy viết hướng dẫn rõ từng lớp.

- Người chơi: Tôi sẽ mang giấy làm bảng điều kiện riêng.
  → NPC: Bạn giữ nguyên điều kiện của từng lớp, không bỏ yêu cầu bài thử chiến đấu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ nguyên điều kiện của từng lớp, không bỏ yêu cầu bài thử chiến đấu.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## bran_open_gate.4

### start — school_wolf_gatekeeper

Ta đã đọc lại quy định. Bảng ở cổng cần phân từng lớp thay vì dùng một câu trả lời cho tất cả.

- Người chơi: Tôi giao hai tờ giấy viết hướng dẫn.
  → NPC: Cảm ơn. Nhờ bạn báo Linh được vào lớp sơ cứu nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Anh sẽ ghi rõ hai nhóm điều kiện trên bảng chứ?
  → NPC: Có. Tôi không để người xin học sơ cứu phải chứng minh chuyện khác nữa. Hai tờ giấy đủ ghi hướng dẫn ở cổng và phòng học.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — school_wolf_gatekeeper

Có. Tôi không để người xin học sơ cứu phải chứng minh chuyện khác nữa. Hai tờ giấy đủ ghi hướng dẫn ở cổng và phòng học.

- Người chơi: Tôi giao hai tờ giấy viết hướng dẫn.
  → NPC: Cảm ơn. Nhờ bạn báo Linh được vào lớp sơ cứu nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_gatekeeper

Cảm ơn. Nhờ bạn báo Linh được vào lớp sơ cứu nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — school_wolf_gatekeeper

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## bran_open_gate.5

### start — town2_student_linh

Bran đã báo có thể vào lớp sơ cứu. Tôi muốn bắt đầu từ phần mình đã tới để học.

- Người chơi: Tôi xác nhận cậu đã nhận hướng dẫn vào lớp.
  → NPC: Cảm ơn. Tôi sẽ tới đúng phòng, không đứng ngoài chờ huy hiệu nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Cổng đã phân rõ lớp sơ cứu. Cậu có muốn bắt đầu học không?
  → NPC: Có. Tôi vẫn luyện để qua bài thử sau này. Nhưng hôm nay tôi có thể học phần giúp người mà mình đã tới để hỏi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town2_student_linh

Có. Tôi vẫn luyện để qua bài thử sau này. Nhưng hôm nay tôi có thể học phần giúp người mà mình đã tới để hỏi.

- Người chơi: Tôi xác nhận cậu đã nhận hướng dẫn vào lớp.
  → NPC: Cảm ơn. Tôi sẽ tới đúng phòng, không đứng ngoài chờ huy hiệu nữa.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town2_student_linh

Cảm ơn. Tôi sẽ tới đúng phòng, không đứng ngoài chờ huy hiệu nữa.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town2_student_linh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## vargan_watch.1

### start — school_wolf_master

Bran bảo ta chỉ mắng người tới muộn mà không nói đủ lý do. Ta muốn cậu xem cách đội ghi giờ về.

- Người chơi: Tôi sẽ hỏi Bran cách báo giờ về.
  → NPC: Được. Ta cũng cần ghi giờ của mình vào sổ.; hành động: chỉ chuyển nhánh
- Người chơi: Ông muốn học viên đúng giờ vì lý do gì?
  → NPC: Đội cứu hộ phải biết đang chờ hay phải đi tìm người. Ta đã nói chuyện đó bằng lời mắng quá nhiều. Bran sẽ giải thích phần quy trình cho cậu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Chuẩn bị xong rồi quay lại, ta chưa tính việc này là hoàn thành.; hành động: chỉ chuyển nhánh

### answer — school_wolf_master

Đội cứu hộ phải biết đang chờ hay phải đi tìm người. Ta đã nói chuyện đó bằng lời mắng quá nhiều. Bran sẽ giải thích phần quy trình cho cậu.

- Người chơi: Tôi sẽ hỏi Bran cách báo giờ về.
  → NPC: Được. Ta cũng cần ghi giờ của mình vào sổ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Chuẩn bị xong rồi quay lại, ta chưa tính việc này là hoàn thành.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_master

Được. Ta cũng cần ghi giờ của mình vào sổ.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — school_wolf_master

Được. Chuẩn bị xong rồi quay lại, ta chưa tính việc này là hoàn thành.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## vargan_watch.2

### start — school_wolf_gatekeeper

Khi quá giờ, đội phải biết nên chờ hay bắt đầu tìm. Sổ cần đủ giờ dự kiến và người nhận tin.

- Người chơi: Tôi sẽ xem sổ báo giờ về.
  → NPC: Cảm ơn. Quy trình cần dùng cho cả người dạy, không chỉ học viên.; hành động: chỉ chuyển nhánh
- Người chơi: Khi có người tới muộn, đội cần biết thông tin gì?
  → NPC: Người ấy đang ở đâu, có đổi kế hoạch không và ai nhận tin. Sổ School có giờ dự kiến, nhưng dòng của Vargan vẫn để trống.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — school_wolf_gatekeeper

Người ấy đang ở đâu, có đổi kế hoạch không và ai nhận tin. Sổ School có giờ dự kiến, nhưng dòng của Vargan vẫn để trống.

- Người chơi: Tôi sẽ xem sổ báo giờ về.
  → NPC: Cảm ơn. Quy trình cần dùng cho cả người dạy, không chỉ học viên.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_gatekeeper

Cảm ơn. Quy trình cần dùng cho cả người dạy, không chỉ học viên.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — school_wolf_gatekeeper

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## vargan_watch.3

### start — narrator

Sổ có dòng của học viên và người dạy. Một dòng còn thiếu thông tin trước chuyến đi.

- Người chơi: Tôi sẽ mang redstone cho Vargan.
  → NPC: Bạn giữ đúng phần còn thiếu, không tự đoán giờ thay ông ấy.; hành động: chỉ chuyển nhánh
- Người chơi: Vargan có điền giờ và người nhận tin trong sổ không?
  → NPC: Chưa. Các học viên có đủ hai phần, dòng của ông ấy thiếu. Một redstone giúp làm bộ nhắc để ông ấy báo giờ trước khi đi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Chưa. Các học viên có đủ hai phần, dòng của ông ấy thiếu. Một redstone giúp làm bộ nhắc để ông ấy báo giờ trước khi đi.

- Người chơi: Tôi sẽ mang redstone cho Vargan.
  → NPC: Bạn giữ đúng phần còn thiếu, không tự đoán giờ thay ông ấy.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ đúng phần còn thiếu, không tự đoán giờ thay ông ấy.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## vargan_watch.4

### start — school_wolf_master

Ta đã chọn giờ dự kiến. Cần bộ nhắc để không chỉ ghi quy tắc cho người khác rồi bỏ dòng của mình.

- Người chơi: Tôi giao redstone làm bộ nhắc giờ.
  → NPC: Được. Nhờ Bran xác nhận đã nhận giờ dự kiến của ta.; hành động: chỉ chuyển nhánh
- Người chơi: Ông có ghi giờ về và tên Bran nhận tin không?
  → NPC: Có. Ta sẽ dùng bộ nhắc và ghi trước khi đi. Một redstone đủ. Việc ta làm theo quy tắc không có nghĩa học viên được bỏ nó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Chuẩn bị xong rồi quay lại, ta chưa tính việc này là hoàn thành.; hành động: chỉ chuyển nhánh

### answer — school_wolf_master

Có. Ta sẽ dùng bộ nhắc và ghi trước khi đi. Một redstone đủ. Việc ta làm theo quy tắc không có nghĩa học viên được bỏ nó.

- Người chơi: Tôi giao redstone làm bộ nhắc giờ.
  → NPC: Được. Nhờ Bran xác nhận đã nhận giờ dự kiến của ta.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Chuẩn bị xong rồi quay lại, ta chưa tính việc này là hoàn thành.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_master

Được. Nhờ Bran xác nhận đã nhận giờ dự kiến của ta.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — school_wolf_master

Được. Chuẩn bị xong rồi quay lại, ta chưa tính việc này là hoàn thành.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## vargan_watch.5

### start — school_wolf_gatekeeper

Vargan đã gửi giờ và tên người nhận. Lần này tôi không phải đoán khi nào cần gọi ông ấy.

- Người chơi: Vậy tôi xác nhận ông ấy cũng dùng cùng quy trình.
  → NPC: Cảm ơn. Tôi sẽ báo nếu giờ đổi, như với mọi người khác trong đội.; hành động: chỉ chuyển nhánh
- Người chơi: Anh đã nhận được giờ về của Vargan chưa?
  → NPC: Rồi. Tôi biết khi nào ông ấy dự kiến về và lúc nào cần liên lạc, không phải đoán từ lời ông ấy dặn học viên.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — school_wolf_gatekeeper

Rồi. Tôi biết khi nào ông ấy dự kiến về và lúc nào cần liên lạc, không phải đoán từ lời ông ấy dặn học viên.

- Người chơi: Vậy tôi xác nhận ông ấy cũng dùng cùng quy trình.
  → NPC: Cảm ơn. Tôi sẽ báo nếu giờ đổi, như với mọi người khác trong đội.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_gatekeeper

Cảm ơn. Tôi sẽ báo nếu giờ đổi, như với mọi người khác trong đội.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — school_wolf_gatekeeper

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## wolf_two_routes.1

### start — school_wolf_trainer_02

Tuyến nhanh trên bản đồ có đoạn dốc hẹp. Tôi muốn đo lại với cả cáng cứu hộ, không chỉ người đi bộ.

- Người chơi: Tôi sẽ kiểm tra độ rộng ở mốc đường cáng.
  → NPC: Cảm ơn. Đo cả phần người được khiêng cần, không chỉ chân người chạy.; hành động: chỉ chuyển nhánh
- Người chơi: Vì sao anh muốn đo lại tuyến nhanh trên bản đồ?
  → NPC: Nó nhanh cho người đi bộ, nhưng không đủ rộng cho cáng. Bạn kiểm tra mốc ở dốc để chúng ta không đưa đội cứu hộ vào chỗ phải quay lại.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — school_wolf_trainer_02

Nó nhanh cho người đi bộ, nhưng không đủ rộng cho cáng. Bạn kiểm tra mốc ở dốc để chúng ta không đưa đội cứu hộ vào chỗ phải quay lại.

- Người chơi: Tôi sẽ kiểm tra độ rộng ở mốc đường cáng.
  → NPC: Cảm ơn. Đo cả phần người được khiêng cần, không chỉ chân người chạy.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_trainer_02

Cảm ơn. Đo cả phần người được khiêng cần, không chỉ chân người chạy.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — school_wolf_trainer_02

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## wolf_two_routes.2

### start — narrator

Mốc dốc có chiều dài nhưng thiếu chiều rộng. Phần người mang cáng cần đi qua có thể đo trực tiếp.

- Người chơi: Tôi sẽ tới Hati xác nhận đường vòng.
  → NPC: Bạn ghi điểm hẹp và chiều rộng cần để phân đúng hai tuyến.; hành động: chỉ chuyển nhánh
- Người chơi: Lối hẹp có đủ cho cáng đi qua không?
  → NPC: Không. Bản đồ chỉ đo quãng đường, bỏ độ rộng. Hati biết tuyến vòng mà đội có thể đưa cả người trở về.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Bản đồ chỉ đo quãng đường, bỏ độ rộng. Hati biết tuyến vòng mà đội có thể đưa cả người trở về.

- Người chơi: Tôi sẽ tới Hati xác nhận đường vòng.
  → NPC: Bạn ghi điểm hẹp và chiều rộng cần để phân đúng hai tuyến.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn ghi điểm hẹp và chiều rộng cần để phân đúng hai tuyến.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## wolf_two_routes.3

### start — school_wolf_trainer_03

Skoll đang đối chiếu đường cáng à? Tôi có một tuyến vòng dùng trong chuyến tập trước.

- Người chơi: Tôi sẽ mang ván làm bảng đường cáng.
  → NPC: Cảm ơn. Ghi rõ mục đích, đừng chỉ gọi nó là đường chậm.; hành động: chỉ chuyển nhánh
- Người chơi: Đường vòng có giữ được cả đội mang cáng không?
  → NPC: Có. Dài hơn, nhưng không phải tháo cáng giữa dốc. Skoll cần hai tấm ván sồi làm bảng chỉ rõ tuyến dùng cho cứu hộ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — school_wolf_trainer_03

Có. Dài hơn, nhưng không phải tháo cáng giữa dốc. Skoll cần hai tấm ván sồi làm bảng chỉ rõ tuyến dùng cho cứu hộ.

- Người chơi: Tôi sẽ mang ván làm bảng đường cáng.
  → NPC: Cảm ơn. Ghi rõ mục đích, đừng chỉ gọi nó là đường chậm.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_trainer_03

Cảm ơn. Ghi rõ mục đích, đừng chỉ gọi nó là đường chậm.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: close

### declined — school_wolf_trainer_03

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## wolf_two_routes.4

### start — school_wolf_trainer_02

Tôi đã nhận tuyến vòng của Hati. Bảng mới cần cho người đi biết họ đang chọn đường cho việc gì.

- Người chơi: Tôi giao hai tấm ván sồi làm bảng.
  → NPC: Cảm ơn. Báo Hati tuyến cáng đã có bảng trước chuyến tập tiếp nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Anh sẽ để cả hai tuyến và ghi rõ ai dùng chứ?
  → NPC: Đúng. Hai tấm ván đủ làm bảng ở chỗ chia đường. Người đi bộ có tuyến nhanh, người mang cáng có tuyến đủ rộng.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — school_wolf_trainer_02

Đúng. Hai tấm ván đủ làm bảng ở chỗ chia đường. Người đi bộ có tuyến nhanh, người mang cáng có tuyến đủ rộng.

- Người chơi: Tôi giao hai tấm ván sồi làm bảng.
  → NPC: Cảm ơn. Báo Hati tuyến cáng đã có bảng trước chuyến tập tiếp nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_trainer_02

Cảm ơn. Báo Hati tuyến cáng đã có bảng trước chuyến tập tiếp nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — school_wolf_trainer_02

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## wolf_two_routes.5

### start — school_wolf_trainer_03

Skoll đã dựng bảng mới. Tôi muốn kiểm tra phần hướng đường cáng trước khi dẫn đội tập.

- Người chơi: Vậy tôi xác nhận hai tuyến đã được phân rõ.
  → NPC: Cảm ơn. Chúng tôi sẽ kiểm tra lại nếu dốc hoặc đường có thay đổi.; hành động: chỉ chuyển nhánh
- Người chơi: Bảng mới có ghi đúng tuyến đủ rộng không?
  → NPC: Có. Người mới không cần tự mắc kẹt mới biết khác biệt. Tôi sẽ dẫn đội tập theo đúng bảng mới.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — school_wolf_trainer_03

Có. Người mới không cần tự mắc kẹt mới biết khác biệt. Tôi sẽ dẫn đội tập theo đúng bảng mới.

- Người chơi: Vậy tôi xác nhận hai tuyến đã được phân rõ.
  → NPC: Cảm ơn. Chúng tôi sẽ kiểm tra lại nếu dốc hoặc đường có thay đổi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — school_wolf_trainer_03

Cảm ơn. Chúng tôi sẽ kiểm tra lại nếu dốc hoặc đường có thay đổi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — school_wolf_trainer_03

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## last_person_letter.1

### start — archive_keeper

Thư mới ký tên đầy đủ. Bạn có thể đọc cả phần hỏi thăm, không chỉ tìm một lời hẹn khẩn.

- Người chơi: Tôi sẽ hỏi Hale muốn trả lời thư thế nào.
  → NPC: Bạn giữ thư đúng như đã viết, không tự biến lời hỏi thăm thành một lời triệu tập.; hành động: chỉ chuyển nhánh
- Người chơi: TOBA có nhờ chúng tôi đến một điểm hẹn mới không?
  → NPC: Không. Thư ký tên đầy đủ và chỉ hỏi những người trong ảnh còn gặp nhau không. Hale là người đầu tiên có thể trả lời câu đó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — archive_keeper

Không. Thư ký tên đầy đủ và chỉ hỏi những người trong ảnh còn gặp nhau không. Hale là người đầu tiên có thể trả lời câu đó.

- Người chơi: Tôi sẽ hỏi Hale muốn trả lời thư thế nào.
  → NPC: Bạn giữ thư đúng như đã viết, không tự biến lời hỏi thăm thành một lời triệu tập.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — archive_keeper

Bạn giữ thư đúng như đã viết, không tự biến lời hỏi thăm thành một lời triệu tập.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — archive_keeper

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## last_person_letter.2

### start — professor_hale

Ta đã đọc thư của TOBA. Ta muốn trả lời điều anh ấy hỏi, không viết lại nguyên một lời giải trình.

- Người chơi: Tôi sẽ hỏi Aurelia về lá thư của bà.
  → NPC: Được. Ta tự ký thư mình, không nhờ nhóm ký thay một lời chung.; hành động: chỉ chuyển nhánh
- Người chơi: Ông muốn viết gì ngoài lời xin lỗi?
  → NPC: Anh ấy hỏi ta ăn ngủ ra sao. Ta sẽ trả lời cả điều đó và kể nhóm vẫn gặp nhau. Nhờ cậu hỏi Aurelia điều bà ấy muốn gửi riêng nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.; hành động: chỉ chuyển nhánh

### answer — professor_hale

Anh ấy hỏi ta ăn ngủ ra sao. Ta sẽ trả lời cả điều đó và kể nhóm vẫn gặp nhau. Nhờ cậu hỏi Aurelia điều bà ấy muốn gửi riêng nhé.

- Người chơi: Tôi sẽ hỏi Aurelia về lá thư của bà.
  → NPC: Được. Ta tự ký thư mình, không nhờ nhóm ký thay một lời chung.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.; hành động: chỉ chuyển nhánh

### accepted — professor_hale

Được. Ta tự ký thư mình, không nhờ nhóm ký thay một lời chung.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — professor_hale

Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## last_person_letter.3

### start — aurelia

Ta muốn gửi TOBA một lời mời riêng. Nó không cần chức danh hay yêu cầu anh ấy kết thúc câu chuyện theo ý ta.

- Người chơi: Tôi sẽ mang giấy cho ba lá thư riêng.
  → NPC: Cảm ơn. Ta sẽ không gộp chúng để tránh đứng tên phần mình.; hành động: chỉ chuyển nhánh
- Người chơi: Bà có đặt điều kiện cho lời mời TOBA trở về không?
  → NPC: Không. Một lời mời gặp, không chức danh hay yêu cầu tha thứ. Người nhận được chọn có đến hay không. Cần ba tờ giấy giữ các thư trả lời riêng.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — aurelia

Không. Một lời mời gặp, không chức danh hay yêu cầu tha thứ. Người nhận được chọn có đến hay không. Cần ba tờ giấy giữ các thư trả lời riêng.

- Người chơi: Tôi sẽ mang giấy cho ba lá thư riêng.
  → NPC: Cảm ơn. Ta sẽ không gộp chúng để tránh đứng tên phần mình.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — aurelia

Cảm ơn. Ta sẽ không gộp chúng để tránh đứng tên phần mình.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — aurelia

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## last_person_letter.4

### start — archive_keeper

Nhóm đã chọn viết thư riêng. Giấy và chữ ký của từng người sẽ được giữ tách nhau.

- Người chơi: Tôi giao ba tờ giấy cho các thư riêng.
  → NPC: Cảm ơn. Bạn hỏi Mara điều cô ấy muốn nói sau cuộc điều tra nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Các thư có được giữ riêng theo người viết không?
  → NPC: Có. Ba tờ giấy đủ cho ba thư trả lời. Mỗi người tự ký, không ghép thành một lời xin lỗi chung mà không ai chịu tên.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — archive_keeper

Có. Ba tờ giấy đủ cho ba thư trả lời. Mỗi người tự ký, không ghép thành một lời xin lỗi chung mà không ai chịu tên.

- Người chơi: Tôi giao ba tờ giấy cho các thư riêng.
  → NPC: Cảm ơn. Bạn hỏi Mara điều cô ấy muốn nói sau cuộc điều tra nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — archive_keeper

Cảm ơn. Bạn hỏi Mara điều cô ấy muốn nói sau cuộc điều tra nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — archive_keeper

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## last_person_letter.5

### start — mara_voss

Tôi đã nghĩ phần muốn nhắn trong thư. Không có báo cáo hay nhiệm vụ nào cần nhờ TOBA làm tiếp.

- Người chơi: Tôi sẽ giữ lời nhắn của cậu trong thư riêng.
  → NPC: Được. Lần này tôi sẽ tự hỏi thăm bằng tên mình, không nhờ một dấu hỏi chuyển lời.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu muốn nhắn TOBA điều gì sau khi mọi chuyện đã rõ?
  → NPC: Rằng chúng tôi vẫn có thể ngồi ăn với nhau, không chỉ gặp khi có việc phải cứu. Tôi muốn biết anh ấy muốn gặp lúc nào, chứ không đưa lịch thay anh ấy.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### answer — mara_voss

Rằng chúng tôi vẫn có thể ngồi ăn với nhau, không chỉ gặp khi có việc phải cứu. Tôi muốn biết anh ấy muốn gặp lúc nào, chứ không đưa lịch thay anh ấy.

- Người chơi: Tôi sẽ giữ lời nhắn của cậu trong thư riêng.
  → NPC: Được. Lần này tôi sẽ tự hỏi thăm bằng tên mình, không nhờ một dấu hỏi chuyển lời.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### accepted — mara_voss

Được. Lần này tôi sẽ tự hỏi thăm bằng tên mình, không nhờ một dấu hỏi chuyển lời.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — mara_voss

Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.1

### start — weather_researcher_mai

Báo của cảng và trạm rừng không khớp nhau. Tôi muốn nghe từ từng nơi trước khi vẽ một kết luận chung.

- Người chơi: Tôi sẽ bắt đầu với Thảo ở cảng.
  → NPC: Cảm ơn. Giữ lời kể của từng nơi riêng để tôi đối chiếu được.; hành động: chỉ chuyển nhánh
- Người chơi: Cô cần xác nhận tình hình ở trạm nào trước?
  → NPC: Cảng báo mưa ba ngày, trạm rừng báo sông cạn. Hỏi Thảo ở cảng rồi Hạnh ở rừng. Tôi cần cả chuyện người và Pokémon đang gặp, không chỉ số đo.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — weather_researcher_mai

Cảng báo mưa ba ngày, trạm rừng báo sông cạn. Hỏi Thảo ở cảng rồi Hạnh ở rừng. Tôi cần cả chuyện người và Pokémon đang gặp, không chỉ số đo.

- Người chơi: Tôi sẽ bắt đầu với Thảo ở cảng.
  → NPC: Cảm ơn. Giữ lời kể của từng nơi riêng để tôi đối chiếu được.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — weather_researcher_mai

Cảm ơn. Giữ lời kể của từng nơi riêng để tôi đối chiếu được.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — weather_researcher_mai

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.2

### start — town8_fisher_thao

Mưa giữ tàu ở bến mấy ngày rồi. Tôi cần đường an toàn hơn một lời bảo cá đang gần bờ.

- Người chơi: Tôi sẽ kiểm tra cột đo triều.
  → NPC: Cảm ơn. Đừng coi cá sát bờ là lý do cho thuyền ra lúc này.; hành động: chỉ chuyển nhánh
- Người chơi: Mưa đang ảnh hưởng chuyến tàu thế nào?
  → NPC: Tàu không ra được. Cá gần bờ nhưng sóng không an toàn để bắt. Cột đo triều ghi mực nước vượt mùa; bạn xem nó rồi hỏi phía đất liền.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town8_fisher_thao

Tàu không ra được. Cá gần bờ nhưng sóng không an toàn để bắt. Cột đo triều ghi mực nước vượt mùa; bạn xem nó rồi hỏi phía đất liền.

- Người chơi: Tôi sẽ kiểm tra cột đo triều.
  → NPC: Cảm ơn. Đừng coi cá sát bờ là lý do cho thuyền ra lúc này.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town8_fisher_thao

Cảm ơn. Đừng coi cá sát bờ là lý do cho thuyền ra lúc này.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town8_fisher_thao

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.3

### start — narrator

Cột triều giữ đủ thời điểm đo, cùng bản ghi hướng gió. Bạn có thể đối chiếu nhịp giữa các lần đo.

- Người chơi: Tôi sẽ hỏi Hạnh về mực nước sông.
  → NPC: Bạn giữ chu kỳ sáu giờ và những lần gió đổi mà triều không đổi.; hành động: chỉ chuyển nhánh
- Người chơi: Mực triều có đổi theo gió không?
  → NPC: Không. Nó lặp theo chu kỳ sáu giờ dù gió thay đổi. Cần bản phía sông để đối chiếu, chưa đủ để kết luận một Pokémon gây ra mọi chuyện.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Nó lặp theo chu kỳ sáu giờ dù gió thay đổi. Cần bản phía sông để đối chiếu, chưa đủ để kết luận một Pokémon gây ra mọi chuyện.

- Người chơi: Tôi sẽ hỏi Hạnh về mực nước sông.
  → NPC: Bạn giữ chu kỳ sáu giờ và những lần gió đổi mà triều không đổi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ chu kỳ sáu giờ và những lần gió đổi mà triều không đổi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.4

### start — town5_ranger_hanh

Pokémon đang dồn tới trạm vì sông cạn. Chúng tôi cần chỗ giữ nước trong lúc tìm nguyên nhân.

- Người chơi: Tôi sẽ chuẩn bị bốn thỏi sắt cho trạm.
  → NPC: Cảm ơn. Khi có chỗ chứa, chúng tôi có thể giữ phần cho những con tới sau.; hành động: chỉ chuyển nhánh
- Người chơi: Trạm cần gì để giúp Pokémon trước mắt?
  → NPC: Bốn thỏi sắt cho thùng chứa nước. Pokémon dồn tới vì sông cạn; tìm nguyên nhân vẫn cần làm, nhưng chúng cần nước ngay hôm nay.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town5_ranger_hanh

Bốn thỏi sắt cho thùng chứa nước. Pokémon dồn tới vì sông cạn; tìm nguyên nhân vẫn cần làm, nhưng chúng cần nước ngay hôm nay.

- Người chơi: Tôi sẽ chuẩn bị bốn thỏi sắt cho trạm.
  → NPC: Cảm ơn. Khi có chỗ chứa, chúng tôi có thể giữ phần cho những con tới sau.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town5_ranger_hanh

Cảm ơn. Khi có chỗ chứa, chúng tôi có thể giữ phần cho những con tới sau.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town5_ranger_hanh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.5

### start — town5_ranger_hanh

Tôi đã chuẩn bị phần thùng. Vật tư tới đủ thì có thể phân nước cho những con còn đang chờ.

- Người chơi: Tôi giao bốn thỏi sắt làm thùng nước.
  → NPC: Cảm ơn. Bạn xem mốc sông giúp tôi, tôi ở lại phân nước cho trạm.; hành động: chỉ chuyển nhánh
- Người chơi: Bốn thỏi sắt đủ cho phần thùng đang thiếu chứ?
  → NPC: Đủ. Tôi sẽ hoàn thiện thùng rồi kiểm tra mốc sông. Việc này giúp hôm nay, nhưng chúng ta vẫn cần biết vì sao nước tiếp tục giảm.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town5_ranger_hanh

Đủ. Tôi sẽ hoàn thiện thùng rồi kiểm tra mốc sông. Việc này giúp hôm nay, nhưng chúng ta vẫn cần biết vì sao nước tiếp tục giảm.

- Người chơi: Tôi giao bốn thỏi sắt làm thùng nước.
  → NPC: Cảm ơn. Bạn xem mốc sông giúp tôi, tôi ở lại phân nước cho trạm.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town5_ranger_hanh

Cảm ơn. Bạn xem mốc sông giúp tôi, tôi ở lại phân nước cho trạm.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town5_ranger_hanh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.6

### start — narrator

Mốc sông có thời điểm nước hạ. Có thể đặt các lần đo cạnh bản triều của cảng.

- Người chơi: Tôi sẽ đưa hai bản đo cho Orin.
  → NPC: Bạn giữ thời điểm ở cả sông và cảng, không chỉ mức nước cuối.; hành động: chỉ chuyển nhánh
- Người chơi: Mực sông có giảm cùng lúc triều lên không?
  → NPC: Có. Hai vùng lặp cùng một nhịp. Orin có hồ sơ thời tiết cũ để phân biệt biến động tự nhiên với một tín hiệu điều khiển chung.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Có. Hai vùng lặp cùng một nhịp. Orin có hồ sơ thời tiết cũ để phân biệt biến động tự nhiên với một tín hiệu điều khiển chung.

- Người chơi: Tôi sẽ đưa hai bản đo cho Orin.
  → NPC: Bạn giữ thời điểm ở cả sông và cảng, không chỉ mức nước cuối.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ thời điểm ở cả sông và cảng, không chỉ mức nước cuối.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.7

### start — dr_orin

Tôi đã xem cả hai chu kỳ. Cần đối chiếu một hệ thống cũ trước khi quy việc này cho hai vùng tự tranh nước.

- Người chơi: Tôi sẽ nhờ Marlow đối chiếu bản vẽ.
  → NPC: Được. Mang chu kỳ đo, không cần đoán tên Pokémon gây ra nó.; hành động: chỉ chuyển nhánh
- Người chơi: Hai vùng có đang tranh nước với nhau không?
  → NPC: Không. Một bộ điều tiết cũ gửi hai lệnh trái nhau. Marlow giữ bản vẽ, chúng ta cần biết hai bộ tham chiếu của nó đang ở đâu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.; hành động: chỉ chuyển nhánh

### answer — dr_orin

Không. Một bộ điều tiết cũ gửi hai lệnh trái nhau. Marlow giữ bản vẽ, chúng ta cần biết hai bộ tham chiếu của nó đang ở đâu.

- Người chơi: Tôi sẽ nhờ Marlow đối chiếu bản vẽ.
  → NPC: Được. Mang chu kỳ đo, không cần đoán tên Pokémon gây ra nó.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.; hành động: chỉ chuyển nhánh

### accepted — dr_orin

Được. Mang chu kỳ đo, không cần đoán tên Pokémon gây ra nó.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — dr_orin

Được. Tôi giữ bản đối chiếu; chúng ta chưa kết luận thay cậu.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.8

### start — archaeologist_marlow

Ta giữ bản vẽ hệ thống đó. Có phần ghi mục đích và hai ổ tham chiếu cần xem riêng.

- Người chơi: Tôi sẽ đọc bia địa tầng trước khi chạm thiết bị.
  → NPC: Được. Đừng rút bộ phận chỉ vì thấy nó sáng; chúng ta cần giữ mạch để đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Bộ điều tiết cũ vốn dùng để làm gì?
  → NPC: Theo dõi nơi trú Pokémon. Nó có phần tham chiếu địa tầng và thủy triều, không phải máy điều khiển thần linh. Bia ở hang chỉ nơi gắn phần địa tầng.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — archaeologist_marlow

Theo dõi nơi trú Pokémon. Nó có phần tham chiếu địa tầng và thủy triều, không phải máy điều khiển thần linh. Bia ở hang chỉ nơi gắn phần địa tầng.

- Người chơi: Tôi sẽ đọc bia địa tầng trước khi chạm thiết bị.
  → NPC: Được. Đừng rút bộ phận chỉ vì thấy nó sáng; chúng ta cần giữ mạch để đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — archaeologist_marlow

Được. Đừng rút bộ phận chỉ vì thấy nó sáng; chúng ta cần giữ mạch để đối chiếu.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — archaeologist_marlow

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.9

### start — narrator

Bia địa tầng chỉ một ổ gắn thiết bị. Bạn có thể kiểm tra vết tháo và giấy vận chuyển bên cạnh.

- Người chơi: Tôi sẽ hỏi Rook về chuyến chuyển thiết bị.
  → NPC: Bạn giữ vết tháo mới và hóa đơn, không gọi ổ trống là hư hại cổ.; hành động: chỉ chuyển nhánh
- Người chơi: Bộ phận địa tầng còn trong ổ không?
  → NPC: Không. Có vết dụng cụ mới và hóa đơn chuyển thiết bị về cảng. Rook biết tuyến vận chuyển đồ máy cũ này.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Có vết dụng cụ mới và hóa đơn chuyển thiết bị về cảng. Rook biết tuyến vận chuyển đồ máy cũ này.

- Người chơi: Tôi sẽ hỏi Rook về chuyến chuyển thiết bị.
  → NPC: Bạn giữ vết tháo mới và hóa đơn, không gọi ổ trống là hư hại cổ.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ vết tháo mới và hóa đơn, không gọi ổ trống là hư hại cổ.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.10

### start — rook

Tôi biết chuyến thiết bị này. Tôi đã giữ biên nhận, nhưng chưa kiểm tra đủ nguồn trước khi bán đường vận chuyển.

- Người chơi: Tôi sẽ hỏi Mai thiết bị đã được gắn ở đâu.
  → NPC: Được. Tôi đưa cả biên nhận của mình, không chỉ lời nói không biết.; hành động: chỉ chuyển nhánh
- Người chơi: Ông đã bán thiết bị hay bán đường vận chuyển?
  → NPC: Đường vận chuyển. Nhóm mua gọi nó là cảm biến rẻ, nhưng tôi không kiểm tra nguồn. Mai nhận dữ liệu của nhóm đó; cô ấy biết nó được nối vào máy nào.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — rook

Đường vận chuyển. Nhóm mua gọi nó là cảm biến rẻ, nhưng tôi không kiểm tra nguồn. Mai nhận dữ liệu của nhóm đó; cô ấy biết nó được nối vào máy nào.

- Người chơi: Tôi sẽ hỏi Mai thiết bị đã được gắn ở đâu.
  → NPC: Được. Tôi đưa cả biên nhận của mình, không chỉ lời nói không biết.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — rook

Được. Tôi đưa cả biên nhận của mình, không chỉ lời nói không biết.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — rook

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.11

### start — weather_researcher_mai

Rook đã chỉ chuyến hàng rồi à? Tôi là người chấp nhận cảm biến vào dự án và cần giải thích cách chúng được nối.

- Người chơi: Tôi sẽ kiểm tra dấu ở buồng Groudon.
  → NPC: Cảm ơn. Tôi giữ dữ liệu gốc và ghi rõ mình đã nhận thiết bị ra sao.; hành động: chỉ chuyển nhánh
- Người chơi: Hai bộ phận có bị gắn vào hai máy khác nhau không?
  → NPC: Có. Tôi nhận thiết bị không rõ nguồn vì muốn báo cáo nhanh. Máy rừng gọi phản ứng nhiệt, máy cảng gọi phản ứng nước. Hai Pokémon đang đáp tín hiệu cứu hộ sai.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — weather_researcher_mai

Có. Tôi nhận thiết bị không rõ nguồn vì muốn báo cáo nhanh. Máy rừng gọi phản ứng nhiệt, máy cảng gọi phản ứng nước. Hai Pokémon đang đáp tín hiệu cứu hộ sai.

- Người chơi: Tôi sẽ kiểm tra dấu ở buồng Groudon.
  → NPC: Cảm ơn. Tôi giữ dữ liệu gốc và ghi rõ mình đã nhận thiết bị ra sao.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — weather_researcher_mai

Cảm ơn. Tôi giữ dữ liệu gốc và ghi rõ mình đã nhận thiết bị ra sao.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — weather_researcher_mai

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.12

### start — narrator

Buồng còn dấu mới của Groudon cùng thời điểm máy phát. Bạn có thể đối chiếu hướng đi trước khi bước sâu hơn.

- Người chơi: Tôi sẽ tìm hồ sơ bộ phận nước ở cảng.
  → NPC: Bạn giữ nhịp tín hiệu và hướng dấu chân, không kết luận từ kích thước Pokémon.; hành động: chỉ chuyển nhánh
- Người chơi: Groudon có đuổi người khi tín hiệu tắt không?
  → NPC: Dấu mới cho thấy nó dừng quanh vùng nước cạn, rồi quay lại khi máy phát. Cần tìm phần tham chiếu nước trước khi dùng một trận đấu thay cho sửa tín hiệu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Dấu mới cho thấy nó dừng quanh vùng nước cạn, rồi quay lại khi máy phát. Cần tìm phần tham chiếu nước trước khi dùng một trận đấu thay cho sửa tín hiệu.

- Người chơi: Tôi sẽ tìm hồ sơ bộ phận nước ở cảng.
  → NPC: Bạn giữ nhịp tín hiệu và hướng dấu chân, không kết luận từ kích thước Pokémon.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ nhịp tín hiệu và hướng dấu chân, không kết luận từ kích thước Pokémon.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.13

### start — town8_captain_hoa

Tôi giữ đường tới hồ sơ thủy triều. Đội tàu vẫn cần xác nhận từ trạm trước khi đi theo lịch cũ.

- Người chơi: Tôi sẽ đọc bản sao ở bàn điện thờ.
  → NPC: Cảm ơn. Chúng tôi giữ tàu lại cho đến khi đường đi được xác nhận.; hành động: chỉ chuyển nhánh
- Người chơi: Chị có thể mở đường đến nơi giữ hồ sơ thủy triều không?
  → NPC: Có, sau khi trạm xác nhận. Đội tàu chưa ra theo lịch cũ vì triều thay đổi. Bàn điện thờ giữ bản sao dấu thủy triều, xem nó trước giúp tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town8_captain_hoa

Có, sau khi trạm xác nhận. Đội tàu chưa ra theo lịch cũ vì triều thay đổi. Bàn điện thờ giữ bản sao dấu thủy triều, xem nó trước giúp tôi.

- Người chơi: Tôi sẽ đọc bản sao ở bàn điện thờ.
  → NPC: Cảm ơn. Chúng tôi giữ tàu lại cho đến khi đường đi được xác nhận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town8_captain_hoa

Cảm ơn. Chúng tôi giữ tàu lại cho đến khi đường đi được xác nhận.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town8_captain_hoa

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.14

### start — narrator

Bản ở bàn điện thờ có dấu chuyển thiết bị và tên kho nhận. Phần địa tầng có một dấu để đối chiếu cùng.

- Người chơi: Tôi sẽ mang dấu này cho Nhi.
  → NPC: Bạn giữ cả hai dấu vận chuyển để chứng minh cùng một hệ thống bị chia đôi.; hành động: chỉ chuyển nhánh
- Người chơi: Phần tham chiếu nước đã bị chuyển về đâu?
  → NPC: Về kho nghiên cứu, cùng dấu sửa của chuyến địa tầng. Dự án đã tách hai phần để bán dữ liệu riêng. Nhi giữ nhật ký Kyogre để đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Về kho nghiên cứu, cùng dấu sửa của chuyến địa tầng. Dự án đã tách hai phần để bán dữ liệu riêng. Nhi giữ nhật ký Kyogre để đối chiếu.

- Người chơi: Tôi sẽ mang dấu này cho Nhi.
  → NPC: Bạn giữ cả hai dấu vận chuyển để chứng minh cùng một hệ thống bị chia đôi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ cả hai dấu vận chuyển để chứng minh cùng một hệ thống bị chia đôi.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.15

### start — weather_guardian_kyogre

Tôi theo dõi Kyogre ở đây. Muốn xử lý tín hiệu, bạn cần biết nó đang đáp lại việc gì.

- Người chơi: Tôi sẽ kiểm tra nhật ký ở buồng Kyogre.
  → NPC: Cảm ơn. Chúng ta cần kết thúc tín hiệu sai, không phạt Pokémon vì đã đáp lại nó.; hành động: chỉ chuyển nhánh
- Người chơi: Kyogre tới vùng ngập để làm gì?
  → NPC: Cứu Pokémon mắc trong nước. Máy cứ phát nên nó tiếp tục đẩy nước lên. Dấu ở buồng cho biết hướng dòng cứu hộ; đọc trước khi tìm cách chặn nó.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — weather_guardian_kyogre

Cứu Pokémon mắc trong nước. Máy cứ phát nên nó tiếp tục đẩy nước lên. Dấu ở buồng cho biết hướng dòng cứu hộ; đọc trước khi tìm cách chặn nó.

- Người chơi: Tôi sẽ kiểm tra nhật ký ở buồng Kyogre.
  → NPC: Cảm ơn. Chúng ta cần kết thúc tín hiệu sai, không phạt Pokémon vì đã đáp lại nó.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — weather_guardian_kyogre

Cảm ơn. Chúng ta cần kết thúc tín hiệu sai, không phạt Pokémon vì đã đáp lại nó.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — weather_guardian_kyogre

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.16

### start — narrator

Nhật ký buồng giữ hướng dòng xoáy và thời điểm phát. Đường dòng nước có thể đối chiếu với cửa cứu hộ.

- Người chơi: Tôi sẽ nhờ Iris phân tích hai mạch.
  → NPC: Bạn giữ đường dòng xoáy cùng thời điểm phát tín hiệu để Iris đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Dòng xoáy bắt đầu từ tàu cá hay cửa cứu hộ?
  → NPC: Từ cửa cứu hộ. Tín hiệu lặp kéo dài hành động đã đáng lẽ kết thúc. Iris biết cách giữ bản gốc và tách tín hiệu lặp của cả hai máy.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Từ cửa cứu hộ. Tín hiệu lặp kéo dài hành động đã đáng lẽ kết thúc. Iris biết cách giữ bản gốc và tách tín hiệu lặp của cả hai máy.

- Người chơi: Tôi sẽ nhờ Iris phân tích hai mạch.
  → NPC: Bạn giữ đường dòng xoáy cùng thời điểm phát tín hiệu để Iris đối chiếu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ đường dòng xoáy cùng thời điểm phát tín hiệu để Iris đối chiếu.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.17

### start — lab_scientist_iris

Tôi đã xem hai mạch. Có thể kết thúc tín hiệu mà không xóa bản ghi, nhưng cần kiểm chứng trước khi gửi.

- Người chơi: Tôi sẽ chuẩn bị hai redstone cho mạch xác nhận.
  → NPC: Cảm ơn. Tôi sẽ không gửi chỉ vì tự mình nói đã đúng.; hành động: chỉ chuyển nhánh
- Người chơi: Cô định tắt tín hiệu mà giữ dữ liệu thế nào?
  → NPC: Ngắt nguồn phát, nối hai phần tham chiếu rồi gửi xác nhận kết thúc cùng chữ ký. Tôi cần hai redstone làm mạch. Hale và Mai phải kiểm tra bản lưu trước khi gửi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — lab_scientist_iris

Ngắt nguồn phát, nối hai phần tham chiếu rồi gửi xác nhận kết thúc cùng chữ ký. Tôi cần hai redstone làm mạch. Hale và Mai phải kiểm tra bản lưu trước khi gửi.

- Người chơi: Tôi sẽ chuẩn bị hai redstone cho mạch xác nhận.
  → NPC: Cảm ơn. Tôi sẽ không gửi chỉ vì tự mình nói đã đúng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — lab_scientist_iris

Cảm ơn. Tôi sẽ không gửi chỉ vì tự mình nói đã đúng.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — lab_scientist_iris

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.18

### start — lab_scientist_iris

Phần mạch xác nhận đã được vẽ. Tôi sẽ lưu bản trước khi nối hai đầu tham chiếu.

- Người chơi: Tôi giao hai redstone cho mạch.
  → NPC: Cảm ơn. Tôi gửi bản kiểm chứng cho Hale và Mai; bạn gặp Khoa nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Mạch có giữ bản sao trước lúc gửi không?
  → NPC: Có. Hai redstone đủ hoàn thiện phần xác nhận. Khoa muốn kiểm tra khả năng giữ đội trước khi mở buồng địa tầng, rồi chúng ta nối lại bộ điều tiết.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — lab_scientist_iris

Có. Hai redstone đủ hoàn thiện phần xác nhận. Khoa muốn kiểm tra khả năng giữ đội trước khi mở buồng địa tầng, rồi chúng ta nối lại bộ điều tiết.

- Người chơi: Tôi giao hai redstone cho mạch.
  → NPC: Cảm ơn. Tôi gửi bản kiểm chứng cho Hale và Mai; bạn gặp Khoa nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — lab_scientist_iris

Cảm ơn. Tôi gửi bản kiểm chứng cho Hale và Mai; bạn gặp Khoa nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — lab_scientist_iris

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.19

### start — weather_guardian_groudon

Tôi nhận phần kiểm tra trước khi mở buồng địa tầng. Đây là bài thử giữ đội gần khu cứu hộ.

- Người chơi: Tôi nhận bài thử của Khoa.
  → NPC: Được. Chúng ta đấu để mở việc kiểm tra, không quyết định thay Pokémon.; hành động: chỉ chuyển nhánh
- Người chơi: Thắng trận này có khiến Groudon phải theo tôi không?
  → NPC: Không. Tôi kiểm tra bạn có thể tới gần mà giữ đội và đường cứu hộ an toàn. Sau trận, bạn mở buồng để kiểm tra bộ điều tiết trung tâm.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — weather_guardian_groudon

Không. Tôi kiểm tra bạn có thể tới gần mà giữ đội và đường cứu hộ an toàn. Sau trận, bạn mở buồng để kiểm tra bộ điều tiết trung tâm.

- Người chơi: Tôi nhận bài thử của Khoa.
  → NPC: Được. Chúng ta đấu để mở việc kiểm tra, không quyết định thay Pokémon.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — weather_guardian_groudon

Được. Chúng ta đấu để mở việc kiểm tra, không quyết định thay Pokémon.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — weather_guardian_groudon

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.20

### start — narrator

Bộ điều tiết có bản nối hai phần tham chiếu. Bạn có thể xem trạng thái gửi và bản tín hiệu được giữ lại.

- Người chơi: Tôi sẽ nhận bài thử cuối của Nhi.
  → NPC: Bạn giữ bản đóng tín hiệu và bản ghi cũ để đối chiếu khi cần.; hành động: chỉ chuyển nhánh
- Người chơi: Hai phần tham chiếu đã trở về cùng một mạch chưa?
  → NPC: Rồi. Tín hiệu cứu hộ giả đã được đóng, bản ghi tháo thiết bị vẫn được giữ. Sông và biển đang trở về nhịp thường; Nhi còn bài thử cuối trước khi xác nhận.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Rồi. Tín hiệu cứu hộ giả đã được đóng, bản ghi tháo thiết bị vẫn được giữ. Sông và biển đang trở về nhịp thường; Nhi còn bài thử cuối trước khi xác nhận.

- Người chơi: Tôi sẽ nhận bài thử cuối của Nhi.
  → NPC: Bạn giữ bản đóng tín hiệu và bản ghi cũ để đối chiếu khi cần.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ bản đóng tín hiệu và bản ghi cũ để đối chiếu khi cần.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.21

### start — weather_guardian_kyogre

Khoa đã gửi xác nhận. Tôi còn bài thử nhịp nước trước khi ghi phần của mình.

- Người chơi: Tôi sẵn sàng nhận bài thử của Nhi.
  → NPC: Được. Tín hiệu đã kết thúc, chúng ta đấu bằng đội của mình.; hành động: chỉ chuyển nhánh
- Người chơi: Nhi muốn kiểm tra phần nào sau bài thử của Khoa?
  → NPC: Cách bạn đổi nhịp khi nước và thời tiết đổi. Hai bài thử xác nhận riêng; nếu vượt qua, bạn có thể nhận lời đồng hành của từng Pokémon.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — weather_guardian_kyogre

Cách bạn đổi nhịp khi nước và thời tiết đổi. Hai bài thử xác nhận riêng; nếu vượt qua, bạn có thể nhận lời đồng hành của từng Pokémon.

- Người chơi: Tôi sẵn sàng nhận bài thử của Nhi.
  → NPC: Được. Tín hiệu đã kết thúc, chúng ta đấu bằng đội của mình.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — weather_guardian_kyogre

Được. Tín hiệu đã kết thúc, chúng ta đấu bằng đội của mình.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — weather_guardian_kyogre

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.22

### start — weather_guardian_groudon

Groudon đã đáp lời đồng hành. Tôi giữ xác nhận riêng để bạn nhận khi sẵn sàng.

- Người chơi: Tôi nhận lời đồng hành của Groudon.
  → NPC: Được. Tôi xác nhận phần của Groudon; phần Kyogre vẫn giữ nguyên.; hành động: chỉ chuyển nhánh
- Người chơi: Nhận Groudon có làm tôi mất quyền nhận Kyogre không?
  → NPC: Không. Hai lời đồng hành được xác nhận riêng. Nếu đội đầy, Groudon được gửi vào hộp lưu trữ. Bạn chỉ nhận mỗi lời một lần trong mùa.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — weather_guardian_groudon

Không. Hai lời đồng hành được xác nhận riêng. Nếu đội đầy, Groudon được gửi vào hộp lưu trữ. Bạn chỉ nhận mỗi lời một lần trong mùa.

- Người chơi: Tôi nhận lời đồng hành của Groudon.
  → NPC: Được. Tôi xác nhận phần của Groudon; phần Kyogre vẫn giữ nguyên.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — weather_guardian_groudon

Được. Tôi xác nhận phần của Groudon; phần Kyogre vẫn giữ nguyên.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — weather_guardian_groudon

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.23

### start — weather_guardian_kyogre

Kyogre đã đáp lời đồng hành. Phần xác nhận này không thay thế phần của Groudon.

- Người chơi: Tôi nhận lời đồng hành của Kyogre.
  → NPC: Được. Tôi xác nhận phần Kyogre và giữ lịch sử đã nhận.; hành động: chỉ chuyển nhánh
- Người chơi: Nếu đội đã đầy, Kyogre được gửi tới đâu?
  → NPC: Vào hộp lưu trữ Pokémon. Bạn vẫn nhận một lần riêng trong mùa, dù đã nhận Groudon. Hai xác nhận không thay thế cho nhau.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — weather_guardian_kyogre

Vào hộp lưu trữ Pokémon. Bạn vẫn nhận một lần riêng trong mùa, dù đã nhận Groudon. Hai xác nhận không thay thế cho nhau.

- Người chơi: Tôi nhận lời đồng hành của Kyogre.
  → NPC: Được. Tôi xác nhận phần Kyogre và giữ lịch sử đã nhận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — weather_guardian_kyogre

Được. Tôi xác nhận phần Kyogre và giữ lịch sử đã nhận.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — weather_guardian_kyogre

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## weather_duo_01.24

### start — weather_researcher_mai

Thời tiết đã trở lại nhịp thường. Tôi còn báo cáo phải công khai về cách mình nhận thiết bị.

- Người chơi: Tôi xác nhận hai vùng đã có bản đối chiếu đầy đủ.
  → NPC: Cảm ơn. Tôi gửi báo cáo cho cảng và trạm rừng, rồi kiểm tra nguồn thiết bị trước đợt đo sau.; hành động: chỉ chuyển nhánh
- Người chơi: Cô sẽ công khai phần nhận thiết bị không rõ nguồn chứ?
  → NPC: Có. Báo cáo giữ cả thiết bị, người nhận và bản xác minh của Iris. Thời tiết tốt lại không khiến quyết định nhận thiết bị của tôi chưa từng xảy ra.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — weather_researcher_mai

Có. Báo cáo giữ cả thiết bị, người nhận và bản xác minh của Iris. Thời tiết tốt lại không khiến quyết định nhận thiết bị của tôi chưa từng xảy ra.

- Người chơi: Tôi xác nhận hai vùng đã có bản đối chiếu đầy đủ.
  → NPC: Cảm ơn. Tôi gửi báo cáo cho cảng và trạm rừng, rồi kiểm tra nguồn thiết bị trước đợt đo sau.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — weather_researcher_mai

Cảm ơn. Tôi gửi báo cáo cho cảng và trạm rừng, rồi kiểm tra nguồn thiết bị trước đợt đo sau.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — weather_researcher_mai

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## fish_out_of_water.1

### start — daycare_mira

Hạnh gọi về một Magikarp mắc ở vũng cạn. Bạn có thể giúp đưa nó tới nơi chăm sóc không?

- Người chơi: Tôi sẽ đến vũng và dùng Poké Ball đưa nó về.
  → NPC: Cảm ơn. Giữ an toàn cho nó, rồi quay lại chỗ mình.; hành động: chỉ chuyển nhánh
- Người chơi: Magikarp đang mắc ở đâu?
  → NPC: Vũng cạn Hạnh đánh dấu trên điện thoại. Bạn bắt nó bằng Poké Ball ở đó rồi đưa về chăm sóc. Mình không muốn mọi người đợi nó tiến hóa để tự thoát.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — daycare_mira

Vũng cạn Hạnh đánh dấu trên điện thoại. Bạn bắt nó bằng Poké Ball ở đó rồi đưa về chăm sóc. Mình không muốn mọi người đợi nó tiến hóa để tự thoát.

- Người chơi: Tôi sẽ đến vũng và dùng Poké Ball đưa nó về.
  → NPC: Cảm ơn. Giữ an toàn cho nó, rồi quay lại chỗ mình.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — daycare_mira

Cảm ơn. Giữ an toàn cho nó, rồi quay lại chỗ mình.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — daycare_mira

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## fish_out_of_water.2

### start — narrator

Vũng được Hạnh đánh dấu. Magikarp còn ở gần bờ, và đường tới Mira đã có trên điện thoại.

- Người chơi: Tôi sẽ bắt Magikarp ở đây rồi đưa tới Mira.
  → NPC: Bạn giữ khoảng cách với bờ trơn và dùng Poké Ball như khi bắt Pokémon khác.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi cần đưa Magikarp tới Mira sau khi bắt chứ?
  → NPC: Đúng. Nếu được gửi vào hộp lưu trữ, hãy đưa nó vào đội trước khi gặp Mira. Cần bắt Magikarp thật tại vũng đang được kiểm tra.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Đúng. Nếu được gửi vào hộp lưu trữ, hãy đưa nó vào đội trước khi gặp Mira. Cần bắt Magikarp thật tại vũng đang được kiểm tra.

- Người chơi: Tôi sẽ bắt Magikarp ở đây rồi đưa tới Mira.
  → NPC: Bạn giữ khoảng cách với bờ trơn và dùng Poké Ball như khi bắt Pokémon khác.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ khoảng cách với bờ trơn và dùng Poké Ball như khi bắt Pokémon khác.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: close

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## fish_out_of_water.3

### start — daycare_mira

Bạn đã đưa Magikarp về rồi. Mình sẽ kiểm tra nó cùng đội trước khi bạn báo lại Hạnh.

- Người chơi: Nhờ Mira kiểm tra đội và Magikarp của tôi.
  → NPC: Được. Mình kiểm tra Magikarp trước, rồi bạn xem lại mốc nước giúp Hạnh nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Mira có chăm Magikarp trong đội của tôi được không?
  → NPC: Có. Đưa nó lại đây cùng đội. Mình chăm nó như mọi Pokémon khác, không đợi nó mạnh hơn rồi mới coi nó đáng cứu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — daycare_mira

Có. Đưa nó lại đây cùng đội. Mình chăm nó như mọi Pokémon khác, không đợi nó mạnh hơn rồi mới coi nó đáng cứu.

- Người chơi: Nhờ Mira kiểm tra đội và Magikarp của tôi.
  → NPC: Được. Mình kiểm tra Magikarp trước, rồi bạn xem lại mốc nước giúp Hạnh nhé.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — daycare_mira

Được. Mình kiểm tra Magikarp trước, rồi bạn xem lại mốc nước giúp Hạnh nhé.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — daycare_mira

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## fish_out_of_water.4

### start — narrator

Mốc nước mới được dựng cạnh lối phụ đã thông. Bạn có thể xem phần nước hiện tại so với vạch an toàn.

- Người chơi: Tôi sẽ báo Hạnh nước đã thông nhưng chưa đủ rộng.
  → NPC: Bạn ghi đúng mức nước hiện tại, không coi việc thông đường là đã xong mọi phần chăm sóc.; hành động: chỉ chuyển nhánh
- Người chơi: Vũng đã đủ rộng để đưa mọi Pokémon trở lại chưa?
  → NPC: Chưa. Đường nước phụ đã thông nhưng vũng vẫn nhỏ. Hạnh sẽ theo dõi mực nước; Magikarp bạn bắt có thể ở lại cùng bạn.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Chưa. Đường nước phụ đã thông nhưng vũng vẫn nhỏ. Hạnh sẽ theo dõi mực nước; Magikarp bạn bắt có thể ở lại cùng bạn.

- Người chơi: Tôi sẽ báo Hạnh nước đã thông nhưng chưa đủ rộng.
  → NPC: Bạn ghi đúng mức nước hiện tại, không coi việc thông đường là đã xong mọi phần chăm sóc.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn ghi đúng mức nước hiện tại, không coi việc thông đường là đã xong mọi phần chăm sóc.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## fish_out_of_water.5

### start — town5_ranger_hanh

Tôi đã nhận tin Magikarp được chăm. Phần vũng nước từ đây cần người theo dõi sau khi bạn đi.

- Người chơi: Vậy tôi xác nhận vũng đã có người theo dõi.
  → NPC: Cảm ơn. Bạn chăm Magikarp, tôi giữ phần theo dõi vũng.; hành động: chỉ chuyển nhánh
- Người chơi: Chị có tiếp tục kiểm tra vũng sau khi tôi rời đi không?
  → NPC: Có. Tôi ghi lịch đo và giữ lối nước phụ thông. Magikarp vẫn thuộc người đã bắt; tôi không lấy nó khỏi đội khi bạn hoàn thành việc giúp trạm.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town5_ranger_hanh

Có. Tôi ghi lịch đo và giữ lối nước phụ thông. Magikarp vẫn thuộc người đã bắt; tôi không lấy nó khỏi đội khi bạn hoàn thành việc giúp trạm.

- Người chơi: Vậy tôi xác nhận vũng đã có người theo dõi.
  → NPC: Cảm ơn. Bạn chăm Magikarp, tôi giữ phần theo dõi vũng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town5_ranger_hanh

Cảm ơn. Bạn chăm Magikarp, tôi giữ phần theo dõi vũng.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town5_ranger_hanh

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## ren_missing_labels.1

### start — pokemall_ren

Nhãn hai lô bị ướt. Tôi giữ hàng ngoài quầy bán cho tới khi đối chiếu được món và số lô.

- Người chơi: Tôi sẽ hỏi Nam trước khi đối chiếu phiếu kho.
  → NPC: Cảm ơn. Tôi giữ cả hai lô ngoài quầy bán cho tới khi nhãn đúng.; hành động: chỉ chuyển nhánh
- Người chơi: Anh định bán hai lô khi nhãn chưa rõ sao?
  → NPC: Không. Một lô thuốc hồi phục, một lô vật tư chăm sóc. Hỏi Nam người gửi rồi xem số lô trên phiếu kho giúp tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — pokemall_ren

Không. Một lô thuốc hồi phục, một lô vật tư chăm sóc. Hỏi Nam người gửi rồi xem số lô trên phiếu kho giúp tôi.

- Người chơi: Tôi sẽ hỏi Nam trước khi đối chiếu phiếu kho.
  → NPC: Cảm ơn. Tôi giữ cả hai lô ngoài quầy bán cho tới khi nhãn đúng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — pokemall_ren

Cảm ơn. Tôi giữ cả hai lô ngoài quầy bán cho tới khi nhãn đúng.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — pokemall_ren

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## ren_missing_labels.2

### start — town3_courier_nam

Tôi giao cả hai thùng cùng chuyến. Phần ảnh và phiếu kho cần xem riêng mới biết còn giữ được thông tin gì.

- Người chơi: Tôi sẽ xem số lô trên phiếu kho.
  → NPC: Cảm ơn. Tôi ghi lại việc thiếu ảnh để không lặp lần giao tới.; hành động: chỉ chuyển nhánh
- Người chơi: Anh còn ảnh nhãn gốc của hai lô không?
  → NPC: Không. Tôi chỉ bọc ngoài nên ảnh cũng không đọc được số bên trong. Phiếu kho ở bàn nhận hàng còn số lô đúng.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — town3_courier_nam

Không. Tôi chỉ bọc ngoài nên ảnh cũng không đọc được số bên trong. Phiếu kho ở bàn nhận hàng còn số lô đúng.

- Người chơi: Tôi sẽ xem số lô trên phiếu kho.
  → NPC: Cảm ơn. Tôi ghi lại việc thiếu ảnh để không lặp lần giao tới.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — town3_courier_nam

Cảm ơn. Tôi ghi lại việc thiếu ảnh để không lặp lần giao tới.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — town3_courier_nam

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## ren_missing_labels.3

### start — narrator

Phiếu kho có số lô và tên vật tư cho từng thùng. Bạn có thể đối chiếu với nhãn đã ướt.

- Người chơi: Tôi sẽ thu thập ba tờ giấy cho Ren.
  → NPC: Bạn giữ số lô của từng thùng, không đổi nhãn theo màu hộp.; hành động: chỉ chuyển nhánh
- Người chơi: Hai lô có dùng thay cho nhau được không?
  → NPC: Không. Phiếu ghi một lô hồi phục và một lô chăm sóc khác. Ren cần ba tờ giấy để làm lại nhãn riêng, có cả số lô.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Phiếu ghi một lô hồi phục và một lô chăm sóc khác. Ren cần ba tờ giấy để làm lại nhãn riêng, có cả số lô.

- Người chơi: Tôi sẽ thu thập ba tờ giấy cho Ren.
  → NPC: Bạn giữ số lô của từng thùng, không đổi nhãn theo màu hộp.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn giữ số lô của từng thùng, không đổi nhãn theo màu hộp.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## ren_missing_labels.4

### start — pokemall_ren

Tôi đã chọn phần cần ghi trên nhãn mới. Trước hết cần chuẩn bị đủ giấy, rồi chúng ta nhận riêng ở quầy.

- Người chơi: Tôi sẽ chuẩn bị đủ ba tờ giấy.
  → NPC: Cảm ơn. Khi đã đủ, chúng ta kiểm đếm và giao giấy tại quầy.; hành động: chỉ chuyển nhánh
- Người chơi: Anh cần nhãn ghi những phần nào?
  → NPC: Tên món, cách dùng và số lô. Thu thập ba tờ giấy trước, rồi mang tới quầy để tôi nhận riêng phần dùng làm nhãn.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — pokemall_ren

Tên món, cách dùng và số lô. Thu thập ba tờ giấy trước, rồi mang tới quầy để tôi nhận riêng phần dùng làm nhãn.

- Người chơi: Tôi sẽ chuẩn bị đủ ba tờ giấy.
  → NPC: Cảm ơn. Khi đã đủ, chúng ta kiểm đếm và giao giấy tại quầy.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — pokemall_ren

Cảm ơn. Khi đã đủ, chúng ta kiểm đếm và giao giấy tại quầy.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: close

### declined — pokemall_ren

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## ren_missing_labels.5

### start — pokemall_ren

Phiếu đã khớp từng thùng. Tôi sẵn sàng nhận giấy và làm nhãn có số lô rõ ràng.

- Người chơi: Tôi giao ba tờ giấy làm nhãn.
  → NPC: Cảm ơn. Hai lô đã được nhận diện đúng; tôi đưa đúng lô về quầy.; hành động: chỉ chuyển nhánh
- Người chơi: Anh đã đối chiếu hai số lô để làm nhãn chưa?
  → NPC: Rồi. Tôi nhận ba tờ giấy và viết nhãn riêng. Nam sẽ lưu ảnh rõ số lô trong những chuyến sau, để khách không phải đoán món trong hộp.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — pokemall_ren

Rồi. Tôi nhận ba tờ giấy và viết nhãn riêng. Nam sẽ lưu ảnh rõ số lô trong những chuyến sau, để khách không phải đoán món trong hộp.

- Người chơi: Tôi giao ba tờ giấy làm nhãn.
  → NPC: Cảm ơn. Hai lô đã được nhận diện đúng; tôi đưa đúng lô về quầy.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — pokemall_ren

Cảm ơn. Hai lô đã được nhận diện đúng; tôi đưa đúng lô về quầy.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — pokemall_ren

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mara_postgame.1

### start — mara_voss

Tôi đã xếp đội mới cho trận sau. Cậu xem bảng ở sân rồi hẵng nhận lời, có vài vai trò đã đổi.

- Người chơi: Tôi sẽ xem bảng luyện mới.
  → NPC: Được. Tôi để chiến thuật trên bảng, nhưng cậu vẫn phải đọc nó trong trận.; hành động: chỉ chuyển nhánh
- Người chơi: Cậu đã đổi đội thế nào cho trận sau?
  → NPC: Đủ sáu Pokémon với vai trò phối hợp rõ hơn. Tôi không chỉ tăng cấp. Bảng ở sân ghi cách đội đổi nhịp; cậu xem rồi quay lại đấu.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### answer — mara_voss

Đủ sáu Pokémon với vai trò phối hợp rõ hơn. Tôi không chỉ tăng cấp. Bảng ở sân ghi cách đội đổi nhịp; cậu xem rồi quay lại đấu.

- Người chơi: Tôi sẽ xem bảng luyện mới.
  → NPC: Được. Tôi để chiến thuật trên bảng, nhưng cậu vẫn phải đọc nó trong trận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### accepted — mara_voss

Được. Tôi để chiến thuật trên bảng, nhưng cậu vẫn phải đọc nó trong trận.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — mara_voss

Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mara_postgame.2

### start — narrator

Bảng luyện mới có đủ thành viên và vai trò. Phần điều chỉnh cấp được ghi riêng với phần phối hợp đội.

- Người chơi: Tôi sẽ quay lại nhận trận với Mara.
  → NPC: Bạn ghi những vai trò mới, không chỉ so con số cấp.; hành động: chỉ chuyển nhánh
- Người chơi: Đội mới chỉ mạnh hơn vì cấp cao hơn sao?
  → NPC: Không. Cấp điều chỉnh theo đội người thách đấu, còn thành viên, vật phẩm và vai trò phối hợp đã đổi. Mara giữ cả cách đổi nhịp lẫn đòn ưu tiên.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### answer — narrator

Không. Cấp điều chỉnh theo đội người thách đấu, còn thành viên, vật phẩm và vai trò phối hợp đã đổi. Mara giữ cả cách đổi nhịp lẫn đòn ưu tiên.

- Người chơi: Tôi sẽ quay lại nhận trận với Mara.
  → NPC: Bạn ghi những vai trò mới, không chỉ so con số cấp.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.; hành động: chỉ chuyển nhánh

### accepted — narrator

Bạn ghi những vai trò mới, không chỉ so con số cấp.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — narrator

Được. Bạn có thể quay lại nói chuyện này khi sẵn sàng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mara_postgame.3

### start — mara_voss

Cậu xem bảng rồi à? Đội mới đã sẵn sàng. Trận này được giữ như một bài luyện riêng.

- Người chơi: Tôi nhận trận tái đấu với đội mới của cậu.
  → NPC: Được. Bắt đầu thôi; chúng ta giữ kết quả mới cùng những trận trước.; hành động: chỉ chuyển nhánh
- Người chơi: Trận này có lặp lại phần thưởng tuyến truyện chính không?
  → NPC: Không. Đây là bài luyện riêng, được ghi kết quả riêng. Tôi vẫn muốn thắng, nên cậu đừng nghĩ tôi mang đội mới chỉ để cho xem.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### answer — mara_voss

Không. Đây là bài luyện riêng, được ghi kết quả riêng. Tôi vẫn muốn thắng, nên cậu đừng nghĩ tôi mang đội mới chỉ để cho xem.

- Người chơi: Tôi nhận trận tái đấu với đội mới của cậu.
  → NPC: Được. Bắt đầu thôi; chúng ta giữ kết quả mới cùng những trận trước.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### accepted — mara_voss

Được. Bắt đầu thôi; chúng ta giữ kết quả mới cùng những trận trước.

- Người chơi: Bắt đầu trận đấu.
  → Kết thúc nút; hành động: battle

### declined — mara_voss

Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mara_postgame.4

### start — professor_hale

Ta nghe sân tập hoạt động lại. Lần này không có ai gọi từ đó để báo một lỗi nhật ký.

- Người chơi: Tôi sẽ gặp Mara để hẹn buổi sau.
  → NPC: Được. Ta giữ phòng nghiên cứu cho công việc của mình, không biến cuộc hẹn ấy thành một cuộc điều tra.; hành động: chỉ chuyển nhánh
- Người chơi: Ông có nghe sân tập hoạt động bình thường không?
  → NPC: Có. Không có chữ ký lạ hay bản ghi bị sửa. Chỉ có hai người còn muốn đấu tiếp. Cậu gặp Mara hẹn lần sau đi, không cần đợi có việc khẩn cấp.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.; hành động: chỉ chuyển nhánh

### answer — professor_hale

Có. Không có chữ ký lạ hay bản ghi bị sửa. Chỉ có hai người còn muốn đấu tiếp. Cậu gặp Mara hẹn lần sau đi, không cần đợi có việc khẩn cấp.

- Người chơi: Tôi sẽ gặp Mara để hẹn buổi sau.
  → NPC: Được. Ta giữ phòng nghiên cứu cho công việc của mình, không biến cuộc hẹn ấy thành một cuộc điều tra.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.; hành động: chỉ chuyển nhánh

### accepted — professor_hale

Được. Ta giữ phòng nghiên cứu cho công việc của mình, không biến cuộc hẹn ấy thành một cuộc điều tra.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — professor_hale

Được, ta giữ phần này. Cậu quay lại khi muốn tiếp tục.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## mara_postgame.5

### start — mara_voss

Tôi muốn hẹn trận sau. Không nhất thiết ngày mai; cứ chọn lúc cả hai có thời gian.

- Người chơi: Tôi sẽ nhắn ngày cụ thể trước cuối tuần.
  → NPC: Được. Tôi tập đội trong lúc chờ, còn cậu nhớ tới đúng hẹn.; hành động: chỉ chuyển nhánh
- Người chơi: Cuối tuần đấu tiếp được không?
  → NPC: Được. Nhắn ngày cụ thể để tôi sắp lịch. Tôi muốn thắng, nhưng cũng có việc khác; cậu không cần tới mỗi ngày để giữ một đối thủ.; hành động: chỉ chuyển nhánh
- Người chơi: Để lúc khác nhé, tôi chưa muốn tiếp tục.
  → NPC: Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### answer — mara_voss

Được. Nhắn ngày cụ thể để tôi sắp lịch. Tôi muốn thắng, nhưng cũng có việc khác; cậu không cần tới mỗi ngày để giữ một đối thủ.

- Người chơi: Tôi sẽ nhắn ngày cụ thể trước cuối tuần.
  → NPC: Được. Tôi tập đội trong lúc chờ, còn cậu nhớ tới đúng hẹn.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi hiểu rồi. Để tôi suy nghĩ thêm.
  → NPC: Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.; hành động: chỉ chuyển nhánh

### accepted — mara_voss

Được. Tôi tập đội trong lúc chờ, còn cậu nhớ tới đúng hẹn.

- Người chơi: Được, tiếp tục nhé.
  → Kết thúc nút; hành động: finish

### declined — mara_voss

Được. Tôi vẫn ở đây; cậu chưa muốn tiếp thì cứ nói thẳng.

- Người chơi: Hẹn gặp lại.
  → Kết thúc nút; hành động: close

## outcome.mara_voss.win

### start — mara_voss

Cậu thắng. Tôi muốn xem lại đoạn cuối, nhưng kết quả trận này rõ rồi.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Tôi sẽ chuẩn bị cho trận sau, không sửa lời nhận thua của mình.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Chữa cho đội trước. Khi cậu sẵn sàng, tôi vẫn nhận lời thách đấu.; hành động: chỉ chuyển nhánh

### thanks — mara_voss

Cảm ơn cậu. Tôi sẽ chuẩn bị cho trận sau, không sửa lời nhận thua của mình.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — mara_voss

Chữa cho đội trước. Khi cậu sẵn sàng, tôi vẫn nhận lời thách đấu.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.mara_voss.loss

### start — mara_voss

Tôi thắng trận này. Cậu đổi đội hơi muộn khi tôi đã giữ được nhịp.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Tôi sẽ chuẩn bị cho trận sau, không sửa lời nhận thua của mình.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Chữa cho đội trước. Khi cậu sẵn sàng, tôi vẫn nhận lời thách đấu.; hành động: chỉ chuyển nhánh

### thanks — mara_voss

Cảm ơn cậu. Tôi sẽ chuẩn bị cho trận sau, không sửa lời nhận thua của mình.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — mara_voss

Chữa cho đội trước. Khi cậu sẵn sàng, tôi vẫn nhận lời thách đấu.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.dr_orin.win

### start — dr_orin

Cậu đã xử lý được khi trạng thái làm kế hoạch đầu tiên không còn dùng được.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu đã kiểm chứng bằng trận đấu. Tôi sẽ cùng cậu đối chiếu lượt quan trọng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Hồi phục rồi thử đổi cách xử lý trạng thái trước khi nhận trận tiếp.; hành động: chỉ chuyển nhánh

### thanks — dr_orin

Cảm ơn cậu đã kiểm chứng bằng trận đấu. Tôi sẽ cùng cậu đối chiếu lượt quan trọng.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — dr_orin

Được. Hồi phục rồi thử đổi cách xử lý trạng thái trước khi nhận trận tiếp.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.dr_orin.loss

### start — dr_orin

Trận này tôi thắng. Cậu tập trung vào sát thương và để trạng thái kéo dài quá lâu.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu đã kiểm chứng bằng trận đấu. Tôi sẽ cùng cậu đối chiếu lượt quan trọng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Hồi phục rồi thử đổi cách xử lý trạng thái trước khi nhận trận tiếp.; hành động: chỉ chuyển nhánh

### thanks — dr_orin

Cảm ơn cậu đã kiểm chứng bằng trận đấu. Tôi sẽ cùng cậu đối chiếu lượt quan trọng.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — dr_orin

Được. Hồi phục rồi thử đổi cách xử lý trạng thái trước khi nhận trận tiếp.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.rook.win

### start — rook

Cậu thắng. Tôi giao đúng phần đã hứa, không thêm giá sau trận.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Một trận rõ kết quả dễ làm việc hơn một giờ mặc cả. Cảm ơn cậu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Cậu chuẩn bị lại, tôi không đổi điều kiện vì trận thua này.; hành động: chỉ chuyển nhánh

### thanks — rook

Một trận rõ kết quả dễ làm việc hơn một giờ mặc cả. Cảm ơn cậu.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — rook

Được. Cậu chuẩn bị lại, tôi không đổi điều kiện vì trận thua này.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.rook.loss

### start — rook

Tôi giữ được nhịp đến cuối. Cậu bỏ chỗ đổi Pokémon sớm hơn mức cần thiết.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Một trận rõ kết quả dễ làm việc hơn một giờ mặc cả. Cảm ơn cậu.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Cậu chuẩn bị lại, tôi không đổi điều kiện vì trận thua này.; hành động: chỉ chuyển nhánh

### thanks — rook

Một trận rõ kết quả dễ làm việc hơn một giờ mặc cả. Cảm ơn cậu.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — rook

Được. Cậu chuẩn bị lại, tôi không đổi điều kiện vì trận thua này.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.selene_kade.win

### start — selene_kade

Cậu thắng, và vẫn giữ được nước đáp ở đoạn cuối. Tôi nhận kết quả.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Tôi giữ lời đã nói trước trận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục trước. Lần sau nhìn lại đội đang đứng trước cậu, không chỉ đội lúc trận bắt đầu.; hành động: chỉ chuyển nhánh

### thanks — selene_kade

Cảm ơn cậu. Tôi giữ lời đã nói trước trận.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — selene_kade

Hồi phục trước. Lần sau nhìn lại đội đang đứng trước cậu, không chỉ đội lúc trận bắt đầu.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.selene_kade.loss

### start — selene_kade

Trận này tôi thắng. Cậu chọn xong một phương án rồi giữ nó cả khi đội tôi đã đổi.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Tôi giữ lời đã nói trước trận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục trước. Lần sau nhìn lại đội đang đứng trước cậu, không chỉ đội lúc trận bắt đầu.; hành động: chỉ chuyển nhánh

### thanks — selene_kade

Cảm ơn cậu. Tôi giữ lời đã nói trước trận.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — selene_kade

Hồi phục trước. Lần sau nhìn lại đội đang đứng trước cậu, không chỉ đội lúc trận bắt đầu.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.sixth_warden.win

### start — sixth_warden

Cậu qua bài thử. Ta thấy cậu giữ được phần sức của đội để trở về.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Ta ghi nhận kết quả này. Cảm ơn cậu đã giữ đội đến cùng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Chuẩn bị lại. Ta vẫn giữ bài thử khi cậu có đội đủ sức.; hành động: chỉ chuyển nhánh

### thanks — sixth_warden

Ta ghi nhận kết quả này. Cảm ơn cậu đã giữ đội đến cùng.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — sixth_warden

Chuẩn bị lại. Ta vẫn giữ bài thử khi cậu có đội đủ sức.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.sixth_warden.loss

### start — sixth_warden

Bài thử chưa qua. Cậu dùng hết nước đáp trước khi đoạn khó kết thúc.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Ta ghi nhận kết quả này. Cảm ơn cậu đã giữ đội đến cùng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Chuẩn bị lại. Ta vẫn giữ bài thử khi cậu có đội đủ sức.; hành động: chỉ chuyển nhánh

### thanks — sixth_warden

Ta ghi nhận kết quả này. Cảm ơn cậu đã giữ đội đến cùng.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — sixth_warden

Chuẩn bị lại. Ta vẫn giữ bài thử khi cậu có đội đủ sức.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.rocket_grunt_01.win

### start — rocket_grunt_01

Tôi thua. Chốt này không còn giữ được cậu.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Tôi nhận kết quả, không có gì để cãi. Cậu đi theo phần đã thắng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Tôi giữ chốt ở đây; cậu muốn thử lại thì chuẩn bị đội trước.; hành động: chỉ chuyển nhánh

### thanks — rocket_grunt_01

Tôi nhận kết quả, không có gì để cãi. Cậu đi theo phần đã thắng.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — rocket_grunt_01

Được. Tôi giữ chốt ở đây; cậu muốn thử lại thì chuẩn bị đội trước.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.rocket_grunt_01.loss

### start — rocket_grunt_01

Tôi thắng. Cậu chưa qua chốt được.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Tôi nhận kết quả, không có gì để cãi. Cậu đi theo phần đã thắng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Tôi giữ chốt ở đây; cậu muốn thử lại thì chuẩn bị đội trước.; hành động: chỉ chuyển nhánh

### thanks — rocket_grunt_01

Tôi nhận kết quả, không có gì để cãi. Cậu đi theo phần đã thắng.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — rocket_grunt_01

Được. Tôi giữ chốt ở đây; cậu muốn thử lại thì chuẩn bị đội trước.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.rocket_grunt_02.win

### start — rocket_grunt_02

Cậu thắng. Tôi không kéo thêm một lượt nữa để giả vờ cửa chưa mở.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Kết quả đã rõ. Tôi không gọi thêm người để đổi nó.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Cậu hồi phục rồi quay lại nếu vẫn muốn qua.; hành động: chỉ chuyển nhánh

### thanks — rocket_grunt_02

Kết quả đã rõ. Tôi không gọi thêm người để đổi nó.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — rocket_grunt_02

Được. Cậu hồi phục rồi quay lại nếu vẫn muốn qua.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.rocket_grunt_02.loss

### start — rocket_grunt_02

Tôi giữ được cậu ở chốt. Cậu để những lần đổi Pokémon của tôi lấy quá nhiều thời gian.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Kết quả đã rõ. Tôi không gọi thêm người để đổi nó.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Cậu hồi phục rồi quay lại nếu vẫn muốn qua.; hành động: chỉ chuyển nhánh

### thanks — rocket_grunt_02

Kết quả đã rõ. Tôi không gọi thêm người để đổi nó.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — rocket_grunt_02

Được. Cậu hồi phục rồi quay lại nếu vẫn muốn qua.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.rocket_grunt_03.win

### start — rocket_grunt_03

Tôi thua. Cửa sau chốt này mở cho cậu.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Tôi nhận trận thua. Đi đúng cửa, đừng nghĩ chỗ nào trong kho cũng an toàn.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục trước. Tôi vẫn ở chốt, không đổi điều kiện sang một thứ khác.; hành động: chỉ chuyển nhánh

### thanks — rocket_grunt_03

Tôi nhận trận thua. Đi đúng cửa, đừng nghĩ chỗ nào trong kho cũng an toàn.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — rocket_grunt_03

Hồi phục trước. Tôi vẫn ở chốt, không đổi điều kiện sang một thứ khác.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.rocket_grunt_03.loss

### start — rocket_grunt_03

Cậu chưa vượt chốt. Đội tôi còn đủ nước đáp khi cậu chỉ giữ một hướng tấn công.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Tôi nhận trận thua. Đi đúng cửa, đừng nghĩ chỗ nào trong kho cũng an toàn.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục trước. Tôi vẫn ở chốt, không đổi điều kiện sang một thứ khác.; hành động: chỉ chuyển nhánh

### thanks — rocket_grunt_03

Tôi nhận trận thua. Đi đúng cửa, đừng nghĩ chỗ nào trong kho cũng an toàn.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — rocket_grunt_03

Hồi phục trước. Tôi vẫn ở chốt, không đổi điều kiện sang một thứ khác.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.rocket_admin_vex.win

### start — rocket_admin_vex

Cậu thắng. Tôi không thể giữ máy bằng trận đấu nữa.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Tôi giữ phần đã hứa. Chứng cứ cậu mang đi vẫn có cả tên tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Cậu muốn thử lại thì chuẩn bị đội. Tôi chưa giao quyền chỉ từ lời nói.; hành động: chỉ chuyển nhánh

### thanks — rocket_admin_vex

Tôi giữ phần đã hứa. Chứng cứ cậu mang đi vẫn có cả tên tôi.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — rocket_admin_vex

Cậu muốn thử lại thì chuẩn bị đội. Tôi chưa giao quyền chỉ từ lời nói.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.rocket_admin_vex.loss

### start — rocket_admin_vex

Tôi thắng. Cậu còn để một phần đội mất nhịp trước khi có nước đáp.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Tôi giữ phần đã hứa. Chứng cứ cậu mang đi vẫn có cả tên tôi.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Cậu muốn thử lại thì chuẩn bị đội. Tôi chưa giao quyền chỉ từ lời nói.; hành động: chỉ chuyển nhánh

### thanks — rocket_admin_vex

Tôi giữ phần đã hứa. Chứng cứ cậu mang đi vẫn có cả tên tôi.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — rocket_admin_vex

Cậu muốn thử lại thì chuẩn bị đội. Tôi chưa giao quyền chỉ từ lời nói.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.seventh_warden.win

### start — seventh_warden

Cậu qua bài thử của ta. Quyền tiếp cận sẽ theo đúng kết quả đã ghi.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Ta nhận lời cảm ơn. Giữ cách quan sát đó khi vào khu vực thực.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục rồi quay lại. Ta không đóng quyền thử chỉ vì một lần chưa đạt.; hành động: chỉ chuyển nhánh

### thanks — seventh_warden

Ta nhận lời cảm ơn. Giữ cách quan sát đó khi vào khu vực thực.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — seventh_warden

Hồi phục rồi quay lại. Ta không đóng quyền thử chỉ vì một lần chưa đạt.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.seventh_warden.loss

### start — seventh_warden

Trận này cậu chưa qua. Kế hoạch cần giữ cả đường rút khi một Pokémon không còn đứng được.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Ta nhận lời cảm ơn. Giữ cách quan sát đó khi vào khu vực thực.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục rồi quay lại. Ta không đóng quyền thử chỉ vì một lần chưa đạt.; hành động: chỉ chuyển nhánh

### thanks — seventh_warden

Ta nhận lời cảm ơn. Giữ cách quan sát đó khi vào khu vực thực.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — seventh_warden

Hồi phục rồi quay lại. Ta không đóng quyền thử chỉ vì một lần chưa đạt.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.captain_dorian.win

### start — captain_dorian

Cậu thắng vòng cảng. Đội của cậu giữ được kế hoạch cả khi mưa đổi nhịp.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Tôi xác nhận kết quả, không bắt cậu thắng thêm một trận cho cùng vòng này.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Hồi phục rồi thử lại; vòng cảng vẫn giữ lượt của cậu.; hành động: chỉ chuyển nhánh

### thanks — captain_dorian

Cảm ơn cậu. Tôi xác nhận kết quả, không bắt cậu thắng thêm một trận cho cùng vòng này.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — captain_dorian

Được. Hồi phục rồi thử lại; vòng cảng vẫn giữ lượt của cậu.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.captain_dorian.loss

### start — captain_dorian

Trận này tôi thắng. Cậu chưa tận dụng được lượt đội mưa cần chuẩn bị.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Tôi xác nhận kết quả, không bắt cậu thắng thêm một trận cho cùng vòng này.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Hồi phục rồi thử lại; vòng cảng vẫn giữ lượt của cậu.; hành động: chỉ chuyển nhánh

### thanks — captain_dorian

Cảm ơn cậu. Tôi xác nhận kết quả, không bắt cậu thắng thêm một trận cho cùng vòng này.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — captain_dorian

Được. Hồi phục rồi thử lại; vòng cảng vẫn giữ lượt của cậu.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.battle_tower_trainer_01.win

### start — battle_tower_trainer_01

Cậu thắng. Cách chuẩn bị lượt sau đã giúp cậu giữ được khoảng trống.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Tôi giữ bản ghi để cả hai còn xem được cách lượt sau đã được chuẩn bị.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Thử dành một nước đáp trước khi chọn đòn mạnh nhất ở lần sau.; hành động: chỉ chuyển nhánh

### thanks — battle_tower_trainer_01

Cảm ơn cậu. Tôi giữ bản ghi để cả hai còn xem được cách lượt sau đã được chuẩn bị.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — battle_tower_trainer_01

Được. Thử dành một nước đáp trước khi chọn đòn mạnh nhất ở lần sau.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.battle_tower_trainer_01.loss

### start — battle_tower_trainer_01

Trận này tôi thắng. Cậu mất nhịp một lượt rồi cố lấy lại bằng những nước quá gấp.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Tôi giữ bản ghi để cả hai còn xem được cách lượt sau đã được chuẩn bị.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Thử dành một nước đáp trước khi chọn đòn mạnh nhất ở lần sau.; hành động: chỉ chuyển nhánh

### thanks — battle_tower_trainer_01

Cảm ơn cậu. Tôi giữ bản ghi để cả hai còn xem được cách lượt sau đã được chuẩn bị.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — battle_tower_trainer_01

Được. Thử dành một nước đáp trước khi chọn đòn mạnh nhất ở lần sau.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.battle_tower_trainer_02.win

### start — battle_tower_trainer_02

Cậu chọn đúng lúc hành động. Tôi nhận trận thua này.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Trận này không cần được kể nhanh hơn để thành một trận hay.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Hồi phục rồi xem lại lúc thứ tự hành động đổi trước khi quay lại.; hành động: chỉ chuyển nhánh

### thanks — battle_tower_trainer_02

Cảm ơn cậu. Trận này không cần được kể nhanh hơn để thành một trận hay.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — battle_tower_trainer_02

Được. Hồi phục rồi xem lại lúc thứ tự hành động đổi trước khi quay lại.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.battle_tower_trainer_02.loss

### start — battle_tower_trainer_02

Tôi thắng. Trật tự hành động đã đổi, nhưng cậu vẫn chọn như ở những lượt trước.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Trận này không cần được kể nhanh hơn để thành một trận hay.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Hồi phục rồi xem lại lúc thứ tự hành động đổi trước khi quay lại.; hành động: chỉ chuyển nhánh

### thanks — battle_tower_trainer_02

Cảm ơn cậu. Trận này không cần được kể nhanh hơn để thành một trận hay.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — battle_tower_trainer_02

Được. Hồi phục rồi xem lại lúc thứ tự hành động đổi trước khi quay lại.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.battle_tower_trainer_03.win

### start — battle_tower_trainer_03

Cậu thắng. Bản ghi sẽ giữ đủ tên hai người.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Kết quả không bị sửa chỉ vì người giữ hồ sơ thua.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục trước. Những điều cậu đã làm đúng vẫn có trong bản ghi để dùng cho lần sau.; hành động: chỉ chuyển nhánh

### thanks — battle_tower_trainer_03

Cảm ơn cậu. Kết quả không bị sửa chỉ vì người giữ hồ sơ thua.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — battle_tower_trainer_03

Hồi phục trước. Những điều cậu đã làm đúng vẫn có trong bản ghi để dùng cho lần sau.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.battle_tower_trainer_03.loss

### start — battle_tower_trainer_03

Trận này tôi thắng. Cậu đọc được thành viên đội nhưng bỏ qua phần thời tiết.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Kết quả không bị sửa chỉ vì người giữ hồ sơ thua.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục trước. Những điều cậu đã làm đúng vẫn có trong bản ghi để dùng cho lần sau.; hành động: chỉ chuyển nhánh

### thanks — battle_tower_trainer_03

Cảm ơn cậu. Kết quả không bị sửa chỉ vì người giữ hồ sơ thua.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — battle_tower_trainer_03

Hồi phục trước. Những điều cậu đã làm đúng vẫn có trong bản ghi để dùng cho lần sau.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.league_elite_01.win

### start — league_elite_01

Cậu thắng. Ta giữ cả những lượt mình muốn quên trong bản ghi này.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Ta không bỏ phần thua khỏi hồ sơ của mình.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Xem lượt lặp trong bản ghi rồi thử cách đáp khác khi quay lại.; hành động: chỉ chuyển nhánh

### thanks — league_elite_01

Cảm ơn cậu. Ta không bỏ phần thua khỏi hồ sơ của mình.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — league_elite_01

Được. Xem lượt lặp trong bản ghi rồi thử cách đáp khác khi quay lại.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.league_elite_01.loss

### start — league_elite_01

Trận này ta thắng. Một cách đáp đã lặp lại dù đội đối thủ không còn ở vị trí cũ.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Ta không bỏ phần thua khỏi hồ sơ của mình.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Xem lượt lặp trong bản ghi rồi thử cách đáp khác khi quay lại.; hành động: chỉ chuyển nhánh

### thanks — league_elite_01

Cảm ơn cậu. Ta không bỏ phần thua khỏi hồ sơ của mình.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — league_elite_01

Được. Xem lượt lặp trong bản ghi rồi thử cách đáp khác khi quay lại.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.league_elite_02.win

### start — league_elite_02

Cậu thắng bằng luật chung. Tôi xác nhận kết quả này.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Tôi giữ đúng kết quả và phần đã hứa trước trận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Lần thử tiếp vẫn dùng cùng luật; chữa cho đội trước khi nhận lời.; hành động: chỉ chuyển nhánh

### thanks — league_elite_02

Cảm ơn cậu. Tôi giữ đúng kết quả và phần đã hứa trước trận.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — league_elite_02

Được. Lần thử tiếp vẫn dùng cùng luật; chữa cho đội trước khi nhận lời.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.league_elite_02.loss

### start — league_elite_02

Trận này tôi thắng. Một nước đổi chậm đã để đội tôi giữ được lợi thế tới cuối.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Tôi giữ đúng kết quả và phần đã hứa trước trận.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Lần thử tiếp vẫn dùng cùng luật; chữa cho đội trước khi nhận lời.; hành động: chỉ chuyển nhánh

### thanks — league_elite_02

Cảm ơn cậu. Tôi giữ đúng kết quả và phần đã hứa trước trận.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — league_elite_02

Được. Lần thử tiếp vẫn dùng cùng luật; chữa cho đội trước khi nhận lời.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.league_elite_03.win

### start — league_elite_03

Cậu thắng. Ta nhớ rõ lượt cuối, không bỏ nó khỏi lời kể.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Ta sẽ giữ bản ghi đủ những lượt đã xảy ra.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục trước. Lần sau giữ thêm một cách đổi đội trước đoạn cuối.; hành động: chỉ chuyển nhánh

### thanks — league_elite_03

Cảm ơn cậu. Ta sẽ giữ bản ghi đủ những lượt đã xảy ra.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — league_elite_03

Hồi phục trước. Lần sau giữ thêm một cách đổi đội trước đoạn cuối.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.league_elite_03.loss

### start — league_elite_03

Trận này ta thắng. Cậu dùng Pokémon cuối làm nước đáp cho quá nhiều tình huống.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Ta sẽ giữ bản ghi đủ những lượt đã xảy ra.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục trước. Lần sau giữ thêm một cách đổi đội trước đoạn cuối.; hành động: chỉ chuyển nhánh

### thanks — league_elite_03

Cảm ơn cậu. Ta sẽ giữ bản ghi đủ những lượt đã xảy ra.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — league_elite_03

Hồi phục trước. Lần sau giữ thêm một cách đổi đội trước đoạn cuối.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.aurelia.win

### start — aurelia

Cậu thắng trận Champion. Ta giữ lời trả lời bằng tên mình.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Không còn chức danh nào thay ta trả lời phần đã hứa.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Ta không đóng lượt thử của cậu sau trận thua; chuẩn bị đội rồi quay lại.; hành động: chỉ chuyển nhánh

### thanks — aurelia

Cảm ơn cậu. Không còn chức danh nào thay ta trả lời phần đã hứa.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — aurelia

Được. Ta không đóng lượt thử của cậu sau trận thua; chuẩn bị đội rồi quay lại.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.aurelia.loss

### start — aurelia

Trận này ta thắng. Đội cậu còn thiếu một nước đáp khi trận kéo dài.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Không còn chức danh nào thay ta trả lời phần đã hứa.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Ta không đóng lượt thử của cậu sau trận thua; chuẩn bị đội rồi quay lại.; hành động: chỉ chuyển nhánh

### thanks — aurelia

Cảm ơn cậu. Không còn chức danh nào thay ta trả lời phần đã hứa.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — aurelia

Được. Ta không đóng lượt thử của cậu sau trận thua; chuẩn bị đội rồi quay lại.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.school_wolf_trainer_01.win

### start — school_wolf_trainer_01

Cậu qua bài thử. Cậu đã dừng tấn công đúng lúc đội cần đổi.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Ta ghi kết quả. Cảm ơn cậu đã làm đúng phần giữ đội của bài thử.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục rồi thử lại. Lần sau xem cả lượt cần dừng, không chỉ lượt có thể đánh.; hành động: chỉ chuyển nhánh

### thanks — school_wolf_trainer_01

Ta ghi kết quả. Cảm ơn cậu đã làm đúng phần giữ đội của bài thử.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — school_wolf_trainer_01

Hồi phục rồi thử lại. Lần sau xem cả lượt cần dừng, không chỉ lượt có thể đánh.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.school_wolf_trainer_01.loss

### start — school_wolf_trainer_01

Cậu chưa qua. Một lượt tiếp tục tấn công đã làm mất cơ hội giữ đội.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Ta ghi kết quả. Cảm ơn cậu đã làm đúng phần giữ đội của bài thử.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục rồi thử lại. Lần sau xem cả lượt cần dừng, không chỉ lượt có thể đánh.; hành động: chỉ chuyển nhánh

### thanks — school_wolf_trainer_01

Ta ghi kết quả. Cảm ơn cậu đã làm đúng phần giữ đội của bài thử.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — school_wolf_trainer_01

Hồi phục rồi thử lại. Lần sau xem cả lượt cần dừng, không chỉ lượt có thể đánh.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.school_wolf_trainer_02.win

### start — school_wolf_trainer_02

Cậu qua bài thử. Cậu nhận ra hướng đổi Pokémon của tôi trước khi nó hoàn tất.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Giữ cách đọc dấu ấy cho bài thử tiếp theo.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Chữa cho đội rồi quay lại; quan sát cần tiếp tục qua từng lượt.; hành động: chỉ chuyển nhánh

### thanks — school_wolf_trainer_02

Cảm ơn cậu. Giữ cách đọc dấu ấy cho bài thử tiếp theo.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — school_wolf_trainer_02

Được. Chữa cho đội rồi quay lại; quan sát cần tiếp tục qua từng lượt.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.school_wolf_trainer_02.loss

### start — school_wolf_trainer_02

Trận này tôi thắng. Cậu đã quan sát đầu trận nhưng bỏ dấu hiệu khi đội tôi đổi nhịp.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Giữ cách đọc dấu ấy cho bài thử tiếp theo.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Chữa cho đội rồi quay lại; quan sát cần tiếp tục qua từng lượt.; hành động: chỉ chuyển nhánh

### thanks — school_wolf_trainer_02

Cảm ơn cậu. Giữ cách đọc dấu ấy cho bài thử tiếp theo.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — school_wolf_trainer_02

Được. Chữa cho đội rồi quay lại; quan sát cần tiếp tục qua từng lượt.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.school_wolf_trainer_03.win

### start — school_wolf_trainer_03

Cậu qua bài thử. Chuẩn bị và cách dùng đội đã khớp nhau.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Tôi ghi nhận kết quả. Cảm ơn cậu đã dùng đúng phần đã chuẩn bị.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục trước. Vật tư đã được ghi nhận, cậu không phải giao lại phần cũ để thử tiếp.; hành động: chỉ chuyển nhánh

### thanks — school_wolf_trainer_03

Tôi ghi nhận kết quả. Cảm ơn cậu đã dùng đúng phần đã chuẩn bị.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — school_wolf_trainer_03

Hồi phục trước. Vật tư đã được ghi nhận, cậu không phải giao lại phần cũ để thử tiếp.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.school_wolf_trainer_03.loss

### start — school_wolf_trainer_03

Bài thử chưa qua. Cậu có đủ phần chuẩn bị, nhưng chưa giữ được người mang nó đến cuối trận.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Tôi ghi nhận kết quả. Cảm ơn cậu đã dùng đúng phần đã chuẩn bị.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục trước. Vật tư đã được ghi nhận, cậu không phải giao lại phần cũ để thử tiếp.; hành động: chỉ chuyển nhánh

### thanks — school_wolf_trainer_03

Tôi ghi nhận kết quả. Cảm ơn cậu đã dùng đúng phần đã chuẩn bị.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — school_wolf_trainer_03

Hồi phục trước. Vật tư đã được ghi nhận, cậu không phải giao lại phần cũ để thử tiếp.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.school_wolf_master.win

### start — school_wolf_master

Cậu thắng. Ta giữ lời mở phần hồ sơ đã hứa.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Ta nhận lời cảm ơn. Những gì ở trong hồ sơ vẫn cần cậu đọc trực tiếp.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Chữa đội rồi quay lại. Ta muốn thấy cách đọc trận đã khác, không cần một lời hứa dài.; hành động: chỉ chuyển nhánh

### thanks — school_wolf_master

Ta nhận lời cảm ơn. Những gì ở trong hồ sơ vẫn cần cậu đọc trực tiếp.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — school_wolf_master

Chữa đội rồi quay lại. Ta muốn thấy cách đọc trận đã khác, không cần một lời hứa dài.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.school_wolf_master.loss

### start — school_wolf_master

Ta thắng trận này. Cậu còn bỏ một dấu hiệu đổi nhịp ở đoạn giữa.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Ta nhận lời cảm ơn. Những gì ở trong hồ sơ vẫn cần cậu đọc trực tiếp.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Chữa đội rồi quay lại. Ta muốn thấy cách đọc trận đã khác, không cần một lời hứa dài.; hành động: chỉ chuyển nhánh

### thanks — school_wolf_master

Ta nhận lời cảm ơn. Những gì ở trong hồ sơ vẫn cần cậu đọc trực tiếp.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — school_wolf_master

Chữa đội rồi quay lại. Ta muốn thấy cách đọc trận đã khác, không cần một lời hứa dài.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.mysterious.win

### start — mysterious

Cậu thắng. Bản ghi lần này có tên của cả người thắng và người thua.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Tôi sẽ không xóa phần của mình khỏi kết quả này.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Giữ đội đủ sức rồi quay lại; câu hỏi của cậu vẫn còn nguyên.; hành động: chỉ chuyển nhánh

### thanks — mysterious

Cảm ơn cậu. Tôi sẽ không xóa phần của mình khỏi kết quả này.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — mysterious

Được. Giữ đội đủ sức rồi quay lại; câu hỏi của cậu vẫn còn nguyên.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.mysterious.loss

### start — mysterious

Trận này tôi thắng. Tôi vẫn giữ bản gốc của cậu nguyên vẹn.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Tôi sẽ không xóa phần của mình khỏi kết quả này.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Giữ đội đủ sức rồi quay lại; câu hỏi của cậu vẫn còn nguyên.; hành động: chỉ chuyển nhánh

### thanks — mysterious

Cảm ơn cậu. Tôi sẽ không xóa phần của mình khỏi kết quả này.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — mysterious

Được. Giữ đội đủ sức rồi quay lại; câu hỏi của cậu vẫn còn nguyên.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.town1_bao.win

### start — town1_bao

Tôi thua. Tôi thấy được lượt mình chọn sai rồi.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu đã đấu thật. Tôi muốn tự sửa phần mình sai, không nhận một trận được nhường.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Tôi đợi cậu chữa đội; khi đấu lại tôi cũng thử sửa phần mình chưa chắc.; hành động: chỉ chuyển nhánh

### thanks — town1_bao

Cảm ơn cậu đã đấu thật. Tôi muốn tự sửa phần mình sai, không nhận một trận được nhường.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — town1_bao

Được. Tôi đợi cậu chữa đội; khi đấu lại tôi cũng thử sửa phần mình chưa chắc.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.town1_bao.loss

### start — town1_bao

Tôi thắng trận này. Tôi vẫn muốn cùng xem lại những chỗ cả hai đã tính.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu đã đấu thật. Tôi muốn tự sửa phần mình sai, không nhận một trận được nhường.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Tôi đợi cậu chữa đội; khi đấu lại tôi cũng thử sửa phần mình chưa chắc.; hành động: chỉ chuyển nhánh

### thanks — town1_bao

Cảm ơn cậu đã đấu thật. Tôi muốn tự sửa phần mình sai, không nhận một trận được nhường.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — town1_bao

Được. Tôi đợi cậu chữa đội; khi đấu lại tôi cũng thử sửa phần mình chưa chắc.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.town1_minh.win

### start — town1_minh

Cậu thắng. Tôi biết mình bỏ lỡ lượt nào trong đoạn giữa.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Trận này cho tôi một chỗ cụ thể để luyện tiếp.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Hồi phục rồi quay lại; tôi giữ bản ghi để cùng đối chiếu.; hành động: chỉ chuyển nhánh

### thanks — town1_minh

Cảm ơn cậu. Trận này cho tôi một chỗ cụ thể để luyện tiếp.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — town1_minh

Được. Hồi phục rồi quay lại; tôi giữ bản ghi để cùng đối chiếu.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.town1_minh.loss

### start — town1_minh

Tôi thắng, nhưng còn mấy lượt tôi muốn xem lại thay vì chỉ kể kết quả.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Trận này cho tôi một chỗ cụ thể để luyện tiếp.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Hồi phục rồi quay lại; tôi giữ bản ghi để cùng đối chiếu.; hành động: chỉ chuyển nhánh

### thanks — town1_minh

Cảm ơn cậu. Trận này cho tôi một chỗ cụ thể để luyện tiếp.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — town1_minh

Được. Hồi phục rồi quay lại; tôi giữ bản ghi để cùng đối chiếu.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.town3_shady_trader.win

### start — town3_shady_trader

Tôi thua. Tôi sẽ giao hóa đơn và trả phần đã hứa.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Được. Cậu giữ giấy tờ, tôi không đặt thêm phí để lấy phần đã thắng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Cậu chuẩn bị lại rồi quay lại. Tôi không bán một gói bảo đảm thắng trận này.; hành động: chỉ chuyển nhánh

### thanks — town3_shady_trader

Được. Cậu giữ giấy tờ, tôi không đặt thêm phí để lấy phần đã thắng.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — town3_shady_trader

Cậu chuẩn bị lại rồi quay lại. Tôi không bán một gói bảo đảm thắng trận này.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.town3_shady_trader.loss

### start — town3_shady_trader

Tôi thắng trận này. Đống hóa đơn vẫn còn, tôi chưa làm nó biến mất.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Được. Cậu giữ giấy tờ, tôi không đặt thêm phí để lấy phần đã thắng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Cậu chuẩn bị lại rồi quay lại. Tôi không bán một gói bảo đảm thắng trận này.; hành động: chỉ chuyển nhánh

### thanks — town3_shady_trader

Được. Cậu giữ giấy tờ, tôi không đặt thêm phí để lấy phần đã thắng.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — town3_shady_trader

Cậu chuẩn bị lại rồi quay lại. Tôi không bán một gói bảo đảm thắng trận này.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.town8_trainer_hai.win

### start — town8_trainer_hai

Cậu thắng. Tôi cần sửa lượt chuẩn bị trước khi nghĩ tên bài mới.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Tôi sẽ xem đội đã làm gì, không chỉ nhớ tiếng mình gọi đòn.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Cậu chữa đội rồi đấu tiếp; tôi cũng muốn thử một cách giữ lượt khác.; hành động: chỉ chuyển nhánh

### thanks — town8_trainer_hai

Cảm ơn cậu. Tôi sẽ xem đội đã làm gì, không chỉ nhớ tiếng mình gọi đòn.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — town8_trainer_hai

Được. Cậu chữa đội rồi đấu tiếp; tôi cũng muốn thử một cách giữ lượt khác.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.town8_trainer_hai.loss

### start — town8_trainer_hai

Tôi thắng trận này. Cloyster đã giữ được lượt tăng sức mạnh, nhưng tôi biết không phải lúc nào cũng có nó.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn cậu. Tôi sẽ xem đội đã làm gì, không chỉ nhớ tiếng mình gọi đòn.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Cậu chữa đội rồi đấu tiếp; tôi cũng muốn thử một cách giữ lượt khác.; hành động: chỉ chuyển nhánh

### thanks — town8_trainer_hai

Cảm ơn cậu. Tôi sẽ xem đội đã làm gì, không chỉ nhớ tiếng mình gọi đòn.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — town8_trainer_hai

Được. Cậu chữa đội rồi đấu tiếp; tôi cũng muốn thử một cách giữ lượt khác.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.weather_guardian_groudon.win

### start — weather_guardian_groudon

Bạn qua bài thử. Tôi có thể xác nhận bạn giữ được đội gần buồng địa tầng.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn bạn. Tôi giữ xác nhận này cùng phần kiểm tra thiết bị.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục rồi thử lại. Chúng ta chưa mở buồng khi đội chưa sẵn sàng.; hành động: chỉ chuyển nhánh

### thanks — weather_guardian_groudon

Cảm ơn bạn. Tôi giữ xác nhận này cùng phần kiểm tra thiết bị.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — weather_guardian_groudon

Hồi phục rồi thử lại. Chúng ta chưa mở buồng khi đội chưa sẵn sàng.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.weather_guardian_groudon.loss

### start — weather_guardian_groudon

Bạn chưa qua bài thử. Đội cần một cách giữ vị trí khi áp lực kéo dài.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn bạn. Tôi giữ xác nhận này cùng phần kiểm tra thiết bị.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Hồi phục rồi thử lại. Chúng ta chưa mở buồng khi đội chưa sẵn sàng.; hành động: chỉ chuyển nhánh

### thanks — weather_guardian_groudon

Cảm ơn bạn. Tôi giữ xác nhận này cùng phần kiểm tra thiết bị.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — weather_guardian_groudon

Hồi phục rồi thử lại. Chúng ta chưa mở buồng khi đội chưa sẵn sàng.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.weather_guardian_kyogre.win

### start — weather_guardian_kyogre

Bạn qua bài thử. Tôi xác nhận phần đổi nhịp theo nước và thời tiết.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn bạn. Phần xác nhận của tôi được giữ riêng với xác nhận địa tầng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Hồi phục trước rồi thử đọc nhịp đổi từ những lượt sớm hơn.; hành động: chỉ chuyển nhánh

### thanks — weather_guardian_kyogre

Cảm ơn bạn. Phần xác nhận của tôi được giữ riêng với xác nhận địa tầng.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — weather_guardian_kyogre

Được. Hồi phục trước rồi thử đọc nhịp đổi từ những lượt sớm hơn.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

## outcome.weather_guardian_kyogre.loss

### start — weather_guardian_kyogre

Bạn chưa qua. Khi nhịp mưa đổi, đội vẫn dùng cùng một nước đáp quá lâu.

- Người chơi: Cảm ơn vì trận đấu.
  → NPC: Cảm ơn bạn. Phần xác nhận của tôi được giữ riêng với xác nhận địa tầng.; hành động: chỉ chuyển nhánh
- Người chơi: Tôi sẽ hồi phục cho đội rồi quay lại.
  → NPC: Được. Hồi phục trước rồi thử đọc nhịp đổi từ những lượt sớm hơn.; hành động: chỉ chuyển nhánh

### thanks — weather_guardian_kyogre

Cảm ơn bạn. Phần xác nhận của tôi được giữ riêng với xác nhận địa tầng.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close

### retry — weather_guardian_kyogre

Được. Hồi phục trước rồi thử đọc nhịp đổi từ những lượt sớm hơn.

- Người chơi: Được, tôi đi chăm đội trước.
  → Kết thúc nút; hành động: close
