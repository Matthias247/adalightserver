parameters([
    color: [type: ColorRgb, default: new ColorRgb(255,0,0)],
    brightness: [type: double, default: 1.0]
])

ledApi.setAllLedsToColor(color.dim(brightness))
ledApi.flush()