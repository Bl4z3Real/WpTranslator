# WpTranslator

Traduttore Android (minSdk 29) in stile Metro / Windows Phone.
Motore di traduzione selezionabile: **Bing** o **Google** (endpoint web pubblici, nessuna API key).

## Compilare con GitHub Actions
1. Crea un repository su GitHub e carica tutto il contenuto di questa cartella.
2. Vai su **Actions → Build APK** (parte da solo a ogni push, oppure "Run workflow").
3. A fine build scarica l'APK da **Artifacts → WpTranslator-debug**.

## Build locale
`gradle assembleDebug` (Gradle 8.7, JDK 17) oppure apri la cartella in Android Studio.
