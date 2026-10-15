#!/usr/bin/env python3
"""Local Maven coordinates for Loom's nested-JAR capability validation. Never publishes."""
from pathlib import Path
import json,zipfile,shutil,hashlib
from xml.sax.saxutils import escape

root=Path(__file__).resolve().parents[1]/"deps"
catalog=[]
runtime=root/"runtime"
runtime.mkdir(exist_ok=True)
for artifact in sorted(root.glob("*.jar")):
 with zipfile.ZipFile(artifact) as archive:
  metadata=json.loads(archive.read("fabric.mod.json"))
  # Loom intentionally removes nested JARs from development mod remapping. Make their
  # library classes available on the QA classpath; production keeps original nesting.
  for nested in metadata.get("jars",[]):
   file=nested["file"]
   (runtime/Path(file).name).write_bytes(archive.read(file))
 name=metadata["id"]
 version=metadata["version"]
 if "$" in version:version="1.5-user-supplied"
 folder=root/"maven/dev/svextract/private"/name/version
 folder.mkdir(parents=True,exist_ok=True)
 jar=folder/f"{name}-{version}.jar"
 sha=hashlib.sha256(artifact.read_bytes()).hexdigest()
 if not jar.exists() or hashlib.sha256(jar.read_bytes()).hexdigest()!=sha:shutil.copyfile(artifact,jar)
 (folder/f"{name}-{version}.pom").write_text(f'<project><modelVersion>4.0.0</modelVersion><groupId>dev.svextract.private</groupId><artifactId>{escape(name)}</artifactId><version>{escape(version)}</version></project>\n')
 catalog.append({"coordinate":f"dev.svextract.private:{name}:{version}","file":artifact.name,"sha256":sha,"metadataVersion":metadata["version"]})
(root/"private-catalog.json").write_text(json.dumps(catalog,indent=2)+"\n")
print(f"Prepared {len(catalog)} local-only dependency coordinates")
