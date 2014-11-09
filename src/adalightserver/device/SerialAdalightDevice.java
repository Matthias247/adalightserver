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

import java.io.IOException;
import java.util.Enumeration;
import adalightserver.types.ColorRgb;
import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

public class SerialAdalightDevice extends AdalightDevice {
    private String comPort;
    private SerialPort serialPort = null;

    private static final int BAUDRATE = 115200;
    private static final int DATA_BITS = SerialPort.DATABITS_8;
    private static final int STOP_BITS = SerialPort.STOPBITS_1;
    private static final int PARITY = SerialPort.PARITY_NONE;

    public SerialAdalightDevice(String comPort) {
        this.comPort = comPort;
    }

    @Override
    public void open() throws Exception {

        Boolean foundPort = false;
        CommPortIdentifier serialPortId = null;
        if (serialPort != null) return;

        @SuppressWarnings("unchecked")
        Enumeration<CommPortIdentifier> enumComm = CommPortIdentifier.getPortIdentifiers();
        while(enumComm.hasMoreElements()) {
            serialPortId = enumComm.nextElement();
            if (comPort.contentEquals(serialPortId.getName())) {
                foundPort = true;
                break;
            }
        }

        if (foundPort != true)
            throw new Exception("Can not find serial port " + comPort);		

        try {
            serialPort = (SerialPort) serialPortId.open("AdaLightServer", 500);
        } catch (PortInUseException e) {
            throw new Exception("Serial port is in use");
        }

        try {
            outputStream = serialPort.getOutputStream();
        } catch (IOException e) {
            throw new Exception("Error getting output stream: " + e.getMessage());
        }

        try {
            serialPort.setSerialPortParams(BAUDRATE, DATA_BITS, STOP_BITS, PARITY);
        } catch(UnsupportedCommOperationException e) {
            throw new Exception("Can not set port parameters: " + e.getMessage());
        }

        stopThread = false;
        writeThread = new Thread(() -> writeThreadProc());
        writeThread.start();
    }

    @Override
    public void close() {
        System.out.println("Closing Serial Port");
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
        if (serialPort != null) {
            try { serialPort.close(); }
            catch (Exception e) {}
            serialPort = null;
        }
    }
}
