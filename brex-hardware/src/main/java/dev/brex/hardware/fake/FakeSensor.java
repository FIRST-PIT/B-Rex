package dev.brex.hardware.fake;

import dev.brex.hardware.SensorPort;
import java.util.LinkedHashMap;
import java.util.Map;

/** A sensor whose readings are set by the test. */
public final class FakeSensor implements SensorPort {

    private final String name;
    private final Map<String, Double> readings = new LinkedHashMap<>();
    private final String[] fields;

    public FakeSensor(String name, String... fields) {
        this.name = name;
        this.fields = fields.clone();
        for (String field : fields) {
            readings.put(field, 0.0);
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String[] fields() {
        return fields.clone();
    }

    @Override
    public double read(String field) {
        Double value = readings.get(field);
        if (value == null) {
            throw new IllegalArgumentException("Sensor '" + name + "' has no reading '" + field + "'");
        }
        return value;
    }

    public FakeSensor set(String field, double value) {
        if (!readings.containsKey(field)) {
            throw new IllegalArgumentException("Sensor '" + name + "' has no reading '" + field + "'");
        }
        readings.put(field, value);
        return this;
    }
}
