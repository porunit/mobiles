# Linux char device (символьное устройство)

Модуль ядра, который регистрирует **символьное устройство** — файл в `/dev`, из
которого можно читать/в который писать побайтово. Используется в `[[adr-0002-real-quote-pipeline]]`
для эмулятора биржи (`7-linux-driver`).

> **Итерация 1 (реализовано):** char-device пока эмулируется **user-space-демоном на C**
> (`7-linux-driver/userspace/quote_gen.c`), который пишет **NDJSON в FIFO** на общем
> томе — тот же wire-контракт, без зависимости от ядра хоста. Описанные ниже механики
> kernel-модуля (cdev / sysfs / таймер ядра) — это **шаг 2**.

## Ключевые понятия
- **major/minor** — номер драйвера и номер конкретного устройства. Выделяем
  динамически: `alloc_chrdev_region`.
- **`struct file_operations`** — таблица колбэков (`.read`, `.open`, `.release`). При
  `cat /dev/financial_quotes` ядро вызывает наш `.read`.
- **`cdev`** — объект символьного устройства: `cdev_init` + `cdev_add`.
- **sysfs** (`/sys/kernel/...`) — атрибуты для управления модулем в рантайме
  (старт/стоп, частота). Создаём через `kobject_create_and_add` + `sysfs_create_group`.
- **Таймер ядра** (`timer_setup`, `mod_timer`) — периодическая генерация котировок.
  Снимать `del_timer_sync` перед выгрузкой.

## Подводные камни (из разбора эталона)
- Гонки за глобальным массивом инструментов между таймером и чтением → нужен `mutex`/`rwlock`.
- `sysfs_create_kobject` не существует → правильно `kobject_create_and_add`.
- Деление на ноль при расчёте `change_percent`, если `last_bid == 0`.

## macOS
Модуль грузится только в Linux-ядро. На macOS — внутри LinuxKit-VM Docker Desktop в
privileged-контейнере (`cap_add: SYS_MODULE`). См. `[[adr-0002-real-quote-pipeline]]`
и `docs/ARCHITECTURE.md` §7.
