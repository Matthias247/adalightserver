/*
 * Copyright 2014 Matthias Einwag
 *
 * The author licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package adalightserver;

import java.io.IOException;
import java.nio.file.Paths;

import adalightserver.device.AdalightDevice;
import adalightserver.device.IpAdalightDevice;
import adalightserver.device.SerialAdalightDevice;
import adalightserver.http.HttpServer;
import adalightserver.scripting.ScriptManager;
import adalightserver.types.ColorRgb;

public class Main {
    
    private static void printUsageHelp() {
        System.out.println("Usage: adalightserver nrLeds mode [serialport | [hostname port]]");
        System.out.println("nrLeds (integer): Number of connected LEDs");
        System.out.println("mode   (string) : ip or serial.");
        System.out.println("  In case of serial the name of the serial port must follow");
        System.out.println("  In case of ip the hostname and the port number of the ip2serial");
        System.out.println("  daemon must follow");
        System.out.println("");
    }
    
    public static void main(String [] args) {
        if (args.length < 3) {
            printUsageHelp();
            return;
        }
        
        AdalightDevice device = null;
        String mode = "serial";
        String comPort = "COM3";
        String host = "localhost";
        int port = 80;
        int ledCount = 50;
        
        try {
            ledCount = Integer.parseInt(args[0]);
        } catch (Exception e) {
            System.err.println("Can not convert " + args[0] + " to the number of attached LEDs");
            printUsageHelp();
            return;
        }
        
        mode = args[1];
        if (mode.equals("serial")) {
            comPort = args[2];
        } else if (mode.equals("ip")) {
            if (args.length < 4) {
                printUsageHelp();
                return;
            }
            host = args[2];
            try {
                port = Integer.parseInt(args[3]);
            } catch (Exception e) {
                System.err.println("Can not convert " + args[3] + " to the used port number");
                printUsageHelp();
                return;
            }
        } else {
            System.out.println("Invalid mode: " + mode);
            printUsageHelp();
            return;
        }
        
        System.out.println("Starting Adalightserver!");
        System.out.println("Press any key to shut down");
        
        if (mode.equals("ip")) {
            device = new IpAdalightDevice(host, port);
        } else if (mode.equals("serial")) {
            device = new SerialAdalightDevice(comPort);
        }
        
        try {
            device.open();
            device.setLedCount(ledCount);
            device.setAllLedsToColor(new ColorRgb(0,0,0));
            device.flush();
        }
        catch (Exception e) {
            System.out.println(e);
            return;
        }
        
        ScriptManager scriptManager = new ScriptManager(Paths.get("scripts"));
        scriptManager.startWatch();
        
        IController controller = new Controller(device, scriptManager);
        HttpServer server = new HttpServer(controller);
        server.start();
        
        try {
            System.in.read();
        } catch (IOException e) {}
    
        server.stop();
        controller.stop();
        
        scriptManager.stopWatch();
        
        device.close();
	}
}
