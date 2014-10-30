parameters([
    interval: [type: int, default: 50],
    spread:   [type: int, default: 5],
    spreadfactor: [type: double, default: 0.7],
    color: [type: ColorRgb, default: new ColorRgb(255,0,0)]
])

enum Direction { Left, Right }
direction = Direction.Right
currentPixelIndex = 0
ledCount = ledApi.ledCount

repeat(interval) {
    ledApi.setAllLedsToColor(new ColorRgb(0,0,0));
    for (int i = currentPixelIndex - spread; i <= currentPixelIndex + spread; i++) {
        if (i < 0 || i >= ledCount) continue
        int dist = Math.abs(i - currentPixelIndex)
        double brightness = Math.pow(spreadfactor, (double)dist)
        ledApi.setLedColor(i, color.dim(brightness))
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
};

