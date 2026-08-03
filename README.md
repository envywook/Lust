<p align="center">
  <img src="docs/assets/maxspeedvpn-banner.svg" alt="MaxSpeedVPN — Android VPN client" width="100%">
</p>

<p align="center">
  <a href="https://github.com/envywook/Lust/releases"><img src="https://img.shields.io/github/v/release/envywook/Lust?include_prereleases&style=for-the-badge&color=9b6dff" alt="Release"></a>
  <a href="https://github.com/envywook/Lust/actions/workflows/android.yml"><img src="https://img.shields.io/github/actions/workflow/status/envywook/Lust/android.yml?branch=main&style=for-the-badge&label=Android%20CI" alt="Android CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/envywook/Lust?style=for-the-badge&color=6f42c1" alt="GPL-3.0"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
</p>

<p align="center">
  Открытый Android VPN-клиент на Kotlin и Jetpack Compose.<br>
  Подписки, выбор сервера, Xray-core и прозрачная диагностика VPN-сессии.
</p>

<p align="center">
  <a href="https://github.com/envywook/Lust/releases"><strong>Скачать APK</strong></a>
  ·
  <a href="#сборка"><strong>Собрать из исходников</strong></a>
  ·
  <a href="#статус-проекта"><strong>Статус проекта</strong></a>
</p>

---

## О проекте

**MaxSpeedVPN** — Android VPN-клиент с тёмным интерфейсом, управлением подписками и нативным сетевым конвейером на базе Xray-core или sing-box и HEV tun2socks.

Проект создаётся как понятная, расширяемая альтернатива перегруженным универсальным клиентам: обычные действия доступны из основного интерфейса, а технические события и ошибки не скрываются от пользователя.

> [!WARNING]
> **Текущий статус — alpha.** APK предназначен для разработки и тестирования. Проект ещё не прошёл полный аудит безопасности и приёмочные испытания на широком наборе устройств. Текущий release использует debug-подпись.

## Возможности

### Уже реализовано

- тёмный интерфейс на **Jetpack Compose + Material 3**;
- единая главная с подключением, серверами, подписками и сворачиваемым журналом; нижняя навигация из двух разделов;
- добавление, обновление и безопасный deep-link/clipboard импорт подписок;
- versioned export/import резервной копии через системный Android document picker с подтверждением и валидацией;
- Base64 и plain-text списки серверов;
- импорт ссылок **VLESS, VMess, Trojan, Shadowsocks, Hysteria2/Hy2, TUIC, NaiveProxy и Mieru**;
- безопасный `mieru://` deep-link импорт с проверкой версии и подтверждением перед сохранением;
- группы, поиск, избранное, TCP endpoint latency и сортировка серверов;
- трафик, лимит и срок действия из `subscription-userinfo`;
- Android `VpnService`, foreground-уведомление и HEV tun2socks;
- реальное переключение **Xray-core / sing-box 1.13.14 extended** с XHTTP и Naive outbound;
- state machine, crash recovery и fail-closed проверка sing-box-конфигурации;
- простые режимы маршрутизации для обоих ядер: всё через VPN, обход LAN и собственные исключения доменов/IP;
- расширенные MTU, DNS, IPv6, выбор ядра и backup скрыты от основного экрана;
- персистентный журнал с фильтрами, очисткой и экспортом;
- unit-тесты, Android smoke и сборка в GitHub Actions;
- universal APK и уменьшенные APK для четырёх ABI с SHA-256.

### В разработке

- импорт QR-кодов;
- исключение отдельных приложений из VPN;
- дополнительные форматы подписок и sing-box transports;
- quick settings tile и проверка обновлений;
- production signing и широкая device matrix.

## Поддерживаемые форматы

| Формат | Импорт | Xray | sing-box | Статус |
|---|:---:|:---:|:---:|---|
| VLESS | ✅ | ✅ | ✅ | TCP, WS, gRPC; XHTTP — sing-box extended |
| VMess | ✅ | ✅ | ✅ | Alpha |
| Trojan | ✅ | ✅ | ✅ | Alpha |
| Shadowsocks | ✅ | ✅ | ✅ | Alpha |
| Hysteria2 / Hy2 | ✅ | — | ✅ | Нативный sing-box outbound |
| TUIC | ✅ | — | ✅ | Нативный sing-box outbound |
| NaiveProxy | ✅ | — | ✅ | `naive+https://`, Cronet-enabled core |
| Subscription URL | ✅ | — | — | Base64 или plain text |
| sing-box runtime | — | — | ✅ | 1.13.14 extended, pinned source и SHA-256 |

> Поддержка формата ссылки не означает совместимость со всеми комбинациями transport/security. Перед использованием проверяйте конкретный профиль и журнал подключения.

## Архитектура

