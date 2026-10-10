package com.lifelog.travel.ticket;

import com.lifelog.common.api.Result;
import com.lifelog.travel.ticket.TravelTicketQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/travel")
@Tag(name = "统计接口", description = "航班/火车/票务相关接口")
public class TravelTicketController {

    @Autowired
    private TravelTicketQueryService ticketService;

    @GetMapping("/tickets")
    @Operation(summary = "查询车票、机票")
    public ResponseEntity<Result> findAll(@RequestParam String mode, @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(
                Result.success(ticketService.findTickets(mode, year))
        );
    }

    @GetMapping("/tickets/summary")
    @Operation(summary = "查询票据汇总")
    public ResponseEntity<Result> summary(@RequestParam String mode, @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(
                Result.success(ticketService.getTicketSummary(mode, year))
        );
    }
}
