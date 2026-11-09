#!/usr/bin/env bash
# Moves the deliberate part of the version. The build number is the git commit count and looks
# after itself, so an ordinary commit needs no bump at all.
#
#   ./scripts/bump-version.sh            # show the version the next build will carry
#   ./scripts/bump-version.sh minor      # 0.2.x -> 0.3.x
#   ./scripts/bump-version.sh major      # 0.2.x -> 1.0.x
#   ./scripts/bump-version.sh 1.4        # explicit major.minor
set -euo pipefail

cd "$(dirname "$0")/.."
file="gradle.properties"

major=$(grep -E '^appVersionMajor=' "$file" | head -1 | cut -d= -f2 | tr -d '[:space:]')
minor=$(grep -E '^appVersionMinor=' "$file" | head -1 | cut -d= -f2 | tr -d '[:space:]')
if [ -z "$major" ] || [ -z "$minor" ]; then
  echo "appVersionMajor and appVersionMinor are missing from $file" >&2
  exit 1
fi

build=$(git rev-list --count HEAD 2>/dev/null || echo 0)
kind="${1:-show}"

if [ "$kind" = "show" ]; then
  echo "$major.$minor.$build"
  exit 0
fi

set -- $(python3 - "$major" "$minor" "$kind" <<'PY'
import sys

major, minor = int(sys.argv[1]), int(sys.argv[2])
kind = sys.argv[3]

if kind == "major":
    major, minor = major + 1, 0
elif kind == "minor":
    minor += 1
elif kind.count(".") == 1:
    try:
        target = tuple(int(part) for part in kind.split("."))
    except ValueError:
        sys.exit(f"not a version: {kind}")
    if target <= (major, minor):
        sys.exit(f"refusing {major}.{minor} -> {kind}: a new version must be higher")
    major, minor = target
else:
    sys.exit(f"usage: bump-version.sh [show|major|minor|x.y] (got {kind})")

print(major, minor)
PY
)
major=$1
minor=$2
code=$(( major * 1000000 + minor * 10000 + build ))

sed -i.bak \
  -e "s/^appVersionMajor=.*/appVersionMajor=$major/" \
  -e "s/^appVersionMinor=.*/appVersionMinor=$minor/" \
  "$file"
rm -f "$file.bak"

echo "$major.$minor.$build (versionCode $code)"
echo "Next: git commit -am \"Release $major.$minor\" && git push"
echo "CI tags v$major.$minor.$build itself; the build number moves with every commit."
