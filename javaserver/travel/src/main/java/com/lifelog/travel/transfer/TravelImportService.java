package com.lifelog.travel.transfer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifelog.travel.flight.FlightRecord;
import com.lifelog.travel.flight.FlightRecordService;
import com.lifelog.travel.footprint.FootSpot;
import com.lifelog.travel.footprint.FootSpotService;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TravelImportService {
    private final FlightRecordService flightService;
    private final FootSpotService footprintService;
    private final ObjectMapper objectMapper;

    public TravelImportService(FlightRecordService flightService, FootSpotService footprintService, ObjectMapper objectMapper) {
        this.flightService = flightService;
        this.footprintService = footprintService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> importWorkbook(MultipartFile file, boolean updateExisting, String type) throws IOException {
        if (!"flight".equals(type) && !"footprint".equals(type)) throw new IllegalArgumentException("Excel 仅支持机票和足迹数据");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择非空 Excel 文件");
        Workbook opened;
        try {
            opened = WorkbookFactory.create(file.getInputStream());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("无法读取 Excel 工作簿，请确认文件未损坏且未加密");
        }

        List<String> errors = new ArrayList<>();
        int imported = 0;
        String sheetName = "flight".equals(type) ? "机票" : "足迹";
        try (Workbook workbook = opened) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null || sheet.getRow(0) == null) throw new IllegalArgumentException("工作簿中缺少“" + sheetName + "”工作表或表头");
            DataFormatter formatter = new DataFormatter(Locale.SIMPLIFIED_CHINESE);
            Map<String, Integer> columns = headers(sheet.getRow(0), formatter);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlank(row, formatter)) continue;
                try {
                    if ("flight".equals(type)) {
                        FlightRecord record = readFlight(row, columns, formatter);
                        Long id = longValue(row, columns, "记录ID", formatter);
                        if (updateExisting) {
                            if (id == null) throw new IllegalArgumentException("更新模式下记录ID不能为空");
                            flightService.update(id, record);
                        } else flightService.save(record);
                    } else {
                        FootSpot spot = readFootprint(row, columns, formatter);
                        Long id = longValue(row, columns, "记录ID", formatter);
                        if (updateExisting) {
                            if (id == null) throw new IllegalArgumentException("更新模式下记录ID不能为空");
                            footprintService.update(id, spot);
                        } else footprintService.add(spot);
                    }
                    imported++;
                } catch (Exception exception) {
                    errors.add(sheetName + "!" + (rowIndex + 1) + "行：" + rootMessage(exception));
                }
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalImported", imported);
        report.put("flightsImported", "flight".equals(type) ? imported : 0);
        report.put("footprintsImported", "footprint".equals(type) ? imported : 0);
        report.put("errors", errors);
        return report;
    }

    private FlightRecord readFlight(Row row, Map<String, Integer> h, DataFormatter formatter) throws IOException {
        FlightRecord record = new FlightRecord();
        record.setFlightNo(text(row, h, "航班号", formatter)); record.setAirline(text(row, h, "航空公司", formatter));
        record.setAircraftType(text(row, h, "机型", formatter)); record.setAircraftRegistration(text(row, h, "飞机注册号", formatter));
        record.setDepartureIcao(text(row, h, "出发机场ICAO", formatter)); record.setDepartureAirportName(text(row, h, "出发机场", formatter));
        record.setDepartureTerminal(text(row, h, "出发航站楼", formatter)); record.setBoardingMethod(text(row, h, "登机方式", formatter));
        record.setDepartureTime(dateTime(row, h, "起飞时间", formatter));
        record.setArrivalIcao(text(row, h, "到达机场ICAO", formatter)); record.setArrivalAirportName(text(row, h, "到达机场", formatter));
        record.setArrivalTerminal(text(row, h, "到达航站楼", formatter)); record.setDeboardingMethod(text(row, h, "下机方式", formatter));
        record.setArrivalTime(dateTime(row, h, "到达时间", formatter)); record.setSeatNo(text(row, h, "座位号", formatter));
        record.setDistanceKm(decimal(row, h, "里程KM", formatter)); record.setNote(text(row, h, "备注", formatter));
        String stopovers = text(row, h, "经停机场JSON", formatter);
        record.setStopovers(parseStopovers(stopovers));
        return record;
    }

    private List<Map<String, Object>> parseStopovers(String value) {
        if (value == null) return List.of();
        String plainValue = value.trim();
        if (plainValue.isEmpty()) return List.of();
        try {
            var json = objectMapper.readTree(plainValue);
            if (json == null || json.isNull()) return List.of();
            if (json.isArray()) return objectMapper.convertValue(json, new TypeReference<>() {});
            if (json.isObject()) return List.of(objectMapper.convertValue(json, new TypeReference<>() {}));
            if (json.isTextual()) plainValue = json.textValue();
        } catch (IOException ignored) {
            // Spreadsheet cells may contain a single airport code/name instead of JSON.
        }

        List<Map<String, Object>> stopovers = new ArrayList<>();
        String[] airports = plainValue.split("[,，;；、\\r\\n]+");
        for (String airport : airports) {
            String item = airport.trim();
            if (item.isEmpty()) continue;
            Map<String, Object> stopover = new LinkedHashMap<>();
            stopover.put("sequence", stopovers.size() + 1);
            if (item.matches("(?i)[a-z0-9]{3,4}")) stopover.put("icao", item.toUpperCase(Locale.ROOT));
            else stopover.put("name", item);
            stopovers.add(stopover);
        }
        return stopovers;
    }

    private FootSpot readFootprint(Row row, Map<String, Integer> h, DataFormatter formatter) {
        FootSpot spot = new FootSpot();
        spot.setPlaceName(text(row, h, "地点名称", formatter)); spot.setVisitType(text(row, h, "记录性质", formatter));
        spot.setVisitDate(date(row, h, "到访日期", formatter)); spot.setLongitude(decimal(row, h, "经度", formatter));
        spot.setLatitude(decimal(row, h, "纬度", formatter)); spot.setPoiReference(text(row, h, "POI引用", formatter));
        spot.setNote(text(row, h, "旅行心得", formatter));
        String mediaIds = text(row, h, "媒体ID列表JSON", formatter);
        try {
            spot.setMediaIds(mediaIds == null ? List.of() : objectMapper.readValue(mediaIds, new TypeReference<>() {}));
        } catch (IOException e) {
            throw new IllegalArgumentException("媒体ID列表JSON格式无效", e);
        }
        return spot;
    }

    private Map<String, Integer> headers(Row row, DataFormatter formatter) {
        Map<String, Integer> result = new HashMap<>();
        for (Cell cell : row) result.put(formatter.formatCellValue(cell).trim(), cell.getColumnIndex());
        return result;
    }
    private String text(Row row, Map<String, Integer> columns, String name, DataFormatter formatter) {
        Integer index = columns.get(name);
        if (index == null || row.getCell(index) == null) return null;
        String value = formatter.formatCellValue(row.getCell(index)).trim();
        return value.isEmpty() ? null : value;
    }
    private boolean isBlank(Row row, DataFormatter formatter) {
        if (row == null) return true;
        for (Cell cell : row) if (!formatter.formatCellValue(cell).isBlank()) return false;
        return true;
    }
    private BigDecimal decimal(Row row, Map<String, Integer> columns, String name, DataFormatter formatter) {
        String value = text(row, columns, name, formatter);
        return value == null ? null : new BigDecimal(value.replace(",", ""));
    }
    private Long longValue(Row row, Map<String, Integer> columns, String name, DataFormatter formatter) {
        String value = text(row, columns, name, formatter);
        return value == null ? null : new BigDecimal(value).longValueExact();
    }
    private LocalDateTime dateTime(Row row, Map<String, Integer> columns, String name, DataFormatter formatter) {
        String value = text(row, columns, name, formatter);
        if (value == null) return null;
        String normalized = value.trim().replace('/', '-').replace(' ', 'T');
        try { return LocalDateTime.parse(normalized); }
        catch (Exception ignored) {
            for (String pattern : List.of("yyyy-M-d'T'H:mm[:ss]", "yyyy-M-d-H:mm[:ss]")) {
                try {
                    return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern(pattern));
                } catch (Exception ignoredPattern) {
                    // Try the next supported date/time separator.
                }
            }
            throw new IllegalArgumentException("日期时间格式应为 yyyy-M-d HH:mm 或 yyyy-M-d-HH:mm[:ss]");
        }
    }
    private LocalDate date(Row row, Map<String, Integer> columns, String name, DataFormatter formatter) {
        String value = text(row, columns, name, formatter);
        if (value == null) return null;
        String normalized = value.trim().replace('/', '-');
        try { return LocalDate.parse(normalized); }
        catch (Exception ignored) { return LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyy-M-d")); }
    }
    private String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? "数据格式无效" : cause.getMessage();
    }
}
