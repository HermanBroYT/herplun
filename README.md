# HerPlun 1.3

Плагин для Minecraft-серверов: баны, муты, жалобы с меню, привилегии,
технические работы, анти-чит, проверка на читы, `/gm`,
**меню как в DeluxeMenus** и поддержка **WorldEdit / WorldGuard**.

---

## Поддерживаемые версии

| Ядро | Версии Minecraft |
|---|---|
| **Paper** (и форки: Purpur и т.п.) | **1.16.x – 1.21.x** |
| **Spigot** | **1.20.x** |

Плагин сам напишет предупреждение в консоль, если запущен на неподдерживаемой версии.
Работает на Java, которая нужна вашему серверу (8–21).

> ⚠️ Версия 1.3 **не работает на 1.12.2–1.15**. Если у вас старая версия сервера,
> используйте HerPlun 1.2.

---

## Что нового в 1.3

1. **Меню как в DeluxeMenus** — свои меню в папке `plugins/HerPlun/menus/`.
2. **WorldEdit и WorldGuard** — топорик `/herplun axe`, справка `/herplun we`,
   сообщение о приватной территории при попытке сломать блок.
3. Версии: Paper 1.16–1.21 и Spigot 1.20.
4. Цвета HEX в меню и настройках: `&#RRGGBB`.

---

## Как обновиться с 1.2

1. Остановите сервер.
2. Замените `HerPlun.jar` в папке `plugins` на новый.
3. Запустите сервер.

Баны, муты, жалобы и настройки сохраняются. При первом запуске создастся папка
`plugins/HerPlun/menus/` с двумя примерами. Новые пункты `config.yml` подхватываются
сами; чтобы увидеть их в файле, сохраните копию старого `config.yml` и удалите его.

---

## Меню (как DeluxeMenus)

### Команды

Можно писать `/herplun dm ...`, `/deluxemenus ...` или `/dm ...`.

| Команда | Что делает | Право |
|---|---|---|
| `/herplun dm help` | Список команд меню | `herplun.dm.help` |
| `/herplun dm list` | Меню, к которым у вас есть доступ (названия кликабельны) | `herplun.dm.list` |
| `/herplun dm open <меню>` | Открыть меню себе | `herplun.dm.open` |
| `/herplun dm open <меню> <игрок>` | Открыть меню другому игроку | `herplun.dm.open.others` |
| `/herplun dm reload` | Обновить меню из папки `menus` | `herplun.dm.reload` |

У каждого меню может быть своё право (`permission` в файле меню). Меню, на которое
у вас нет права, не показывается в `list` и не открывается.
При `open <меню> <игрок>` проверка права меню пропускается (открывает администратор).

### Как сделать своё меню

1. Создайте файл `plugins/HerPlun/menus/shop.yml` (имя файла = имя меню).
2. Заполните по образцу `example.yml`.
3. Введите `/herplun dm reload`.

```yaml
menu_title: "&6Магазин"
size: 27                  # 9, 18, 27, 36, 45 или 54
open_command: shop        # /shop откроет это меню (можно списком)
permission: herplun.menu.shop   # необязательно
open_commands: []         # действия при открытии
close_commands: []        # действия при закрытии
fill_item:                # чем заполнить пустые слоты (необязательно)
  material: GRAY_STAINED_GLASS_PANE
  display_name: " "
items:
  apple:
    slot: 13              # или slots: ["0-8", 18]
    material: APPLE
    amount: 1
    display_name: "&cЯблоко"
    lore:
      - "&7Привет, %player_name%!"
    glow: true            # блеск
    priority: 0           # меньше число — выше приоритет при совпадении слотов
    permission: herplun.menu.shop.apple      # кто ВИДИТ предмет
    click_permission: herplun.menu.shop.buy  # кто может НАЖИМАТЬ
    no_permission_message: "&cНет доступа"
    left_click_commands:
      - "[console] give %player_name% apple 1"
      - "[message] &aГотово!"
    right_click_commands: []
    shift_left_click_commands: []
    shift_right_click_commands: []
    middle_click_commands: []
    click_commands: []    # любой клик, если для него нет своего списка
```

Для головы игрока: `material: PLAYER_HEAD` и `skull_owner: "%player_name%"`.

### Действия

| Действие | Что делает |
|---|---|
| `[player] команда` | Игрок вводит команду |
| `[console] команда` | Консоль выполняет команду |
| `[message] текст` | Сообщение игроку |
| `[broadcast] текст` | Сообщение всем |
| `[chat] текст` | Игрок пишет в чат |
| `[close]` | Закрыть меню |
| `[open] меню` | Открыть другое меню |
| `[sound] ключ [громкость] [тон]` | Звук, например `block.chest.open 1 1` |
| `[title] заголовок;подзаголовок` | Надпись на экране |

Команды пишите без `/` или с одним `/`. Команды WorldEdit пишутся так:
`//pos1` (две косые черты).

### Переменные

`%player_name%`, `%player_displayname%`, `%player_world%`, `%server_online%`,
`%server_max%`, `{player}`. Если установлен **PlaceholderAPI**, работают и его переменные.

> ⚠️ **Внимание!** Ошибка в отступах (пробелах) YAML ломает меню. Команда
> `[console]` выполняется с правами консоли — давайте доступ к файлам меню
> только администраторам. После правки пишите `/herplun dm reload`: ошибки
> покажутся в чате и в консоли.

---

## WorldEdit и WorldGuard

HerPlun **не заменяет** эти плагины: все их команды работают как раньше.
Должны быть установлены сами **WorldEdit** (или FastAsyncWorldEdit) и **WorldGuard**.

