# Wolkenberg Stundenplan

This is a native android client for the (private) TimeTable API of the Wolkenberg Gymnasium Michendorf, Germany. 
It is written in pure Java to ensure the best startup performance even under extreme conditions like the awfully slow internet in the school building or very old/cheap smartphones

## Startup time comparison


| Test-Device           | Startup time with official client | Startup time with this client | Speed increase |
|-----------------------|-----------------------------------|-------------------------------| -------------- |
| Samsung Galaxy A12    | 6.923                             | 0.784                         | 8.83x          |
| Samsung Galaxy Tab S9 | 1.768                             | 0.336                         | 5.26x          |

Notes about testing conditions:
- Startup time measured from 60 FPS video footage with both apps being completely closed.
- The in-official client is loading the timetable from cache (while checking for online updates)
- The offician timetable also had the chance to cache timetable before the test.
- The startup times is measured as the time between the tab onto the app icon to the first time it's possible to read the timetable.

These conditions are selected because they mimic trying to get to the right room as fast as you can while waiting for the timetable to load.
