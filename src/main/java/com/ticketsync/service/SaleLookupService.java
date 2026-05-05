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
 * Servicio para buscar ventas completadas y sus ítems de línea desde la base de datos.
 *
 * <p>Fachada delgada sobre {@link SaleDAO} que adquiere una conexión de base de datos para
 * cada operación. Las costuras inyectables por constructor permiten realizar pruebas
 * unitarias sin una base de datos activa.
 */
public class SaleLookupService {

    private final SaleDAO saleDAO;
    private final ConnectionFactory connFactory;

    /**
     * Crea un {@code SaleLookupService} de producción respaldado por el
     * {@link SaleDAOImpl} predeterminado y la fábrica de conexiones en pool.
     */
    public SaleLookupService() {
        this(new SaleDAOImpl(), DatabaseConfig::getConnection);
    }

    SaleLookupService(SaleDAO saleDAO, ConnectionFactory connFactory) {
        this.saleDAO = saleDAO;
        this.connFactory = connFactory;
    }

    /**
     * Busca una venta por su clave primaria.
     *
     * @param saleId el ID de venta a buscar; valores no positivos devuelven empty inmediatamente
     * @return la venta coincidente, o {@link java.util.Optional#empty()} si no se encuentra
     * @throws java.sql.SQLException si ocurre un error de base de datos
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
     * Devuelve todos los ítems de venta que pertenecen a la venta dada.
     *
     * @param saleId el ID de venta; valores no positivos devuelven una lista vacía inmediatamente
     * @return lista no modificable de ítems de venta; nunca {@code null}
     * @throws java.sql.SQLException si ocurre un error de base de datos
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
