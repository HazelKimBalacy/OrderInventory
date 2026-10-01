package edu.cit.balacy.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "channel_processed_events")
class ProcessedFeedEvent {

    @Id
    @Column(name = "event_id", columnDefinition = "text")
    private String eventId;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    protected ProcessedFeedEvent() {
    }

    ProcessedFeedEvent(String eventId, long sequenceNumber) {
        this.eventId = eventId;
        this.sequenceNumber = sequenceNumber;
    }
}
