# RTPMenu

Paper plugin for Minecraft Java 1.21.x. Provides `/rtp`, a GUI for selecting Overworld, Nether, or The End, confirmation, a 3-second countdown, and cancellation if the player moves.

## Build

Requires Java 21 and Maven:

```sh
mvn clean package
```

The plugin JAR will be at `target/rtpmenu-1.0.0.jar`.

## Build without a computer

Upload this project to a GitHub repository. Open **Actions**, choose **Build RTPMenu**, and press **Run workflow**. Download the `RTPMenu` artifact from the completed run, extract it, and place the JAR in the server's `plugins` folder. Restart the Paper server.

## Notes

This project targets Paper API 1.21.1 and Java 21. Test on a backup server first. Random teleport searches a bounded number of candidate locations, so it can report no safe location if it fails to find one.
