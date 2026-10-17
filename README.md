# fffdw
# fffdw

## Запуск клиента

```
run client.bat                # ник из gradle.properties
run client.bat Steve          # свой ник
run client.bat Steve 4G       # ник + heap для игры
```

Лицензия Minecraft **не нужна**: Fabric Loom поднимает игру в dev-режиме,
Fabric Loader сам подставляет фиктивный accessToken, а клиент стартует с
`--offlineDeveloperMode` (офлайн-сессия, без обращений к Mojang, без профиля
и без ключей подписи чата). Играются одиночка, LAN и offline-mode серверы.

Настройки — в `gradle.properties`: `dev_username`, `dev_xmx`,
`dev_offline_dev_mode`. Их можно перекрыть переменными окружения
`GODWEER_USERNAME` / `GODWEER_XMX`. Мир и настройки лежат в `run\`.

Нужна JDK 25. Первый запуск долгий: Loom качает Minecraft, ассеты и нативы.

Эквивалент вручную: `gradlew runClient --console=plain`.

## Скачать готовую джарку

Клиент собирается в GitHub Actions на JDK 25, артефакт называется `GodWeer-Phone`:

https://github.com/alekdlc354-crypto/GodWeer/actions/workflows/build.yml?query=branch%3Aarena%2F105465d2-godweer

Открой последнюю успешную сборку и внизу страницы нажми `GodWeer-Phone` —
скачается zip с `Phone-Sosiski.jar`. Это обычный Fabric-мод: кладёшь в
`.minecraft/mods` рядом с Fabric API и Fabric Loader. Лицензия не нужна,
запускать можно через `run client.bat` или любой лаунчер в офлайн-режиме.
