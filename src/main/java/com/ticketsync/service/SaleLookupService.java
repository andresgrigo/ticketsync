package com.ticketsync.service;

import com.ticketsync.dao.SaleDAO;
import com.ticketsync.dao.SaleDAOImpl;
import com.ticketsync.model.Sale;
import com.ticketsync.util.DatabaseConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public class SaleLookupService {

    private final SaleDAO saleDAO;
    private final ConnectionFactory connFactory;

    public SaleLookupService() {
        this(new SaleDAOImpl(), DatabaseConfig::getConnection);
    }

    SaleLookupService(SaleDAO saleDAO, ConnectionFactory connFactory) {
        this.saleDAO = saleDAO;
        this.connFactory = connFactory;
    }

    public Optional<Sale> getSaleById(int saleId) throws SQLException {
        if (saleId <= 0) {
            return Optional.empty();
        }
        try (Connection conn = connFactory.get()) {
            return saleDAO.findById(conn, saleId);
        }
    }
}
