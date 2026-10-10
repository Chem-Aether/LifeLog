package com.lifelog.travel.train;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class TrainRouteSampleRequest {
    private String mode;
    private String trainNo;
    private LocalDate date;
    private List<String> stations;
}
