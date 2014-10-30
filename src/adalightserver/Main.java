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
import adalightserver.http.HttpServer;
import adalightserver.scripting.ScriptManager;
import adalightserver.types.ColorRgb;

public class Main {
    public static void main(String [] args) {
        System.out.println("Starting Adalightserver!");
        System.out.println("Press any key to shut down");
        
        AdalightDevice device;
        String port = "COM3";
        int ledCount = 50;
        if (args.length >= 1) port = args[0];
        if (args.length >= 2) {
            try {
                ledCount = Integer.parseInt(args[1]);
            } catch (Exception e) {
                System.err.println("Can not convert " + args[1] + " to the number of attached LEDs");
                return;
            }
        }
        
        try {
            device = new AdalightDevice(port);
            device.openPort();
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
        
        device.closePort();
	}
}
