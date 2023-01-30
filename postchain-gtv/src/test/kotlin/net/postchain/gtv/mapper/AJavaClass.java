package net.postchain.gtv.mapper;

import java.util.Objects;

public class AJavaClass {
    private final String value;

    public AJavaClass(@Name(name = "value") String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AJavaClass aJavaClass = (AJavaClass) o;
        return Objects.equals(value, aJavaClass.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
