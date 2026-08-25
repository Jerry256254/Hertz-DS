# Hertz-DS

**Lokální agentní AI asistent pro Android napojený na DeepSeek API.**
"OpenClaw pro mobil" — žádný účet, žádný cloud, žádné sledování. Veškerá data zůstávají v zařízení.

## 📥 Stažení

Nejnovější APK: **[Releases](../../releases)** → stáhněte `Hertz-DS-<verze>-debug.apk` a nainstalujte
(povolte "Instalovat z neznámých zdrojů").

> Vyžaduje Android 8.0+ (arm64 / armv7).

## ✨ Funkce

- **Agentní chování** – DeepSeek dostává nástroje (tool calling):
  - 🔎 vyhledávání na internetu + čtení stránek
  - 📁 čtení/zápis souborů v sandbox složce (text, JSON, CSV…)
  - 🖼️ práce s obrázky: OCR (ML Kit offline, volitelně Mistral), generování obrázků
  - ⏰ cron-like úlohy na pozadí (WorkManager) s notifikacemi
- **Dlouhodobá paměť** – FTS fulltext retrieval plně offline; agent si sám ukládá fakta (`remember`)
- **Ghost chaty** – více paralelních konverzací s vlastním system promptem, modelem i teplotou
- **Streamovaná odpověď + TTS** – odpověď se čte nahlas po větách už během generování;
  volitelně offline Piper hlasy (čeština "Jirka") + Whisper STT + Silero VAD hands-free režim
- **Řetězení API klíčů** – při 401/402/429 automaticky přejde na další klíč v pořadí
- **Přehled kreditů** – živý zůstatek z `/user/balance`, ruční dobití, spotřeba v $ i %
  včetně upozornění na nízký kredit a peak/off-peak sazeb DeepSeek (×2 ve špičce)
- **Soukromí** – klíče šifrované AES-256/GCM přes Android Keystore; jediné, co opustí telefon,
  jsou dotazy odeslané přímo na `api.deepseek.com` pod vaším klíčem

## 🚀 První spuštění

1. Nainstalujte APK a spusťte aplikaci
2. Potvrďte souhlas se zpracováním (EULA)
3. *Kredity* → přidejte klíč z [platform.deepseek.com](https://platform.deepseek.com)
4. Pište nebo podržte mikrofon pro hands-free režim

## 🛠️ Build ze zdrojáků

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Vyžaduje JDK 17+. Sherpa-onnx engine se stahuje automaticky při prvním buildu;
hlasové modely si uživatel stahuje přímo v aplikaci (nic není bundlováno v APK).

## Architektura

```
com.hertzds
├── deepseek/      SSE klient, tool-call akumulace, pricing (peak/off-peak)
├── agent/         agentní smyčka, tool registry (web/files/OCR/memory/schedule/time)
├── voice/         system TTS/STT + sherpa-onnx (Piper, Whisper, VAD), downloader
├── work/          WorkManager worker pro naplánované úlohy
├── data/          Room (chaty, zprávy, paměť FTS4, klíče, usage), DataStore nastavení
├── core/crypto/   Android Keystore AES-GCM SecretStore
└── ui/            Compose UI (chat, ghost drawer, klíče, paměť, úlohy, nastavení)
```

---
Licence: MIT
