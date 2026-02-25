#!/usr/bin/env python3
"""
Minerva Project Documentation PDF Generator
Generates A4 PDF with Times New Roman (or DejaVu Serif fallback), 12pt, 1.5 line spacing
"""

import os
from fpdf import FPDF

class MinervaDoc(FPDF):
    def __init__(self):
        super().__init__(orientation='P', unit='mm', format='A4')
        # Try to load DejaVu Serif (supports Cyrillic)
        font_dir = '/usr/share/fonts/truetype/dejavu/'
        self.add_font('DejaVu', '', os.path.join(font_dir, 'DejaVuSerif.ttf'))
        self.add_font('DejaVu', 'B', os.path.join(font_dir, 'DejaVuSerif-Bold.ttf'))
        self.add_font('DejaVu', 'I', os.path.join(font_dir, 'DejaVuSerif-Italic.ttf'))
        self.add_font('DejaVu', 'BI', os.path.join(font_dir, 'DejaVuSerif-BoldItalic.ttf'))
        self.font_name = 'DejaVu'
        self.set_auto_page_break(auto=True, margin=25)
        # Line height for 12pt with 1.5 spacing = 12 * 1.5 * 0.3528 mm ≈ 6.35mm
        self.line_h = 6.35

    def header(self):
        if self.page_no() > 1:
            self.set_font(self.font_name, 'I', 9)
            self.set_text_color(128, 128, 128)
            self.set_x(self.l_margin)
            self.cell(self.epw, 8, 'Minerva – Децентрализирана P2P платформа за споделяне на музика', align='C')
            self.set_xy(self.l_margin, self.t_margin + 10)
            self.set_text_color(0, 0, 0)

    def footer(self):
        self.set_y(-20)
        self.set_font(self.font_name, 'I', 9)
        self.set_text_color(128, 128, 128)
        self.cell(0, 10, f'Страница {self.page_no()}', align='C')
        self.set_text_color(0, 0, 0)

    def section_title(self, num, title):
        self.set_font(self.font_name, 'B', 14)
        self.ln(4)
        self.set_x(self.l_margin)
        self.multi_cell(0, 8, f'{num}. {title}', new_x='LMARGIN', new_y='NEXT')
        self.ln(2)
        self.set_font(self.font_name, '', 12)

    def subsection_title(self, num, title):
        self.set_font(self.font_name, 'B', 12)
        self.ln(2)
        self.set_x(self.l_margin)
        self.multi_cell(0, self.line_h, f'{num} {title}', new_x='LMARGIN', new_y='NEXT')
        self.ln(1)
        self.set_font(self.font_name, '', 12)

    def body_text(self, text):
        self.set_font(self.font_name, '', 12)
        self.set_x(self.l_margin)
        self.multi_cell(0, self.line_h, text, new_x='LMARGIN', new_y='NEXT')
        self.ln(1)

    def bold_text(self, text):
        self.set_font(self.font_name, 'B', 12)
        self.set_x(self.l_margin)
        self.multi_cell(0, self.line_h, text, new_x='LMARGIN', new_y='NEXT')
        self.set_font(self.font_name, '', 12)

    def italic_text(self, text):
        self.set_font(self.font_name, 'I', 12)
        self.set_x(self.l_margin)
        self.multi_cell(0, self.line_h, text, new_x='LMARGIN', new_y='NEXT')
        self.set_font(self.font_name, '', 12)

    def bullet(self, text, indent=10):
        self.set_x(self.l_margin + indent)
        self.set_font(self.font_name, '', 12)
        w = self.epw - indent
        self.multi_cell(w, self.line_h, f'• {text}', new_x='LMARGIN', new_y='NEXT')

    def numbered_item(self, num, text, indent=10):
        self.set_x(self.l_margin + indent)
        self.set_font(self.font_name, '', 12)
        w = self.epw - indent
        self.multi_cell(w, self.line_h, f'{num}. {text}', new_x='LMARGIN', new_y='NEXT')

    def bold_inline(self, label, value):
        self.set_x(self.l_margin)
        self.set_font(self.font_name, 'B', 12)
        lw = self.get_string_width(label)
        self.cell(lw, self.line_h, label)
        self.set_font(self.font_name, '', 12)
        self.multi_cell(0, self.line_h, value, new_x='LMARGIN', new_y='NEXT')


