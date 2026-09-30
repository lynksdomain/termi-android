#!/usr/bin/env python3
"""Build and verify a signed APK. Local signing material is never printed or committed."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent
PRIVATE = ROOT / ".release"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--generate-key", action="store_true", help="Create a local release key if none exists")
args = parser.parse_args()
env = os.environ.copy()
credentials = PRIVATE / "signing.json"
keystore = PRIVATE / "termi-release.p12"
if args.generate_key:
    PRIVATE.mkdir(mode=0o700, exist_ok=True)
    if credentials.exists() or keystore.exists():
        raise SystemExit("Signing material already exists; refusing to replace it. Run without --generate-key.")
    password = secrets.token_urlsafe(32)
    data = {
        "TERMI_KEYSTORE_PATH": str(keystore),
        "TERMI_KEYSTORE_PASSWORD": password,
        "TERMI_KEY_ALIAS": "termi",
        "TERMI_KEY_PASSWORD": password,
    }
    keyenv = env | data
    subprocess.run(["keytool", "-genkeypair", "-keystore", str(keystore), "-storetype", "PKCS12",
                    "-alias", "termi", "-keyalg", "RSA", "-keysize", "4096", "-validity", "10000",
                    "-dname", "CN=Termi Android", "-storepass:env", "TERMI_KEYSTORE_PASSWORD",
                    "-keypass:env", "TERMI_KEY_PASSWORD"], env=keyenv, check=True)
    keystore.chmod(0o600)
    fd = os.open(credentials, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as handle:
        json.dump(data, handle)
    print("Created signing material in .release/. Back up this folder securely before publishing.")

# Prefer explicit environment configuration (CI or an existing external keystore).
keys = ["TERMI_KEYSTORE_PATH", "TERMI_KEYSTORE_PASSWORD", "TERMI_KEY_ALIAS", "TERMI_KEY_PASSWORD"]
if not any(env.get(key) for key in keys) and credentials.exists():
    env.update(json.loads(credentials.read_text()))
if not all(env.get(key) for key in keys):
    raise SystemExit("Supply the four TERMI signing variables or run once with --generate-key.")

subprocess.run([str(ROOT / "gradlew"), "assembleRelease", "lintRelease", "testDebugUnitTest"], cwd=ROOT, env=env, check=True)
metadata = json.loads((ROOT / "app/build/outputs/apk/release/output-metadata.json").read_text())
element = metadata["elements"][0]
source = ROOT / "app/build/outputs/apk/release" / element["outputFile"]
sdk = Path(env.get("ANDROID_HOME") or env.get("ANDROID_SDK_ROOT") or "")
if not sdk.is_dir() or not (sdk / "build-tools").is_dir():
    local = ROOT / "local.properties"
    if local.exists():
        for line in local.read_text().splitlines():
            if line.startswith("sdk.dir="):
                sdk = Path(line.split("=", 1)[1])
                break
candidates = list((sdk / "build-tools").glob("*/apksigner"))
if not candidates:
    raise SystemExit("Set ANDROID_HOME to an SDK containing apksigner.")
# SDK versions consist of numeric components; prerelease directories sort after stable equivalents.
apksigner = max(candidates, key=lambda p: tuple(int(x) if x.isdigit() else 0 for x in p.parent.name.split(".")))
subprocess.run([str(apksigner), "verify", "--verbose", "--print-certs", str(source)], check=True)
destination = ROOT / "dist"
destination.mkdir(exist_ok=True)
output = destination / f"termi-android-{element['versionName']}.apk"
shutil.copy2(source, output)
digest = hashlib.sha256(output.read_bytes()).hexdigest()
(destination / "SHA256SUMS").write_text(f"{digest}  {output.name}\n")
print(f"Verified release: {output.relative_to(ROOT)}")
