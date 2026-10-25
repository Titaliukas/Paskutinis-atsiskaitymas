# Builder ir dvi arenos

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
| `CombatArenaBuilder` | Penki paviršiai, keturios žaidėjų vietos, du NPC aprašai ir porinės šonų angos |
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

## Arenų duomenys ir vaizdo koordinatės

Abu originalūs kliento PNG patikrinti: **1672 × 941**. Pasaulis **960 × 540**:

```text
worldX = imageX * 960 / 1672
worldY = imageY * 540 / 941
```

Combat geometrija sutikrinta su švariu fonu ir pateiktu žymėjimu. Žymėjimas lieka tik projektavimo nuoroda;
raudonos/mėlynos linijos, užrašai ir geltona dangaus žymė į resursus nekopijuojami. Originalūs PNG ir patvirtintas
`class_after_builder.svg` nekeičiami.

| Combat paviršius | World X | Viršaus world Y | Plotis | Storis | Atitinkamas image X / Y |
| --- | ---: | ---: | ---: | ---: | --- |
| Pastato stogas | 52 | 222 | 857 | 6 | 90.6 / 386.9 |
| Įėjimo stogelis | 339 | 309 | 281 | 6 | 590.4 / 538.5 |
| Kairė šoninė platforma | 0 | 351 | 183 | 6 | 0 / 611.7 |
| Dešinė šoninė platforma | 785 | 351 | 175 | 6 | 1367.2 / 611.7 |
| Apatinė žemė | 0 | 467 | 960 | 73 | 0 / 813.8 |

Ploni paviršiai praleidžia iš apačios ir sulaiko leidžiantis. Fasadas netampa siena. Nusileidimas tikrina visus
kertamus paviršius ir parenka mažiausią Y, todėl sąrašo tvarka nelemia nusileidimo aukščio.
Šoninės platformos sulygiuotos Y=351; mėlyna jungianti rodyklė nėra platforma.
Platformų receptai išdėstomi per esamą Prototype `copyAt(...)`; senųjų prototipinių platformų ar papildomų laiptelių nėra.

Training žemė yra `(0,480)`, plotis 960, storis 60. Tai priekinė žolė žemiau stadiono turėklo, apie imageY=836.4.
Tai sąmoningai pasirinkta horizontali žaidimo atrama perspektyviniame stadione. Pakeltų platformų nėra.

Abiejų arenų žaidėjų X pagal slotus: **180, 738, 60, 858**. Kūno viršus yra žemės Y − 64:
Combat **403**, Training **416**. Kiekvienas kūnas telpa ribose, pradžioje nesikerta su kitais ar NPC.
`GameCharacter` išsaugo gautą pradinę poziciją ir po mirties į ją grįžta.

| Combat NPC | Pradinė pozicija | Patruliavimo kairiojo kūno krašto ribos | Atrama |
| --- | --- | --- | --- |
| 1 | (360,158) | [52,867] | Stogas Y=222; dešinė riba = 909 − 42 |
| 2 | (580,403) | [530,650] | Žemė Y=467 |

Training nepakeičia kovos, daiktų ar build sistemos. Daiktų centrai lieka ties žemės Y − veikėjo aukštis,
išlaikant ankstesnę rinkimo prie viršutinės kūno dalies semantiką. X apribotas taip, kad visas 16 vienetų spindulys
tilptų pasaulyje; daiktą galima surinkti stovint ant pasirinktos arenos žemės.

## Šoninio perėjimo taisyklės

Combat `SidePassage(openingTop=275, supportY=351)` aprašo abi susietas angas. 76 vienetų aukštis talpina 42×64 kūną.
Įprastai einančio kūno viršus yra 287, pėdos 351. Tikrinamas kūnas ir jo vertikalus poslinkis per žingsnį;
stovint ant atramos ignoruojamas tik gravitacijos poslinkis, kurį tą patį žingsnį pašalins nusileidimas.

- Kairėn nukreiptas įvedimas ir judėjimas už X=0 perkelia į **X=918**.
- Dešinėn nukreiptas įvedimas ir kūno judėjimas už X=918 perkelia į **X=0**.
- Tikrinama prieš horizontalų clamp. Kryptis turi sutapti su kraštu; vien knockback be įvedimo nepakanka.
- Išsaugomas vertikalus poslinkis/greitis; einantis veikėjas įprastu nusileidimu lieka ant priešingos platformos.
- Pakeičiamas tik X. Tapatybė, build, health, stress, efektai, cooldown ir pradinė atgimimo vieta išlieka.
  Laikmačiai toliau natūraliai mažėja per įprastą tick.
- Atsiradęs veikėjas juda į arenos vidų. Kitas tick nekerta išėjimo krašto; stovėjimas neperkelia.
- Angos ribų netelpantis kūnas, apatinė žemė, stogas ir visos Training pozicijos lieka uždaros.
- NPC, daiktų ir sviedinių kraštų elgsena nesikeičia. Klientas gauna įprastą autoritetingos pozicijos snapshot.

Jokios kliento teleportavimo komandos, laikmačio ar papildomo valdymo šablono nereikia.

## Šuolio kelias

Greitis 600, gravitacija 1500 ir 100/100/100 build teoriškai duoda 120 vienetų šuolį.
Ankstesnis pusiau implicitinis Euler integravimas 60 Hz dažniu duodavo tik **115**, todėl **116** vienetų
žemė → šoninė platforma pakopa buvo nepasiekiama. `GameCharacter` dabar skaičiuoja tikslų pastovaus pagreičio
poslinkį `v*dt + g*dt²/2` ir tada greitį `v + g*dt`. Gravitacija, greitis ir statistikų biudžetas nepakeisti.

