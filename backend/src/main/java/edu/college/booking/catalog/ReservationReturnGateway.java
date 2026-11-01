package edu.college.booking.catalog;

import java.time.Instant;
import java.util.List;

/** Implemented by booking module in the SAME datasource/transaction, not via remote HTTP.
 * lock() must lock the reservation; it must verify its immutable item/unit binding.
 * finish() must atomically record reviewer/reason and transition reservation state.
 */
public interface ReservationReturnGateway {
    ReturnRecord lock(long reservationId);
    List<ReturnRecord> queue(CatalogAccess.Actor actor,int page,int size);
    void finish(long reservationId,String status,String actor,String reason);
    record ReturnRecord(long id,long itemId,String itemName,long locationId,List<Long> unitIds,
                        String unitCode,String borrower,Instant submittedAt,String remark,
                        List<ItemImage> photos,String status) {}
}
