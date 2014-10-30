parameters ([
    interval: [type: int, default: 20],
    brightness: [type: double, default: 1.0]
])

i = 0

repeat (interval) {
    double h = (double)i
    double s = 1.0
    double v = 1.0
    ledApi.setAllLedsToColor(new ColorHsv(h, s, v).dim(brightness).toRgb())
    ledApi.flush()
    
    i++
    if (i == 360) i = 0
}