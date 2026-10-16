# KSAWU23 Client Ana (Fabric 1.21.4)

Budowanie: `./gradlew build` (potrzebny JDK 21, wrapper wygeneruj `gradle wrapper`) -> jar w build/libs.
Wrzuc jar + Fabric API do .minecraft/mods.

- Prawy Ctrl = GUI (LPM wl/wyl, PPM ustawienia, SPM keybind modulu)
- Wlasne tlo GUI: .minecraft/ksawu23/gui.png, tlo ekwipunku: .minecraft/ksawu23/inventory.png
  (potem Motyw -> "Przeladuj obrazki")
- Config: .minecraft/config/ksawu23.json
- Texturepacki PvP wrzucasz normalnie do .minecraft/resourcepacks

## Budowanie bez instalowania czegokolwiek (GitHub Actions)
1. Wrzuc caly folder na swoje repo GitHub (nowe, puste).
2. Zakladka Actions -> workflow "build" -> po ~3 min pobierz artefakt `ksawu23-client-ana` (jar bez "-sources").
3. Jar + Fabric API 0.118.0+1.21.4 do .minecraft/mods (Fabric Loader 0.16.10+).
