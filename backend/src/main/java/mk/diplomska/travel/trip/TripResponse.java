package mk.diplomska.travel.trip;

import mk.diplomska.travel.itinerary.TripPlan;

import java.util.UUID;

public record TripResponse(UUID id, TripPlan plan) {
}
