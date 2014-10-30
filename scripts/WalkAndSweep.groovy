parameters([
    interval: [type: int, default: 50],
    spread:   [type: int, default: 5],
    spreadfactor: [type: double, default: 0.5]
])

enum Direction { Left, Right }
direction = Direction.Right
currentPixelIndex = 0
hi = 0
ledCount = ledApi.ledCount

repeat(interval) {
    double h = (double)hi
    double s = 1.0
    double v = 1.0
    ColorRgb currentColor = new ColorHsv(h, s, v).toRgb();
    
    ledApi.setAllLedsToColor(new ColorRgb(0,0,0));
    for (int i = currentPixelIndex - spread; i <= currentPixelIndex + spread; i++) {
        if (i < 0 || i >= ledCount) continue
        int dist = Math.abs(i - currentPixelIndex) // Distance from the middle
        double brightness = Math.pow(spreadfactor, (double)dist)
        ledApi.setLedColor(i, currentColor.dim(brightness))
    }
    ledApi.flush()
    
    if (direction == Direction.Right) {
        currentPixelIndex++
        if (currentPixelIndex == ledCount) {
            currentPixelIndex = ledCount - 2
            direction = Direction.Left
        }
    }
    else {
        currentPixelIndex--
        if (currentPixelIndex == -1) {
            currentPixelIndex = 1
            direction = Direction.Right
        }
    }
    
    hi++
    if (hi == 360) hi = 0
};

