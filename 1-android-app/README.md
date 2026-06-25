# 1-android-app — нативный терминал (Kotlin + Compose)

**Итерация 1.** Статус: спроектировано (UI можно заимствовать из эталона), сетевой слой
не реализован.

Мобильный терминал клиента. UI на Jetpack Compose + **реальный сетевой слой** и MVVM
(в эталоне сети не было — всё было замокано).

## Что делает
- Экраны: авторизация/регистрация, портфель, рынок, детали инструмента, сделка,
  аналитика, профиль, кошелёк.
- Сеть: Retrofit/OkHttp к шлюзу `http://10.0.2.2:8080/api/v1` (эмулятор) по
  каноническому контракту (`docs/ARCHITECTURE.md` §4).
- MVVM: ViewModel + Repository; токен в DataStore; авто-refresh на `401` (итер. 2).
- `BigDecimal` сериализуется как строка (кастомный адаптер).

## Архитектура
`ui/` (Compose, экраны, тема) · `data/` (Retrofit API, DTO, репозитории) ·
`domain/` (модели) · `viewmodel/`. DI — Hilt или ручной.

## Слои
UI → ViewModel → Repository → Retrofit → шлюз. Никакой бизнес-логики на клиенте, кроме
форматирования и состояния экрана.

---

## Implementation notes (current build)

Single-module Android app (`:app`, namespace `com.rmp.trader`) implementing the
contract above.

### Stack
- Kotlin 1.9.25, AGP 8.5.2, `compileSdk 34`, `minSdk 26`
- Jetpack Compose (Material 3), Compose BOM 2024.09.00
- Retrofit + OkHttp + Moshi (Kotlin), Coroutines
- ViewModel + StateFlow (MVVM), Navigation-Compose
- Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`)

### Configuration
API base is exposed via `BuildConfig.BASE_URL` (a `buildConfigField` in
`app/build.gradle.kts`), default `http://10.0.2.2:8080` (emulator → host). All
calls go under `/api/v1`. Cleartext HTTP is enabled for dev via
`android:usesCleartextTraffic="true"`.

### Building & running
This project does **not** ship `gradle-wrapper.jar` (binary). Open the
`1-android-app/` folder in Android Studio (it provides the wrapper and syncs),
or with a system Gradle 8.9+ run:

```bash
cd 1-android-app
gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug
```

Run on an emulator (so `10.0.2.2` resolves to the host) with the gateway up.

### Money handling
Money/quantity fields arrive as decimal strings (e.g. `"217.34626900"`); DTO
fields are `String` and parsed via `BigDecimal` (`ui/Money.kt`) — never float.

### Screens
Auth (login/register, persisted token) · Quotes (auto-refresh ~2s, tap to trade)
· Portfolio (cash, positions, total value/P&L, refresh) · Trade (BUY/SELL market
orders + deposit). Bottom nav switches Quotes/Portfolio/Trade; top-bar logout
clears the session; a `401` on any protected call returns to Auth.
