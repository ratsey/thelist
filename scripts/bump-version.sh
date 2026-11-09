#!/usr/bin/env bash
# Bumps the one version number in gradle.properties and prints the derived versionCode.
#
#   ./scripts/bump-version.sh patch     # 0.2.0 -> 0.2.1
#   ./scripts/bump-version.sh minor     # 0.2.1 -> 0.3.0
#   ./scripts/bump-version.sh major     # 0.3.0 -> 1.0.0
#   ./scripts/bump-version.sh 1.4.2     # explicit
set -euo pipefail

cd "$(dirname "$0")/.."

kind="${1:-patch}"
file="gradle.properties"

current=$(grep -E '^appVersionName=' "$file" | head -1 | cut -d= -f2 | tr -d '[:space:]')
if [ -z "$current" ]; then
  echo "appVersionName is missing from $file" >&2
  exit 1
fi

new=$(python3 - "$current" "$kind" <<'PY'
import sys

current, kind = sys.argv[1], sys.argv[2]
parts = (current.split(".") + ["0", "0"])[:3]
try:
    major, minor, patch = (int(p) for p in parts)
except ValueError:
    sys.exit(f"current version {current!r} is not x.y.z")

if kind == "major":
    major, minor, patch = major + 1, 0, 0
elif kind == "minor":
    minor, patch = minor + 1, 0
elif kind == "patch":
    patch += 1
elif kind.count(".") == 2:
    try:
        major, minor, patch = (int(p) for p in kind.split("."))
    except ValueError:
        sys.exit(f"not a version: {kind}")
else:
    sys.exit(f"usage: bump-version.sh patch|minor|major|x.y.z (got {kind})")

if (major, minor, patch) <= tuple(int(p) for p in parts):
    sys.exit(f"refusing {current} -> {major}.{minor}.{patch}: a new version must be higher")

print(f"{major}.{minor}.{patch}")
PY
)

code=$(( $(echo "$new" | cut -d. -f1) * 10000 + $(echo "$new" | cut -d. -f2) * 100 + $(echo "$new" | cut -d. -f3) ))

sed -i.bak "s/^appVersionName=.*/appVersionName=$new/" "$file"
rm -f "$file.bak"

echo "$current -> $new (versionCode $code)"
echo "Next: git commit -am \"Release $new\" && git push"
