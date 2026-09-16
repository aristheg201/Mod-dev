# Permissions

SVHub hỗ trợ Fabric Permissions API khi có mặt; nếu không có thì fallback về vanilla permission level.

## Core

- `svhub.open`
- `svhub.editor`
- `svhub.editor.publish`
- `svhub.editor.all`
- `svhub.editor.page.create`
- `svhub.editor.assets`
- `svhub.editor.settings`
- `svhub.editor.history`
- `svhub.admin.reload`
- `svhub.admin.debug`
- `svhub.admin.rollback`

## Per-page

```text
svhub.editor.page.<page-id>
```

Ví dụ:

```text
svhub.editor.page.pokemon
svhub.editor.page.updates
```

`svhub.editor.all` bypass granular page/assets/settings checks cho owner/admin toàn quyền.

## Action permission

Mỗi `HubActionSpec` có thể có permission riêng. Permission này được re-check trên server ngay trước khi execute.