`ArenaPhysicsTest` abiem pusėmis realiais 1/60 s žingsniais patikrina:

1. Iš X=180 arba X=858 šokama nuo žemės ant šoninės platformos (30 žingsnių).
2. Dešinėje prieinama prie vidinio krašto (16 žingsnių kairėn); kairėje X=180 jau yra prie krašto.
3. Šokama link stogelio (44 žingsniai; galutiniai X≈370.67 arba X=598).
4. Šokama tiesiai aukštyn ant stogo (38 žingsniai).

Tiesioginis šuolis nuo apatinės žemės iki stogo neįmanomas ir patikrintas atskirai. Mažesnio šuolio build
natūraliai gali nepasiekti šio kelio. NPC integravimas ir elgsena dėl šio žaidėjų pataisymo nekeičiami.

## Papildymai prie patvirtintos UML

Patvirtinti dalyviai ir Director/Builder/Product ryšiai išlaikyti. SVG nekeistas.

| Papildymas | Paskirtis ir ryšys |
| --- | --- |
| `ArenaType` (`COMBAT`, `TRAINING`) ir `Arena.type` | Stabili domeno tapatybė; pavadinimas tik rodymui |
| `Arena.groundY` | Aiškus pasirinktos arenos žemės aukštis daiktų atsiradimui; nepriklauso nuo sąrašo tvarkos |
| `Arena.sidePassage: Optional<SidePassage>` | Produktas turi 0..1 nekintamą porinių angų aprašą |
| `SidePassage.openingTop/supportY` | Duomenys domeno fizikai; nėra tinklo ar grafikos objektas |
| `ArenaSnapshot.arenaType: String` | Transporto allowlist: tik `combat` arba `training`; nėra domain enum ar PNG kelias |
| `ArenaSelection` | Serverio paleidimo konfigūracija; vienintelis konkretaus builderio pasirinkimas |

`Arena` lieka record su `double` matmenimis, `Platform` lieka su UUID. `NPCSpawn.id` atitinka diagramoje esantį
Integer, o `position` naudoja esamą `Position` su double. Domain/application neturi UI, tinklo ar Jackson importų;
client priklauso tik nuo protocol. Protokolo pakeitimas reikalauja kartu atnaujinti serverio ir kliento JAR.

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

Builder demonstracija pateikia Combat (5 paviršiai, 2 NPC), Training (1 paviršius, 0 NPC) ir pakartotinį Combat
be kaupimo. Strategy demonstracija dabar turi savo aiškią plokščią areną ir išlaiko ankstesnį keturių strategijų ciklą.

## Patikros ir ribos

Automatinės patikros apima Director seką, abiejų builderių produktus, reset, kolekcijų apsaugą, atskirus sesijų
NPC/valdiklius, patruliavimą ant stogo, pradžios vietas/atgimimą, visų paviršių nusileidimą, kelią iki stogo,
abi perėjimo kryptis, būsenos išlaikymą, angų ribas, uždarus Training/žemės/stogo kraštus, daiktus, JSON tapatybės
validaciją, serverio atrankos prioritetus ir abiejų konfigūracijų realų WebSocket kelią. Ankstesnėms elgsenoms
naudojamos aiškios testinės arenos; testai neišjungti, Enforcer ir Checkstyle lieka įjungti.

`GamePanelArenaTest` tikrina fono parinkimą, pradinių PNG matmenis, pixel-art atvaizdavimą, debug kontūrus ir
centravimą 960×540, 640×360, 640×400 ir 1000×540 plotuose. Fonai kraunami vieną kartą į `AssetManager`;
normaliai nepiešiami seni rudi stačiakampiai, o trūkstant fono lieka saugus grafikos fallback.

```sh
./scripts/render-arenas.sh
```

Šis QA scenarijus paima tikrų builderių → sesijų → `SnapshotMapper` JSON ir piešia tikrą `GamePanel` EDT gijoje.
`game-client/target/arena-previews/` gauna abiejų arenų 960×540, 640×360 ir 640×400 PNG, įprastus ir debug variantus.
Tai offscreen automatinė/vizualinė patikra; ji nepakeičia rankinio kelių langų žaidimo bandymo.
Vienas serveris turi vieną areną visam procesui; nėra lobby, kambarių, Facade, viešos serverio prieigos,
kliento prognozavimo ar interpoliacijos.

Patikrinta 2026-10-25 su standalone Temurin Java 25: `./mvnw -B -ntp clean verify` baigėsi **BUILD SUCCESS**.
Iš viso **205 testai**, 0 failures, 0 errors, 0 skipped: domain 81, application 4, protocol 51, server 16, client 53.
Enforcer ir Checkstyle įjungti. Abi main demonstracijos įvykdytos; Strategy ciklas baigėsi ties tick=165.
Visi 12 sugeneruotų GamePanel vaizdų vizualiai peržiūrėti: stogas, stogelis ir šonai sutampa su fonu,
Training atrama yra priekinėje žolėje, mažesni ir netipinio santykio langai išlaiko tą pačią transformaciją.
Originalių PNG baitai palyginti su Git HEAD ir sutampa. Atskirai patikrintas tikro serverio main paleidimas:
numatytasis Combat, Training iš aplinkos, JVM pirmenybė ir klaidingos reikšmės atmetimas; visi procesai uždaryti.

Rankinis kelių GUI langų žaidimo bandymas neatliktas; WebSocket kelias ir piešimas patikrinti automatiškai.
Išlaikytos esamos HUD kortelės apačioje dengia dalį ant žemės esančių veikėjų, ypač 640×360 lange;
HUD išdėstymas ir mastelis šiame etape nekeisti. Tai matoma peržiūrų PNG. Pasaulio fizika nuo šio perdengimo nepriklauso.
