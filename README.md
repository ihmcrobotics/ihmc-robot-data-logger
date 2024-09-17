IHMC Robot Data Logger
======================
[ Download ](https://search.maven.org/artifact/us.ihmc/ihmc-robot-data-logger)
[ ![ihmc-robot-data-logger](https://maven-badges.herokuapp.com/maven-central/us.ihmc/ihmc-robot-data-logger/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/us.ihmc/ihmc-robot-data-logger)

## Logger computer system requirements

- Tons of hard drive space
	- The logger can take 100GB/hour easily, depending on the number of variables and video streams.
- 4 GB RAM (8 GB when more than one video stream is captured)
- 2.5GHz or faster Intel i7 or Xeon E3, E5 or E7
	- 1 + n cores, where n is the number of video streams
- Capture cards to record video feeds (optional)
  - [Magewell Pro Capture Card](https://www.magewell.com/products/pro-capture-sdi)
  - BlackMagic Decklink Mini Recorder Capture Card (Deprecated)
    - This is deprecated because it only works on Ubuntu 20.04 and is no longer supported by the maintainers of this repository

## Ubuntu 22.04 (recommended)
- Install Ubuntu Desktop 22.04 LTS (its convenient to have a GUI to work with)
	- Make sure to install OpenSSH
- Install the firmware for the Magewell Capture Card: [Pro Capture Linux x86 Driver](https://www.magewell.com/downloads/pro-capture#/driver/linux-x86)
- Setup a workspace where ihmc-robot-data-logger is cloned: `git clone https://github.com/ihmcrobotics/ihmc-robot-data-logger.git`
- Reboot the computer

---
## Publishing and configuring the logger

- Navigate to where you have cloned ihmc-robot-data-logger: `cd ihmc-robot-data-logger`
- From there you can use `gradle deploy`. This will show a deploy GUI which allows installation and setup of a logger on a remote computer.
  - This GUI allows you to select several options for the deployed logger

    ### Logging to a Network volume

    If you would like to log to a network volume, ~/robotLogs can be a symbolic link to a mount point. The IHMC convention is to create a RobotLogs/incoming directory on the network storage volume, auto-mount this volume using the OS's fstab, and then symlinking the "incoming" directory to be the ~/robotLogs directory

    ### Customizing the logging location
    If you would like to log somewhere other than ~/robotLogs, you can change the directory using the "-d" command line flag when you launch the logger
---
## <p style="text-align:center;">Starting the Logger</p>

Depending on how the logger is deployed, it can be setup to automatically start when the computer boots, or restart at the beginning of each day.
To start the logger navigate to `/opt/ihmc/logger/bin/` and run `./IHMCLogger`. This will start the logger manually if you have it configured to not run on boot or restart at the beginning of each day.
The Logger can also be run as a service, to see if thats the case this command `ps aux | grep java` tells you if java processes are running.
The service should be `ihmc-robot-data-logger.service`.

Before starting the logger, you need to setup the camera file, and the host file. These are important to capturing video feeds, as well as logging from specific hosts.
The steps below walk through how to set those two files up. They should be setup on the remote computer that is doing the logging. This is why we recommend using Ubuntu Desktop because setting these up are much simpler if you have access to the GUI.

---

## Setting up cameras

This file holds the information regarding the cameras that the logger will try to capture the feeds from.
It is possible to configure the cameras from the `gradle deploy` but since they get changes often its important to know how to change it manually.

Create a new file ~/.ihmc/CameraSettings.yaml. A basic setup looks like this:

```
cameras:
- type: CAPTURE_CARD_MAGEWELL
  camera_id: 1
  name: User-Friendly-Name
  identifier: 1
- type: NETWORK_STREAM
  camera_id: 2
  name: Another-User-Friendly-Name
  identifier: /stream_topic
```
			
This adds two cameras to the logger, a capture card and a stream. The following fields are needed for each camera:

- type: CAPTURE_CARD_MAGEWELL or CAPTURE_CARD for capture cards or NETWORK_STREAM for streaming over DDS/RTPS
- camera_id: An unique id from 0 to 127 to refer to the camera in the static hosts section
- name: A user friendly name used to name the file of the recorded video when logging
- identifier: For capture cards, this is the numeric id of the device, for NETWORK_STREAM this is the DDS topic name 

---
## Setting up hosts

This file contains the hosts that will be logged when a server starts on that device. It is possible to configure the cameras from the `gradle deploy` but since they get changes often its important to know how to change it manually.

Create a new file `~/.ihmc/ControllerHosts.yaml`. A basic setup looks like this:
```
disableAutoDiscovery: false
hosts:
- hostname: "10.0.0.10"
  port: 8008
- hostname: "10.0.0.11"
  port: 8008
  cameras: [1, 2]
```

This adds the host `10.0.0.10` without cameras and the host `10.0.0.11` with two cameras with camera_id 1 and 2.  

Alternatively, you can start `SCSVisualizer` from `ihmc-robot-data-visualizer` and add hosts using the GUI. After you close the visualizer, the hosts you added will be saved  `~/.ihmc/ControllerHosts.yaml`. You can copy that file to the logger if it is on a different computer.
 
