#!/usr/bin/env bash
set -euo pipefail
: "${GH_TOKEN:?Set DEPS_TOKEN with Contents read access to TF-Minecraft/ServerAssets}"
ref=dd1e3ec5ee27d2467d5c07f9458bef212344ec41
mkdir -p libs
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/bf1951014517/joml-1.10.8.jar?ref=$ref" > "libs/joml-1.10.8.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/3bc6fa2477eb/BungeeCord.jar?ref=$ref" > "libs/BungeeCord.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/c84700df5942/MMOItems-6.10.jar?ref=$ref" > "libs/MMOItems-6.10.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/660ff2a6ec86/MythicLib-1.7.jar?ref=$ref" > "libs/MythicLib-1.7.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/14850d745437/MMOCore-1.13.1.jar?ref=$ref" > "libs/MMOCore-1.13.1.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/0116d714822b/ItemsAdder_3.5.0-r2.jar?ref=$ref" > "libs/ItemsAdder_3.5.0-r2.jar"
sha256sum --check .github/dependencies.sha256
