# SVHub

SVHub là Hub trực quan client + server cho Minecraft 1.21.1/Fabric, thiết kế để trở thành trung tâm Wiki, hướng dẫn, lệnh và thông tin Cobblemon của server.

## Mục tiêu

- Người chơi nhấn **H** để mở Hub và tìm mọi thứ từ một giao diện pixel trực quan.
- Server giữ quyền sở hữu nội dung, permission, action và publish; client chỉ render/sync/editor draft.
- Pokémon/Fakemon render bằng pipeline thật của Cobblemon `cobblemon:pokemon_model`, không cần maintain một bộ ảnh Pokémon riêng.
- Nội dung là data-driven: Java/Kotlin định nghĩa capability; page/component/theme/action được định nghĩa bằng content.

## Tính năng đã có trong source 0.1.0

- Home, How to Play, Pokémon, Fakemon, Essential Commands, Commands, Updates, Mods.
- Search hợp nhất page + Pokémon/Fakemon + command tree + mod environment + provider mở rộng.
- Pokédex/Fakédex động từ `PokemonSpecies`; custom namespace và `species + aspects` đều hỗ trợ.
- Trang Pokémon/Fakemon có live model, xoay/zoom, type, height/weight, ability, base stat, Pokédex text và link Wiki custom.
- Commands catalog đọc command tree server đã sync cho client; Essential Commands là danh sách curated.
- Pixel theme, generated backgrounds, animated text, sprite-sheet animated image, asset registry.
- Editor trong game: page/component palette, undo/redo, preview draft, Asset Studio, Fakemon editor, publish theo revision.
- Server-authoritative atomic publish, history/rollback, conflict detection và validation.
- Granular permissions với fallback vanilla OP level; optional Fabric Permissions API/LuckPerms ecosystem.
- Chunked compressed sync, transfer caps, decompression cap, client cache và I/O ngoài render/tick thread cho các đường nặng.
- Integration API cho custom server actions, dynamic grids và dynamic search providers.

## Cài đặt runtime

Yêu cầu:

- Minecraft 1.21.1
- Java 21
- Fabric Loader >= 0.18.4
- Fabric API
- Fabric Language Kotlin
- Cobblemon >= 1.8.0

SVHub phải có ở **cả client và server**.

## Build

```bash
gradle clean test build
```

JAR remap dự kiến:

```text
build/libs/SVHub-fabric-1.21.1-0.1.0.jar
```

Môi trường tạo source bundle trong phiên này không có Gradle/dependency network nên không có binary JAR được xác nhận build. Xem `BUILD_STATUS.md`.

## Content

Lần chạy đầu server tạo:

```text
config/svhub/content.json
config/svhub/history/
```

Admin có thể chỉnh bằng editor trong game; file JSON vẫn là representation bền vững cho backup/version control.

## Keybind / command

- `H`: mở SVHub.
- `/hub`: mở Hub.
- `/wiki`: mở Wiki/Hub.
- `/svhub editor`: mở editor nếu có quyền.
- `/svhub reload`: reload content.
- `/svhub history`: xem history.
- `/svhub rollback <revision>`: rollback nội dung cũ thành revision mới.

## Quy tắc kiến trúc

1. Server là source of truth.
2. Client không gửi command string tùy ý; chỉ gửi action ID đã tồn tại trong snapshot server.
3. Nội dung không hardcode thành class riêng cho Pokémon/Commands/Updates.
4. Resource/model Pokémon do Cobblemon sở hữu; SVHub chỉ trình bày.
5. Tác vụ nặng như JSON/compression/disk không chạy trên server tick/render thread.
