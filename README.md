KFJC-Android-Player
===================
This app streams the mp3 netcast from kfjc.org, a college radio station in Los Altos Hills, California, USA. It's really just UI around the Android MediaPlayer library.

Features:
 - MP3 Streaming!
 - Updates current DJ/Artist/Track info from kfjc website
 - Moderately cool graphical effect during buffering
 - Fullscreen mode loops a trippy video (video not included in github repo)
 - Graceful handling audio focus
 - Android Notification integration
 - Volume control

Test plan
---------
Throw all you can at it:

 - Hardware: connect/disconnect headphones, bluetooth sets, etc
 - Devices: different screen sizes, android os versions (>=3.0 Honeycomb)
 - Internet connection: when it drops out, recovery when it returns. Try streaming on your data plan while driving, say.
 - Interaction with other audio apps: make/take calls, play music from other apps, use spoken map directions while streaming

Please report any crashes or behavior that's strange or unexpected.