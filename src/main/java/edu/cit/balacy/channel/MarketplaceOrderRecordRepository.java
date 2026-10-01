package edu.cit.balacy.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface MarketplaceOrderRecordRepository extends JpaRepository<MarketplaceOrderRecord, String> {

    List<MarketplaceOrderRecord> findByDecisionIsNotNullAndDecisionSentFalseOrderByTianggeOrderIdAsc();

    List<MarketplaceOrderRecord> findByDecisionIsNullOrderByTianggeOrderIdAsc();

    List<MarketplaceOrderRecord> findByCancellationPendingTrueAndCancellationSentFalseOrderByTianggeOrderIdAsc();

    List<MarketplaceOrderRecord> findByDecisionAndResolutionIsNullOrderByTianggeOrderIdAsc(String decision);

    List<MarketplaceOrderRecord> findByResolutionIsNotNullAndResolutionSentFalseOrderByTianggeOrderIdAsc();
}
