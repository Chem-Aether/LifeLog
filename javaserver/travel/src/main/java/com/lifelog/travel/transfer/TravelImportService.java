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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 解析旅行 Excel 文件并逐行导入航班或足迹数据。 */
@Service
public class TravelImportService {
    private final FlightRecordService flightService;
    private final FootSpotService footprintService;
    private final ObjectMapper objectMapper;

    /** 创建旅行数据导入服务。 */
    public TravelImportService(FlightRecordService flightService, FootSpotService footprintService, ObjectMapper objectMapper) {
        this.flightService = flightService;
        this.footprintService = footprintService;
        this.objectMapper = objectMapper;
    }

    /** 校验工作簿表头并导入记录，返回成功数及逐行错误。 */
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

    /** 按足迹模板列名解析 CSV，并沿用逐行新增或按 ID 更新流程。 */
    public Map<String, Object> importFootprintsCsv(MultipartFile file, boolean updateExisting) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择非空 CSV 文件");
        List<List<String>> rows = parseCsv(decodeCsv(file.getBytes()));
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV 文件缺少表头");
        Map<String, Integer> columns = new HashMap<>();
        List<String> header = rows.get(0);
        for (int i = 0; i < header.size(); i++) columns.put(header.get(i).trim().replaceFirst("^\\uFEFF", ""), i);
        if (!columns.containsKey("地点名称")) throw new IllegalArgumentException("CSV 表头无效，缺少“地点名称”列");

        List<String> errors = new ArrayList<>();
        int imported = 0;
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (row.stream().allMatch(String::isBlank)) continue;
            try {
                FootSpot spot = readFootprint(row, columns);
                Long id = csvLong(row, columns, "记录ID");
                if (updateExisting) {
                    if (id == null) throw new IllegalArgumentException("更新模式下记录ID不能为空");
                    footprintService.update(id, spot);
                } else footprintService.add(spot);
                imported++;
            } catch (Exception exception) {
                errors.add("足迹!" + (rowIndex + 1) + "行：" + rootMessage(exception));
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalImported", imported);
        report.put("flightsImported", 0);
        report.put("footprintsImported", imported);
        report.put("errors", errors);
        return report;
    }

    private FootSpot readFootprint(List<String> row, Map<String, Integer> columns) throws IOException {
        FootSpot spot = new FootSpot();
        spot.setPlaceName(csvText(row, columns, "地点名称"));
        spot.setVisitType(csvText(row, columns, "记录性质"));
        spot.setVisitDate(csvDate(row, columns, "到访日期"));
        spot.setLongitude(csvDecimal(row, columns, "经度"));
        spot.setLatitude(csvDecimal(row, columns, "纬度"));
        spot.setRegionId(csvText(row, columns, "行政区ID"));
        spot.setLocationType(csvText(row, columns, "地点类型"));
        spot.setNote(csvText(row, columns, "旅行心得"));
        String mediaUrls = csvText(row, columns, "媒体URL列表JSON");
        spot.setMediaUrls(mediaUrls == null ? List.of() : objectMapper.readValue(mediaUrls, new TypeReference<>() {}));
        return spot;
    }

