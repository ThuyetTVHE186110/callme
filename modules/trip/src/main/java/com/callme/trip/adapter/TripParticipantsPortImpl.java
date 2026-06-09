package com.callme.trip.adapter;

import com.callme.common.port.TripParticipantsPort;
import com.callme.common.port.dto.TripParticipants;
import com.callme.trip.entity.TripStatus;
import com.callme.trip.repository.TripRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Adapter side of TripParticipantsPort — lets rating verify a rating's rater/ratee against the real trip record without depending on this module's internals. */
@Component
public class TripParticipantsPortImpl implements TripParticipantsPort {

    private final TripRepository tripRepository;

    public TripParticipantsPortImpl(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    @Override
    public Optional<TripParticipants> findParticipants(UUID tripId) {
        return tripRepository.findById(tripId)
                .map(trip -> new TripParticipants(trip.getCustomerId(), trip.getDriverId(), trip.getStatus() == TripStatus.COMPLETED));
    }
}
