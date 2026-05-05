package com.ticketsync.service;

import com.ticketsync.dao.SaleDAO;
import com.ticketsync.dao.SaleDAOImpl;
import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import com.ticketsync.util.DatabaseConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Service for looking up completed sales and their line items from the database.
 *
 * <p>Thin façade over {@link SaleDAO} that acquires a database connection for
 * each operation. Constructor-injectable seams allow unit testing without a
 * live database.
 */
public class SaleLookupService {

    private final SaleDAO saleDAO;
    private final ConnectionFactory connFactory;

    /**
     * Creates a production {@code SaleLookupService} backed by the default
     * {@link SaleDAOImpl} and the pooled connection factory.
     */
    public SaleLookupService() {
        this(new SaleDAOImpl(), DatabaseConfig::getConnection);
    }

    SaleLookupService(SaleDAO saleDAO, ConnectionFactory connFactory) {
        this.saleDAO = saleDAO;
        this.connFactory = connFactory;
    }

    /**
     * Finds a sale by its primary key.
     *
     * @param saleId the sale ID to look up; non-positive values return empty immediately
     * @return the matching sale, or {@link java.util.Optional#empty()} if not found
     * @throws java.sql.SQLException if a database error occurs
     */
    public Optional<Sale> getSaleById(int saleId) throws SQLException {
        if (saleId <= 0) {
            return Optional.empty();
        }
        try (Connection conn = connFactory.get()) {
            return saleDAO.findById(conn, saleId);
        }
    }

    /**
     * Returns all sale items that belong to the given sale.
     *
     * @param saleId the sale ID; non-positive values return an empty list immediately
     * @return unmodifiable list of sale items; never {@code null}
     * @throws java.sql.SQLException if a database error occurs
     */
    public List<SaleItem> getSaleItemsBySaleId(int saleId) throws SQLException {
        if (saleId <= 0) {
            return List.of();
        }
        try (Connection conn = connFactory.get()) {
            return saleDAO.findSaleItemsBySaleId(conn, saleId);
        }
    }
}