    private String csvText(List<String> row, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null || index >= row.size()) return null;
        String value = row.get(index).trim();
        return value.isEmpty() ? null : value;
    }

    private Long csvLong(List<String> row, Map<String, Integer> columns, String name) {
        String value = csvText(row, columns, name);
        return value == null ? null : new BigDecimal(value).longValueExact();
    }

    private BigDecimal csvDecimal(List<String> row, Map<String, Integer> columns, String name) {
        String value = csvText(row, columns, name);
        return value == null ? null : new BigDecimal(value.replace(",", ""));
    }

    private LocalDate csvDate(List<String> row, Map<String, Integer> columns, String name) {
        String value = csvText(row, columns, name);
        if (value == null) return null;
        String normalized = value.replace('/', '-');
        try { return LocalDate.parse(normalized); }
        catch (Exception ignored) { return LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyy-M-d")); }
    }

    private String decodeCsv(byte[] bytes) throws IOException {
        int offset = 0;
        Charset charset = StandardCharsets.UTF_8;
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf) offset = 3;
        else if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe) { charset = StandardCharsets.UTF_16LE; offset = 2; }
        else if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xff) == 0xff) { charset = StandardCharsets.UTF_16BE; offset = 2; }
        try {
            return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
        } catch (CharacterCodingException exception) {
            if (charset != StandardCharsets.UTF_8) throw exception;
            return Charset.forName("GB18030").decode(ByteBuffer.wrap(bytes)).toString();
        }
    }

    private List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                else if (ch == '"') quoted = false;
                else field.append(ch);
            } else if (ch == '"' && field.isEmpty()) quoted = true;
            else if (ch == ',') { row.add(field.toString()); field.setLength(0); }
            else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0); rows.add(row); row = new ArrayList<>();
            } else field.append(ch);
        }
        if (quoted) throw new IllegalArgumentException("CSV 引号未闭合");
        if (!row.isEmpty() || !field.isEmpty()) { row.add(field.toString()); rows.add(row); }
        return rows;
    }

    /** 将 Excel 行转换为航班记录。 */
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
        record.setCabinClass(text(row, h, "舱位类型", formatter));
        record.setDistanceKm(decimal(row, h, "里程KM", formatter)); record.setNote(text(row, h, "备注", formatter));
        String stopovers = text(row, h, "经停机场JSON", formatter);
        record.setStopovers(parseStopovers(stopovers));
        return record;
    }

    /** 解析 JSON 或分隔文本形式的经停机场列表。 */
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

    /** 将 Excel 行转换为足迹记录。 */
    private FootSpot readFootprint(Row row, Map<String, Integer> h, DataFormatter formatter) {
        FootSpot spot = new FootSpot();
        spot.setPlaceName(text(row, h, "地点名称", formatter)); spot.setVisitType(text(row, h, "记录性质", formatter));
        spot.setVisitDate(date(row, h, "到访日期", formatter)); spot.setLongitude(decimal(row, h, "经度", formatter));
        spot.setLatitude(decimal(row, h, "纬度", formatter)); spot.setRegionId(text(row, h, "行政区ID", formatter));
        spot.setLocationType(text(row, h, "地点类型", formatter));
        spot.setNote(text(row, h, "旅行心得", formatter));
        String mediaUrls = text(row, h, "媒体URL列表JSON", formatter);
        try {
            spot.setMediaUrls(mediaUrls == null ? List.of() : objectMapper.readValue(mediaUrls, new TypeReference<>() {}));
        } catch (IOException e) {
            throw new IllegalArgumentException("媒体URL列表JSON格式无效", e);
        }
        return spot;
    }

    /** 将工作表表头映射为列名和列索引。 */
    private Map<String, Integer> headers(Row row, DataFormatter formatter) {
        Map<String, Integer> result = new HashMap<>();
        for (Cell cell : row) result.put(formatter.formatCellValue(cell).trim(), cell.getColumnIndex());
        return result;
    }
    /** 按列名读取并清理文本单元格。 */
    private String text(Row row, Map<String, Integer> columns, String name, DataFormatter formatter) {
        Integer index = columns.get(name);
        if (index == null || row.getCell(index) == null) return null;
        String value = formatter.formatCellValue(row.getCell(index)).trim();
        return value.isEmpty() ? null : value;
    }
    /** 判断工作表行是否没有有效单元格内容。 */
    private boolean isBlank(Row row, DataFormatter formatter) {
        if (row == null) return true;
        for (Cell cell : row) if (!formatter.formatCellValue(cell).isBlank()) return false;
        return true;
    }
    /** 读取并解析十进制数值单元格。 */
    private BigDecimal decimal(Row row, Map<String, Integer> columns, String name, DataFormatter formatter) {
        String value = text(row, columns, name, formatter);
        return value == null ? null : new BigDecimal(value.replace(",", ""));
    }
    /** 读取并精确解析长整型单元格。 */
    private Long longValue(Row row, Map<String, Integer> columns, String name, DataFormatter formatter) {
        String value = text(row, columns, name, formatter);
        return value == null ? null : new BigDecimal(value).longValueExact();
    }
    /** 解析支持多种分隔符的日期时间文本。 */
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
    /** 解析 ISO 或年月日格式的日期文本。 */
    private LocalDate date(Row row, Map<String, Integer> columns, String name, DataFormatter formatter) {
        String value = text(row, columns, name, formatter);
        if (value == null) return null;
        String normalized = value.trim().replace('/', '-');
        try { return LocalDate.parse(normalized); }
        catch (Exception ignored) { return LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyy-M-d")); }
    }
    /** 获取异常链最深层的错误消息。 */
    private String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? "数据格式无效" : cause.getMessage();
    }
}
