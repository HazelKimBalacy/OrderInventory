package edu.cit.balacy.supplier;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * All XML and all LegacySupply status codes are confined to this class.
 * Element names follow the interface manual, Rev. 2.3.1.
 */
@Component
class XmlTranslator {

    // ---- building requests -------------------------------------------------

    String loginXml(String clientId, String apiKey) {
        return "<AuthRequest><ClientId>" + esc(clientId) + "</ClientId><ApiKey>" + esc(apiKey) + "</ApiKey></AuthRequest>";
    }

    String purchaseOrderXml(String buyerRef, String sku, int cases) {
        return "<PurchaseOrder>"
                + "<SupplierSku>" + esc(sku) + "</SupplierSku>"
                + "<Qty>" + cases + "</Qty>"
                + "<BuyerRef>" + esc(buyerRef) + "</BuyerRef>"
                + "</PurchaseOrder>";
    }

    // ---- parsing responses -------------------------------------------------

    String parseSessionToken(String xml) {
        return required(parse(xml), "SessionToken");
    }

    LegacySupplyClient.Ack parseAck(String xml) {
        Document doc = parse(xml);
        return new LegacySupplyClient.Ack(required(doc, "PoNumber"), required(doc, "StatusCode"));
    }

    String parseStatusCode(String xml) {
        return required(parse(xml), "StatusCode");
    }

    /** PurchaseOrderList -> the first order, if any. */
    Optional<LegacySupplyClient.Ack> parseFirstOrder(String xml) {
        Document doc = parse(xml);
        String po = text(doc, "PoNumber");
        if (po == null || po.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LegacySupplyClient.Ack(po, text(doc, "StatusCode")));
    }

    List<LegacySupplyClient.CatalogItem> parseCatalog(String xml) {
        Document doc = parse(xml);
        List<LegacySupplyClient.CatalogItem> items = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName("Item");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String sku = childText(el, "SupplierSku");
            String pack = childText(el, "PackSize");
            if (sku == null || pack == null) {
                continue;
            }
            try {
                items.add(new LegacySupplyClient.CatalogItem(sku, childText(el, "Description"), Integer.parseInt(pack)));
            } catch (NumberFormatException ignored) {
                // skip malformed item
            }
        }
        return items;
    }

    /** Extracts <Code> from an LSError body. Never throws; returns null if unreadable. */
    String parseErrorCode(String body) {
        try {
            return text(parse(body), "Code");
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- status translation (their codes -> our enum) ----------------------

    SupplierOrderStatus mapStatus(String code) {
        if (code == null) {
            return SupplierOrderStatus.UNKNOWN;
        }
        return switch (code.trim()) {
            case "10", "20" -> SupplierOrderStatus.SUBMITTED;   // 10 Accepted, 20 Picking
            case "30" -> SupplierOrderStatus.IN_TRANSIT;        // 30 Shipped
            case "40" -> SupplierOrderStatus.DELIVERED;         // 40 Delivered
            // Anything else (e.g. a cancellation code the manual does not list)
            // is UNKNOWN. Read the code from the warning log, then add it here.
            default -> SupplierOrderStatus.UNKNOWN;
        };
    }

    // ---- helpers -----------------------------------------------------------

    private Document parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // XXE protection
            return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml == null ? "" : xml)));
        } catch (Exception e) {
            // A 2xx with an unreadable body is ambiguous: the PO may exist.
            // Treat as transient; a retry with the same X-Request-Id is safe.
            throw new SupplierUnavailableException("Unreadable supplier response", e);
        }
    }

    private String text(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private String childText(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private String required(Document doc, String tag) {
        String v = text(doc, tag);
        if (v == null || v.isEmpty()) {
            throw new SupplierUnavailableException("Supplier response missing <" + tag + ">", null);
        }
        return v;
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
