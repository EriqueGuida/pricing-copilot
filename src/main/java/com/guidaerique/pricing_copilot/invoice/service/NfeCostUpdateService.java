package com.guidaerique.pricing_copilot.invoice.service;

import com.guidaerique.pricing_copilot.audit.domain.PriceHistory;
import com.guidaerique.pricing_copilot.audit.domain.PriceOrigin;
import com.guidaerique.pricing_copilot.audit.repository.PriceHistoryRepository;
import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.domain.Product;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import com.guidaerique.pricing_copilot.invoice.domain.ProcessedInvoice;
import com.guidaerique.pricing_copilot.invoice.dto.InvoiceImportResponse;
import com.guidaerique.pricing_copilot.invoice.repository.ProcessedInvoiceRepository;
import com.guidaerique.pricing_copilot.shared.exception.DuplicateInvoiceException;
import com.guidaerique.pricing_copilot.shared.exception.InvalidInvoiceXmlException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class NfeCostUpdateService {

    private final ProcessedInvoiceRepository processedInvoiceRepository;
    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    public NfeCostUpdateService(
            ProcessedInvoiceRepository processedInvoiceRepository,
            ProductRepository productRepository,
            PriceHistoryRepository priceHistoryRepository) {
        this.processedInvoiceRepository = processedInvoiceRepository;
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @Transactional
    public InvoiceImportResponse processInvoiceXml(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidInvoiceXmlException("O arquivo XML da NF-e não foi fornecido.");
        }

        NfeParsedData parsedData = parseNfeXmlSafely(file);

        if (processedInvoiceRepository.existsByAccessKey(parsedData.accessKey())) {
            throw new DuplicateInvoiceException(parsedData.accessKey());
        }

        int processedCount = 0;
        int pricesAdjustedCount = 0;

        for (NfeItem item : parsedData.items()) {
            var productOpt = productRepository.findBySku(item.sku());
            if (productOpt.isEmpty()) {
                continue;
            }

            Product product = productOpt.get();
            BigDecimal newCost = item.unitCost();
            product.setCostPrice(newCost);
            processedCount++;

            Category category = product.getCategory();
            BigDecimal minMargin = category.getMinMargin();
            BigDecimal currentPrice = product.getCurrentPrice();

            BigDecimal currentMargin = currentPrice.subtract(newCost)
                    .divide(currentPrice, 4, RoundingMode.HALF_UP);

            if (currentMargin.compareTo(minMargin) < 0) {
                BigDecimal divisor = BigDecimal.ONE.subtract(minMargin);
                if (divisor.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalStateException("Piso de margem da categoria inválido: " + minMargin);
                }

                // CORREÇÃO ADR-001: Uso estrito de CEILING no piso para proteger a margem mínima
                BigDecimal calculatedFloorPrice = newCost.divide(divisor, 2, RoundingMode.CEILING);
                BigDecimal oldPrice = product.getCurrentPrice();

                product.setCurrentPrice(calculatedFloorPrice);
                pricesAdjustedCount++;

                PriceHistory history = new PriceHistory(
                        product,
                        oldPrice,
                        calculatedFloorPrice,
                        PriceOrigin.COST_UPDATE,
                        null,
                        "Reajuste automático para garantia do piso de margem via NF-e chave " + parsedData.accessKey()
                );
                priceHistoryRepository.save(history);
            }

            productRepository.save(product);
        }

        ProcessedInvoice invoice = new ProcessedInvoice(
                parsedData.accessKey(),
                parsedData.issuerCnpj(),
                parsedData.totalAmount(),
                processedCount,
                pricesAdjustedCount
        );

        try {
            processedInvoiceRepository.saveAndFlush(invoice);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateInvoiceException(parsedData.accessKey());
        }

        return new InvoiceImportResponse(
                parsedData.accessKey(),
                parsedData.issuerCnpj(),
                parsedData.totalAmount(),
                processedCount,
                pricesAdjustedCount,
                "NF-e processada com sucesso."
        );
    }

    private NfeParsedData parseNfeXmlSafely(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);
            doc.getDocumentElement().normalize();

            String accessKey = extractAccessKey(doc);
            if (accessKey == null || accessKey.length() != 44 || !accessKey.chars().allMatch(Character::isDigit)) {
                throw new InvalidInvoiceXmlException("Chave de acesso da NF-e inválida ou ausente. Esperados 44 dígitos numéricos.");
            }

            String issuerCnpj = extractTagValue(doc.getDocumentElement(), "emit", "CNPJ");
            if (issuerCnpj == null || issuerCnpj.isBlank()) {
                issuerCnpj = "00000000000000";
            }

            String totalAmountStr = extractTagValue(doc.getDocumentElement(), "ICMSTot", "vNF");
            BigDecimal totalAmount = totalAmountStr != null ? new BigDecimal(totalAmountStr) : BigDecimal.ZERO;

            NodeList detNodes = doc.getElementsByTagName("det");
            List<NfeItem> items = new ArrayList<>();

            for (int i = 0; i < detNodes.getLength(); i++) {
                Element detElement = (Element) detNodes.item(i);
                NodeList prodNodes = detElement.getElementsByTagName("prod");
                if (prodNodes.getLength() > 0) {
                    Element prodElement = (Element) prodNodes.item(0);
                    String cProd = getDirectTextContent(prodElement, "cProd");
                    String vUnCom = getDirectTextContent(prodElement, "vUnCom");

                    if (cProd != null && vUnCom != null) {
                        items.add(new NfeItem(cProd.trim(), new BigDecimal(vUnCom.trim())));
                    }
                }
            }

            return new NfeParsedData(accessKey, issuerCnpj, totalAmount, items);
        } catch (InvalidInvoiceXmlException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidInvoiceXmlException("Falha na interpretação estrutural do arquivo XML da NF-e: " + e.getMessage(), e);
        }
    }

    private String extractAccessKey(Document doc) {
        NodeList chNfeNodes = doc.getElementsByTagName("chNFe");
        if (chNfeNodes.getLength() > 0) {
            return chNfeNodes.item(0).getTextContent().trim();
        }

        NodeList infNfeNodes = doc.getElementsByTagName("infNFe");
        if (infNfeNodes.getLength() > 0) {
            Element infNfe = (Element) infNfeNodes.item(0);
            String idAttr = infNfe.getAttribute("Id");
            if (idAttr != null && idAttr.startsWith("NFe")) {
                return idAttr.substring(3).trim();
            }
        }
        return null;
    }

    private String extractTagValue(Element parent, String parentTag, String childTag) {
        NodeList parentNodes = parent.getElementsByTagName(parentTag);
        if (parentNodes.getLength() > 0) {
            Element pElem = (Element) parentNodes.item(0);
            return getDirectTextContent(pElem, childTag);
        }
        return null;
    }

    private String getDirectTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }

    private record NfeParsedData(
            String accessKey,
            String issuerCnpj,
            BigDecimal totalAmount,
            List<NfeItem> items
    ) {}

    private record NfeItem(
            String sku,
            BigDecimal unitCost
    ) {}
}