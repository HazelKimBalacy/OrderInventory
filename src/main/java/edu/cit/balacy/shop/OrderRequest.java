package edu.cit.balacy.shop;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** POST /api/orders body: { items: [{ productId, quantity }, ...] }. */
public record OrderRequest(
        @NotEmpty(message = "An order must contain at least one item")
        @Valid
        List<LineItem> items
) {
    public record LineItem(
            @NotBlank String productId,
            @Min(value = 1, message = "Quantity must be at least 1") int quantity
    ) {
    }
}
