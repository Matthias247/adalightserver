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

package adalightserver.device;

import java.net.Socket;
import adalightserver.types.ColorRgb;

public class IpAdalightDevice extends AdalightDevice {
    private String hostAddress;
    private int port;
    private Socket socket;

    public IpAdalightDevice(String hostAddress, int port) {
        this.hostAddress = hostAddress;
        this.port = port;
    }

    @Override
    public void open() throws Exception {
        socket = new Socket(hostAddress, port);
        outputStream = socket.getOutputStream();

        stopThread = false;
        writeThread = new Thread(() -> writeThreadProc());
        writeThread.start();
    }

    @Override
    public void close() {
        System.out.println("Closing Socket");
        synchronized (mutex) {
            // Switch light off
            // Will get flushed before thread stops
            for (int i = 0; i < frontBuffer.size(); i++) {
                frontBuffer.set(i, new ColorRgb(0, 0, 0));
            }
            stopThread = true;
            mutex.notifyAll();
        }
        if (writeThread != null) {
            try {
                writeThread.join();
            } catch (InterruptedException e) {
            }
            writeThread = null;
        }
        if (outputStream != null) {
            try { outputStream.close(); }
            catch (Exception e) {}
            outputStream = null;
        }
        if (socket != null) {
            try { socket.close(); }
            catch (Exception e) {}
            socket = null;
        }
    }
}
