adalightserver
==============

This project contains a daemon for controlling RGB LED stripes like
the adafruit adalight through a webinterface.

The following diagram shows the required hardware and data flow:

~~~~
 +------------------+           +----------------+      +---------+     +------------+
 |   Controller     | HTTP +    |                | USB  |         |     |            |
 | (e.g. phone      | Websocket | adalightserver | UART | Arduino | SPI | LED Stripe |
 |  with a browser) | --------> |                | ---> |         | --> |            |
 +------------------+           +----------------+      +---------+     +------------+
~~~~

This server (developed in Java) sends the RGB data for the individual pixels
through a serial connection to an Arduino board, which feeds the colors through
SPI to the WS2801 LEDs.

For the Arduino board the usage of the firmware in the following repository is
required: https://github.com/adafruit/Adalight/tree/master/Arduino/LEDstream

The actual lighting programs are implemented as Groovy scripts which are
deployed in the `/scripts/` subdirectory. See the directory for example of
scripts.  
The daemon will continuously scan the directory for new scripts and reload them
in case of changes. All available scripts will be announced towards connected
clients.

Scripts can declare parameters. The type of parameters will be announced towards
connected clients, which can set the parameters to any value through the API.

The daemon will listen on port `8081` for incoming connections and will server
HTTP and websocket connections from there. On HTTP static files in the
`/static/` subdirectory will be served. This feature can be used to host a
client application (in form of an `index.html` and further assets) on the
server. For an example client application please see the
https://github.com/Matthias247/adalightclient. This client can be built with
Dart and then be deployed in the `/static/` subdirectory.

For actual controlling of the scripts and status information update a websocket
API is used which listens on the `/ws` path. The client application will utilize
this.

The daemon requires 2 commandline parameters:

1. The name of the COM port which should be used to connect to Arduino
2. The number of LEDs that the connected LED stripe provides. If not supplied a
   number of 50 LEDs will be used.
