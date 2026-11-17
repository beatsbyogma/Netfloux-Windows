#!/bin/bash
# Régénère les sources Windows et prépare l'archive à envoyer au PC
set -e
python3 /home/claude/port-tools/port.py
N=${1:-x}
rm -f /mnt/user-data/outputs/nfw-*.tar
tar -cf /mnt/user-data/outputs/nfw-$N.tar -C /home/claude/NetflouxWindows src/main/kotlin build.gradle.kts settings.gradle.kts gradle.properties
echo /mnt/user-data/outputs/nfw-$N.tar
