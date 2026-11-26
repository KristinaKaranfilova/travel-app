package mk.diplomska.travel.itinerary;

/** Темпо на патувањето: колку атракции дневно. */
public enum Pace {
    RELAXED(4),
    NORMAL(5),
    INTENSIVE(7);

    private final int placesPerDay;

    Pace(int placesPerDay) {
        this.placesPerDay = placesPerDay;
    }

    public int placesPerDay() {
        return placesPerDay;
    }
}
