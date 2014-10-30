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

public class ColorHsv {
    double h;
    double s;
    double v;
    
    public ColorHsv(double h, double s, double v) {
        if (h > 360.0) h = 360.0;
        else if (h < 0.0) h = 0.0;
        this.h = h;
        
        if (s > 1.0) s = 1.0;
        else if (s < 0.0) s = 0.0;
        this.s = s;
        
        if (v > 1.0) v = 1.0;
        else if (v < 0.0) v = 0.0;
        this.v = v;
    }
    
    public ColorHsv(ColorHsv rhs) {
        h = rhs.h;
        s = rhs.s;
        v = rhs.v;
    }
    
    public double H() {
        return h;
    }
    
    public double S() {
        return s;
    }
    
    public double V() {
        return v;
    }
    
    public ColorHsv dim(double factor) {
        return new ColorHsv(h, s, v * factor);
    }

    public ColorRgb toRgb() {
        double x = h / 60.0;
        int hi =  (int)(x);
        double f = x - hi;
        
        double p = v * (1.0 - s);
        double q = v * (1.0 - s * f);
        double t = v * (1.0 - s * (1.0 - f));
        
        int r = 0;
        int g = 0;
        int b = 0;
        
        switch (hi) {
        case 1: {
            r = (int)(q * 255.0);
            g = (int)(v * 255.0);
            b = (int)(p * 255.0);
            break;
        }
        case 2: {
            r = (int)(p * 255.0);
            g = (int)(v * 255.0);
            b = (int)(t * 255.0);
            break;
        }
        case 3: {
            r = (int)(p * 255.0);
            g = (int)(q * 255.0);
            b = (int)(v * 255.0);
            break;
        }
        case 4: {
            r = (int)(t * 255.0);
            g = (int)(p * 255.0);
            b = (int)(v * 255.0);
            break;
        }
        case 5: {
            r = (int)(v * 255.0);
            g = (int)(p * 255.0);
            b = (int)(q * 255.0);
            break;
        }
        default: { // 1 & 6
            r = (int)(v * 255.0);
            g = (int)(t * 255.0);
            b = (int)(p * 255.0);
            break;
        }
        }
        
        return new ColorRgb(r, g, b);
    }
    
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("ColorHsv(");
        str.append(h);
        str.append(", ");
        str.append(s);
        str.append(", ");
        str.append(v);
        str.append(')');
        return str.toString();
    }
}
