package edu.cit.balacy.channel;

public interface MarketplaceChannel {

    void publishStockChanged(String productId, int available);
}
