package com.lifelog.travel.transfer;

import com.lifelog.travel.flight.FlightRecord;
import com.lifelog.travel.flight.FlightRecordMapper;
import com.lifelog.travel.footprint.FootSpot;
import com.lifelog.travel.footprint.FootSpotMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Service
public class TravelExportService {
    private final FlightRecordMapper flights;
    private final FootSpotMapper footprints;

    public TravelExportService(FlightRecordMapper flights, FootSpotMapper footprints) {
        this.flights = flights;
        this.footprints = footprints;
    }

    public byte[] exportCategory(String type) throws IOException { return buildWorkbook(type, null, false); }
    public byte[] exportOne(String type, Long id) throws IOException { return buildWorkbook(type, id, false); }
    public byte[] exportTemplate(String type) throws IOException { return buildWorkbook(type, null, true); }

    private byte[] buildWorkbook(String type, Long id, boolean template) throws IOException {
        if (!"flight".equals(type) && !"footprint".equals(type)) throw new IllegalArgumentException("Excel 仅支持机票和足迹数据");
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            if ("flight".equals(type)) {
                List<? extends List<?>> rows = template ? List.of() : flights.selectList(null).stream()
                        .filter(item -> id == null || id.equals(item.getFlightId())).map(this::flightRow).toList();
                writeSheet(workbook, "机票", List.of("记录ID", "航班号", "航空公司", "机型", "飞机注册号", "出发机场ICAO", "出发机场", "出发航站楼", "登机方式", "起飞时间", "到达机场ICAO", "到达机场", "到达航站楼", "下机方式", "到达时间", "经停机场JSON", "座位号", "舱位类型", "里程KM", "备注"), rows, headerStyle);
            } else {
                List<? extends List<?>> rows = template ? List.of() : footprints.selectRecords(null).stream()
                        .filter(item -> id == null || id.equals(item.getFootprintId())).map(this::footprintRow).toList();
                writeSheet(workbook, "足迹", List.of("记录ID", "地点名称", "记录性质", "到访日期", "经度", "纬度", "POI引用", "旅行心得", "媒体ID列表JSON"), rows, headerStyle);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private List<?> flightRow(FlightRecord row) {
        return Arrays.asList(row.getFlightId(), row.getFlightNo(), row.getAirline(), row.getAircraftType(), row.getAircraftRegistration(),
                row.getDepartureIcao(), row.getDepartureAirportName(), row.getDepartureTerminal(), row.getBoardingMethod(), row.getDepartureTime(),
                row.getArrivalIcao(), row.getArrivalAirportName(), row.getArrivalTerminal(), row.getDeboardingMethod(), row.getArrivalTime(),
                row.getStopoversJson(), row.getSeatNo(), row.getCabinClass(), row.getDistanceKm(), row.getNote());
    }

    private List<?> footprintRow(FootSpot row) {
        return Arrays.asList(row.getFootprintId(), row.getPlaceName(), row.getVisitType(), row.getVisitDate(), row.getLongitude(),
                row.getLatitude(), row.getPoiReference(), row.getNote(), row.getMediaIdsJson());
    }

    private void writeSheet(Workbook workbook, String name, List<String> columns, List<? extends List<?>> rows, CellStyle style) {
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        for (int column = 0; column < columns.size(); column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(columns.get(column));
            cell.setCellStyle(style);
            sheet.setColumnWidth(column, Math.min(36, Math.max(14, columns.get(column).length() * 2 + 4)) * 256);
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            for (int column = 0; column < columns.size(); column++) {
                Object value = rows.get(rowIndex).get(column);
                row.createCell(column).setCellValue(value == null ? "" : String.valueOf(value));
            }
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(rows.size(), 1), 0, columns.size() - 1));
    }
}
