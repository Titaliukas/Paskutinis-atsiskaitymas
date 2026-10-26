# Builder ir dvi arenos

Palaikomi iki 4 žaidėjų. Combat ir Training yra dvi įgyvendintos arenų konfigūracijos, tačiau vienas serverio
procesas paleidžia vieną sesiją su viena paleidimo metu pasirinkta arena. Visi prisijungę žaidėjai žaidžia toje
pačioje sesijoje. Lobby, kambariai ir arenos keitimas veikiančiame serveryje neįgyvendinti.

## Problema ir pasirinkimas

Anksčiau `GameSession.createDefault()` vienoje vietoje kūrė platformas, parinkdavo koordinates ir kurdavo gyvus NPC.
Žaidėjai ir daiktai nepriklausomai rėmėsi globaliu `GROUND_Y`. Pridėjus antrą areną tokia konstrukcija būtų
kartojama arba sesija turėtų žinoti kiekvienos arenos išdėstymą.

Builder nuosekliai surenka vieną nekintamą `Arena` konfigūraciją. Ta pati kūrimo seka pagamina skirtingus
produktus. Gyvas pasaulis lieka `GameSession`, jo Strategy valdikliuose ir esamoje vieno savininko kilpoje.

| Dalyvis | Atsakomybė |
| --- | --- |
| `IArenaBuilder` | Sutartis: `reset`, `buildBounds`, `buildPlatforms`, `buildPlayerSpawns`, `buildNPCSpawns`, `build` |
| `ArenaDirector` | Visada vykdo šią seką; nežino koordinačių ar NPC kiekio |
| `CombatArenaBuilder` | Keturi paviršiai, keturios žaidėjų vietos, du NPC aprašai ir porinės šonų angos |
| `TrainingArenaBuilder` | Stadionas, viena žemė, keturios vietos, be NPC ir perėjimo |
| `Arena` | Galutinis nekintamas produktas: ribos, geometrija, pradžios vietos ir perėjimo duomenys |
| `NPCSpawn` | NPC identifikatorius, viršutinio kairiojo kūno kampo pozicija ir patruliavimo ribos |

Kūrimo metodai grąžina `IArenaBuilder`, o `build()` grąžina `Arena`. `reset()` išvalo ribas ir visus sąrašus;
Director jį kviečia kiekvieną kartą. `List.copyOf` atskiria produktą nuo builderio ir išorinių sąrašų.
`Position`, `NPCSpawn`, `SidePassage` yra nekintami; `Platform.copyAt(...)` keičia tik naują kopiją.
Platformų UUID lieka atskiri. Skirtingos sesijos iš tų pačių aprašų gauna savo NPC ir valdiklius.

`ArenaSelection` serverio paleidimo metu vienoje vietoje pasirenka konkretų builderį ir perduoda jį Director.
Produktas perduodamas į `GameWebSocketServer` → `GameRuntime` → `GameSession` → esamą kilpą.
`GameSession.createDefault()` taip pat naudoja Director su Combat builderiu.
Sesija inicializacijos metu kiekvienam `NPCSpawn` kviečia esamą `spawnNPC(...)`; antros elgesio sistemos nėra.

## v2 resursai ir koordinatės

Naudojami `assets/training_arena_v2.png` ir `assets/combat_arena_v2.png`, abu **1672 × 941**.
Resursai jau buvo repozitorijoje ir baitas į baitą sutampa su `backgrounds/training_arena_v2 copy.png` bei
`backgrounds/combat_arena_v2 copy.png`. Užduotyje nurodytų vardų be ` copy` tame kataloge nebuvo.
Kopijuoti iš naujo nereikėjo. Nei originalai, nei v1 resursai, nei patvirtintas SVG nekeisti.

Pasaulis lieka **960 × 540**, o koordinatės skaičiuojamos taip:

```text
worldX = imageX * 960 / 1672
worldY = imageY * 540 / 941
```

| Arena / paviršius | World X | Viršaus world Y | Plotis | Storis | Atitinkamas image Y |
| --- | ---: | ---: | ---: | ---: | ---: |
| Combat stogas | 45 | 215 | 870 | 6 | 374.66 |
| Combat įėjimo stogelis | 333 | 344 | 294 | 6 | 599.44 |
| Combat aptarnavimo pakyla | 210 | 280 | 120 | 6 | 487.93 (piešiama kode) |
| Combat žemė | 0 | 421 | 960 | 119 | 733.65 |
| Training žemė | 0 | 448 | 960 | 92 | 780.68 |

