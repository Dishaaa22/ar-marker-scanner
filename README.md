# my_app

AR MARKER SCANNER

An augmented reality mobile application built with Flutter, Dart, Kotlin, and Google ARCore. The application uses the device camera to detect predefined image markers and plays video content directly over the tracked image, creating an interactive AR experience.

How It Works:

1. Open Camera
The user opens the camera through the mobile application.

2. Detect Marker
The application continuously analyzes the camera feed for a predefined image marker.

3. Track Image
Once the marker is detected, its position and movement are tracked.

4. Render Video
A video is rendered over the detected image, creating an augmented-reality effect where the content appears attached to the physical image.

5. Handle Tracking
When the marker moves, the video follows its position.

When the marker is temporarily lost: Video playback pauses.

When the marker is detected again: Video resumes.
