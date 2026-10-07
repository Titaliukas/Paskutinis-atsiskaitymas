package lt.ktu.paskutinisatsiskaitymas.domain;

/** Creates an independent copy of a domain object. */
public interface Prototype<T> {
    T clone();
}
