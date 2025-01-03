[Deutsch](readme-de.md)
# Wolkenberg Stundenplan

[Play Store](https://play.google.com/store/apps/details?id=de.zenonet.stundenplan)

This is a native android client for the (private) TimeTable API of the Wolkenberg Gymnasium Michendorf, Germany. 
It runs natively on the JVM to ensure the best startup performance even under extreme conditions like the awfully slow internet in the school building or very old/cheap smartphones.
Additionally the app features a blazingly fast caching system to allow for loading your timetable as quickly as possible.

## How it works

This App is built by reverse-engineering the official app and making use of its APIs. To the School servers, it looks like you're just using the official app.
In reality though, you're loading your timetable 8 times faster.


## Additional features

- Homework Entries as part of the timetable
- Info about the current lesson (start/end-time, progress, etc.)
- Notifications about changes
- Homescreen widget
- Status notification (always shows your next lesson while you're in school)
- WearOS-App (App on Smartwatches, including subject and room data on watch face)
- Daily quotes
- TokenViewer (you can export API tokens from the app to play around with the API yourself)

<!---
## Startup time comparison

All time measurements are in seconds.

| Test-Device           | Startup time with official client | Startup time with this client | Speed increase |
|-----------------------|-----------------------------------|-------------------------------|----------------|
| Samsung Galaxy A12    | 6.923                             | 0.784                         | 8.83x          |
| Samsung Galaxy Tab S9 | 1.768                             | 0.336[^1]                     | 5.26x          |

Notes about testing conditions:
- Startup time measured from 60 FPS video footage with both apps being completely closed.
- The in-official client is loading the timetable from cache (while checking for online updates)
- The offician timetable also had the chance to cache timetable before the test.
- The startup times is measured as the time between the tab onto the app icon to the first time it's possible to read the timetable.
- All measurements were made while battery saving mode was enabled on the test-device

These conditions are selected because they mimic trying to get to the right room as fast as you can while waiting for the timetable to load.

[^1]: Startup time is capped by app opening animation. With animations disabled in developer settings, the startup time is 0.239s
-->
