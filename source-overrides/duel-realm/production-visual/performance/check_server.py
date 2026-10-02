"""Bounded focused boot check. Stop as soon as real server authority is proven."""
import argparse
import os
from pathlib import Path
import signal
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--gradle', default='gradle')
args = parser.parse_args()
output = Path('build/focused-runtime.log')
output.parent.mkdir(parents=True, exist_ok=True)
latest = Path('run-focused/logs/latest.log')
latest.unlink(missing_ok=True)
required = ('Done (', 'CARDWORLDS_QA_EFFECT_COSTS', 'CARDWORLDS_QA_ECONOMY',
            'CARDWORLDS_QA_GACHA', 'CARDWORLDS_QA_BALANCES', 'CARDWORLDS_QA_REWARDS')
with output.open('w') as log:
    process = subprocess.Popen([args.gradle, '--no-daemon', '--max-workers=4',
                                'runFocusedCardWorldsServer'], stdout=log,
                               stderr=subprocess.STDOUT, start_new_session=True)
    try:
        deadline = time.monotonic() + 150
        while time.monotonic() < deadline:
            text = latest.read_text(errors='replace') if latest.exists() else ''
            if all(marker in text for marker in required):
                for line in text.splitlines():
                    if 'CARDWORLDS_QA_' in line:
                        print(line, flush=True)
                process.wait(timeout=30)
                assert process.returncode == 0, 'Focused server failed during clean stop: ' + output.read_text()[-8000:]
                break
            if process.poll() is not None:
                raise AssertionError('Focused server exited before readiness: ' + output.read_text()[-8000:])
            time.sleep(1)
        else:
            raise AssertionError('Focused server readiness exceeded 150s: ' + output.read_text()[-8000:])
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGINT)
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
                raise AssertionError('Focused server failed to stop and drain within 30s')
assert 'CARDWORLDS_PERF_SHUTDOWN pools=closed history=flushed receipts=closed' in latest.read_text(), \
    'Focused server did not prove clean Card Worlds shutdown'
print('CARDWORLDS_FOCUSED_SERVER_PASS authority=beconomy shutdown=clean')
