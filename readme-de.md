<div align="center">


<img src="https://github.com/user-attachments/assets/4b137aa3-100f-4e32-ab47-37476a9bdb44" height="120">

# Wolkenberg Stundenplan

Eine effiziente App für die Stundenpläne des Wolkenberg Gymnasiums

<a href="https://play.google.com/store/apps/details?id=de.zenonet.stundenplan"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/de_badge_web_generic.png" height="70"/></a>
</div>

Dies ist ein nativer Android-Client für die (undokumentierte) TimeTable API des Wolkenberg Gymnasiums Michendorf.
Er läuft nativ auf der JVM, um auch unter extremen Bedingungen wie dem furchtbar langsamen Internet im Schulgebäude oder sehr alten/preiswerten Smartphones die beste Startgeschwindigkeit zu gewährleisten.
Zusätzlich verfügt die App über ein blitzschnelles Caching-System, um den Stundenplan so schnell wie möglich laden zu können.

## Wie es funktioniert

Diese App wurde durch Reverse-Engineering der offiziellen App und die Nutzung ihrer APIs entwickelt. Für die Schulserver sieht es so aus, als würden Sie nur die offizielle App verwenden.
In Wirklichkeit aber laden Sie Ihren Stundenplan 8 Mal schneller.


## Zusätzliche Funktionen

- Hausaufgaben Einträge als Teil des Stundenplans
- Informationen über die aktuelle Stunde (Beginn/End-Zeit, Fortschritt, etc.)
- Benachrichtigungen über Änderungen
- Homescreen-Widget
- Status-Benachrichtigung (zeigt in der Schulzeit die nächste Stunde an)
- WearOS-App (App auf Smartwatches, inklusive Fach- und Raumdaten auf dem Zifferblatt)
- Tägliche Zitate
- TokenViewer (Sie können API-Tokens aus der App exportieren, um selbst mit der API herumzuspielen)
