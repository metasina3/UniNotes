"""Sign a CI-built standalone APK with a private local keystore; never prints passwords.

Usage: python scripts/sign-release.py unsigned.apk signed.apk
Required environment: ANDROID_HOME, UNINOTES_KEYSTORE_PATH,
UNINOTES_STORE_PASSWORD, UNINOTES_KEY_PASSWORD. Alias defaults to uninotes.
"""
import os
import subprocess
import sys
from pathlib import Path

source, destination = map(Path, sys.argv[1:3])
sdk = Path(os.environ['ANDROID_HOME'])
version = sorted((sdk / 'build-tools').iterdir(), key=lambda p: tuple(int(x) for x in p.name.split('.') if x.isdigit()))[-1]
aligned = destination.with_suffix('.aligned.apk')
try:
    subprocess.run([str(version / 'zipalign'), '-f', '-P', '16', '4', str(source), str(aligned)], check=True)
    subprocess.run([
        str(version / 'apksigner'), 'sign', '--ks', os.environ['UNINOTES_KEYSTORE_PATH'],
        '--ks-key-alias', os.environ.get('UNINOTES_KEY_ALIAS', 'uninotes'),
        '--ks-pass', 'env:UNINOTES_STORE_PASSWORD', '--key-pass', 'env:UNINOTES_KEY_PASSWORD',
        '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true',
        '--out', str(destination), str(aligned),
    ], check=True)
    subprocess.run([str(version / 'apksigner'), 'verify', '--verbose', '--print-certs', str(destination)], check=True)
    subprocess.run([str(version / 'zipalign'), '-c', '-P', '16', '4', str(destination)], check=True)
finally:
    aligned.unlink(missing_ok=True)
