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
The server also supports a mode which uses a TCP/IP connection to support the
lighting data instead of a direct serial connection. This allows to run the
server on a different hardware than where the Arduino is connected to.

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

The daemon requires at least 3 commandline parameters:

1. The number of LEDs that the connected LED stripe provides
2. The connection type between adalightserver and the LED Controller board.
   Either a direct serial port connection can be used or a connection which
   tunnels the serial port data over TCP/IP.  
   If a direct serial port connection is used parameter 2 must be set to `serial`  
   If the serial port data should be sent over IP instead parameter 2 must be set
   to `ip`
3. In case of connection type `serial` this is the name of the serialport to use,
   e.g. `COM3` or `/dev/ttyusb`.  
   In case of an connection over IP this is the hostname to connect to over TCP.
4. In case of an IP connection this is the port number of the serial2ip converter.
