# Build status — source bundle 0.1.0

## Target

- Minecraft 1.21.1
- Java 21
- Fabric Loader 0.18.4+
- Fabric API 0.116.8+1.21.1
- Fabric Language Kotlin 1.13.6+kotlin.2.2.20
- Cobblemon 1.8.0+1.21.1

## Trạng thái trong môi trường tạo bundle

Môi trường hiện tại có Java/Kotlin compiler nhưng **không có Gradle wrapper/distribution và shell network không resolve dependency repositories**. Vì vậy không thể chạy Fabric Loom remap/build thật trong phiên này.

Không có JAR giả được đóng gói. Artifact giao kèm là source project có build config, tests, verifier và generated assets.

## QA có thể chạy không cần Gradle

```bash
python3 tools/generate_pixel_assets.py
python3 tools/verify_project.py
python3 -m compileall tools
```

Ngoài ra source được đưa qua Kotlin parser sanity check; unresolved Minecraft/Fabric/Cobblemon symbols là expected khi compiler không có dependency classpath.

## Build thật ở môi trường có network/dependency cache

```bash
gradle clean test build
```

Chỉ coi JAR là release candidate sau khi `test` + `remapJar/build` thành công và smoke-test cả dedicated server lẫn client.