```text
┌───────────────────────────── LUST ──────────────────────────────┐
│                                                                 │
│  Jetpack Compose UI                                             │
│     │                                                           │
│     ├── SubscriptionRepository → Parser → Xray JSON             │
│     │                                                           │
│     └── DualCoreVpnService                                      │
│             │                                                   │
│             ▼                                                   │
│       VpnSessionCoordinator ───────────────→ Persistent AppLog  │
│             │                                  ▲                │
│             ├── XrayEngine ────────────────────┤                │
│             │       ▲                          │                │
│             └── Android TUN → HEV tun2socks ──┘                │
│                                   │                             │
│                                   └──→ SOCKS 127.0.0.1:10808    │
│                                                │                │
│                                                └──→ Xray → сеть │
└─────────────────────────────────────────────────────────────────┘
```

### Поток подключения

```text
Профиль подписки
      │
      ▼
SubscriptionParser → Xray JSON с локальным SOCKS inbound
      │
      ▼
DualCoreVpnService → VpnSessionCoordinator
      │
      ├── Android VpnService создаёт TUN
      ├── HEV tun2socks получает TUN FD
      └── XrayEngine запускает локальный SOCKS и outbound

Android traffic → TUN → HEV → SOCKS 127.0.0.1:10808 → Xray → сеть
```

## Скачать

Готовые сборки и описание изменений публикуются на странице [GitHub Releases](https://github.com/envywook/Lust/releases). Полная история — в [CHANGELOG.md](CHANGELOG.md).

Выбирайте файл по архитектуре устройства:

| APK | Для кого | Размер debug-сборки |
|---|---|---:|
| `arm64-v8a` | большинство современных телефонов | ~49 MiB |
| `armeabi-v7a` | старые 32-битные ARM-устройства | ~50 MiB |
| `x86_64` | эмуляторы и редкие x86_64-устройства | ~51 MiB |
| `x86` | старые x86-устройства/эмуляторы | ~52 MiB |
| `universal` | если архитектура неизвестна | ~151 MiB |

ABI-specific APK примерно на 65–68% меньше universal. Для каждого релиза публикуется `SHA256SUMS.txt`; проверяйте checksum перед установкой. Android может предупреждать об установке приложения вне магазина — это нормально для GitHub-сборки.

## Сборка

### Требования

| Компонент | Версия/требование |
|---|---|
| JDK | 17 |
| Android SDK | 34 |
| Gradle wrapper | 8.5 |
| Android Gradle Plugin | 8.2.2 |
| Kotlin | 1.9.22 |
| CLI | `curl`, `unzip`, `readelf`, `strings` |

### Команды

```bash
git clone https://github.com/envywook/Lust.git MaxSpeedVPN
cd MaxSpeedVPN

./scripts/prepare-native-deps.sh
./gradlew testDebugUnitTest assembleDebug
```

APK появятся здесь:

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
app/build/outputs/apk/debug/app-x86_64-debug.apk
app/build/outputs/apk/debug/app-x86-debug.apk
app/build/outputs/apk/debug/app-universal-debug.apk
```

Нативные бинарники не хранятся в Git. `prepare-native-deps.sh` загружает закреплённые версии и проверяет SHA-256:

- `AndroidLibXrayLite v26.7.19`;
- HEV-библиотеки из официальных APK v2rayNG `2.2.6`.

## CI/CD

Workflow [Android CI and Release](.github/workflows/android.yml):

| Событие | Результат |
|---|---|
| Push в `main` | unit-тесты, сборка APK, CI artifact |
| Pull request | unit-тесты и сборка |
| Тег `v*` | тесты, сборка и GitHub pre-release с APK и SHA-256 |
| Ручной запуск | тесты и CI artifact |

[![Android CI](https://github.com/envywook/Lust/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/envywook/Lust/actions/workflows/android.yml)

## Статус проекта

| Подсистема | Состояние |
|---|---|
| UI shell | 🟡 Базовые пять разделов готовы |
| Подписки и выбор сервера | 🟡 Реализовано, расширяется совместимость |
| Xray runtime | 🟡 Интегрирован, продолжается device acceptance |
| HEV tun2socks | 🟡 JNI подключён, startup pipeline стабилизируется |
| Персистентная диагностика | 🟢 Работает |
| sing-box runtime | ⚪ Запланирован |
| Production signing | ⚪ Не настроен |
| Security review | ⚪ Не завершён |

## Безопасность

- Не публикуйте subscription URL, UUID, ключи REALITY и другие секреты в issues или логах.
- Не устанавливайте APK из непроверенных зеркал.
- Уязвимости отправляйте по инструкции в [SECURITY.md](SECURITY.md), а не через публичный issue.
- Правила использования сторонних компонентов перечислены в [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Участие в разработке

Issues и pull requests приветствуются. Для отчёта об ошибке приложите:

1. модель устройства и версию Android;
2. версию MaxSpeedVPN;
3. тип профиля без секретных параметров;
4. точную последовательность действий;
5. обезличенный фрагмент журнала.

Перед pull request выполните:

```bash
./gradlew testDebugUnitTest assembleDebug
```

## Лицензия

Исходный код MaxSpeedVPN распространяется по лицензии [GNU GPL-3.0](LICENSE).
Сторонние компоненты сохраняют собственные лицензии — см. [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

<p align="center">
  <strong>MaxSpeedVPN</strong> · Android · Kotlin · Xray-core · sing-box · HEV tun2socks
</p>