Tai sveikų world vienetų suapvalinimai pagal tikras PNG briaunas: stogo imageY≈375, stogelio≈599–600,
Combat pagrindo≈734 ir Training priekinės tako/žolės briaunos≈781. Skirtumai nuo šių matavimų nesiekia
vieno world vieneto. Training veikėjo pėdos remiasi į priekinį tako kraštą; žemiau matomas gruntas yra dekoracija.
Ankstesnės ilgos šoninės platformos Y=351 **pašalintos**. Nebenaudojami v1 atramų aukščiai 222/309/351/467/480.
Vartai, žibintai, skulptūra, krūmai, langai ir antena neprideda kolizijos; pastato fasadas nėra siena.

Visos platformos lieka vienkryptės: leidžiantis sustojama ant aukščiausio kertamo paviršiaus, o šokant iš apačios
prasiskverbiama pro jį. `Platform.copyAt(...)` ir atskiri UUID išlaikyti; kopija išsaugo ir platformos paskirtį.

Abiejų arenų žaidėjų X pagal slotus: **180, 738, 60, 858**. Combat kūno Y yra **357**, Training **384**.
Visi keturi kūnai 42×64 telpa pasaulyje, nesikerta tarpusavyje ar su NPC ir nėra prie angų (jų vidinė grafika
siekia maždaug X=44 ir X=916). `GameCharacter` saugo gautą pradinę poziciją ir į ją atgimsta.

| Combat NPC | Pradinė pozicija | Patruliavimo kairiojo kūno krašto ribos | Atrama |
| --- | --- | --- | --- |
| 1 | (360,151) | [45,873] | Stogas Y=215; 915 − kūno plotis 42 |
| 2 | (580,357) | [530,650] | Žemė Y=421 |

Training turi tik žemę, keturias vietas ir 0 NPC. Combat turi tiksliai 2 NPC, sukuriamus per esamą
`spawnNPC(...)` ir Strategy valdiklius. Sesijos nesidalija gyvais NPC ar valdikliais.
Daiktai toliau atsiranda pagal `Arena.groundY − 64` ir esamą rinkimo semantiką; jų 16 vienetų surinkimo
spindulys nepakeistas. Tik ikonos grafinis dydis sumažintas iki **22 world units**, shield žymė iki **20**.

## Pagalbinė platforma ir šuolių kelias

Stogelio ir stogo aukščių skirtumas **129**, o standartinio 100/100/100 build šuolis pasiekia **120**.
Todėl pridedama vienintelė 120×6 aptarnavimo pakyla `(210,280)`: ji turi tikrą koliziją ir kode piešiamą
siaurą pilką tūrį su briauna bei mažais laikikliais. PNG nekeičiamas. Pakylos X papildomai perkeltas iš 330 į 210, t. y. 120 vienetų (per vieną jos plotį) kairėn;
aukštis Y=280 ir 120×6 matmenys nepakeisti. Simuliacija patvirtino kelią iš abiejų arenos pusių.

`ArenaPhysicsTest` tikrais 1/60 s žingsniais patikrina:

1. Iš X=180 arba X=858 žaidėjas prieina žeme po stogeliu (51 žingsnis dešinėn arba 106 kairėn).
2. Vertikalus šuolis pasiekia stogelį Y=344 (40 žingsnių).
3. Atskiras tiesioginis šuolis nuo stogelio ties X=550 (už pakylos) **nepasiekia stogo** ir baigiasi ant stogelio.
4. Šuolis 28 žingsnius kairėn ir dar 16 be horizontalios įvesties užbaigiamas ant pakylos Y=280.
5. Vertikalus šuolis užbaigiamas ant stogo Y=215 (44 žingsniai).

Gravitacija 1500, šuolio greitis 600, build sistema ir kūno matmenys 42×64 nepakeisti.
Išlaikytas ankstesnis pastovaus pagreičio poslinkis `v*dt + g*dt²/2`. Tiesioginis žemės → stogo šuolis
neįmanomas. Mažesnio šuolio build gali turėti natūralių pasiekiamumo apribojimų.

## Combat perėjimas žemės lygyje

Serverio `SidePassage(openingTop=343, supportY=421)` aprašo **78** vienetų aukščio porines angas.
Įprastai einančio kūno viršus yra 357, pėdos 421. Tikrinamas visas kūnas ir jo vertikalus poslinkis per žingsnį.
Stovint ant atramos atsižvelgiama į tame pačiame žingsnyje išsprendžiamą nusileidimą.

- Kairėn nukreipta įvestis ir judėjimas už X=0 perkelia į **X=918**.
- Dešinėn nukreipta įvestis ir judėjimas už X=918 perkelia į **X=0**.
- Tikrinama prieš horizontalų clamp; vien knockback be kryptingos įvesties nepakanka.
- Po perkėlimo kūnas telpa viduje ir įprasta fizika remiasi į žemę. Tolimesnis judesys veda į arenos vidų,
  todėl nėra momentinio grįžimo. Stovėjimas prie krašto neperkelia.
