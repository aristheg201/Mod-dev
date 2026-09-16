# Kiến trúc SVHub

## Phân lớp

```text
SVHub
├── Common/Core
│   ├── Content model + codec + validator
│   ├── Search index
│   ├── Action/permission contracts
│   ├── Network payloads
│   └── Integration API
├── Server
│   ├── Authoritative HubStore
│   ├── Snapshot projection
│   ├── Permission + edit authorization
│   ├── Action dispatcher
│   ├── Revision/history/rollback
│   └── Environment manifest
└── Client
    ├── Hub GUI + search/navigation
    ├── Pixel renderer/theme
    ├── Cobblemon model viewer
    ├── Dynamic command/mod providers
    ├── Editor/Asset Studio/Fakemon editor
    └── Client cache
```

## Server-authoritative flow

```text
Client Editor
  -> draft local
  -> validate preview
  -> chunked publish(baseRevision)
  -> server decode off tick thread
  -> permission/edit-diff authorization on server thread
  -> atomic commit under HubStore lock
  -> new revision
  -> projected snapshots broadcast
```

Không có đường nào để client tự ghi `content.json` trên server.

## Content runtime

`HubContent` gồm:

- `themes`
- `assets`
- `pages`
- `cobblemonWiki`

Mỗi `HubPage` là document gồm `HubComponent`. Renderer dispatch theo `component.type`; extension có thể cung cấp grid/search/action provider mà không cần thêm page subclass.

## Cobblemon integration

- Species đọc từ `PokemonSpecies.implemented` đã được Cobblemon sync xuống client.
- Pokémon model được tạo bằng `PokemonItem.from(species, aspects)` và render như item GUI, do đó đi qua built-in renderer của `cobblemon:pokemon_model`.
- Custom namespace có thể được tự phân loại Fakemon.
- Fakemon dạng aspect dùng `FakemonEntry(species, aspects)` và cùng pipeline model thật.

## Networking

- Protocol/version handshake.
- Server/client environment manifests tách biệt.
- Snapshot gzip + Base64, chunk giới hạn 20k chars.
- Giới hạn compressed transfer và decompressed bytes chống oversized/gzip-bomb payload.
- Client cache theo server identity hash + revision.

## Security

- Action request chỉ mang `actionId`.
- Server resolve action từ snapshot authoritative.
- Server re-check page/component visibility + action permission.
- Command action loại newline/CR/semicolon và giới hạn chiều dài; command vẫn chạy dưới `ServerPlayer` source nên Brigadier permission tiếp tục áp dụng.
- Editor publish cần permission riêng, revision match và diff authorization.
- History rollback không giảm revision; nó tạo revision mới để giữ monotonic state.
