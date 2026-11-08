#!/usr/bin/env python3
"""Download pinned official Cubism SDK archives into the local dependency cache."""

import argparse
import hashlib
from pathlib import Path
import shutil
import tempfile
import urllib.request
import zipfile


SDK_VERSION = "5-r.5"
SDK_ARCHIVES = {
    "native": (
        "CubismSdkForNative-5-r.5",
        "https://cubism.live2d.com/sdk-native/bin/CubismSdkForNative-5-r.5.zip",
        "7ff3a4bbc19c0a8728965aa522ab77eb11b252916453e68a8a78d3b71188bb12",
    ),
    "web": (
        "CubismSdkForWeb-5-r.5",
        "https://cubism.live2d.com/sdk-web/bin/CubismSdkForWeb-5-r.5.zip",
        "67064a7fb1812cf502f5c4a03bfe12cc638c75a621bb4acf06bb28763df06ba0",
    ),
}


def digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            checksum.update(block)
    return checksum.hexdigest()


def fetch(cache, sdk):
    name, url, expected = SDK_ARCHIVES[sdk]
    archive = cache / f"{name}.zip"
    if not archive.exists():
        with tempfile.NamedTemporaryFile(dir=cache, suffix=".download", delete=False) as output:
            temporary = Path(output.name)
            try:
                with urllib.request.urlopen(url, timeout=60) as response:
                    shutil.copyfileobj(response, output)
                output.close()
                if digest(temporary) != expected:
                    raise RuntimeError(f"SHA-256 mismatch: {name}")
                temporary.replace(archive)
            finally:
                temporary.unlink(missing_ok=True)
    if digest(archive) != expected:
        raise RuntimeError(f"SHA-256 mismatch: {archive}; the cached archive was not overwritten")

    destination = cache / name
    if not destination.exists():
        with tempfile.TemporaryDirectory(dir=cache, prefix="extract-") as staging:
            staging_root = Path(staging).resolve()
            with zipfile.ZipFile(archive) as package:
                for entry in package.infolist():
                    target = (staging_root / entry.filename).resolve()
                    if not target.is_relative_to(staging_root):
                        raise RuntimeError(f"Invalid archive path: {entry.filename}")
                    if (entry.external_attr >> 16) & 0o170000 == 0o120000:
                        raise RuntimeError(f"Unexpected archive link: {entry.filename}")
                package.extractall(staging_root)
            (staging_root / name).rename(destination)
    for relative in ("cubism-info.yml", "LICENSE.md", "Core", "Framework"):
        if not (destination / relative).exists():
            raise RuntimeError(f"Incomplete SDK: {destination / relative}")
    print(f"{sdk}: {destination} (archive SHA-256 verified)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", choices=("native", "web", "all"), default="all")
    parser.add_argument("--cache", type=Path,
                        default=Path(__file__).resolve().parents[1] / ".cache" / "cubism")
    options = parser.parse_args()
    cache = options.cache.resolve()
    cache.mkdir(parents=True, exist_ok=True)
    for sdk in SDK_ARCHIVES if options.sdk == "all" else (options.sdk,):
        fetch(cache, sdk)


if __name__ == "__main__":
    main()
