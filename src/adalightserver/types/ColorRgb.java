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

package adalightserver.types;

import java.security.InvalidParameterException;

public class ColorRgb {
    short r;
    short g;
    short b;
    
    public ColorRgb(int r, int g, int b) {
        this.r = (short)r;
        this.g = (short)g;
        this.b = (short)b;
    }
    
    public ColorRgb(ColorRgb rhs) {
        r = rhs.r;
        g = rhs.g;
        b = rhs.b;
    }
    
    public short R() {
        return r;
    }
    
    public short G() {
        return g;
    }
    
    public short B() {
        return b;
    }
    
    public ColorRgb dim(double factor) {
        return new ColorRgb((int)(r * factor), (int)(g * factor), (int)(b * factor));
    }

	private static int valFromChar(char c) throws InvalidParameterException {
        int a = (int)c;
        if (a >= '0' && a <= '9') {
            return (a - '0');
        }
        else if (a >= 'a' && a <= 'f') {
            return 10 + (a - 'a');
        }
        else if (a >= 'A' && a <= 'F') {
            return 10 + (a - 'A');
        }
        else throw new InvalidParameterException();
    }

    public static ColorRgb parseColor(String colorStr) throws InvalidParameterException {
        if (colorStr.length() != 6)
            throw new InvalidParameterException();
        
        int r = (valFromChar(colorStr.charAt(0)) << 4) + valFromChar(colorStr.charAt(1));
        int g = (valFromChar(colorStr.charAt(2)) << 4) + valFromChar(colorStr.charAt(3));
        int b = (valFromChar(colorStr.charAt(4)) << 4) + valFromChar(colorStr.charAt(5));
        
        if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255)
            throw new InvalidParameterException();
        
        return new ColorRgb(r, g, b);
    }
    
    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("ColorRgb(");
        s.append(r);
        s.append(", ");
        s.append(g);
        s.append(", ");
        s.append(b);
        s.append(')');
        return s.toString();
    }
    
    public String toHexString() {
        StringBuilder s = new StringBuilder();
        if (r < 0x10) s.append('0');
        s.append(Integer.toHexString(r));
        if (g < 0x10) s.append('0');
        s.append(Integer.toHexString(g));
        if (b < 0x10) s.append('0');
        s.append(Integer.toHexString(b));
        return s.toString();
    }
}