| Команда | Что делает | Право |
|---|---|---|
| `/herplun axe` | Выдаёт **топорик** (деревянный топор — палочка выделения WorldEdit) | `herplun.axe` |
| `/herplun we` | Справка по основным командам WorldEdit и WorldGuard | `herplun.axe` |

Топорик: **ЛКМ по блоку** — точка 1, **ПКМ по блоку** — точка 2. Для выделения у игрока
должны быть права WorldEdit (например `worldedit.selection.*`).

Приват:
1. Выделите область топориком.
2. Введите `/rg claim <имя>` (нужно право WorldGuard `worldguard.region.claim`)
   или `/rg define <имя>` (для админов).
3. Добавить игрока: `/rg addmember <имя> <игрок>`.

Готовое меню с кнопками для этих команд: `/herplun dm open tools` (или `/tools`).

### Сообщение о приватной территории

Если игрок пытается сломать блок на чужой приватной территории WorldGuard,
он увидит:

> **Вы не сможете сломать, так как он за приватность территории игрока**

Администраторы с правом `worldguard.region.bypass.<мир>` ломать могут.
Текст и включение — в `config.yml` (`messages.region-break`, `worldguard.custom-message`).

---

## Остальные команды

| Команда | Что делает | Право |
|---|---|---|
| `/ban <игрок> [причина]` | Бан навсегда | `herplun.ban` |
| `/unban <игрок>` | Разбан | `herplun.unban` |
| `/timeban <игрок> <время> [причина]` | Временный бан | `herplun.timeban` |
| `/mute <игрок> [время]` | Мут (без времени — навсегда) | `herplun.mute` |
| `/unmute <игрок>` | Снять мут | `herplun.unmute` |
| `/help` | Список команд (только доступные вам) | `herplun.help` |
| `/report <игрок> <причина>` | Подать жалобу | `herplun.report` |
| `/report cancel <номер>` | Отменить свою жалобу | `herplun.report` |
| `/report open` | Меню жалоб | `herplun.open` |
| `/voice` | Ссылка на голосовой чат | `herplun.voice` |
| `/check on <игрок> <время>` | Вызвать на проверку читов | `herplun.check.on` |
| `/check off [игрок]` | Завершить проверку | `herplun.check.off` |
| `/check tp` | Телепорт к проверяемому | `herplun.check.tp` |
| `/check time <время>` | Продлить проверку | `herplun.check.time` |
| `/gm <режим> [игрок]` | Режим игры | `herplun.gm` |
| `/gamemode <режим> [игрок]` | Режим игры | `herplun.gamemode` |
| `/herplun privilege set/addtemp/remove ...` | Привилегии (нужен LuckPerms) | `herplun.privilege` |
| `/herplun technical on/off` | Технические работы | `herplun.technical` |
| `/herplun reload` | Перезагрузить настройки | OP или `herplun.technical` |

**Время:** `s`, `m`, `h`, `d`, `w` (`30m`, `12h`, `1d2h30m`).
Если права нет: `"HerPlun" У вас нет прав что прописать команду!`

---

## Все права

```
herplun.ban            herplun.unban          herplun.timeban
herplun.mute           herplun.unmute         herplun.help
herplun.report         herplun.open           herplun.voice
herplun.privilege      herplun.technical      herplun.gm
herplun.gamemode       herplun.anti-cheat     herplun.axe
herplun.dm.help        herplun.dm.list        herplun.dm.open
herplun.dm.open.others herplun.dm.reload
herplun.check.on       herplun.check.off      herplun.check.tp
herplun.check.time
```

По умолчанию всем доступны: `herplun.help`, `herplun.report`, `herplun.open`,
`herplun.voice`, `herplun.dm.help`, `herplun.dm.list`, `herplun.dm.open`.
Остальное — только OP.

---

## Анти-чит

Сообщения видят игроки с правом `herplun.anti-cheat`. Плагин **только предупреждает**
и никого не наказывает. Проверки: Fly, Speed, Reach, KillAura.

> ⚠️ Это простой анти-чит, возможны **ложные срабатывания**. Перед наказанием
> проверяйте игрока через `/check on`. Пороги — в разделе `anticheat` файла `config.yml`.

---

## Файлы (`plugins/HerPlun/`)

| Файл / папка | Что хранит |
|---|---|
| `config.yml` | Настройки |
| `menus/` | Меню (`*.yml`) |
| `bans.yml`, `mutes.yml`, `reports.yml` | Баны, муты, жалобы |
| `state.yml` | Технические работы |

> ⚠️ **Внимание!** Ошибка в `config.yml` (лишний пробел, неверный отступ, пропущенная
> кавычка) может сломать плагин, а в худшем случае привести к **краху сервера**.
> Перед правкой сделайте копию. Если что-то сломалось — удалите `config.yml`,
> он создастся заново.

---

## Возможные конфликты

Команды `/ban`, `/unban`, `/mute`, `/help`, `/gamemode` есть в других плагинах и в игре.
Если срабатывает не та, пишите `/herplun:ban`, `/herplun:gamemode` и так далее.
Если на сервере уже стоит настоящий DeluxeMenus, команда `/dm` может принадлежать ему —
используйте `/deluxemenus` или `/herplun dm`. Команды вида `open_command` в меню HerPlun
имеют приоритет над другими плагинами.

---

## Сборка .jar через GitHub

1. Загрузите все файлы проекта в репозиторий GitHub.
2. Вкладка **Actions** → **Build HerPlun** → последний запуск.
3. Внизу в **Artifacts** скачайте `HerPlun`. Внутри лежит `HerPlun.jar`.