- Sveikata, stress, build, efektai, cooldown, tapatybė ir atgimimo vieta nesikeičia; laikmačiai tik natūraliai tiksena.
- Už angos aukščio bei visur Training kraštai uždari. NPC, daiktų ir sviedinių taisyklės nepakeistos.

Angos klientui perduodamos kaip `ArenaSnapshot.sidePassage`. Tamsus vidus, 3 vienetų pilkas rėmas ir du
prislopinti melsvai pilki krypties ženklai piešiami **prieš veikėjus**, virš žemės, pagal tą patį intervalą.
Grafika iš dalies nukirsta arenos kraštu; jos plotis yra dekoratyvi angos aukščio proporcija. Rėmas neturi kolizijos.
Klientas neturi teleportavimo komandos ir nekopijuoja fizikos konstantų.

## Briaunos, mastelis ir HUD

Stogo, stogelio ir žemės viršų sustiprina 1 world vieneto prislopinta šviesi briauna bei 2 vienetų permatomas
šešėlis; galai pažymimi trumpais santūriais brūkšniais. Pakyla turi pilną 6 vienetų tūrį.
`ArenaGeometryRenderer` naudoja tik platformų ir angų snapshot duomenis. Debug atskirai parodo plonus
magenta kolizijų kontūrus. Trūkstant fono išlieka dangaus ir platformų fallback.

`AssetManager` kartą užkrauna v2 fonus; vaizdas, kolizijos, briaunos, angos ir kūnai naudoja tą pačią centruotą
pasaulio–ekrano transformaciją su nearest-neighbor fono masteliu. Nekeičiamas kūno dydis pagal Y.
Kontaktinis šešėlis piešiamas tik esant realiai atramai: žaidėjui dar reikia `grounded=true`, NPC tikrinama
snapshot pėdų ir paviršiaus atitiktis. Ore prie pėdų šešėlis neprilimpa.

Keturios HUD kortelės išlaiko slotus, portretus, vardus, stress ir vietinio žaidėjo žymėjimą. Mažesniame lange
jos proporcingai mažinamos kartu su arenos masteliu (iki 1:1). Apatinių kortelių dydį papildomai riboja
snapshot pilno pločio žemės paviršius ir dekoratyvus plotas po juo, kad jos neuždengtų angų ar Training pėdų.
16:9 pradinio lango parinkimas, rankinis lango dydžio keitimas, klaviatūros fokusas ir valdymas nepakeisti.

## UML ir protokolo papildymai

Patvirtinti Builder dalyviai, jų metodai, Director seka ir ryšiai išlaikyti. SVG nekeistas.
Esami `ArenaType`, `Arena.groundY`, `Arena.sidePassage` ir `NPCSpawn` išlieka.

| Papildymas | Paskirtis |
| --- | --- |
| `Platform.Kind` (`STANDARD`, `SERVICE`) | Skiria aptarnavimo pakylą nuo PNG paviršių; nekeičia fizikos |
| `Platform.kind()` ir penkių parametrų konstruktorius | Prototype kopija išsaugo paskirtį; senas konstruktorius reiškia STANDARD |
| `PlatformSnapshot.kind` | Validuojama transporto reikšmė `standard` arba `service`, be domain importo kliente |
| `SidePassageSnapshot(openingTop,supportY)` | Baigtiniai tvarkingai išdėstyti angos aukščiai toje pačioje koordinačių sistemoje |
| `ArenaSnapshot.sidePassage` | Combat angų aprašas arba aiškus JSON null; Training turi null |
| `ArenaGeometryRenderer` | Kliento piešimo pagalbininkas, ne antra arenos kūrimo ar fizikos sistema |

Jackson reikalauja naujų laukų JSON. Protokolo testai tikrina round trip, trūkstamus / nežinomus laukus ir
neteisingus intervalus; serverio bei kliento JAR reikia atnaujinti kartu. Domain/application išlieka be Swing,
AWT, Jackson ir tinklo priklausomybių, client priklauso tik nuo protocol.

## Paleidimas ir demonstravimas

Iš projekto šaknies, su Java 25:

```sh
./mvnw -B -ntp clean verify
java -Dgame.arena=combat -jar game-server/target/game-server.jar
# Arba, sustabdžius ankstesnį serverį:
java -Dgame.arena=training -jar game-server/target/game-server.jar
```

`game.arena` JVM nustatymas turi pirmenybę prieš `GAME_ARENA`; nenurodžius parenkama `combat`.
Tiksliai leidžiamos mažosios reikšmės `combat`, `training`; tuščia ar nežinoma reikšmė sukelia aiškią klaidą.

