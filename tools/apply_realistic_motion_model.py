from pathlib import Path

path = Path('app/src/main/java/com/seannmichael/mockdrive/MockLocationService.java')
text = path.read_text()

replacements = []

replacements.append((
"""    private int configuredUpdateMs = 1000;\n    private volatile boolean gpsProviderReady;\n""",
"""    private int configuredUpdateMs = 1000;\n    private double simulatedSpeedMps;\n    private float simulatedBearingDegrees;\n    private boolean simulatedBearingInitialized;\n    private double gpsDriftNorthMeters;\n    private double gpsDriftEastMeters;\n    private volatile boolean gpsProviderReady;\n"""))

replacements.append((
"""                Random random = new Random();\n                log(\"CONFIG\", \"speedMph=\" + averageMph + \" variation=\" + variation + \" updateMs=\" + configuredUpdateMs + \" holdAtDestination=\" + hold + \" providers=gps+fused\");\n\n                int segment = 0;\n""",
"""                Random random = new Random();\n                simulatedSpeedMps = 0;\n                simulatedBearingDegrees = 0;\n                simulatedBearingInitialized = false;\n                gpsDriftNorthMeters = 0;\n                gpsDriftEastMeters = 0;\n                log(\"CONFIG\", \"speedMph=\" + averageMph + \" variation=\" + variation + \" updateMs=\" + configuredUpdateMs + \" holdAtDestination=\" + hold + \" providers=gps+fused motionModel=realistic-v1\");\n\n                int segment = 0;\n"""))

replacements.append((
"""                    long loopStarted = SystemClock.elapsedRealtime();\n                    double mph = averageMph * (1 + ((random.nextDouble() * 2 - 1) * variation));\n                    double metersPerSecond = mph * 0.44704;\n                    double step = metersPerSecond * configuredUpdateMs / 1000.0;\n""",
"""                    long loopStarted = SystemClock.elapsedRealtime();\n                    double targetMph = averageMph * (1 + ((random.nextDouble() * 2 - 1) * variation));\n                    double targetSpeedMps = targetMph * 0.44704;\n                    double intervalSeconds = configuredUpdateMs / 1000.0;\n                    double accelerationLimit = 1.8 * intervalSeconds;\n                    double brakingLimit = 3.2 * intervalSeconds;\n                    simulatedSpeedMps = approach(simulatedSpeedMps, targetSpeedMps,\n                            targetSpeedMps >= simulatedSpeedMps ? accelerationLimit : brakingLimit);\n                    double metersPerSecond = simulatedSpeedMps;\n                    double mph = metersPerSecond / 0.44704;\n                    double step = metersPerSecond * intervalSeconds;\n"""))

replacements.append((
"""                    float heading = bearing(from, to);\n                    double traveledMeters = cumulative[segment] + Math.min(onSegment, length);\n""",
"""                    float rawHeading = bearing(from, to);\n                    float heading = smoothBearing(rawHeading, configuredUpdateMs / 1000.0);\n                    Point injectedPoint = applyGpsDrift(current, Math.max(accuracy, 3f));\n                    float dynamicAccuracy = (float) clamp(accuracy + random.nextGaussian() * 0.55, 2.5, 7.5);\n                    double traveledMeters = cumulative[segment] + Math.min(onSegment, length);\n"""))

replacements.append((
"""                            .put(\"requestedSpeedMph\", mph)\n                            .put(\"requestedSpeedMps\", metersPerSecond)\n                            .put(\"requestedBearingDegrees\", heading)\n""",
"""                            .put(\"targetSpeedMph\", targetMph)\n                            .put(\"targetSpeedMps\", targetSpeedMps)\n                            .put(\"requestedSpeedMph\", mph)\n                            .put(\"requestedSpeedMps\", metersPerSecond)\n                            .put(\"rawRouteBearingDegrees\", rawHeading)\n                            .put(\"requestedBearingDegrees\", heading)\n                            .put(\"gpsDriftNorthMeters\", gpsDriftNorthMeters)\n                            .put(\"gpsDriftEastMeters\", gpsDriftEastMeters)\n                            .put(\"routeLatitude\", current.lat)\n                            .put(\"routeLongitude\", current.lon)\n                            .put(\"dynamicAccuracyMeters\", dynamicAccuracy)\n"""))

replacements.append((
"""                    injectAndRecord(current, heading, (float) metersPerSecond, accuracy, runtime);\n""",
"""                    injectAndRecord(injectedPoint, heading, (float) metersPerSecond, dynamicAccuracy, runtime);\n"""))

insert_before = """    private static String safe(String value) { return value == null || value.trim().isEmpty() ? \"Unknown error\" : value; }\n"""
helpers = """    private double approach(double current, double target, double maxDelta) {\n        if (current < target) return Math.min(target, current + maxDelta);\n        return Math.max(target, current - maxDelta);\n    }\n\n    private float smoothBearing(float target, double intervalSeconds) {\n        if (!simulatedBearingInitialized) {\n            simulatedBearingDegrees = target;\n            simulatedBearingInitialized = true;\n            return simulatedBearingDegrees;\n        }\n        float delta = ((target - simulatedBearingDegrees + 540f) % 360f) - 180f;\n        float maxTurn = (float) Math.max(8, 42 * intervalSeconds);\n        if (delta > maxTurn) delta = maxTurn;\n        if (delta < -maxTurn) delta = -maxTurn;\n        simulatedBearingDegrees = (simulatedBearingDegrees + delta + 360f) % 360f;\n        return simulatedBearingDegrees;\n    }\n\n    private Point applyGpsDrift(Point point, float accuracyMeters) {\n        double driftScale = Math.max(.08, Math.min(.35, accuracyMeters * .06));\n        gpsDriftNorthMeters = clamp(gpsDriftNorthMeters * .94 + motionNoise() * driftScale, -2.5, 2.5);\n        gpsDriftEastMeters = clamp(gpsDriftEastMeters * .94 + motionNoise() * driftScale, -2.5, 2.5);\n        double lat = point.lat + gpsDriftNorthMeters / 111320.0;\n        double lonScale = 111320.0 * Math.max(.2, Math.cos(Math.toRadians(point.lat)));\n        double lon = point.lon + gpsDriftEastMeters / lonScale;\n        return new Point(lat, lon);\n    }\n\n    private double motionNoise() {\n        return new Random(SystemClock.elapsedRealtimeNanos() ^ Thread.currentThread().getId()).nextGaussian();\n    }\n\n""" + insert_before
replacements.append((insert_before, helpers))

for old, new in replacements:
    if old not in text:
        raise SystemExit('Expected source block not found:\n' + old[:180])
    text = text.replace(old, new, 1)

path.write_text(text)
print('Applied realistic motion model')
