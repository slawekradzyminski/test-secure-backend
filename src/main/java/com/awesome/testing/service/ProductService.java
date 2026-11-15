package com.awesome.testing.service;

import static com.awesome.testing.utils.EntityUpdater.updateIfNotNull;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.controller.exception.ProductNotFoundException;
import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.dto.product.ProductCreateDto;
import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.dto.product.ProductListDto;
import com.awesome.testing.dto.product.ProductSummaryDto;
import com.awesome.testing.dto.product.ProductUpdateDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.ProductSpecifications;
import com.awesome.testing.repository.pagination.OffsetPageRequest;
import com.awesome.testing.service.stock.StockCommandDispatcher;
import com.awesome.testing.service.stock.StockCommandOutbox;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final StockCommandOutbox stockCommands;
    private final StockCommandDispatcher stockCommandDispatcher;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public ProductService(ProductRepository productRepository, InventoryService inventoryService,
                          StockCommandOutbox stockCommands, StockCommandDispatcher stockCommandDispatcher,
                          PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.stockCommands = stockCommands;
        this.stockCommandDispatcher = stockCommandDispatcher;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getAllProducts() {
        return productRepository.findAll().stream().map(ProductDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductListDto listProducts(int offset, int limit, String category, Boolean inStockOnly) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        Pageable pageable = new OffsetPageRequest(safeOffset, safeLimit);

        Page<ProductEntity> result =
                productRepository.findAll(ProductSpecifications.catalog(category, inStockOnly), pageable);

        List<ProductSummaryDto> content = result.getContent().stream().map(ProductSummaryDto::from).toList();

        return ProductListDto.builder()
                .products(content)
                .total(result.getTotalElements())
                .page(safeOffset)
                .size(safeLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        return ProductDto.from(findProductById(id));
    }

    @Transactional(readOnly = true)
    public ProductDto getProductByName(String name) {
        return productRepository
                .findFirstByNameIgnoreCaseOrderByIdAsc(name)
                .map(ProductDto::from)
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));
    }

    /**
     * Stores the product with its initial stock in the projection and an INITIAL_STOCK command, then,
     * after commit and with no transaction open, sends the command so the product can be ordered as
     * soon as this returns (contract section 4.3). When warehouse-service is down the product is
     * still created and the outbox retries the command.
     */
    public ProductDto createProduct(ProductCreateDto productCreateDto) {
        CreatedProduct created = Objects.requireNonNull(writeTransaction.execute(status -> {
            ProductEntity product = productRepository.save(ProductEntity.from(productCreateDto));
            return new CreatedProduct(ProductDto.from(product),
                    stockCommands.initialStock(product, "admin").getId());
        }));
        stockCommandDispatcher.sendNow(created.commandId());
        return created.product();
    }

    private record CreatedProduct(ProductDto product, long commandId) {
    }

    @Transactional(readOnly = true)
    public VersionedProduct getVersionedProduct(Long id) {
        ProductEntity product = findProductById(id);
        return new VersionedProduct(ProductDto.from(product), product.getVersion());
    }

    public ProductDto updateProduct(Long id, ProductUpdateDto productUpdateDto) {
        return updateProduct(id, productUpdateDto, null).product();
    }

    /**
     * Updates a product. When {@code expectedVersion} is supplied (from If-Match), a mismatch with
     * the stored version fails with 412 before any change is applied.
     *
     * <p>A changed stock quantity is an admin adjustment of warehouse-service's ledger (contract
     * section 4.5), made with no transaction open between a read that checks the product and its
     * version and the write of the other fields. A rejected adjustment (409, 503) leaves the product
     * unchanged.
     */
    public VersionedProduct updateProduct(Long id, ProductUpdateDto productUpdateDto, Long expectedVersion) {
        Integer requestedStock = productUpdateDto.getStockQuantity();
        if (requestedStock != null) {
            Integer currentStock = readTransaction.execute(status -> {
                ProductEntity product = findProductById(id);
                checkVersion(product, expectedVersion);
                return product.getStockQuantity();
            });
            if (!requestedStock.equals(currentStock)) {
                inventoryService.adjust(
                        id,
                        InventoryAdjustmentDto.builder()
                                .delta(requestedStock - currentStock)
                                .reason("Product update")
                                .requestId(UUID.randomUUID())
                                .build(),
                        "admin");
                // The adjustment moved the version; the If-Match check has been made.
                return writeTransaction.execute(status -> applyUpdate(id, productUpdateDto, null));
            }
        }
        return writeTransaction.execute(status -> applyUpdate(id, productUpdateDto, expectedVersion));
    }

    private VersionedProduct applyUpdate(Long id, ProductUpdateDto productUpdateDto, Long expectedVersion) {
        ProductEntity product =
                expectedVersion == null
                        ? findProductById(id)
                        : productRepository
                                .findByIdForUpdate(id)
                                .orElseThrow(() -> new ProductNotFoundException("Product not found"));
        checkVersion(product, expectedVersion);
        applyProductUpdates(productUpdateDto, product);
        productRepository.saveAndFlush(product);
        return new VersionedProduct(ProductDto.from(product), product.getVersion());
    }

    private static void checkVersion(ProductEntity product, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(product.getVersion())) {
            throw new CustomException(
                    "Product was modified by someone else; reload it and try again", HttpStatus.PRECONDITION_FAILED);
        }
    }

    /**
     * Deletes the product and records a DELETE stock command. The row is locked first, so a stock
     * level being applied concurrently (the after-commit initial-stock reply, a level push) either
     * finishes before the delete reads the version or finds the product gone afterwards; the delete
     * never fails on a stale optimistic-lock version.
     */
    @Transactional
    public boolean deleteProduct(Long id) {
        return productRepository
                .findByIdForUpdate(id)
                .map(
                        product -> {
                            try {
                                productRepository.delete(product);
                                productRepository.flush();
                                stockCommands.delete(id);
                            } catch (DataIntegrityViolationException ex) {
                                throw new CustomException(
                                        "Product is referenced by existing data", HttpStatus.CONFLICT, ex);
                            }
                            return true;
                        })
                .orElse(false);
    }

    private void applyProductUpdates(ProductUpdateDto productUpdateDto, ProductEntity product) {
        updateIfNotNull(productUpdateDto.getName(), ProductEntity::setName, product);
        updateIfNotNull(productUpdateDto.getDescription(), ProductEntity::setDescription, product);
        updateIfNotNull(productUpdateDto.getPrice(), ProductEntity::setPrice, product);
        // An empty category on update means "not provided": the course contract sends "" and expects no error.
        updateIfNotNull(emptyToNull(productUpdateDto.getCategory()), ProductEntity::setCategory, product);
        updateIfNotNull(productUpdateDto.getImageUrl(), ProductEntity::setImageUrl, product);
    }

    private ProductEntity findProductById(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException("Product not found"));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
