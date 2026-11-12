#!/usr/bin/env python3
"""Build the bundled mpio executable for every supported Android ABI."""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", default=os.environ.get("ANDROID_NDK_HOME") or os.environ.get("ANDROID_NDK_ROOT"),
                        help="Android NDK directory (or set ANDROID_NDK_HOME)")
    args = parser.parse_args()
    if not args.ndk:
        parser.error("--ndk or ANDROID_NDK_HOME is required")

    root = Path(__file__).resolve().parents[1]
    prebuilts = Path(args.ndk) / "toolchains/llvm/prebuilt"
    host = "windows-x86_64" if os.name == "nt" else "darwin-x86_64" if os.sys.platform == "darwin" else "linux-x86_64"
    clang = prebuilts / host / "bin" / ("clang.exe" if os.name == "nt" else "clang")
    targets = {
        "arm64-v8a": "aarch64-linux-android23",
        "armeabi-v7a": "armv7a-linux-androideabi23",
        "x86": "i686-linux-android23",
        "x86_64": "x86_64-linux-android23",
    }
    build = root / "app/build/mpio"
    build.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="build-", dir=build) as temporary:
        for abi, target in targets.items():
            output = Path(temporary) / abi
            subprocess.run([
                str(clang), "--target=" + target, "-std=c11", "-O2", "-Wall", "-Wextra", "-Werror",
                "-fPIE", "-pie", "-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections",
                "-Wl,-z,max-page-size=16384", "-Wl,-z,common-page-size=16384", "-Wl,-s",
                str(root / "app/src/main/cpp/mpio/mpio.c"), "-o", str(output),
            ], check=True)
        # Do not replace any assets until all four builds have succeeded.
        for abi in targets:
            destination = root / "app/src/main/assets/bin" / abi / "mpio"
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(Path(temporary) / abi, destination)
            print(f"Built {abi}: {destination.stat().st_size} bytes")


if __name__ == "__main__":
    main()
