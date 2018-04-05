# Simple-Server, Spring 2018
A project for my Spring 2018 Network Theory class to create a simple server for (mock) communication between police vehicles and computers monitoring them.

The original detailed assignment documentation given to us by the professor is avilable in Assignment3Description.pdf

Two programs were given to us by the professor. gpsDummy.jar acts as the police vehicles sending GPS information in NMEA 0183 RMC format to the server on UDP port 9099. getVehs.jar acts as a monitor requesting the vehicle information from the server in XML format. Images showing these programs in action are available in gpsDummyExample.png and getVehsExample.png

There are several classes, but they were kept in one file to make turning the assignment in easier.

The server program does the following:
Has one thread listening for UDP messages from the vehicles on UDP port 9099 and dealing with them.
Maintains a list of all the vehicles.
Has one thread listening for TCP connections from monitors and creating new threads to deal with them.
Recieves and parses XML requests from the monitors, and responds to them in JSON format.
There are also threads to delete vehicles and close connections after a 30 second timeout period.

There is also a simple GUI which shows the vehicle list and documents all server activities.
Most of the GUI code was generated using Netbean's GUI maker, but all of the active code was written by me.
There is an example image of the program running in ServerGUIExample.png
