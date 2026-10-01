package edu.cit.balacy.channel;

import org.springframework.data.jpa.repository.JpaRepository;

interface StockSyncRecordRepository extends JpaRepository<StockSyncRecord, String> {
}