def generate():
    pdf = MinervaDoc()
    pdf.set_margins(25, 25, 25)

    # ===== TITLE PAGE =====
    pdf.add_page()
    pdf.ln(50)
    pdf.set_font(pdf.font_name, 'B', 24)
    pdf.multi_cell(0, 12, 'MINERVA', align='C', new_x='LMARGIN', new_y='NEXT')
    pdf.ln(5)
    pdf.set_font(pdf.font_name, '', 16)
    pdf.multi_cell(0, 9, 'Децентрализирана peer-to-peer платформа\nза споделяне и стрийминг на музика', align='C', new_x='LMARGIN', new_y='NEXT')
    pdf.ln(15)
    pdf.set_font(pdf.font_name, '', 13)
    pdf.multi_cell(0, 8, 'Проектна документация', align='C', new_x='LMARGIN', new_y='NEXT')
    pdf.ln(30)
    pdf.set_font(pdf.font_name, '', 12)
    pdf.multi_cell(0, pdf.line_h, '2025/2026 г.', align='C', new_x='LMARGIN', new_y='NEXT')

    # ===== 1. ТЕМА =====
    pdf.add_page()
    pdf.section_title('1', 'ТЕМА')
    pdf.body_text(
        'Minerva – Децентрализирана peer-to-peer (P2P) платформа за споделяне и стрийминг на музика.'
    )
    pdf.body_text(
        'Проектът представлява настолно приложение, което комбинира BitTorrent технология '
        'за децентрализирано разпределение на музикални файлове с модерен потребителски интерфейс '
        'за управление на музикална библиотека, търсене в мрежата от равноправни възли (peers), '
        'стрийминг на аудио и управление на плейлисти.'
    )

    # ===== 2. АВТОРИ =====
    pdf.section_title('2', 'АВТОРИ')
    pdf.body_text('(Информацията за авторите е пропусната по съображения за поверителност. '
                  'Моля попълнете: три имена, ЕГН, адрес, телефон, имейл, училище, клас.)')

    # ===== 3. РЪКОВОДИТЕЛ =====
    pdf.section_title('3', 'РЪКОВОДИТЕЛ')
    pdf.body_text('(Моля попълнете: три имена, телефон, имейл, длъжност.)')

    # ===== 4. РЕЗЮМЕ =====
    pdf.section_title('4', 'РЕЗЮМЕ')

    # --- 4.1 Цели ---
    pdf.subsection_title('4.1.', 'Цели')
    pdf.bold_text('Предназначение:')
    pdf.body_text(
        'Minerva е създадена с цел да предостави на потребителите средство за споделяне, '
        'откриване и слушане на музика чрез децентрализирана мрежа, без нужда от централен '
        'сървър. Всеки потребител е едновременно клиент и сървър – споделя музика от своята '
        'библиотека и може да открива и изтегля музика от други участници в мрежата.'
    )
    pdf.ln(2)
    pdf.bold_text('Анализ на потребностите:')
    pdf.body_text(
        'Съвременните платформи за музикален стрийминг (Spotify, Apple Music, YouTube Music) '
        'са централизирани – зависят от корпоративни сървъри, изискват абонамент и налагат '
        'ограничения върху наличното съдържание. Потребителите нямат контрол върху своите данни '
        'и библиотеки. Необходимо е решение, което дава пълен контрол на потребителя и позволява '
        'свободно споделяне на музика в рамките на самоорганизираща се мрежа.'
    )
    pdf.ln(2)
    pdf.bold_text('Анализ на съществуващите решения:')
    pdf.bullet('BitTorrent клиенти (qBittorrent, Transmission) – мощни за файлов трансфер, но '
               'нямат музикален интерфейс, търсене по ключови думи или стрийминг.')
    pdf.bullet('Децентрализирани музикални проекти (Audius, Funkwhale) – или са базирани на '
               'блокчейн (бавни, скъпи), или изискват централен сървър за федерация.')
    pdf.bullet('Класически P2P (Napster, LimeWire, Soulseek) – липса на модерен интерфейс, '
               'слабо структуриране на метаданни, изоставени проекти.')
    pdf.body_text(
        'Minerva запълва тази ниша, като комбинира изпитания BitTorrent протокол с DHT-базирано '
        'търсене по ключови думи, директен TCP протокол между Minerva възли и модерен Electron '
        'интерфейс за пълноценно музикално изживяване.'
    )

    # --- 4.2 Основни етапи ---
    pdf.subsection_title('4.2.', 'Основни етапи в реализирането на проекта')
    pdf.bold_text('Етап 1: Проектиране на архитектурата')
    pdf.body_text(
        'Дефиниране на модулната структура: Java бекенд (REST API + BitTorrent двигател + DHT мрежа), '
        'Electron фронтенд (потребителски интерфейс), комуникационен протокол между възлите (MINERVA1).'
    )
    pdf.bold_text('Етап 2: Реализация на ядрото')
    pdf.body_text(
        'Имплементиране на торент мениджъра (JLibTorrentManager) за сийдване, изтегляне и управление на '
        'торенти чрез библиотеката jlibtorrent. Създаване на LibraryManager за управление на локалната '
        'библиотека, метаданни и файлова структура.'
    )
    pdf.bold_text('Етап 3: DHT и мрежово търсене')
    pdf.body_text(
        'Разработка на DHTKeywordManager за обявяване и търсене на ключови думи чрез DHT. '
        'Имплементиране на протокола MINERVA1 – TCP сървър (KeywordSearchServer) и клиент '
        '(KeywordSearchClient) за директна комуникация между Minerva възли.'
    )
    pdf.bold_text('Етап 4: REST API и бекенд интеграция')
    pdf.body_text(
        'Създаване на BackendServer с Javalin framework – 25+ REST ендпойнта за стрийминг, търсене, '
        'качване, изтегляне, управление на плейлисти и мониторинг на трансфери.'
    )
    pdf.bold_text('Етап 5: Потребителски интерфейс')
    pdf.body_text(
        'Разработка на Electron приложение с модерен дизайн – начална страница, библиотека с грид изглед, '
        'детайлен изглед на албуми, търсене в мрежата (Discover), мониторинг на изтеглянията (Downloads), '
        'upload wizard, плейлисти и аудио плейър.'
    )
    pdf.bold_text('Етап 6: Тестване и оптимизация')
    pdf.body_text(
        'Тестване с множество инстанции чрез Docker Compose и ръчно тестване с изолирани директории. '
        'Отстраняване на проблеми с peer discovery, торент сийдване, конкурентен достъп и native сегфолтове.'
    )

    # --- 4.3 Ниво на сложност ---
    pdf.subsection_title('4.3.', 'Ниво на сложност на проекта')
    pdf.body_text(
        'Проектът е с високо ниво на сложност поради комбинацията от множество технологии и '
        'необходимостта от коректна синхронизация между тях:'
    )
    pdf.ln(1)
    pdf.bold_text('Проблем 1: DHT-базирано търсене по ключови думи')
    pdf.body_text(
        'BitTorrent DHT е проектиран за намиране на peers по infohash, не по ключови думи. Създаден е '
        'оригинален подход: всяка ключова дума се хешира (SHA-1) с наставка ".minerva" до детерминиран infohash, '
        'който се обявява в DHT. Така DHT се превръща в разпределена хеш-таблица за keyword→peers mapping.'
    )
    pdf.bold_text('Проблем 2: Разпознаване на Minerva възли от стандартни BitTorrent peers')
    pdf.body_text(
        'Тъй като Minerva използва стандартен DHT, обикновени BitTorrent клиенти могат да се свържат. '
        'Създаден е TCP хендшейк протокол "MINERVA1", който позволява разпознаване на Minerva възли – '
        'само peers, отговарящи с правилния хендшейк, се заявяват за ключови думи.'
    )
    pdf.bold_text('Проблем 3: Конкурентен достъп до native библиотека')
    pdf.body_text(
        'jlibtorrent използва native C++ код чрез SWIG bindings. Едновременен достъп от множество нишки '
        'води до сегментационни грешки (SIGSEGV). Решението включва ReentrantReadWriteLock за всички '
        'SessionManager операции и отложено пресийдване на импортирани торенти до рестартиране.'
    )
    pdf.bold_text('Проблем 4: Създаване на валидни .torrent файлове')
    pdf.body_text(
        'Генерирането на .torrent файлове изисква правилно bencode кодиране с валидни piece hashes. '
        'Използвана е комбинация от ttorrent за създаване при качване и TorrentInfo.bencode() от '
        'jlibtorrent за запис при изтегляне от мрежата.'
    )
    pdf.bold_text('Проблем 5: Peer свързване между локални инстанции')
    pdf.body_text(
        'TorrentHandle.connectPeer() не е наличен в Java обвивката на jlibtorrent 2.0.11.0. '
        'Намерено е решение чрез директно извикване на SWIG слоя: '
        'th.swig().connect_peer(new tcp_endpoint(address.from_string(host), port)).'
    )
    pdf.bold_text('Проблем 6: Дедупликация на резултати от търсенето')
    pdf.body_text(
        'Търсенето в мрежата връща резултати от множество peers. Дедупликацията само по torrentHash '
        'губи песни от различни албуми. Имплементиран е композитен ключ torrentHash|title за '
        'прецизна дедупликация, запазваща разнообразието на резултатите.'
    )

    # --- 4.4 Логическо и функционално описание ---
    pdf.subsection_title('4.4.', 'Логическо и функционално описание на решението')
    pdf.ln(1)
    pdf.bold_text('Обща архитектура:')
    pdf.body_text(
        'Minerva следва двуслойна архитектура: Java бекенд сървър (REST API + P2P двигател) и '
        'Electron фронтенд (настолно приложение). Комуникацията между тях е чрез HTTP REST API '
        'на localhost. P2P комуникацията с други Minerva възли е чрез BitTorrent протокол (UDP/TCP) '
        'и MINERVA1 TCP протокол.'
    )
    pdf.ln(2)

    pdf.bold_text('Модул 1: JLibTorrentManager (Торент двигател)')
    pdf.body_text(
        'Отговорност: Управление на libtorrent сесията – сийдване, изтегляне, DHT комуникация.'
    )
    pdf.bullet('Singleton инстанция с ReentrantReadWriteLock за нишкова безопасност')
    pdf.bullet('Конфигурация: настройваем порт, DHT bootstrap възли, 250 конекции, 100 активни seeds')
    pdf.bullet('seedTorrent() – добавя торент за сийдване с forceRecheck за верификация')
    pdf.bullet('seedMagnet() – резолва magnet URI, изтегля метаданни, стартира изтегляне')
    pdf.bullet('Alert handler – следи TORRENT_FINISHED, PEER_CONNECT, PEER_DISCONNECTED')
    pdf.bullet('saveTorrentFile() – записва .torrent файл чрез bencode сериализация')
    pdf.ln(2)

    pdf.bold_text('Модул 2: LibraryManager (Управление на библиотеката)')
    pdf.body_text(
        'Отговорност: CRUD операции върху музикалната библиотека, метаданни, файлова структура.'
    )
    pdf.bullet('Файлова структура: library/{Артист}/{Албум}/{DD_TT_Заглавие.ext}')
    pdf.bullet('Метаданни: JSON файлове в torrents/ директория (TorrentMetadata)')
    pdf.bullet('loadLibraryFromTorrents() – зарежда всички песни от метаданни файловете')
    pdf.bullet('importCompletedDownload() – премества файлове от downloads/ в library/, създава .json')
    pdf.bullet('uploadSingleFile() / uploadAlbum() – обработка на качени файлове')
    pdf.bullet('announceAllKeywords() – извлича ключови думи и ги обявява в DHT')
    pdf.bullet('search() / searchLocal() – локално търсене по заглавие/артист/жанр')
    pdf.ln(2)

    pdf.bold_text('Модул 3: DHTKeywordManager (DHT търсене по ключови думи)')
    pdf.body_text(
        'Отговорност: Обявяване и търсене на ключови думи чрез BitTorrent DHT мрежата.'
    )
    pdf.bullet('keywordToInfohash() – SHA-1(keyword.toLowerCase() + ".minerva") → infohash')
    pdf.bullet('announceKeyword() – dhtAnnounce() за регистриране на възела за дадена ключова дума')
    pdf.bullet('searchKeyword() – двустранно търсене: (1) директно в известни peers, (2) чрез DHT peers')
    pdf.bullet('Дедупликация с композитен ключ, паралелно изпълнение с 4s timeout')
    pdf.ln(2)

    pdf.bold_text('Модул 4: KeywordSearchServer / KeywordSearchClient (MINERVA1 протокол)')
    pdf.body_text(
        'Отговорност: Директна TCP комуникация между Minerva възли за търсене по ключови думи.'
    )
    pdf.bullet('Протокол: клиентът изпраща "MINERVA1\\n" → сървърът отговаря "MINERVA1\\n" → '
               'клиентът изпраща "keyword.minerva\\n" → сървърът връща JSON масив с резултати')
    pdf.bullet('ServerSocket с CachedThreadPool, 2s connect timeout, 3s read timeout')
    pdf.bullet('Отхвърля заявки без .minerva наставка (филтрира стандартни BitTorrent peers)')
    pdf.ln(2)

    pdf.bold_text('Модул 5: BackendServer (REST API)')
    pdf.body_text(
        'Отговорност: HTTP API за комуникация с фронтенда, оркестрация на всички подсистеми.'
    )
    pdf.bullet('Javalin framework на настройваем порт (по подразбиране 4567)')
    pdf.bullet('25+ REST ендпойнта: tracks, albums, playlists, stream, download, upload, search, '
               'dht-search, fetch-torrent, downloads management, cover art')
    pdf.bullet('CORS поддръжка за cross-origin заявки')
    pdf.bullet('Download completion callback – свързва TORRENT_FINISHED alert с library import')
    pdf.bullet('pendingDownloads ConcurrentHashMap за проследяване на метаданни за текущи изтегляния')
    pdf.ln(2)

    pdf.bold_text('Модул 6: TorrentCreator (Създаване на торенти)')
    pdf.body_text(
        'Отговорност: Създаване на .torrent файлове от качени аудио файлове.'
    )
    pdf.bullet('createSingleTorrent() / createAlbumTorrent() – използва ttorrent библиотека')
    pdf.bullet('Копира файлове в библиотеката със стандартизирани имена')
    pdf.bullet('10 публични тракера за по-добра peer discovery')
    pdf.bullet('Автоматично изчисляване на SHA-1 infohash и преименуване на .torrent файла')
    pdf.ln(2)

    pdf.bold_text('Модул 7: PlaylistManager (Плейлисти)')
    pdf.body_text(
        'Отговорност: Управление на потребителски плейлисти.'
    )
    pdf.bullet('JSON-базирано съхранение с атомични записи (temp file → rename)')
    pdf.bullet('ReentrantReadWriteLock за нишкова безопасност')
    pdf.bullet('Системен плейлист "Liked Songs" (не може да се изтрие)')
    pdf.bullet('CRUD: създаване, редактиране, изтриване, добавяне/премахване на песни, пренареждане')
    pdf.ln(2)

    pdf.bold_text('Модул 8: MusicMetadataExtractor (Извличане на метаданни)')
    pdf.body_text(
        'Отговорност: Извличане на ID3 тагове от аудио файлове.'
    )
    pdf.bullet('Използва jaudiotagger за четене на тагове: заглавие, артист, албум, жанр, номер на песен и др.')
    pdf.bullet('Извличане на аудио параметри: битрейт, продължителност, формат, sample rate')
    pdf.bullet('Извличане на вградено album art → base64 кодиране + запис на диск')
    pdf.bullet('Fallback парсиране от име на файл (формати: "Артист - Заглавие", "Артист_Заглавие")')
    pdf.ln(2)

    pdf.bold_text('Модул 9: Electron Frontend (Потребителски интерфейс)')
    pdf.body_text(
        'Отговорност: Настолно приложение с модерен музикален интерфейс.'
    )
    pdf.bullet('main.js – Electron main process, BrowserWindow, IPC handlers')
    pdf.bullet('preload.js – Context bridge, expose window.minerva API')
    pdf.bullet('Модулна JavaScript архитектура: 17 специализирани модула')
    pdf.bullet('CSS архитектура: base/ (reset, variables, typography), layout/ (app, sidebar, player-bar), '
               'components/ (buttons, discover, downloads, forms, home, library, modals, playlist, track-list), '
               'utils/ (scrollbar, utilities), responsive.css')
    pdf.ln(2)

    pdf.bold_text('Взаимодействия между модулите:')
    pdf.body_text(
        '1. Потребителят взаимодейства с Electron интерфейса.\n'
        '2. Electron изпраща IPC заявки → main.js → HTTP към BackendServer.\n'
        '3. BackendServer делегира към LibraryManager (библиотека), JLibTorrentManager (торенти), '
        'PlaylistManager (плейлисти), DHTKeywordManager (мрежово търсене).\n'
        '4. JLibTorrentManager комуникира P2P с други Minerva възли чрез BitTorrent + DHT.\n'
        '5. KeywordSearchServer/Client осъществява директна TCP комуникация за търсене.\n'
        '6. При изтегляне: TORRENT_FINISHED alert → callback → importCompletedDownload() → '
        'файлове се преместват в библиотеката.'
    )

    # --- 4.5 Реализация ---
    pdf.subsection_title('4.5.', 'Реализация')
    pdf.ln(1)
    pdf.bold_text('Програмни езици и платформи:')
    pdf.bullet('Java 17 – бекенд (REST API, торент двигател, DHT, мрежови протоколи)')
    pdf.bullet('JavaScript (ES6+) – фронтенд (Electron, модулна архитектура)')
    pdf.bullet('HTML5/CSS3 – потребителски интерфейс (CSS Grid, Flexbox, CSS Custom Properties)')
    pdf.ln(1)

    pdf.bold_text('Ключови библиотеки и фреймуъркове:')
    pdf.bullet('Javalin 4.6.8 – лек REST API фреймуърк за Java')
    pdf.bullet('jlibtorrent 2.0.11.0 (frostwire) – native BitTorrent и DHT имплементация '
               'с SWIG bindings към libtorrent-rasterbar C++')
    pdf.bullet('ttorrent-core 1.5 – създаване на .torrent файлове')
    pdf.bullet('jaudiotagger 3.0.1 – извличане на аудио метаданни (ID3v1, ID3v2, Vorbis)')
    pdf.bullet('Jackson 2.15.2 – JSON сериализация/десериализация')
    pdf.bullet('Electron 28 – настолно приложение с Chromium рендер')
    pdf.bullet('Feather Icons – SVG иконна библиотека')
    pdf.bullet('SLF4J 2.0.12 + Logback 1.4.14 – логиране')
    pdf.ln(1)

    pdf.bold_text('Инструменти за разработка:')
    pdf.bullet('Apache Maven – build management, dependency resolution, shade plugin за fat JAR')
    pdf.bullet('npm / electron-builder – управление на Electron зависимости')
    pdf.bullet('Docker + Docker Compose – контейнеризация и тестване с множество възли')
    pdf.bullet('Git – версионен контрол')
    pdf.ln(1)

    pdf.bold_text('Ключови алгоритми:')
    pdf.bullet('SHA-1 хеширане на ключови думи за DHT mapping (keyword → deterministic infohash)')
    pdf.bullet('Паралелно търсене с ExecutorService и CompletionService за мрежови заявки')
    pdf.bullet('Релевантност при търсене: праг ≥50% съвпадение на ключови думи, ранкиране по брой съвпадения')
    pdf.bullet('Композитна дедупликация (torrentHash|title) за резултати от множество peers')
    pdf.bullet('Атомични файлови записи (temp file → rename) за JSON персистенция')
    pdf.bullet('forceRecheck алгоритъм за верификация на piece hashes след добавяне на торент')

    # --- 4.6 Описание на приложението ---
    pdf.subsection_title('4.6.', 'Описание на приложението')
    pdf.ln(1)
    pdf.bold_text('Стартиране и инсталиране:')
    pdf.body_text('Предварителни изисквания:')
    pdf.bullet('Java 17 или по-нова версия (JRE или JDK)')
    pdf.bullet('Node.js 18+ и npm (за Electron фронтенда)')
    pdf.bullet('Native libtorrent библиотека за съответната платформа (включена за Linux x86_64)')
    pdf.ln(1)

    pdf.body_text('Стартиране на бекенда:')
    pdf.set_font(pdf.font_name, 'I', 11)
    pdf.multi_cell(0, pdf.line_h,
        '# Компилиране\n'
        'mvn package -DskipTests\n\n'
        '# Стартиране\n'
        'java -Djava.library.path=natives/lib/x86_64 -jar target/minerva-1.0.0.jar',
        new_x='LMARGIN', new_y='NEXT'
    )
    pdf.set_font(pdf.font_name, '', 12)
    pdf.ln(2)

    pdf.body_text('Конфигуриране чрез променливи на средата:')
    pdf.bullet('API_PORT (подразбиране: 4567) – HTTP порт за REST API')
    pdf.bullet('SEARCH_PORT (подразбиране: 4568) – DHT и TCP порт за мрежово търсене')
    pdf.bullet('LIBRARY_DIR (подразбиране: library) – директория за музикалната библиотека')
    pdf.bullet('TORRENT_DIR (подразбиране: torrent_files) – директория за .torrent файлове')
    pdf.bullet('DOWNLOADS_DIR (подразбиране: downloads) – директория за изтегляния')
    pdf.bullet('ALBUM_ART_DIR (подразбиране: album_art) – директория за album art')
    pdf.bullet('DHT_BOOTSTRAP_NODES – адреси на известни Minerva възли (формат: host:port,host:port)')
    pdf.ln(2)

    pdf.body_text('Стартиране на фронтенда:')
    pdf.set_font(pdf.font_name, 'I', 11)
    pdf.multi_cell(0, pdf.line_h,
        'cd minerva-electron\n'
        'npm install\n'
        'npm start',
        new_x='LMARGIN', new_y='NEXT'
    )
    pdf.set_font(pdf.font_name, '', 12)
    pdf.ln(2)

    pdf.body_text('Стартиране с Docker Compose (две инстанции за тестване):')
    pdf.set_font(pdf.font_name, 'I', 11)
    pdf.multi_cell(0, pdf.line_h, 'docker-compose up --build', new_x='LMARGIN', new_y='NEXT')
    pdf.set_font(pdf.font_name, '', 12)
    pdf.ln(3)

    pdf.bold_text('Как се използва:')
    pdf.ln(1)

    pdf.set_font(pdf.font_name, 'B', 12)
    pdf.multi_cell(0, pdf.line_h, 'Начална страница (Home)', new_x='LMARGIN', new_y='NEXT')
    pdf.set_font(pdf.font_name, '', 12)
    pdf.body_text(
        'При стартиране потребителят вижда поздравително съобщение (различно според часа на деня) '
        'и три секции с по 6 карти на албуми от библиотеката, произволно разбъркани. Щракването '
        'върху карта показва детайлния изглед на албума.'
    )

    pdf.set_font(pdf.font_name, 'B', 12)
    pdf.multi_cell(0, pdf.line_h, 'Библиотека (Library)', new_x='LMARGIN', new_y='NEXT')
    pdf.set_font(pdf.font_name, '', 12)
    pdf.body_text(
        'Показва всички албуми от локалната библиотека в CSS Grid изглед с обложка, заглавие, '
        'артист, година и брой песни. Албумите се зареждат от /api/albums ендпойнта.'
    )

    pdf.set_font(pdf.font_name, 'B', 12)
    pdf.multi_cell(0, pdf.line_h, 'Търсене (Search)', new_x='LMARGIN', new_y='NEXT')
    pdf.set_font(pdf.font_name, '', 12)
    pdf.body_text(
        'Полето за търсене в горната лента извършва локално търсене в библиотеката по заглавие и артист.'
    )

    pdf.set_font(pdf.font_name, 'B', 12)
    pdf.multi_cell(0, pdf.line_h, 'Мрежово търсене (Discover)', new_x='LMARGIN', new_y='NEXT')
    pdf.set_font(pdf.font_name, '', 12)
    pdf.body_text(
        'Позволява търсене в P2P мрежата. Потребителят въвежда заявка, системата я разделя на '
        'ключови думи и изпращай заявки към всички известни Minerva peers и DHT peers. '
        'Резултатите се групират по торент хеш (албуми срещу единични песни) и се показват '
        'като карти с бутон за изтегляне. Филтри: All / Albums / Singles.'
    )

    pdf.set_font(pdf.font_name, 'B', 12)
    pdf.multi_cell(0, pdf.line_h, 'Изтегляния (Downloads)', new_x='LMARGIN', new_y='NEXT')
    pdf.set_font(pdf.font_name, '', 12)
    pdf.body_text(
        'Мониторинг на активните торент трансфери с актуализация на всеки 2 секунди. Показва '
        'общ брой торенти, скорост на качване/сваляне, статус (downloading/seeding/paused), '
        'прогрес бар и контроли за пауза/подновяване/премахване.'
    )

    pdf.set_font(pdf.font_name, 'B', 12)
    pdf.multi_cell(0, pdf.line_h, 'Качване (Upload)', new_x='LMARGIN', new_y='NEXT')
    pdf.set_font(pdf.font_name, '', 12)
    pdf.body_text(
        'Тристъпков wizard: (1) Избор на файлове – автоматично разпознаване на единична песен '
        'или албум. (2) Попълване на метаданни – автоматично извлечени от ID3 тагове, с '
        'възможност за ръчна редакция (заглавие, артист, албум, година, жанр, обложка). '
        '(3) Потвърждение и качване.'
    )

    pdf.set_font(pdf.font_name, 'B', 12)
    pdf.multi_cell(0, pdf.line_h, 'Плейлисти', new_x='LMARGIN', new_y='NEXT')
    pdf.set_font(pdf.font_name, '', 12)
    pdf.body_text(
        'Потребителят може да създава, редактира и изтрива плейлисти от страничната лента. '
        'Системният плейлист "Liked Songs" се попълва чрез бутона ♥ на всяка песен. '
        'Контекстното меню на всяка песен позволява добавяне към плейлист или към опашката.'
    )

    pdf.set_font(pdf.font_name, 'B', 12)
    pdf.multi_cell(0, pdf.line_h, 'Аудио плейър', new_x='LMARGIN', new_y='NEXT')
    pdf.set_font(pdf.font_name, '', 12)
    pdf.body_text(
        'Долната лента съдържа информация за текущата песен (обложка, заглавие, артист), '
        'контроли за възпроизвеждане (предишна/play-pause/следваща), прогрес бар с възможност '
        'за seek, и регулатор на силата на звука. Аудиото се стриймва от бекенда чрез HTML5 '
        '<audio> елемент. При край на песента автоматично се пуска следващата от опашката.'
    )

    pdf.set_font(pdf.font_name, 'B', 12)
    pdf.multi_cell(0, pdf.line_h, 'Опашка (Queue)', new_x='LMARGIN', new_y='NEXT')
    pdf.set_font(pdf.font_name, '', 12)
    pdf.body_text(
        'Линейна опашка от песни. При пускане на албум/плейлист/резултат от търсене, цялата '
        'опашка се заменя. Отделни песни могат да се добавят чрез контекстното меню. '
        'Модален прозорец показва текущата опашка с възможност за премахване и изчистване.'
    )
    pdf.ln(3)

    pdf.bold_text('Как се поддържа:')
    pdf.body_text(
        'Приложението не изисква специална поддръжка. Данните се съхраняват като файлове на диска '
        '(музикални файлове, .torrent файлове, JSON метаданни, JSON плейлисти). За добавяне на нов '
        'възел е достатъчно да се стартира нова инстанция с DHT_BOOTSTRAP_NODES, сочещ към '
        'съществуващ Minerva възел. Обновяването става чрез повторно компилиране и рестартиране.'
    )

    # --- 4.7 Заключение ---
    pdf.subsection_title('4.7.', 'Заключение')
    pdf.ln(1)
    pdf.bold_text('Основен резултат:')
    pdf.body_text(
        'Създадена е напълно функционална децентрализирана P2P платформа за споделяне и стрийминг '
        'на музика. Системата позволява качване на музика, автоматично споделяне чрез BitTorrent, '
        'търсене по ключови думи в мрежата от Minerva възли, изтегляне и импортиране в локалната '
        'библиотека, стрийминг на аудио и управление на плейлисти – всичко това без централен сървър.'
    )
    pdf.ln(1)

    pdf.bold_text('Текущо състояние:')
    pdf.body_text(
        'Проектът е тестван успешно с множество инстанции (чрез Docker Compose и ръчно с изолирани '
        'директории). Торент трансферите работят коректно между възлите, DHT търсенето връща '
        'релевантни резултати, а изтеглените албуми се импортират в библиотеката и се зареждат '
        'при следващото стартиране.'
    )
    pdf.ln(1)

    pdf.bold_text('Възможности за развитие:')
    pdf.bullet('Автоматично пресийдване след импорт – елиминиране на необходимостта от рестартиране '
               'чрез решаване на native threading проблемите с jlibtorrent')
    pdf.bullet('Криптирана комуникация – TLS за MINERVA1 протокола и криптирани торент конекции')
    pdf.bullet('Потребителски профили – децентрализирана идентификация с публичен/частен ключ')
    pdf.bullet('Автоматично откриване на peers – mDNS/Bonjour за локални мрежи без ръчна конфигурация')
    pdf.bullet('Мобилно приложение – React Native или Flutter фронтенд със същия REST API бекенд')
    pdf.bullet('Препоръчителна система – базирана на колаборативно филтриране между peers')
    pdf.bullet('Поддръжка на повече формати – FLAC, OGG, WAV, AAC освен MP3')
    pdf.bullet('Last.fm интеграция – вече частично имплементирана (OAuth flow), за пълно scrobbling')
    pdf.bullet('Подобрена UI анимация и достъпност – keyboard navigation, screen reader поддръжка')

    # Output
    output_path = '/home/kaloyan/Projects/Minerva/Minerva_Documentation.pdf'
    pdf.output(output_path)
    print(f'PDF generated: {output_path}')
    print(f'Pages: {pdf.page_no()}')


if __name__ == '__main__':
    generate()
