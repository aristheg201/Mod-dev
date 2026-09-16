# SVHub 0.1.0 — QA report

## Đã kiểm tra trong môi trường hiện tại

- `tools/verify_project.py`: **PASS**
  - required source/resources tồn tại và không zero-byte
  - `fabric.mod.json` + lang JSON parse hợp lệ
  - 6 generated pixel PNG có đúng format/kích thước
  - các marker implementation quan trọng tồn tại
- Python tools compile: **PASS**
- Pixel asset generator: **PASS**
- GIF → sprite-sheet smoke test: **PASS** (4 × 16x16 -> 64x16 strip)
- Pure-JVM network smoke test: **PASS**
  - gzip/Base64 encode/decode
  - chunk out-of-order reassembly
  - invalid chunk rejection
  - action rate limiter + clear
- Kotlin parser sanity (không có dependency classpath): **0 syntax/parser errors**.

## Chưa thể xác nhận trong môi trường hiện tại

Không thể chạy Fabric Loom compile/remap/test vì container không có Gradle distribution/dependency cache và shell network không resolve Maven/Gradle repositories. Vì vậy các unresolved Minecraft/Fabric/Cobblemon symbols trong raw `kotlinc` check là expected và không được tính là build pass.

## Gate bắt buộc trước khi gọi JAR là release candidate

1. `gradle clean test build`
2. Dedicated server smoke boot với Minecraft 1.21.1/Fabric/Cobblemon 1.8.x.
3. Client join + handshake/snapshot/cache.
4. H key -> Hub; Ctrl+F search; Pokémon/Fakemon model render.
5. Editor draft/preview/publish/conflict/history/rollback.
6. Test user không permission gửi action/editor packets thủ công.
7. Profile FPS ở Pokédex/Fakédex grid và latency khi publish content lớn.
