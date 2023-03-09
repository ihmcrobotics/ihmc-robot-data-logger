IHMC Robot Data Logger
======================
[ Download ](https://search.maven.org/artifact/us.ihmc/ihmc-robot-data-logger)
[ ![ihmc-robot-data-logger](https://maven-badges.herokuapp.com/maven-central/us.ihmc/ihmc-robot-data-logger/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/us.ihmc/ihmc-robot-data-logger)
[ ![buildstatus](https://bamboo.ihmc.us/plugins/servlet/wittified/build-status/LIBS-IHMCROBOTDATALOGGER)](https://bamboo.ihmc.us/plugins/servlet/wittified/build-status/LIBS-IHMCROBOTDATALOGGER)

## Logger computer system requirements

- Tons of hard drive space
	- The logger can take 100GB/hour easily, depending on the number of variables and video streams.
- 4 GB RAM (8 GB when more than one video stream is captured)
- 2.5GHz or faster Intel i7 or Xeon E3, E5 or E7
	- 1 + n cores, where n is the number of video streams
- (optional) BlackMagic Decklink Mini Recorder capture cards
	- [https://www.blackmagicdesign.com/products/decklink](https://www.blackmagicdesign.com/products/decklink)
	- The other Decklink capture cards might work, but are not tested yet.

## Setting up a logging computer's dependencies

- Disable secure boot (Or follow instructions during the Ubuntu installation to enable third party drivers)

### Ubuntu 20.04 (recommended)
- Install Ubuntu 20.04 64 bit (Server is recommended, no need for a GUI)
	- Make sure to install OpenSSH server
- Install IHMC Java Decklink dependencies and Java 8.
    - `sudo apt-get install libavformat58 libavcodec58 libswscale5 libboost-thread1.67.1 openjdk-8-jre`
- (If logging video streams with capture card) Install BlackMagic software
    - Get "Desktop Video 12.1" for Linux from [https://www.blackmagicdesign.com/support/family/capture-and-playback](https://www.blackmagicdesign.com/support/family/capture-and-playback).
        - You do not need the SDK, just the plain Desktop Video product. The registration has a "Download only" link in the bottom left to bypass.
    - Untar Desktop video: `tar xzvf Blackmagic_Desktop_Video_Linux_12.1.tar.gz`
    - Install debian packages: `sudo dpkg -i Blackmagic_Desktop_Video_Linux_12.1/deb/x86_64/desktopvideo_12.1a9_amd64.deb`
    - Possible run `sudo apt --fix-broken install` to install missing dependencies.
- (If logging video streams with capture card) Update Blackmagic firmware for each Decklink card (first card is 0, second 1, etc).
    - `BlackmagicFirmwareUpdater update [Decklink card]`
- Reboot the computer


## Publishing and configuring the logger

- clone ihmc-robot-data-logger
- `cd ihmc-robot-data-logger`
- `gradle deploy`

This will show a deploy GUI which allows installation and setup of a logger on a remote computer.

Note: The logger gets unstable after a few days. To avoid issues, there is a hack in the deploy application to restart the logger at midnight. This makes sure there is a working logger every morning.

### Logging to a Network volume

If you would like to log to a network volume, ~/robotLogs can be a symbolic link to a mount point. The IHMC convention is to create a RobotLogs/incoming directory on the network storage volume, auto-mount this volume using the OS's fstab, and then symlinking the "incoming" directory to be the ~/robotLogs directory

### Customizing the logging location
If you would like to log somewhere other than ~/robotLogs, you can change the directory using the "-d" command line flag when you launch the logger

## Starting the logger

When installed using the `gradle deploy` an systemd script is automatically setup to start the logger on boot. Optionally, a script to restart the logger at midnight is added to cron. 


## Setting up cameras

The easiest way is to use the configuration application from `gradle deploy`. If you want to manually do the setup, follow these steps.

Create a new file ~/.ihmc/CameraSettings.yaml. A basic setup looks like this

```
---
cameras:
- type: CAPTURE_CARD
  camera_id: 1
  name: User-Friendly-Name
  identifier: 1
- type: NETWORK_STREAM
  camera_id: 2
  name: Another-User-Friendly-Name
  identifier: /stream_topic
```
			
This adds two cameras to the logger, a capture card and a stream. The following fields are needed for each camera:

- type: CAPTURE_CARD for Decklink capture cards or NETWORK_STREAM for streaming over DDS/RTPS
- camera_id: An unique id from 0 to 127 to refer to the camera in the static hosts section
- name: A user friendly name used to name the file
- identifier: For CAPTURE_CARD, this is the numeric id of the decklink device, for NETWORK_STREAM this is the DDS topic name 


## Adding static hosts

If the logger cannot auto-discover a host, you can add a static host to `~/.ihmc/ControllerHosts.yaml`. Adding static hosts allows adding a camera to the robot logs.

The file is formatted in YAML format. You can easily add more host/port stanzas. For example, to add `10.0.0.10:8008` and `10.0.0.11:8008` as static hosts, put the following in `~/.ihmc/ControllerHosts.yaml`:

```
---
disableAutoDiscovery: false
hosts:
- hostname: "10.0.0.10"
  port: 8008
- hostname: "10.0.0.11"
  port: 8008
  cameras: [1, 2]
```

This adds the host 10.0.0.10 without cameras and the host 10.0.0.11 with two cameras with camera_id 1 and 2.  

Alternatively, you can start `SCSVisualizer` from `ihmc-robot-data-visualizer` and add hosts using the GUI. After you close the visualizer, the hosts you added will be saved  `~/.ihmc/ControllerHosts.yaml`. You can copy that file to the logger if it is on a different computer.
 
