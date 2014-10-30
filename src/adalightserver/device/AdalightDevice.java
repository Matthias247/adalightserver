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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import adalightserver.types.ColorRgb;
import adalightserver.types.LedApi;
import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

public class AdalightDevice implements LedApi {
    private String comPort;
    private SerialPort serialPort = null;
    private OutputStream outputStream;
    private Thread writeThread = null;

    private static final int BAUDRATE = 115200;
    private static final int DATA_BITS = SerialPort.DATABITS_8;
    private static final int STOP_BITS = SerialPort.STOPBITS_1;
    private static final int PARITY = SerialPort.PARITY_NONE;
    private static final int MAX_LEDS = 1024;

    private List<ColorRgb> frontBuffer = new ArrayList<ColorRgb>();
    private List<ColorRgb> backBuffer = new ArrayList<ColorRgb>();
    private boolean bufferUpdated = false;

    private Object mutex = new Object();
    private Boolean stopThread = true;

    public AdalightDevice(String comPort) {
        this.comPort = comPort;
    }

    public void openPort() throws Exception {

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

    public void closePort() {
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

    private void writeThreadProc() {
        ByteBuffer buffer = null;
        int bufferSize = -1;
        boolean stop = false;

        while (!stop) {
            synchronized (mutex) {
                if (bufferUpdated || stopThread) {
                    stop = stopThread;
                    bufferUpdated = false;
                    int ledCount = frontBuffer.size();
                    int reqSize = ledCount * 3 + 6;
                    if (bufferSize != reqSize) {
                        buffer = ByteBuffer.allocate(ledCount * 3 + 6);
                        bufferSize = reqSize;
                    }
                    else {
                        buffer.clear();
                    }

                    // System.out.println("Writing " + ledCount + " leds");

                    // Initialize buffer header
                    int ledsCountHi = ((ledCount - 1) >> 8) & 0xff;
                    int ledsCountLo = (ledCount  - 1) & 0xff;

                    buffer.put((byte)'A');
                    buffer.put((byte)'d');
                    buffer.put((byte)'a');
                    buffer.put((byte)ledsCountHi);
                    buffer.put((byte)ledsCountLo);
                    buffer.put((byte)(ledsCountHi ^ ledsCountLo ^ 0x55));

                    for (int i = 0; i < ledCount; i++) {
                        ColorRgb c = frontBuffer.get(i);
                        buffer.put((byte)c.R());
                        buffer.put((byte)c.G());
                        buffer.put((byte)c.B());
                    }
                }
                else {
                    // Wait 3 seconds or until interrupted
                    try {
                        mutex.wait(3000);
                    } catch (InterruptedException e) {
                    }
                }
            }

            if (buffer != null) {
                try {
                    outputStream.write(buffer.array(), 0, buffer.position());
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void setLedCount(int ledCount) throws Exception {
        if (ledCount > MAX_LEDS)
            throw new Exception("Maximum LED count exceeded");

        synchronized (mutex) {
            // System.out.println("Setting count to " + ledCount);
            if (ledCount == backBuffer.size()) return;
            backBuffer = new ArrayList<ColorRgb>(ledCount);
            for (int i = 0; i < ledCount; i++) {
                ColorRgb c = new ColorRgb((short)0, (short)0, (short)0);
                backBuffer.add(c);
            }
        }
    }

    @Override
    public int getLedCount() {
        synchronized (mutex) {
            return backBuffer.size();
        }
    }

    @Override
    public void setLedColor(int position, ColorRgb color) throws Exception {
        synchronized (mutex) {
            if (position < 0 || position >= backBuffer.size()) {
                throw new Exception("Index of ouf bounds");
            }
            backBuffer.set(position, color);
        }
    }

    @Override
    public void setAllLedsToColor(ColorRgb color) {
        synchronized (mutex) {
            for (int i = 0; i < backBuffer.size(); i++) {
                backBuffer.set(i, color);
            }
        }
    }

    @Override
    public void flush() {
        synchronized (mutex) {
            if (frontBuffer.size() != backBuffer.size()) {
                frontBuffer = new ArrayList<ColorRgb>(backBuffer);
            }
            else {
                for (int i = 0; i < frontBuffer.size(); i++) {
                    frontBuffer.set(i, backBuffer.get(i));
                }
            }

            bufferUpdated = true;
            mutex.notifyAll();
        }
    }

}
