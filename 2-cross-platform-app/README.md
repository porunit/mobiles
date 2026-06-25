# 2-cross-platform-app — кросс-платформенный клиент (React Native)

**Итерация 2.** Статус: спроектировано, реализация позже.

Кросс-платформенный клиент на React Native + Expo с тем же функционалом, что и
нативный. Подключается к **реальному** шлюзу по каноническому контракту (в эталоне
клиент говорил на «диалекте шлюза» и работал на моках).

## Что делает
- Экраны: auth, рынок, портфель, инструмент, сделка, кошелёк, аналитика, настройки.
- API: `USE_MOCK=false`, запросы к `/api/v1/*` (см. `docs/ARCHITECTURE.md` §4);
  `loginUser→/auth/login {email,password}`, `buy/sell→/orders/* {ticker,quantity}`
  (без `price`), `getStocks→/stocks` (paged), кошелёк → `/wallet/*`.
- JWT в AsyncStorage. Ответы — канонические формы (`{accessToken, user}`).

## Выравнивание с контрактом
Старый `api/*` слой эталона переписывается под единый контракт; моки возвращают
канонические shape'ы. Эндпоинты и поля — только из `docs/ARCHITECTURE.md` §4.

---

## Run (Expo SDK 51, managed workflow, TypeScript)

```bash
npm install        # or: yarn
npx expo start
```

Then press `a` (Android emulator), `i` (iOS simulator), or `w` (web), or scan
the QR code with Expo Go. Type-check with `npm run tsc`.

### API base URL

Resolved by `src/api.ts` in this order:

1. `expo.extra.apiBaseUrl` in `app.json` (set it to override, e.g. a LAN IP for
   a physical device: `"http://192.168.1.50:8080"`).
2. Per-platform default via `Platform.select`:
   - Android emulator: `http://10.0.2.2:8080` (host loopback)
   - iOS simulator / web: `http://localhost:8080`

The client appends `/api/v1` to the base URL.

### Implementation notes

- Money & quantity fields are decimal **strings** on the wire and stay strings
  in TS types (`src/types.ts`); only `Number()`-parsed at the display boundary
  (`src/format.ts`).
- Bearer token lives in memory + AsyncStorage (`src/auth.tsx`); a `401` from the
  gateway clears the session and returns the user to the auth screen.