```sh
GAME_ARENA=training java -jar game-server/target/game-server.jar
GAME_ARENA=training docker compose up --build -d game-server
java -jar game-client/target/game-client.jar ws://localhost:8080/game Alice
java -Dgame.debug.geometry=true -jar game-client/target/game-client.jar ws://localhost:8080/game Alice
java -cp game-domain/target/game-domain.jar lt.ktu.paskutinisatsiskaitymas.domain.BuilderDemo
java -cp game-domain/target/game-domain.jar lt.ktu.paskutinisatsiskaitymas.domain.NPCStrategyDemo
```

Builder demonstracija pateikia Combat (4 paviršiai, 2 NPC), Training (1 paviršius, 0 NPC) ir pakartotinį Combat
be kaupimo. Strategy demonstracija dabar turi savo aiškią plokščią areną ir išlaiko ankstesnį keturių strategijų ciklą.

## Patikros ir peržiūros

`./mvnw -B -ntp clean verify` tikrina Builder produktus ir reset, kolekcijų apsaugą, atskirus NPC/valdiklius,
visas pradžios ir atgimimo vietas, aukščiausią nusileidimą, abipusį šuolių kelią, abiejų angų kryptis, būsenos
saugojimą, aukščio ribas, uždarą Training, daiktus, protokolą ir realų WebSocket kelią. Enforcer ir Checkstyle
įjungti. Ankstesnių elgsenų testinės arenos lieka aiškios; testai neišjungti.

```sh
./scripts/render-arenas.sh
```

`game-client/target/arena-previews/` gauna tikrų Builder produktų → sesijų → serverio mapperio JSON ir tikro
GamePanel EDT piešimo PNG. Atskirų pozų QA sesijose pakeičiama tik pradinė pirmojo žaidėjo vieta, paliekant
Builder geometriją; pozų aukščiai gaunami iš produkto. Visose peržiūrose lieka 4 HUD kortelės ir Combat NPC.

Scenarijų vardai: `training`, `combat`, `combat-canopy`, `combat-service`, `combat-roof`,
`combat-left-opening`, `combat-right-opening`. Kiekvienam sugeneruojami 960×540, 640×360 ir 640×400
(juostų/centravimo patikra) įprastas bei `-debug` variantai: iš viso **42 PNG** ir 7 šaltinio JSON.
Pavyzdžiai: `combat-service-960x540.png`, `combat-left-opening-640x360-debug.png`.

`ArenaDecorationTest` tikrina, kad abi angos neuždengtos keturių HUD kortelių įprastame ir mažame lange,
kad aptarnavimo platforma matoma, šešėlis priklauso nuo atramos/grounded, o daikto grafika telpa į 22 vienetus.
`GamePanelArenaTest` tikrina foną, matmenis, debug, resize ir centravimą. Dydžio parinkimo testai išlaikyti.

Vienas serveris turi vieną areną. Nėra lobby, kambarių, Facade, viešos serverio prieigos ar kliento prognozavimo.

Ankstesnės v2 užduoties patikros įrašas (ne šio dokumentacijos pakeitimo testavimo rezultatas):
**216 testų**, 0 failures, 0 errors, 0 skipped (domain 82, application 4, protocol 53,
server 16, client 61). `clean verify` baigėsi BUILD SUCCESS su įjungtais Enforcer ir Checkstyle.
Patikros žurnalas: `target/arena-v2-verify.log`. Builder ir Strategy main demonstracijos įvykdytos.
42 peržiūros sugeneruotos; visų septynių scenarijų įprasti ir mažesni vaizdai bei pasirinkti debug kontūrai
vizualiai sutikrinti. Patikrinta, kad PNG ir `ConnectionWindow` / `GameWindowSizing` baitai nepasikeitė.

Abiem arenoms paleistas tikras Swing langas ir tikras serveris: automatinis GUI scenarijus patikrino pradinį
pasirinkimo ekraną, prisijungimą, 960×540 GamePanel, formos/statuso tilpimą, lango ribas, rankinio dydžio pakeitimo
imitaciją, disconnect ir reconnect išsaugant vartotojo dydį. Žurnalai: `target/arena-v2-gui-combat.log` ir
`target/arena-v2-gui-training.log`; visi sukurti procesai uždaryti. OS langus paliko fone, todėl šiame paleidime
natūralus klaviatūros fokusas nepatvirtintas. Fokusą grąžinantis kodas nepakeistas.
Rankinis žaidimas su klaviatūra / valdikliu ir kelių žaidėjų rankinis kovos bandymas neatlikti;
šuolių ir perėjimo fizika patikrinta simuliacijos testais. Žinomų likusių geometrijos ar angų/HUD persidengimų
patikrintuose 960×540 ir 640×360 dydžiuose nenustatyta. Mažame Training lange apatinės kortelės kompaktiškesnės.
