#!/bin/bash
echo "???? === MINECRAFT PLUGIN ANALYZER === ????"
echo
echo "???? STATYSTYKI PROJEKTU:"
echo "??? Pliki Java: $(find . -name "*.java" | wc -l)"
echo "??? Linie kodu: $(find . -name "*.java" -exec wc -l {} + | tail -1 | awk '{print $1}')"
echo "??? Pliki pom.xml: $(find . -name "pom.xml" | wc -l)"
echo

echo "??????  POTENCJALNE PROBLEMY:"
echo "??? Deprecated API: $(grep -r "@Deprecated\|deprecated" . --include="*.java" | wc -l)"
echo "??? Null pointer risks: $(grep -r "\.get\|\.access" . --include="*.java" | grep -v "null" | wc -l)"
echo "??? Empty catches: $(grep -r "catch.*{.*}" . --include="*.java" | wc -l)"
echo

echo "???? MINECRAFT SPECIFICS:"
echo "??? Event listeners: $(grep -r "@EventHandler\|implements Listener" . --include="*.java" | wc -l)"
echo "??? Player interactions: $(grep -r "Player\|CommandSender" . --include="*.java" | wc -l)"
echo "??? Bukkit/Spigot API: $(grep -r "org\.bukkit\|org\.spigotmc" . --include="*.java" | wc -l)"
echo
