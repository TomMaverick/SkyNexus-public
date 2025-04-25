# SkyNexus

## Projektskizze

SkyNexus ist eine Verwaltungsapplikation für Airlines. Sie bietet eine benutzerfreundliche Verwaltung der Airline und
ihrer Flugzeuge, Flüge und Passagiere. Die App ermöglicht eine effiziente Organisation und Überwachung des Flugbetriebs
durch eine grafische Benutzeroberfläche. Funktionen wie die Verwaltung von Flugzeugen, Ticketpreisen,
Passagierinformationen und Flugplandaten erleichtern die Arbeit von Airline-Mitarbeitern. Zielgruppe sind
Fluggesellschaften, die eine optimierte und leicht bedienbare Lösung für ihr Flugmanagement benötigen.

## Projektteam

TomMaverick & Juppel

## Projektzeitraum

27.02.2025 bis 15.05.2025

## Analyse

### Problemstellung

- Es gibt einen Mangel an intuitiven Verwaltungssystemen für Airlines.
- Oft nutzen Airlines unterschiedliche Applikationen für Flug-, Flotten- und Passagierdaten. SkyNexus integriert diese
  Informationen in einer einzigen Plattform, um einen ganzheitlichen Überblick zu ermöglichen und verbindet dies mit
  einer benutzerfreundlichen Oberfläche.

### Motivation und Bedarf

- Bedarf nach einer übersichtlichen und nutzerfreundlichen Software zur Verwaltung von Airline-Daten.
- Erleichterung der Arbeitsprozesse im Bereich Flugmanagement und Datenhaltung.

### Ausgangslage

Bisherige Verwaltungsapplikationen für Airlines sind oft mehrere, nicht zusammenhängende Programme. Jedes davon ist auf
sein Aufgabengebiet spezialisiert, aber sie bieten kaum Schnittstellen für die anderen Applikationen. Diese Lösungen
sind auf Grund der vielen benötigten Applikationen sehr kostenintensiv. Die Applikation FLIGHTLOGGER ist Aufgrund des
Preises für kleinere Airlines interessant, bietet aber kein Ticketing System. Eine Applikation wie Amadeus Altea bietet
Ticketing, ist aber für kleine Airlines nicht bezahlbar. Dafür bietet es aber keine Routenverwaltung, die in
FLIGHTLOGGER verfügbar ist. Wir wollen alles in einer App kombinieren.

### Ideen, Ziele, Visionen

Unser Ziel ist es, eine intuitive Applikation mit GUI zu entwickeln, in den Airlines ihre Flugzeuge, Flüge und
Passagiere verwalten können. Persistente Speicherung wird mittels MariaDB umgesetzt.

### Erfolgskriterien

- Ein benutzerfreundliches User Interface. Mindestens als Terminal UI, im besten Fall aber als GUI.
- Persistente Datenhaltung in einer SQL DB (voraussichtlich MariaDB)
- Eine funktionierende Funktion zum Anlegen, bearbeiten und löschen von Daten.

### Risiken und Realisierbarkeit

**Herausforderung:**
Die Datenbankverbindung und Implementierung der Recovery-Funktionalität könnte komplex sein. Außerdem haben wir bisher
keine Erfahrung mit UI-Design in Java.

**Lösungsansatz:**
Frühzeitige Tests, ob wir die DB-Anbindung und das UI-Design umgesetzt bekommen. Ansonsten müssen wir auf eine
Terminal-UI mit Menüstruktur und zur Erzeugung von Testdaten in der Verwaltungsklasse zurückgreifen.

### Business Case

Durch eine zentralisierte und intuitive Plattform wird der Zeitaufwand für Verwaltung und Administration gesenkt.
Wegfallende Lizenzgebühren für die verschiedenen Applikationen sind ein weiterer Vorteil. Das alles führt zu einer
Kostensenkung.

Durch die optimierte Flug- und Passagierverwaltung verbessert sich außerdem die Servicequalität, was die
Kundenzufriedenheit ansteigen lässt.

Alles in allem wird mit SkyNexus eine flexible und moderne Grundlage für die digitale Airline Verwaltung geschaffen, die
nicht nur für große Fluggesellschaften attraktiv sein sollte.

### Schlussfolgerung

Durch den All-in-One Ansatz verbunden mit einer intuitiven Oberfläche bietet SkyNexus eine klare Verbesserung gegenüber
aktuell verfügbaren Verwaltungslösungen. Das Projekt erscheint realisierbar und entspricht den Anforderungen.

## Planung

### Kostenabschätzung

Bei einer Schätzung von 80 – 140 Stunden und einem Stundensatz von 45€ pro Stunde, kommen wir auf 3600€ bis 6300€ an
Arbeitszeitkosten. Die restlichen Kosten sind vernachlässigbar, da kostenfreie Software zum Einsatz kommt.

## Ausblick

### Chancen und Risiken

SkyNexus kann auch in Zukunft durch neue Module erweitert werden. Zum Beispiel könnte ein System zur Optimierung des
Flugplans eingebaut werden. Durch die flexiblen Nutzungsmöglichkeiten können sowohl kleine als auch große
Fluggesellschaften die Software nutzen. Des Weiteren könnte die Software in Zukunft als Cloudanwendung zur Verfügung
stehen. Dadurch würden die Hardwareanforderungen der einzelnen Arbeitsplätze sinken. Statt eines Desktop PCs könnten
Thin-Clients eingesetzt werden.

Auf der anderen Seite muss die Datenbankstruktur perfekt sein, da es ansonsten zu langen Ladezeiten oder Anomalien
kommen könnte. Ein weiteres Risiko wäre eine schlecht umgesetzte UI und die dadurch entstehende ineffizient. Als größtes
Problem sehen wir aber den bestehenden Markt. Wir befürchten das es schwer wird sich gegen die Konkurrenz durchzusetzen
und Kunden zu finden.

## Technische Details

### Architektur

- MVC-Muster
- Defensive Programmierung mit umfassender Validierung
- Einheitliche Fehlerbehandlungsstrategien
- KISS-Prinzip: Einfachheit und Verständlichkeit priorisieren

### Technologie-Stack

- Datenbankschicht: JDBC mit MariaDB
- Logging: SLF4J und Logback
- UI-Framework: JavaFX mit FXML und CSS
