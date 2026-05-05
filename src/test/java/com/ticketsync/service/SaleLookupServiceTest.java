package com.ticketsync.service;

import com.ticketsync.dao.SaleDAO;
import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleLookupServiceTest {

    @Mock
    private SaleDAO saleDAO;

    @Mock
    private ConnectionFactory connFactory;

    @Mock
    private Connection connection;

    private SaleLookupService service;

    @BeforeEach
    void setUp() {
        service = new SaleLookupService(saleDAO, connFactory);
    }

    @Test
    void defaultConstructor_createsServiceWithoutImmediateIo() {
        SaleLookupService lookupService = new SaleLookupService();

        assertFalse(lookupService == null);
    }

    @Test
    void getSaleById_withInvalidId_returnsEmptyWithoutDaoAccess() throws SQLException {
        assertEquals(Optional.empty(), service.getSaleById(0));
        verifyNoInteractions(connFactory, saleDAO);
    }

    @Test
    void getSaleById_withValidId_returnsDaoResult() throws SQLException {
        Sale sale = new Sale(12, 7, 3, new BigDecimal("42.00"), LocalDateTime.now(), "Booth-4");
        when(connFactory.get()).thenReturn(connection);
        when(saleDAO.findById(connection, 12)).thenReturn(Optional.of(sale));

        Optional<Sale> result = service.getSaleById(12);

        assertEquals(Optional.of(sale), result);
        verify(saleDAO).findById(connection, 12);
    }

    @Test
    void getSaleItemsBySaleId_withInvalidId_returnsEmptyListWithoutDaoAccess() throws SQLException {
        assertEquals(List.of(), service.getSaleItemsBySaleId(-1));
        verifyNoInteractions(connFactory, saleDAO);
    }

    @Test
    void getSaleItemsBySaleId_withValidId_returnsDaoItems() throws SQLException {
        SaleItem item = new SaleItem();
        item.setSeatId(5);
        item.setPricePaid(new BigDecimal("21.00"));
        when(connFactory.get()).thenReturn(connection);
        when(saleDAO.findSaleItemsBySaleId(connection, 12)).thenReturn(List.of(item));

        List<SaleItem> result = service.getSaleItemsBySaleId(12);

        assertEquals(List.of(item), result);
        verify(saleDAO).findSaleItemsBySaleId(connection, 12);
    }
}
