from pathlib import Path
import json, os, secrets, socket, struct, subprocess, time

root = Path(__file__).resolve().parent
env = dict(os.environ, JAVA_HOME=r'C:\Program Files\Java\jdk-21', GRADLE_EXIT_CONSOLE='')
gradle = r'E:\YU-GI-OH\local-tools\gradle-8.14.3\bin\gradle.bat'
results = []
for name, task, port in [
    ('run-server', 'runProductionCardWorldsServer', 25675),
    ('run-core-server', 'runProductionCardWorldsCoreServer', 25676),
]:
    run = root / name
    run.mkdir(exist_ok=True)
    password = secrets.token_hex(16)
    (run/'eula.txt').write_text('eula=true\n')
    (run/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nview-distance=3\nsimulation-distance=3\nenable-rcon=true\nrcon.port={port+10}\nrcon.password={password}\n')
    log = root/'build'/f'{name}-local.log'
    started = time.time()
    with log.open('w', encoding='utf-8') as out:
        proc = subprocess.Popen([gradle, '--no-daemon', '-I', 'local-runtime.init.gradle', task, '--console=plain'], cwd=root, env=env, stdout=out, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
        ready = False
        deadline = time.monotonic()+300
        while proc.poll() is None and time.monotonic()<deadline:
            latest = run/'logs/latest.log'
            text = latest.read_text(encoding='utf-8', errors='replace') if latest.exists() and latest.stat().st_mtime >= started else ''
            if 'Done (' in text:
                ready = True
                time.sleep(2)
                try:
                    with socket.create_connection(('127.0.0.1', port+10), timeout=10) as connection:
                        def packet(kind, body):
                            data = struct.pack('<ii', 12, kind)+body.encode()+b'\0\0'
                            connection.sendall(struct.pack('<i',len(data))+data)
                            return connection.recv(4096)
                        packet(3, password)
                        packet(2, 'stop')
                except OSError as error:
                    print(name, 'RCON stop:', error, flush=True)
                break
            time.sleep(2)
        try:
            proc.wait(timeout=30)
        except subprocess.TimeoutExpired:
            subprocess.run(['taskkill', '/PID', str(proc.pid), '/T', '/F'], capture_output=True)
    result = dict(runtime=name, booted=ready, exitCode=proc.poll(), log=str(log))
    results.append(result)
    print(json.dumps(result), flush=True)
(root/'build/local-server-evidence.json').write_text(json.dumps(results, indent=2))
