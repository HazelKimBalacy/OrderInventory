package edu.cit.balacy.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "channel_state")
class MarketplaceCursor {

    @Id
    private Integer id;

    @Column(name = "feed_cursor", nullable = false)
    private long feedCursor;

    protected MarketplaceCursor() {
    }

    MarketplaceCursor(long feedCursor) {
        this.id = 1;
        this.feedCursor = feedCursor;
    }

    long getFeedCursor() {
        return feedCursor;
    }

    void setFeedCursor(long feedCursor) {
        this.feedCursor = feedCursor;
    }
}
