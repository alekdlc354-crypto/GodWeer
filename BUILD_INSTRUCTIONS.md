# 📦 Инструкция по сборке мода

## Требования

- **Java Development Kit (JDK) 21** или выше
- Интернет-соединение для загрузки зависимостей

## Установка JDK 21

### Windows:
1. Скачайте JDK 21 с [Adoptium](https://adoptium.net/temurin/releases/?version=21)
2. Установите JDK
3. Убедитесь, что `JAVA_HOME` установлен правильно

### Linux (Ubuntu/Debian):
```bash
sudo apt update
sudo apt install openjdk-21-jdk
```

### Linux (Fedora/RHEL):
```bash
sudo dnf install java-21-openjdk-devel
```

### macOS:
```bash
brew install openjdk@21
```

## Сборка мода

### Способ 1: Использование Gradle Wrapper (рекомендуется)

1. Откройте терминал/командную строку в папке проекта `GodWeer`

2. **Windows:**
   ```cmd
   gradlew.bat build
   ```

3. **Linux/macOS:**
   ```bash
   chmod +x gradlew
   ./gradlew build
   ```

### Способ 2: Использование системного Gradle

Если у вас установлен Gradle:
```bash
gradle build
```

## Результат сборки

После успешной сборки JAR файл будет находиться в:
```
build/libs/Phone-Sosiski.jar
```

## Установка мода

1. Установите **Fabric Loader** для Minecraft 26.3
   - Скачайте с [fabricmc.net](https://fabricmc.net/use/)

2. Установите **Fabric API** для Minecraft 26.3
   - Скачайте с [Modrinth](https://modrinth.com/mod/fabric-api) или [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fabric-api)

3. Скопируйте `Phone-Sosiski.jar` в папку mods:
   - **Windows:** `%appdata%\.minecraft\mods`
   - **Linux:** `~/.minecraft/mods`
   - **macOS:** `~/Library/Application Support/minecraft/mods`

4. Запустите Minecraft с Fabric профилем

## Возможные проблемы

### Ошибка: "JAVA_HOME is not set"
**Решение:** Установите переменную окружения JAVA_HOME:

**Windows:**
```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21
```

**Linux/macOS:**
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
```

### Ошибка: "Could not resolve dependencies"
**Решение:** Проверьте интернет-соединение и повторите сборку

### Ошибка компиляции
**Решение:** Убедитесь, что используете JDK 21 или выше:
```bash
java -version
```

## Включение новых эффектов в игре

1. Запустите Minecraft с модом
2. Откройте ClickGUI (обычно правый Shift)
3. Найдите модули в категории **Render**:
   - **Trails** - красивые следы
   - **PopEffect** - эффекты ударов/убийств/тотемов
4. Включите модули и настройте их под свой вкус

## Рекомендации по настройке

### Для Trails:
- Mode: **Rainbow** или **Aurora**
- Trail Length: **25-35**
- Brightness: **4.0-6.0**
- Включите **Spiral Mode** для красивых спиральных следов

### Для PopEffect:
- Hit Effect: **Explosion**
- Kill Effect: **Beam**
- Totem Effect: **Spiral**
- Brightness: **4.0-5.0**
- Включите **Through Walls** для видимости сквозь стены

## Поддержка

Если у вас возникли проблемы:
1. Проверьте логи в `.minecraft/logs/latest.log`
2. Убедитесь, что все зависимости установлены
3. Проверьте версию Minecraft (должна быть 26.3)

---

**Приятной игры! 🎮✨**
